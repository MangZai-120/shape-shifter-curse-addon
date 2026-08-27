package net.jackcooper.shapeShifterCurseAddon.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

import java.util.Random;

/**
 * 荧光幼灵被动③——黏液保护膜闪避（由 FluorescentDodgeMixin 迁移到官方 {@link ServerLivingEntityEvents#ALLOW_DAMAGE}）。
 * <p>荧光幼灵形态受到【物理攻击】伤害时 20% 概率完全闪避（取消伤害 + 水花粒子 + 音效）。
 * 仅物理类伤害（近战/弹射物/荆棘/仙人掌等）生效；魔法/火焰/岩浆/窒息/饥饿/虚空等不闪避。
 * <p>{@code ALLOW_DAMAGE} 仅服务端触发、返回 false 即取消伤害，语义等价于原 mixin 在 damage 返回 false，
 * 且天然满足原 mixin 的「仅服务端」判定。
 */
public final class FluorescentDodgeHandler {

	private static final float DODGE_CHANCE = 0.20f;
	private static final Random DODGE_RNG = new Random();

	private FluorescentDodgeHandler() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			// 仅玩家、荧光幼灵形态、物理类伤害
			if (!(entity instanceof PlayerEntity self)) {
				return true;
			}
			if (!FormUtils.isAxolotlFluorescent(self)) {
				return true;
			}
			if (!isPhysicalDamage(source)) {
				return true;
			}
			if (DODGE_RNG.nextFloat() < DODGE_CHANCE) {
				// 闪避成功：取消伤害 + 水花反馈
				if (self.getWorld() instanceof ServerWorld sw) {
					sw.spawnParticles(ParticleTypes.SPLASH, self.getX(), self.getY() + 1.0, self.getZ(), 16, 0.4, 0.6, 0.4, 0.3);
					sw.spawnParticles(ParticleTypes.BUBBLE, self.getX(), self.getY() + 1.0, self.getZ(), 8, 0.4, 0.6, 0.4, 0.1);
					sw.playSound(null, self.getX(), self.getY() + 1.0, self.getZ(),
							SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.PLAYERS, 0.6f, 1.5f);
				}
				return false;
			}
			return true;
		});
	}

	/** 判定是否为物理类伤害（可被闪避）。 */
	private static boolean isPhysicalDamage(DamageSource source) {
		String id = source.getType().msgId();
		// 物理攻击/弹射物/荆棘/仙人掌/飞镖等；排除魔法/火焰/岩浆/药水/窒息/饥饿/虚空/溺水/干渴
		return "mob".equals(id) || "player".equals(id) || "sting".equals(id)
				|| "arrow".equals(id) || "trident".equals(id) || "fireball".equals(id)
				|| "thrown".equals(id) || "thorns".equals(id) || "cactus".equals(id)
				|| "sweetBerryBush".equals(id) || "stalagmite".equals(id) || "fallingStalactite".equals(id)
				|| (source.getSource() != null && source.getSource().getType().isIn(EntityTypeTags.ARROWS));
	}
}
