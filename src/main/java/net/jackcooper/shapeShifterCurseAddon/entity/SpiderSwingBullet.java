package net.jackcooper.shapeShifterCurseAddon.entity;

import net.jackcooper.shapeShifterCurseAddon.ability.SpiderMoonWeaverSwingManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.entity.projectile.WebBullet;
import net.onixary.shapeShifterCurseFabric.mana.ManaComponent;
import net.onixary.shapeShifterCurseFabric.mana.RegManaComponent;

/**
 * 月织蛛「蛛丝荡漾」次技能 - 蛛丝飞弹（抛物线投射物，复用主技能蛛丝弹外观）。
 *
 * <p>按次键从玩家眼前发射，带轻微下坠抛物线飞向准星方向。飞行中按已飞长度扣 mana（每 2 格 1 点）。
 * <ul>
 *   <li><b>命中方块</b> → 钩住进入摆荡。</li>
 *   <li><b>命中生物</b> → 连接进入 tether 拖拽。</li>
 *   <li><b>飞到最大绳长（32 格）仍未命中 / mana 耗尽</b> → 球与丝线<b>同时立即消失</b>（miss，5 秒 CD）。</li>
 * </ul>
 * 客户端渲染「玩家→飞弹」的蛛丝：弹存在即画，弹消失（命中/miss）丝线随之消失。
 */
public class SpiderSwingBullet extends WebBullet {

	private double traveled = 0.0;
	private double lastManaCharge = 0.0;
	private int life = 0;

	/** 客户端最近一次看到蛛丝弹存活的 world tick（实体 tick 必跑，天然可靠；渲染器据此门控，避免每帧全实体扫描）。 */
	private static volatile long sscAddon$lastClientBulletSeenTick = -1L;

	public SpiderSwingBullet(EntityType<? extends SpiderSwingBullet> type, World world) {
		super(type, world);
	}

	public SpiderSwingBullet(LivingEntity owner) {
		super(RegAddonEntities.SPIDER_SWING_BULLET, owner.getWorld());
		this.owner = owner;
		this.setOwner(owner);
		this.setPosition(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
	}

	/** 渲染器门控：最近 3 个 world tick 内是否有蛛丝弹存活（弹消失后标记最多保留 3t 即过期）。 */
	public static boolean isBulletRecentlySeen(long currentWorldTick) {
		return sscAddon$lastClientBulletSeenTick >= 0L && currentWorldTick - sscAddon$lastClientBulletSeenTick <= 3L;
	}

	/** 客户端断线时重置存活标记（断线时实体不逐个走 remove）。 */
	public static void resetClientState() {
		sscAddon$lastClientBulletSeenTick = -1L;
	}

	@Override
	protected float getGravity() {
		return 0.025f; // 轻微下坠抛物线
	}

	@Override
	public void tick() {
		// 客户端存活标记：只要本弹还在 tick 就刷新（渲染器据此判断是否需要扫实体画丝）
		if (this.getWorld().isClient) {
			sscAddon$lastClientBulletSeenTick = this.getWorld().getTime();
		}
		if (this.getWorld() instanceof ServerWorld sw && this.owner instanceof PlayerEntity player) {
			traveled += this.getVelocity().length();
			ManaComponent m = RegManaComponent.MANA.get(player);
			while (traveled - lastManaCharge >= 2.0) {
				if (m.getMana() < 1.0) {
					fadeOut(sw); // mana 耗尽 → 球与丝线立即消失
					this.discard();
					return;
				}
				m.consumeMana(1.0);
				lastManaCharge += 2.0;
			}
			if (traveled > SpiderMoonWeaverSwingManager.MAX_ROPE_REACH) {
				fadeOut(sw); // 飞到最大绳长仍未命中 → 球与丝线同时立即消失
				this.discard();
				return;
			}
		}
		life++;
		super.tick(); // WebBullet.tick：发射音效 + 轨迹粒子 + 移动 + 碰撞检测
		if (life > 140 && !this.isRemoved()) {
			if (this.getWorld() instanceof ServerWorld sw) fadeOut(sw);
			this.discard();
		}
	}

	@Override
	public void onBlockHit(BlockHitResult hit) {
		if (this.getWorld() instanceof ServerWorld sw && this.owner instanceof ServerPlayerEntity sp) {
			Vec3d anchor = hit.getPos();
			SpiderMoonWeaverSwingManager.onBulletHitBlock(sp, anchor);
			sw.playSound(null, anchor.x, anchor.y, anchor.z,
					SoundEvents.BLOCK_TRIPWIRE_ATTACH, SoundCategory.PLAYERS, 0.9f, 1.4f);
		}
		this.discard();
	}

	@Override
	public void onEntityHit(EntityHitResult hit) {
		Entity e = hit.getEntity();
		if (this.getWorld() instanceof ServerWorld sw && this.owner instanceof ServerPlayerEntity sp
				&& e instanceof LivingEntity living && living != sp) {
			SpiderMoonWeaverSwingManager.onBulletHitEntity(sp, living);
			sw.playSound(null, living.getX(), living.getY(), living.getZ(),
					SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.9f, 1.6f); // 叮！
			this.discard();
		}
		// 命中无效目标（如自己）：不 discard，继续飞
	}

	private void fadeOut(ServerWorld sw) {
		sw.spawnParticles(ParticleTypes.CLOUD, this.getX(), this.getY(), this.getZ(),
				12, 0.2, 0.2, 0.2, 0.02);
		sw.playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.BLOCK_WOOL_BREAK, SoundCategory.NEUTRAL, 0.5f, 1.2f);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		// 统一 miss 处理（超程/超时/液体/未命中）：onBulletMiss 幂等，仅仍在飞（未命中）时给 5 秒 CD
		if (!this.getWorld().isClient && this.owner instanceof ServerPlayerEntity sp) {
			SpiderMoonWeaverSwingManager.onBulletMiss(sp);
		}
		super.remove(reason);
	}
}
