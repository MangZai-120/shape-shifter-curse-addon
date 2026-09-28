package net.jackcooper.shapeShifterCurseAddon.power;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.Active;
import io.github.apace100.apoli.power.ActiveCooldownPower;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.util.HudRender;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns;
import net.minecraft.server.network.ServerPlayerEntity;
import net.jackcooper.shapeShifterCurseAddon.util.TrinketUtils;

import java.util.List;

public class TrueInvisibilityAbilityPower extends ActiveCooldownPower implements net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownHolder {

	private final net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec spec;
	private final int configuredCooldownTicks;
	private final int effectDuration;
	// Internal cooldown tracking (separate from parent class)
	private long internalCooldownEndTime = 0;
	private long activeCastId = -1;
	private int gracePeriodTicks = 0;
	private int lastAmplifier = 0;

	private boolean wasInvisible = false;
	private boolean wasUsingItem = false;
	private boolean wasHandSwinging = false;

	public TrueInvisibilityAbilityPower(PowerType<?> type, LivingEntity entity,
	                                    net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec spec,
	                                    int effectDuration, HudRender hudRender, Active.Key key) {
		super(type, entity, Math.max(1, spec.cooldown()), hudRender, (e) -> {
		});
		this.spec = spec;
		this.configuredCooldownTicks = spec.cooldown();
		this.effectDuration = effectDuration;
		this.setKey(key);
		this.setTicking(true);
	}

	@Override
	public net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec cooldownSpec() {
		return spec;
	}

	public static PowerFactory<Power> createFactory() {
		return new PowerFactory<>(new Identifier("my_addon", "true_invisibility"),
				net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec.addFields(new SerializableData()
						.add("duration", SerializableDataTypes.INT, 100)
						.add("hud_render", ApoliDataTypes.HUD_RENDER, HudRender.DONT_RENDER)
						.add("key", ApoliDataTypes.BACKWARDS_COMPATIBLE_KEY, new Active.Key()),
						240, net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager.START_ON_END),
				data -> {
					var spec = net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec.read(data);
					return (type, player) -> new TrueInvisibilityAbilityPower(type, player, spec,
							data.getInt("duration"), data.get("hud_render"), data.get("key"));
				}
		).allowCondition();
	}

	private boolean hasInvisibilityCloak() {
		return TrinketUtils.isWearing(entity, SscAddon.INVISIBILITY_CLOAK);
	}

	public int getEffectDuration() {
		if (hasInvisibilityCloak()) {
			return this.effectDuration + 40; // Add 2 seconds (40 ticks)
		}
		return this.effectDuration;
	}

	@Override
	public void tick() {
		super.tick();

		if (entity == null || entity.getWorld().isClient) return;
		if (entity.isDead()) {
			interruptCast();
			return;
		}

		if (entity.hasStatusEffect(SscAddon.PURIFIED)) {
			if (entity.hasStatusEffect(SscAddon.PRE_INVISIBILITY)) {
				entity.removeStatusEffect(SscAddon.PRE_INVISIBILITY);
				entity.removeStatusEffect(StatusEffects.INVISIBILITY);
				interruptCast();
			}
			if (entity.hasStatusEffect(SscAddon.TRUE_INVISIBILITY)) {
				breakInvisibility(false);
			}
			wasInvisible = false;
			return;
		}

		boolean isInvisible = entity.hasStatusEffect(SscAddon.TRUE_INVISIBILITY);
		boolean isPrecasting = entity.hasStatusEffect(SscAddon.PRE_INVISIBILITY);

		// 蓄力被其他来源清除也必须结算失败，不能遗留门禁。
		if (!isInvisible && !isPrecasting) applyUniversalCooldown();
		// 保留自然结束的音效；冷却结算本身是幂等的。
		if (wasInvisible && !isInvisible && !isPrecasting && lastAmplifier == 0 && !entity.isDead()) {
			applyUniversalCooldown();
			// Play glass break sound for natural expiration
			entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
					SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
		}

		if (!isInvisible) {
			wasUsingItem = false;
			wasHandSwinging = false;
			wasInvisible = false;
			gracePeriodTicks = 5; // Reset grace period when not invisible
			lastAmplifier = 0; // Reset amplifier tracking
			return;
		}

		// Track current amplifier
		StatusEffectInstance currentEffect = entity.getStatusEffect(SscAddon.TRUE_INVISIBILITY);
		if (currentEffect != null) {
			lastAmplifier = currentEffect.getAmplifier();
		}

		// Decrease grace period if > 0
		if (gracePeriodTicks > 0) {
			gracePeriodTicks--;
			// Update previous states to prevent immediate break after grace period
			wasUsingItem = entity.isUsingItem();
			wasHandSwinging = entity.handSwinging;
			wasInvisible = isInvisible;
			return;
		}

		// Particles while invisible
		if (entity.getRandom().nextFloat() < 0.07f) {
			ServerWorld serverWorld = (ServerWorld) entity.getWorld();
			net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils.spawnParticles(serverWorld, net.minecraft.particle.ParticleTypes.SQUID_INK,
					entity.getX(), entity.getY() + entity.getHeight() * 0.5, entity.getZ(),
					1, 0.3, 0.5, 0.3, 0.05);
		}

		// Check for actions that break invisibility
		boolean shouldBreak = false;

		// 1. Using item
		boolean isUsingItem = entity.isUsingItem();
		if (isUsingItem && !wasUsingItem) shouldBreak = true;
		wasUsingItem = isUsingItem;

		// 2. Hand swinging
		boolean isHandSwinging = entity.handSwinging;
		if (isHandSwinging && !wasHandSwinging) shouldBreak = true;
		wasHandSwinging = isHandSwinging;

		if (shouldBreak) {
			breakInvisibility(false); // false = action break (glass break sound)
			return;
		}

		wasInvisible = isInvisible;
	}

	/**
	 * Check if internal cooldown is ready
	 * 使用服务端tick，保证多人一致性
	 */
	public boolean isInternalCooldownReady() {
		if (entity instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
			return net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns.ready(sp, powerIdentifier());
		}
		return entity.getWorld().getTime() >= internalCooldownEndTime;
	}

	/** 蓄力效果成功转为真实隐身时调用。 */
	public void onInvisibilityReleased() {
		if (!(entity instanceof ServerPlayerEntity player) || activeCastId < 0) return;
		SkillCastManager.get(player.getServerWorld()).released(activeCastId, SkillCastManager.now(player));
		wasInvisible = true;
		lastAmplifier = 0;
		gracePeriodTicks = 5;
	}

	private void interruptCast() {
		if (!(entity instanceof ServerPlayerEntity player) || activeCastId < 0) return;
		SkillCastManager.get(player.getServerWorld()).interrupt(activeCastId, SkillCastManager.now(player));
		activeCastId = -1;
		internalCooldownEndTime = entity.getWorld().getTime() + SkillCooldowns.remaining(player, powerIdentifier());
	}

	/** 隐身结束：只结算本次施放，重复破隐不能重置已起算的 CD。 */
	public void applyUniversalCooldown() {
		if (!(entity instanceof ServerPlayerEntity serverPlayer) || activeCastId < 0) return;
		SkillCastManager.get(serverPlayer.getServerWorld()).interrupt(activeCastId, SkillCastManager.now(serverPlayer));
		activeCastId = -1;
		internalCooldownEndTime = entity.getWorld().getTime() + SkillCooldowns.remaining(serverPlayer, powerIdentifier());

		// Also set dash ability cooldown
		List<TrueInvisibilityDashAbilityPower> dashPowers = PowerHolderComponent.getPowers(entity, TrueInvisibilityDashAbilityPower.class);
		for (TrueInvisibilityDashAbilityPower dashPower : dashPowers) {
			dashPower.applyInternalCooldown();
		}
	}

	/**
	 * Breaks invisibility with appropriate sound effect
	 *
	 * @param byKey true if broken by pressing the key again (cat hiss), false if broken by action (glass break)
	 */
	public void breakInvisibility(boolean byKey) {
		if (entity == null || entity.getWorld().isClient) return;

		if (!entity.hasStatusEffect(SscAddon.TRUE_INVISIBILITY)) return;

		// Check amplifier before removing
		StatusEffectInstance currentEffect = entity.getStatusEffect(SscAddon.TRUE_INVISIBILITY);
		int currentAmp = (currentEffect != null) ? currentEffect.getAmplifier() : 0;

		// Remove invisibility effect
		entity.removeStatusEffect(SscAddon.TRUE_INVISIBILITY);
		entity.removeStatusEffect(StatusEffects.INVISIBILITY);
		wasInvisible = false;

		ServerWorld serverWorld = (ServerWorld) entity.getWorld();

		// player.sendMessage(Text.of("§c隐身被打破!"), true);
		if (byKey) {
			// Key Cancel: Cat Hiss
			serverWorld.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
					SoundEvents.ENTITY_CAT_HISS, SoundCategory.PLAYERS, 1.0f, 1.0f);

			// Add Buffs: Guaranteed Crit & Speed II for 5 seconds
			entity.addStatusEffect(new StatusEffectInstance(SscAddon.GUARANTEED_CRIT, 100, 0, false, false, true));
			entity.addStatusEffect(new StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SPEED, 100, 1, false, false, true));

		} else {
			// Action Break: Glass Break
			serverWorld.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
					SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
		}

		// Apply universal 12s cooldown ONLY when breaking invisibility AND it was the main ability (Amp 0)
		if (currentAmp == 0) {
			applyUniversalCooldown();
		}
	}

	@Override
	public boolean canUse() {
		// Always allow use - we handle cooldown logic in onUse
		return true;
	}

	@Override
	public void onUse() {
        if (entity instanceof net.minecraft.server.network.ServerPlayerEntity syncPlayer
                && !net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.isPlayerReady(syncPlayer)) return;
		if (entity == null || entity.getWorld().isClient) return;

		boolean isInvisible = entity.hasStatusEffect(SscAddon.TRUE_INVISIBILITY);
		boolean isPrecasting = entity.hasStatusEffect(SscAddon.PRE_INVISIBILITY);

		if (isInvisible) {
			// Already invisible - pressing key again cancels with cat hiss
			breakInvisibility(true); // true = key break (cat hiss)
		} else if (isPrecasting) {
			// Currently casting - do nothing
		} else {
			// Not invisible - try to cast
			if (entity instanceof ServerPlayerEntity player && isInternalCooldownReady()
					&& SkillCooldowns.begin(player, powerIdentifier())) {
				activeCastId = SkillCastManager.get(player.getServerWorld()).control(player.getUuid(), powerIdentifier()).castId;
				// 冻结本次斗篷档位；后续破隐不能重启 on_cast/on_release 的计时。
				SkillCooldowns.retune(player, powerIdentifier(), hasInvisibilityCloak() ? spec.extra("cloak") : configuredCooldownTicks);
				// Apply pre-invisibility (casting phase)
				if (!entity.addStatusEffect(new StatusEffectInstance(SscAddon.PRE_INVISIBILITY, 20, 0, false, false, true))) {
					interruptCast();
				}
			}
		}
	}

	/** 本 power 的稳定技能 ID（= power 注册路径，统一冷却服务存储键）。 */
	public String powerIdentifier() {
		return type != null && type.getIdentifier() != null
				? type.getIdentifier().toString() : "my_addon:true_invisibility";
	}
}
