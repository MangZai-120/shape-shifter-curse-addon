package net.jackcooper.shapeShifterCurseAddon.entity;

import net.jackcooper.shapeShifterCurseAddon.block.WebMembraneBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.entity.projectile.WebBullet;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

/**
 * 魔法蜘蛛「攻击模式」蛛丝弹：复用原版 {@link WebBullet} 的飞行 / 粒子 / 发射音效，
 * 但改写命中效果——命中方块 / 实体后按蓄力档在半径内贴面铺减速蛛网（{@link WebMembraneBlock}），
 * 而非原版的临时天梯。tier1/2/3 → 半径 3 / 5 / 6 格。
 */
public class WebMembraneBullet extends WebBullet {

	public WebMembraneBullet(EntityType<? extends WebMembraneBullet> type, World world) {
		super(type, world);
	}

	public WebMembraneBullet(LivingEntity owner, int tier) {
		super(RegAddonEntities.WEB_MEMBRANE_BULLET, owner.getWorld());
		this.owner = owner;
		this.Tier = tier;
		this.setOwner(owner);
		this.setPosition(owner.getX(), owner.getEyeY() - 0.10000000149011612, owner.getZ());
	}

	/** 蛛网弹落地爆炸的蜘网缠身范围半径（格）：与蓄力档无关，固定 8 格。 */
	private static final double AURA_RADIUS = 8.0;
	/** 蛛网缠身范围施加持续时长（tick）：5 秒（与踩网施加一致）。 */
	private static final int AURA_DURATION = 100;

	/** 蓄力档 → 减速网半径：tier3=6 / tier2=5 / 其余=3（最低 3 格）。 */
	private static double radiusForTier(int tier) {
		if (tier >= 3) return 6.0;
		if (tier >= 2) return 5.0;
		return 3.0;
	}

	@Override
	public void onBlockHit(BlockHitResult blockHitResult) {
		if (this.getWorld() instanceof ServerWorld world) {
			BlockPos hit = blockHitResult.getBlockPos();
			WebMembraneBlock.coatArea(world, hit, radiusForTier(this.Tier), this.owner != null ? this.owner.getUuid() : null);
			applyBoundAura(world, hit); // 爆炸范围直接施加蜘网缠身
			playHit(world);
		}
		this.discard();
	}

	@Override
	public void onEntityHit(EntityHitResult entityHitResult) {
		Entity entity = entityHitResult.getEntity();
		if (this.getWorld() instanceof ServerWorld world) {
			BlockPos hit = entity.getBlockPos();
			WebMembraneBlock.coatArea(world, hit, radiusForTier(this.Tier), this.owner != null ? this.owner.getUuid() : null);
			applyBoundAura(world, hit); // 爆炸范围裹茧 / 施加蜘网缠身
			// 直接命中：结茧走原版逻辑——确定性叠加缠身时长（200/400/600t），累积满 500t 转茧（与原版 SSC 一致）
			if (entity instanceof LivingEntity living && !isProtected(living)) {
				int buffTime = this.Tier >= 3 ? 600 : (this.Tier >= 2 ? 400 : 200);
				net.onixary.shapeShifterCurseFabric.status_effects.EntangledEffectUtils.applyEntangledEffect(this.owner, living, buffTime);
			}
			playHit(world);
		}
		this.discard();
	}

	/**
	 * 蛛网弹落地爆炸：半径 8 格内所有非免疫生物——
	 * <ul>
	 *   <li>按概率裹茧（走原版 applyEntangledEffect 一次叠满转 ENTANGLED_FULL_EFFECT，含茧模型/玩家5s/怪物15s/死亡蛛网/掉流食囊）；</li>
	 *   <li>未中签裹茧者施加蜘网缠身（减速+挖掘疲劳+虚弱）。</li>
	 * </ul>
	 * 裹茧概率：中心（0格）→ 边缘（8格）线性衰减，随蓄力档递增（t1:10%→0%、t2:35%→5%、t3:60%→10%）；
	 * 踩网加成：目标身上蜘网缠身每剩 20t 裹茧概率 +5%（上限 +30%，即短时间内踩网过多更易被裹）。
	 */
	private void applyBoundAura(ServerWorld world, BlockPos center) {
		double r2 = AURA_RADIUS * AURA_RADIUS;
		double cx = center.getX() + 0.5, cy = center.getY() + 0.5, cz = center.getZ() + 0.5;
		// 中心→边缘裹茧概率（随蓄力档递增）：t1 10%→0%、t2 35%→5%、t3 60%→10%
		double base = this.Tier >= 3 ? 0.60 : (this.Tier >= 2 ? 0.35 : 0.10);
		double edge = this.Tier >= 3 ? 0.10 : (this.Tier >= 2 ? 0.05 : 0.00);
		for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class,
				new net.minecraft.util.math.Box(center).expand(AURA_RADIUS), e -> !isBoundImmune((LivingEntity) e))) {
			double distSq = living.squaredDistanceTo(cx, cy, cz);
			if (distSq > r2) continue;
			// 距离线性衰减 + 踩网加成（用 sqrt 还原线性距离参与衰减比例计算）
			double dist = Math.sqrt(distSq);
			double prob = base + (edge - base) * (dist / AURA_RADIUS);
			StatusEffectInstance bound = living.getStatusEffect(net.jackcooper.shapeShifterCurseAddon.effect.RegAddonEffects.SPIDER_WEB_BOUND);
			int boundLeft = bound != null ? bound.getDuration() : 0;
			prob += Math.min(0.30, (boundLeft / 20.0) * 0.05);
			if (world.getRandom().nextDouble() < prob) {
				// 走原版缠身一次叠满转茧逻辑（500t = 5×100，达 ENTANGLED_DURATION_PER_LEVEL×(MAX_LEVEL+1) 阈值）
				net.onixary.shapeShifterCurseFabric.status_effects.EntangledEffectUtils.applyEntangledEffect(this.owner, living, 500);
			} else {
				// 未裹茧：施加蜘网缠身（减速+挖掘疲劳+虚弱），为后续踩网/再次命中累积裹茧概率
				living.addStatusEffect(new StatusEffectInstance(
						net.jackcooper.shapeShifterCurseAddon.effect.RegAddonEffects.SPIDER_WEB_BOUND,
						AURA_DURATION, 0, false, false, true));
			}
		}
	}

	/** 蛛网缠身范围免疫：蜘蛛类 / 悦灵系 / 施法者白名单个体豁免。 */
	private boolean isBoundImmune(LivingEntity target) {
		if (target instanceof net.minecraft.entity.mob.SpiderEntity) return true;
		if (target instanceof net.minecraft.entity.player.PlayerEntity p
				&& (net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils.isForm(p, net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers.SPIDER_MAGIC)
				|| net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils.isForm(p, net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers.ALLAY_SP)
				|| net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils.isForm(p, net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers.FALLEN_ALLAY_SP))) {
			return true;
		}
		return isProtected(target);
	}

	private boolean isProtected(LivingEntity target) {
		return this.owner instanceof ServerPlayerEntity sp && WhitelistUtils.isProtected(sp, target);
	}

	private void playHit(ServerWorld world) {
		world.spawnParticles(ParticleTypes.CLOUD, this.getX(), this.getY(), this.getZ(),
				24, 0.35, 0.35, 0.35, 0.05);
		world.playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.BLOCK_WET_GRASS_BREAK, SoundCategory.NEUTRAL, 1.0f, 0.7f + this.random.nextFloat() * 0.3f);
	}
}
