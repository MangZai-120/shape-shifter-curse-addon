package net.jackcooper.shapeShifterCurseAddon.mixin.render;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.client.UpgradeAxolotlSpearRenderState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 进化美西螈「投掷水矛」蓄力期：第三人称<b>纯渲染</b>把主手渲染的物品替换成 3D 水矛（不动玩家背包）。
 *
 * <p>重定向 {@code HeldItemFeatureRenderer.render} 里的 {@code entity.getMainHandStack()}：
 * 蓄力中返回带 {@code CustomModelData=1} 的水矛（恒定 3D）。覆盖左/右利手（该调用在两种利手分支各出现一次，
 * 只有实际利手那支执行）与<b>空手</b>情况（返回非空水矛使渲染分支不被 isEmpty 跳过）。
 * 举矛过肩姿势由 {@code UpgradeAxolotlSpearChargeArmMixin} 同步形态骨骼和原版手持物挂点。多人下对被追踪的
 * 蓄力玩家同样生效（蓄力状态已广播给追踪端）。</p>
 */
@Mixin(HeldItemFeatureRenderer.class)
public class UpgradeAxolotlThirdPersonSpearMixin {

	@WrapOperation(
			method = "render",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/entity/LivingEntity;getMainHandStack()Lnet/minecraft/item/ItemStack;"),
			require = 0
	)
	private ItemStack ssc_addon$swapTpSpear(LivingEntity entity, Operation<ItemStack> original) {
		if (entity instanceof AbstractClientPlayerEntity
				&& UpgradeAxolotlSpearRenderState.isCharging(entity.getUuid())) {
			ItemStack spear = new ItemStack(SscAddon.WATER_SPEAR);
			spear.getOrCreateNbt().putInt("CustomModelData", 1);
			return spear;
		}
		return original.call(entity);
	}

	// 物品沿已同步的手臂挂点渲染；不要在模型原点额外旋转，否则握点会偏离手心。

	/**
	 * 血雾 / 真隐身期间隐藏玩家手持物品（主手 + 副手）。（原 MistFormHeldItemMixin 合并至此；同为 HeldItemFeatureRenderer 目标，行为不变。）
	 * vanilla 隐身不隐藏手持物，需在 render 头部直接 cancel。
	 */
	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void ssc_addon$hideHeldItemDuringMist(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
												 LivingEntity entity, float limbAngle, float limbDistance,
												 float tickDelta, float animationProgress, float headYaw, float headPitch,
												 CallbackInfo ci) {
		if (entity.hasStatusEffect(SscAddon.MIST_FORM)
				|| entity.hasStatusEffect(SscAddon.TRUE_INVISIBILITY)) {
			ci.cancel();
		}
	}
}
