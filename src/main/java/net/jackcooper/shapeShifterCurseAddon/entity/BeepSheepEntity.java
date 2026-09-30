package net.jackcooper.shapeShifterCurseAddon.entity;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/**
 * 月尘魔法·咩弹（召唤系「羊了个羊 Beep Sheep」，jackcooper，2026-09-29 用户定稿）。
 *
 * <p><b>飞行轨迹与鸡蛋一致</b>：继承 {@link ThrownItemEntity} 保留默认重力（0.03 阻力 + 0.05 重力），
 * 呈标准抛物线。命中活体后将其<b>变成羊</b>（施加 {@link SscAddon#SHEEP_FORM} 效果，
 * 客户端由 SheepFormClientHooks 用原版羊模型替换渲染），期间目标无法攻击 / 无法破坏 / 无法放置方块。</p>
 *
 * <p><b>目标限制（2026-09-29 用户定稿改血量门）</b>：Boss 类（凋灵/监守者）与<b>最大生命值
 * 超过 50 点</b>的目标免疫（原「碰撞箱宽>1.2 或高>2.0」体型门已移除——mod NPC 普遍身高
 * 超 2.0 被误杀，导致「NPC 生物无法变成羊」；血量门天然覆盖村民 20 血/多数 NPC，同时仍
 * 挡住铁傀儡 100 血/末影龙 200 血/凋灵 300 血等大块头）。</p>
 *
 * <p>默认白名单：主人在线且目标受保护（玩家及其宠物/召唤物）则不施加。命中 Boss 免疫体时
 * 弹体反被「羊化反弹」演出（咩叫 + 粒子），不施加效果。</p>
 */
public class BeepSheepEntity extends ThrownItemEntity {

	/** 血量门阈值（2026-09-29 用户定稿）：最大生命值 > 50 点的目标免疫变羊。 */
	private static final float MAX_TARGET_HEALTH = 50.0F;

	/** 变羊时长（tick），DataTracker 无需——仅服务端结算用，NBT 持久化。 */
	private int sheepDuration = 120;
	/** 魔法等级（1-5）。 */
	private int spellLevel = 1;
	/** 书内施法编号（solo 为 null；流派命中返还用）。 */
	private java.util.UUID refundCastId;

	public void setRefundCastId(java.util.UUID castId) {
		refundCastId = castId;
	}

	public BeepSheepEntity(EntityType<? extends BeepSheepEntity> entityType, World world) {
		super(entityType, world);
	}

	public BeepSheepEntity(World world, LivingEntity owner) {
		super(SscAddon.BEEP_SHEEP_ENTITY, owner, world);
	}

	@Override
	protected Item getDefaultItem() {
		// 渲染物品：白色染料最接近「羊」意象（小白色方块弹道，FlyingItemEntityRenderer 直接用）
		return Items.WHITE_DYE;
	}

	@Override
	public void tick() {
		super.tick();
		// 飞行拖尾（服务端撒，天然多人同步）：白色羊毛絮 + 轻微咩概率音不刷（弹道安静，命中才叫）
		if (this.getWorld() instanceof ServerWorld serverWorld && this.age % 2 == 0) {
			serverWorld.spawnParticles(ParticleTypes.WHITE_ASH,
					this.getX(), this.getY(), this.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
		}
		// 超时自毁（8 秒；鸡蛋式抛物线落地会触发 onCollision，此为兜底）
		if (this.age > 160) {
			this.discard();
		}
	}

	@Override
	protected void onEntityHit(EntityHitResult entityHitResult) {
		super.onEntityHit(entityHitResult);
		Entity target = entityHitResult.getEntity();
		if (this.getWorld().isClient || !(target instanceof LivingEntity livingTarget)) {
			return;
		}
		// 默认白名单：主人在线且目标受保护 → 不施加（弹体消散）
		if (this.getOwner() instanceof ServerPlayerEntity ownerPlayer
				&& WhitelistUtils.isProtected(ownerPlayer, livingTarget)) {
			return;
		}
		// Boss / 大型生物免疫（用户定稿：不允许变 boss 类生物，只能变小型生物）
		if (isImmuneTarget(livingTarget)) {
			// 免疫演出：咩一声 + 粒子弹开（幽默反馈：羊魔法对大块头无效）
			this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.ENTITY_SHEEP_AMBIENT, SoundCategory.PLAYERS, 1.0f, 0.6f);
			if (this.getWorld() instanceof ServerWorld serverWorld) {
				serverWorld.spawnParticles(ParticleTypes.SNEEZE,
						target.getX(), target.getBodyY(0.5), target.getZ(), 8, 0.3, 0.4, 0.3, 0.05);
			}
			return;
		}
		// 施加变羊：amplifier = 等级-1（L1=0 … L5=4，供 lang/扩展显示）
		boolean hadHarmfulEffect = net.jackcooper.shapeShifterCurseAddon.spell.FormCastingStyle.hasHarmfulEffect(livingTarget);
		boolean applied = livingTarget.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
				SscAddon.SHEEP_FORM, sheepDuration, spellLevel - 1, false, true, true), this.getOwner());
		if (applied) {
			// 登记「受惊逃离源」（2026-09-29 用户定稿：变羊 NPC 的羊 AI 默认受惊远离施法者）
			net.jackcooper.shapeShifterCurseAddon.effect.SheepFormAiController.setFleeFrom(
					livingTarget.getUuid(), this.getOwner() != null ? this.getOwner().getUuid() : null);
		}
		if (applied && this.getOwner() instanceof ServerPlayerEntity caster) {
			net.jackcooper.shapeShifterCurseAddon.spell.FormCastingStyle.onSpellHit(caster, livingTarget,
					net.jackcooper.shapeShifterCurseAddon.spell.FormationElement.SUMMON, refundCastId, hadHarmfulEffect);
		}
		// 变身演出：咩！+ 羊毛爆散
		this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.ENTITY_SHEEP_AMBIENT, SoundCategory.PLAYERS, 1.0f, 1.0f);
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.spawnParticles(ParticleTypes.POOF,
					target.getX(), target.getBodyY(0.6), target.getZ(), 12, 0.35, 0.5, 0.35, 0.03);
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (!this.getWorld().isClient) {
			// 落地演出：小团羊毛尘（不咩叫——只命中活体才叫）
			if (this.getWorld() instanceof ServerWorld serverWorld) {
				serverWorld.spawnParticles(ParticleTypes.POOF,
						this.getX(), this.getY(), this.getZ(), 5, 0.15, 0.15, 0.15, 0.02);
			}
			this.discard();
		}
	}

	@Override
	protected boolean canHit(Entity entity) {
		// 排除盔甲架与施法者本人（同诅咒标记弹）
		return super.canHit(entity) && entity != this.getOwner()
				&& entity instanceof LivingEntity && !(entity instanceof ArmorStandEntity);
	}

	/** Boss / 高血量目标免疫判定（服务端权威，2026-09-29 用户定稿血量门）：凋灵、监守者、
	 *  最大生命值 > 50 点。体型门已移除（mod NPC 身高普遍 >2.0 被误挡，村民/低血 NPC 现在可变羊）。 */
	public static boolean isImmuneTarget(LivingEntity target) {
		if (target instanceof WitherEntity
				|| target instanceof net.minecraft.entity.mob.WardenEntity) {
			return true;
		}
		return target.getMaxHealth() > MAX_TARGET_HEALTH;
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		refundCastId = nbt.containsUuid("RefundCastId") ? nbt.getUuid("RefundCastId") : null;
		if (nbt.contains("SheepDuration")) sheepDuration = nbt.getInt("SheepDuration");
		if (nbt.contains("SpellLevel")) spellLevel = nbt.getInt("SpellLevel");
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (refundCastId != null) nbt.putUuid("RefundCastId", refundCastId);
		nbt.putInt("SheepDuration", sheepDuration);
		nbt.putInt("SpellLevel", spellLevel);
	}

	/** 设置变羊时长（tick，服务端施法时调用）。 */
	public void setSheepDuration(int durationTicks) {
		this.sheepDuration = Math.max(20, durationTicks);
	}

	/** 设置魔法等级（1-5）。 */
	public void setSpellLevel(int level) {
		this.spellLevel = Math.max(1, Math.min(5, level));
	}
}
