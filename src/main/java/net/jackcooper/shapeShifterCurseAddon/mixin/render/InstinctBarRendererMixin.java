package net.jackcooper.shapeShifterCurseAddon.mixin.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.InstinctBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the item-anchor lock overlay to SSC's instinct bar. */
@Mixin(InstinctBarRenderer.class)
public class InstinctBarRendererMixin {
	private static final Identifier SSCA_ANCHOR_BAR = new Identifier("shape-shifter-curse", "textures/gui/instinct_bar.png");

	/** 道具固定沿用 SSC 自身的锁定外观，不改原版旗标或月相判断。 */
	@Inject(method = "renderInstinctBar", at = @At("TAIL"), remap = false)
	private void ssca$showAnchorLock(DrawContext context, float tickDelta, int x, int y, PlayerEntity player, CallbackInfo ci) {
		if (net.jackcooper.shapeShifterCurseAddon.util.InstinctAnchors.locked(player)
				&& !net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon.isInCursedMoon(player.getWorld())
				&& !net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.LockInstinct.hasFlag(PlayerFormComponent.COMPONENT.get(player).nowForm)) {
			context.drawTexture(SSCA_ANCHOR_BAR, x, y, 0, 35, 80, 5, 160, 40);
		}
	}

}
