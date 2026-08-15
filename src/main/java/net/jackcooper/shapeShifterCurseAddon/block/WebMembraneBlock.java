package net.jackcooper.shapeShifterCurseAddon.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ConnectingBlock;
import net.minecraft.block.LichenGrower;
import net.minecraft.block.MultifaceGrowthBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.jackcooper.shapeShifterCurseAddon.effect.RegAddonEffects;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.UUID;

/**
 * 蛛网膜：多面生长的薄层蛛网方块（可贴地面 / 墙面 / 天花板等任意面，机制同发光地衣）。
 * 非免疫生物踩上或接触时水平移动减速 30%；蜘蛛类实体、玩家及其已驯服宠物免疫。
 * 遇水即被冲毁（不掉落，由 Settings.dropsNothing() 保证）；可燃且蔓延快（可燃在 RegAddonBlocks 注册）。
 */
@SuppressWarnings("deprecation") // 本类覆写多个 vanilla 标注 @Deprecated 的 Block 方法（碰撞 / 邻居更新 / 放置），统一抑制
public class WebMembraneBlock extends MultifaceGrowthBlock {
	private final LichenGrower grower = new LichenGrower(this);

	/** 定时消失下限（tick）：60 秒。 */
	public static final int LIFESPAN_MIN = 1200;
	/** 定时消失上限（tick）：90 秒。技能铺的每块随机取 [1200,1800]，实现「60 秒开始、90 秒全消失」的错峰消散。 */
	public static final int LIFESPAN_MAX = 1800;

	/** 「蜘网缠身」持续时长（tick）：踩过刷新，离开后 5 秒消散。 */
	private static final int WEB_BOUND_DURATION = 100;

	/** 踩网蓝色高亮（仅施法者可见）持续时长（tick）。 */
	private static final int WEB_HIGHLIGHT_DURATION = 60;

	public WebMembraneBlock(AbstractBlock.Settings settings) {
		super(settings);
	}

	@Override
	public LichenGrower getGrower() {
		return this.grower;
	}

	// 放置即排定随机寿命（错峰消失）；被水冲毁 / 烧掉时提前失效，scheduledTick 命中空气则无操作
	@Override
	public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		if (!world.isClient) {
			int life = LIFESPAN_MIN + world.getRandom().nextInt(LIFESPAN_MAX - LIFESPAN_MIN + 1);
			world.scheduleBlockTick(pos, this, life);
		}
	}

	@Override
	public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (state.isOf(this)) {
			world.setBlockState(pos, Blocks.AIR.getDefaultState());
			WebMembraneOwners.remove(pos);
		}
	}

	/**
	 * 以 center 为球心、按 TNT 爆炸式辐射把「贴着实体面的空气格」涂覆成蛛网膜（多面贴附）。
	 * 近心区高概率填满、边缘概率衰减、基础半径外留 2 格溅射带低概率随机延伸（最远点可超基础半径，
	 * 形成参差不齐的辐射状边缘而非规整球面）。每格按其周围可附着的实体面设置对应朝向布尔属性；
	 * 无可附着面则跳过（多面块放不上）。由蛛丝弹命中调用，服务端执行（概率用服务端随机，多人一致）；
	 * 每块经 onBlockAdded 各自排定 60~90s 随机寿命，并登记施法者 ownerId 供白名单判定。
	 */
	public static void coatArea(ServerWorld world, BlockPos center, double radius, UUID ownerId) {
		int r = (int) Math.ceil(radius);
		int splash = r + 2; // 溅射带：基础半径外多扫 2 格，低概率延伸出辐射枝杈
		double core = r * 0.7; // 近心核心区：高概率填满
		double r2 = radius * radius;
		BlockState base = RegAddonBlocks.WEB_MEMBRANE.getDefaultState();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int dx = -splash; dx <= splash; dx++) {
			for (int dy = -splash; dy <= splash; dy++) {
				for (int dz = -splash; dz <= splash; dz++) {
					double dist2 = (double) (dx * dx + dy * dy + dz * dz);
					// 爆炸式保留概率：核心区≈全留 → 半径内线性衰减 → 溅射带低概率随机
					double keep;
					if (dist2 <= core * core) {
						keep = 0.97;            // 近心填满（留少量空洞更像爆炸灼痕）
					} else if (dist2 <= r2) {
						double t = (Math.sqrt(dist2) - core) / (r - core);
						keep = 0.97 - 0.47 * t; // 0.97 → 0.50 线性衰减
					} else {
						keep = 0.15;            // 溅射带：随机延伸出边缘枝杈
					}
					if (world.getRandom().nextDouble() > keep) continue;
					pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					if (!world.getBlockState(pos).isAir()) continue; // 只填空气，不破坏既有方块
					BlockState st = base;
					boolean anchored = false;
					for (Direction d : Direction.values()) {
						BlockPos np = pos.offset(d);
						if (world.getBlockState(np).isSideSolidFullSquare(world, np, d.getOpposite())) {
							st = st.with(ConnectingBlock.FACING_PROPERTIES.get(d), true);
							anchored = true;
						}
					}
					if (anchored) {
						BlockPos placed = pos.toImmutable();
						world.setBlockState(placed, st);
						WebMembraneOwners.set(placed, ownerId);
					}
				}
			}
		}
	}

	// 接触到水就被冲毁（返回空气）；不掉落由 dropsNothing() 保证
	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
												WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		if (isTouchingWater(world, pos)) {
			return Blocks.AIR.getDefaultState();
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	private static boolean isTouchingWater(WorldAccess world, BlockPos pos) {
		if (world.getFluidState(pos).isIn(FluidTags.WATER)) {
			return true;
		}
		for (Direction d : Direction.values()) {
			if (world.getFluidState(pos.offset(d)).isIn(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	// 减速 30% + 施加「蛛网缠身」并踩烂脚下这块网（消耗式）；蜘蛛 / 月织蛛自身 / 施法者白名单个体（队友·宠物）免疫
	@Override
	public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (isImmune(world, pos, entity)) {
			return;
		}
		entity.slowMovement(state, new Vec3d(0.7D, 1.0D, 0.7D));
		if (!world.isClient && entity instanceof LivingEntity living) {
			// 施加/刷新蛛网缠身（防牛奶、任何形态不免疫）；脚下蛛网粒子由效果自身逐 tick 生成
			// 带网主（若在线）作 source，供食梦魔「入梦」debuff 拦截归因
			UUID casterId0 = WebMembraneOwners.get(pos);
			ServerPlayerEntity webCaster = world instanceof ServerWorld sw0 && casterId0 != null
					? sw0.getServer().getPlayerManager().getPlayer(casterId0) : null;
			living.addStatusEffect(new StatusEffectInstance(RegAddonEffects.SPIDER_WEB_BOUND, WEB_BOUND_DURATION, 0, false, false, true), webCaster);
			// 踩烂前先取施法者，用于「仅施法者可见」的蓝色高亮
			UUID casterId = WebMembraneOwners.get(pos);
			// 踩烂脚下这块网：非免疫生物走过即毁
			world.setBlockState(pos, Blocks.AIR.getDefaultState());
			WebMembraneOwners.remove(pos);
			if (world instanceof ServerWorld sw) {
				// 踩网音效
				sw.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						SoundEvents.BLOCK_WET_GRASS_BREAK, SoundCategory.BLOCKS, 0.5f, 1.1f);
				// 蓝色高亮：仅通知施法者本机给受害者描蓝边
				if (casterId != null) {
					ServerPlayerEntity caster = sw.getServer().getPlayerManager().getPlayer(casterId);
					if (caster != null && caster != living) {
						SscAddonNetworking.sendWebHighlight(caster, living.getId(), WEB_HIGHLIGHT_DURATION);
					}
				}
			}
		}
	}

	/**
	 * 免疫判定：
	 * <ul>
	 *   <li>蜘蛛类实体 / 月织蛛形态玩家 → 始终免疫（不困住蜘蛛自己）；</li>
	 *   <li>悦灵系形态（悦灵 / 堕落悦灵）玩家 → 始终免疫（轻盈精灵不怕蛛网）；</li>
	 *   <li>施法者在线 → 走其白名单：队友 / 宠物 /（白名单为空时）所有玩家免疫，敌人（非白名单）被缠；</li>
	 *   <li>施法者离线 / 记录丢失（重启）→ 安全回退为默认白名单（玩家 + 已驯服宠物免疫，怪物受影响）。</li>
	 * </ul>
	 */
	private static boolean isImmune(World world, BlockPos pos, Entity entity) {
		if (entity instanceof SpiderEntity) {
			return true;
		}
		if (!(entity instanceof LivingEntity living)) {
			return true;
		}
		if (living instanceof PlayerEntity player
				&& (FormUtils.isForm(player, FormIdentifiers.SPIDER_MOON_WEAVER)
				|| FormUtils.isForm(player, FormIdentifiers.ALLAY_SP)
				|| FormUtils.isForm(player, FormIdentifiers.FALLEN_ALLAY_SP))) {
			return true;
		}
		UUID ownerId = WebMembraneOwners.get(pos);
		if (ownerId != null && world instanceof ServerWorld sw) {
			ServerPlayerEntity owner = sw.getServer().getPlayerManager().getPlayer(ownerId);
			if (owner != null) {
				return WhitelistUtils.isProtected(owner, living); // 施法者白名单：队友免疫，敌人被缠
			}
		}
		// 施法者未知（离线 / 重启后记录丢失）：默认白名单（玩家 + 宠物免疫，怪物受影响）
		if (living instanceof PlayerEntity) {
			return true;
		}
		return living instanceof TameableEntity tameable && tameable.getOwnerUuid() != null;
	}
}
