package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jackcooper.shapeShifterCurseAddon.client.renderer.DomainRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 领域相机墙（jackcooper，2026-09-21 反馈）：第三人称相机拉远时不得穿出领域球壳。
 *
 * <p>已反编译核实 1.20.1 {@code Camera.update} 调用序：
 * {@code setPos(玩家眼睛插值位) → clipToSpace(4 格方块裁剪) → moveBy(方向 × 拉远量)}，
 * {@code moveBy} 尾部以 {@code setPos(Vec3d 目标绝对坐标)} 落位（字节码 143 行）。
 * 本 mixin 用 {@code @WrapOperation} 包装这一次 {@code setPos(Vec3d)} 调用：
 * 若「相机起点（当前 pos，即玩家眼睛）→ 拉远目标」跨越领域壳边界，二分缩回到贴壳内侧，
 * 相机被留在壳内——第三人称视角与第一人称一样看不见领域外部。</p>
 *
 * <p>判定与缩放复用 {@link DomainRenderer#clampCamera(Vec3d, Vec3d)}（与服务端同一套
 * DomainRules 几何 + 二分算法）；玩家眼睛不在任何壳内（含无壳/壳外）时不干预。
 * 扩张期壳同样生效（相机出不去，符合单向阀）。</p>
 */
@Mixin(Camera.class)
public abstract class DomainCameraWallMixin {
	@Shadow protected abstract void setPos(Vec3d pos);

	@WrapOperation(method = "moveBy", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/Camera;setPos(Lnet/minecraft/util/math/Vec3d;)V"))
	private void ssca$domainCameraWall(Camera camera, Vec3d target, Operation<Void> original) {
		Vec3d from = camera.getPos();
		Vec3d clamped = DomainRenderer.clampCamera(from, target);
		original.call(camera, clamped);
	}

	/**
	 * 终检兜底（2026-09-22 反馈修正）：update 尾部最后校验一次相机坐标——不管前面链路
	 * （含第三方相机/视角模组在 moveBy 之后改写过 pos）如何，只要玩家眼睛在壳内而相机
	 * 终点在壳外，就强制把相机压回壳内（径向投影）。这保证第三人称视角出不去。
	 */
	@Inject(method = "update", at = @At("TAIL"))
	private void ssca$domainCameraFinalClamp(net.minecraft.world.BlockView area, net.minecraft.entity.Entity focused,
	                                         boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
		if (!thirdPerson) return;
		Vec3d eye = focused == null ? null : focused.getLerpedPos(tickDelta).add(0, focused.getStandingEyeHeight(), 0);
		if (eye == null) return;
		Vec3d clamped = DomainRenderer.projectInside(eye, ((Camera) (Object) this).getPos());
		if (clamped != null) setPos(clamped);
	}
}
