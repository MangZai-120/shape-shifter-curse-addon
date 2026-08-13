package net.jackcooper.shapeShifterCurseAddon.entity;

import net.jackcooper.shapeShifterCurseAddon.ability.AddonWebBridgeAction;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.additional_power.WebBridgeAction;
import net.onixary.shapeShifterCurseFabric.blocks.RegCustomBlock;
import net.onixary.shapeShifterCurseFabric.entity.projectile.WebBullet;

/**
 * 月织蛛「搭路模式」蛛丝弹（jackcooper）：复用原版 {@link WebBullet} 的飞行 / 粒子 /
 * 发射音效 / 命中实体缠绕（含箭毒纺锤加成），仅把命中方块的搭天梯逻辑换成附属
 * {@link AddonWebBridgeAction#BuildWebLadder}——梯块路径上遇到附属减速蛛网膜
 * （web_membrane）时先拆网膜再放置，实现「搭路方块替代网膜」。
 *
 * <p>天梯参数直接引用原版 {@code WebBullet} 的 tier1/2/3 静态配置，保证档位数值
 * 与原版蜘蛛搭路完全一致。</p>
 */
public class BridgeWebBullet extends WebBullet {

	public BridgeWebBullet(EntityType<? extends BridgeWebBullet> type, World world) {
		super(type, world);
	}

	public BridgeWebBullet(LivingEntity owner, int tier) {
		super(RegAddonEntities.BRIDGE_WEB_BULLET, owner.getWorld());
		this.owner = owner;
		this.Tier = tier;
		this.setOwner(owner);
		this.setPosition(owner.getX(), owner.getEyeY() - 0.10000000149011612, owner.getZ());
	}

	@Override
	public void onBlockHit(BlockHitResult blockHitResult) {
		// 档位 → 原版天梯配置（数值与原版蜘蛛搭路一致）
		WebBridgeAction.WebLadderConfig src = switch (this.Tier) {
			case 3 -> ladderConfigTier3;
			case 2 -> ladderConfigTier2;
			default -> ladderConfigTier1;
		};
		if (!this.EnableTopBlockBuild) {
			// 与原版对齐：禁顶建时把 TopBlockNum 清零
			src = new WebBridgeAction.WebLadderConfig(src.SideBlockNum(), src.BottomBlockNum(), 0,
					src.LargerLadder(), src.LargerLadderCountPercent());
		}
		AddonWebBridgeAction.WebLadderConfig config = new AddonWebBridgeAction.WebLadderConfig(
				src.SideBlockNum(), src.BottomBlockNum(), src.TopBlockNum(),
				src.LargerLadder(), src.LargerLadderCountPercent());
		AddonWebBridgeAction.BuildWebLadder(this.getWorld(), blockHitResult, config, RegCustomBlock.TEMP_WEB_BRIDGE);
		playHitEffects();
		this.discard();
	}

	private void playHitEffects() {
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.spawnParticles(ParticleTypes.CLOUD,
					this.getX(), this.getY(), this.getZ(),
					20, 0.3, 0.3, 0.3, 0.05);
			serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.BLOCK_WET_GRASS_BREAK, SoundCategory.NEUTRAL, 1.0f, 0.8f + this.random.nextFloat() * 0.4f);
		}
	}
}
