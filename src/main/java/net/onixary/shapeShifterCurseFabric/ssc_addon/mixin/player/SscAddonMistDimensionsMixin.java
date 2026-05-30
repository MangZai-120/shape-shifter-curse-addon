package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.player;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 幽雾化形碰撞箱缩小：雾化期间将玩家碰撞箱缩为 0.2×0.2 格（宽×高），
 * 使其可穿过约 1/4 格（0.25）大小的缝隙——无论竖缝还是横向矮洞均可钻入。
 * 两端均需生效（服务端用于碰撞、客户端用于渲染/相机），故置于通用 mixins 数组。
 */
@Mixin(PlayerEntity.class)
public abstract class SscAddonMistDimensionsMixin {

	@Unique
	private static final float MIST_WIDTH = 0.2f;

	@Unique
	private static final float MIST_HEIGHT = 0.1f;

	@Unique
	private static final float MIST_EYE_HEIGHT = 0.08f;

	@Unique
	private boolean sscAddon$constructed = false;

	@Unique
	private boolean sscAddon$mistDimensionsApplied = false;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void sscAddon$markConstructed(World world, BlockPos pos, float yaw, GameProfile gameProfile, CallbackInfo ci) {
		this.sscAddon$constructed = true;
	}

	@Unique
	private boolean sscAddon$isMistFormActive() {
		return this.sscAddon$constructed && ((PlayerEntity) (Object) this).hasStatusEffect(SscAddon.MIST_FORM);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void sscAddon$refreshMistDimensions(CallbackInfo ci) {
		boolean mistFormActive = this.sscAddon$isMistFormActive();
		if (mistFormActive != this.sscAddon$mistDimensionsApplied) {
			this.sscAddon$mistDimensionsApplied = mistFormActive;
			((PlayerEntity) (Object) this).calculateDimensions();
		}
	}

	@Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
	private void sscAddon$mistDimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		if (this.sscAddon$isMistFormActive()) {
			// 高度压到 0.1，确保可以穿过 0.25 格高的缝隙
			cir.setReturnValue(EntityDimensions.changing(MIST_WIDTH, MIST_HEIGHT));
		}
	}

	@Inject(method = "getActiveEyeHeight", at = @At("RETURN"), cancellable = true)
	private void sscAddon$mistEyeHeight(EntityPose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir) {
		if (this.sscAddon$isMistFormActive()) {
			// 眼睛高度压到碰撞箱内，避免钻 0.25 格矮洞时视角卡进上方方块导致黑屏
			cir.setReturnValue(MIST_EYE_HEIGHT);
		}
	}
}
