package net.jackcooper.shapeShifterCurseAddon.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;

public class FoxFireBurnEffect extends StatusEffect {
	public FoxFireBurnEffect() {
		super(StatusEffectCategory.HARMFUL, 0x3366FF); // Blue color
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return true;
	}

	private static DamageSource damageSource(LivingEntity entity) {
		FoxFireBurnOwnership origin = FoxFireBurnOwnership.resolve(entity);
		if (origin.recorded()) {
			PlayerEntity owner = origin.uuid() == null ? null : entity.getWorld().getPlayerByUuid(origin.uuid());
			// 已知归属离线或旧记录有歧义时，保留火焰伤害，不转嫁给旧施法者或最近攻击者。
			return owner == null ? entity.getDamageSources().inFire() : entity.getDamageSources().playerAttack(owner);
		}
		if (entity.getLastAttacker() instanceof PlayerEntity player) return entity.getDamageSources().playerAttack(player);
		if (entity.getAttacker() instanceof PlayerEntity player) return entity.getDamageSources().playerAttack(player);
		return entity.getDamageSources().inFire();
	}

	@Override
	public void applyUpdateEffect(LivingEntity entity, int amplifier) {
		if (entity.hasStatusEffect(SscAddon.PURIFIED)) {
			entity.removeStatusEffect(SscAddon.FOX_FIRE_BURN);
			return;
		}

		if (entity.getWorld().isClient) {
			// Client side particles can be handled here or via separate client tick handler
			// But applyUpdateEffect runs on both usually if registered correctly.
			// For simple visuals, we might rely on server spawning particles or client side implementation.
			// StatusEffect particles are usually handled by the game automatically if color is set,
			// but we want Soul Fire particles.
			entity.getWorld().addParticle(ParticleTypes.SOUL_FIRE_FLAME, true,
					entity.getX() + (entity.getRandom().nextDouble() - 0.5) * entity.getWidth(),
					entity.getY() + entity.getRandom().nextDouble() * entity.getHeight(),
					entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * entity.getWidth(),
					0, 0, 0);
		} else {
			// Server side logic
			// Damage every second (20 ticks) like vanilla fire
			if (entity.age % 20 == 0) {
				DamageSource source = damageSource(entity);

				net.minecraft.util.math.Vec3d oldVelocity = entity.getVelocity();
				DamageSource burnSource = source;
				net.jackcooper.shapeShifterCurseAddon.spell.DomainManager.runAttachedEffect(burnSource.getAttacker(), entity, () -> {
					if (net.jackcooper.shapeShifterCurseAddon.ability.KillEmpowerManager.damageBurn(entity, burnSource, 1.0f)) {
						entity.setVelocity(oldVelocity);
					}
				});

				// Spawn explicit particles on server for everyone to see
				if (entity.getWorld() instanceof ServerWorld serverWorld) {
					net.jackcooper.shapeShifterCurseAddon.util.ParticleUtils.spawnParticles(serverWorld, ParticleTypes.SOUL_FIRE_FLAME,
							entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ(),
							2, 0.3, 0.3, 0.3, 0.05);
				}
			}
		}
	}
}
