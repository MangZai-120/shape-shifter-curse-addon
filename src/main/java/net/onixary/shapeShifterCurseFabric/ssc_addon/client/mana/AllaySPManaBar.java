package net.onixary.shapeShifterCurseFabric.ssc_addon.client.mana;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.VariableIntPower;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.util.UIPositionUtils;

import java.util.List;

@Environment(EnvType.CLIENT)
public class AllaySPManaBar implements HudRenderCallback {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Identifier BarTexFullID = new Identifier("my_addon", "textures/gui/allay_sp_mana_bar_full.png");
    private static final Identifier BarTexEmptyID = new Identifier("my_addon", "textures/gui/allay_sp_mana_bar_empty.png");
    private static final Identifier RESOURCE_ID = new Identifier("my_addon", "form_allay_sp_mana_resource");

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        if (mc.options.hudHidden || mc.player == null) return;
        
        PlayerEntity player = mc.player;
        VariableIntPower resourcePower = null;
        
        List<VariableIntPower> powers = PowerHolderComponent.KEY.get(player).getPowers(VariableIntPower.class);
        for (VariableIntPower power : powers) {
            if (power.getType().getIdentifier().equals(RESOURCE_ID)) {
                resourcePower = power;
                break;
            }
        }

        if (resourcePower != null) {
            int current = resourcePower.getValue();
            int max = resourcePower.getMax();
            double percent = (double) current / (double) max;
            
            // Default values
            int posType = 8;
            int offsetX = 100;
            int offsetY = -17;

            try {
                Object config = ShapeShifterCurseFabric.clientConfig;
                if (config != null) {
                    Class<?> configClass = config.getClass();
                    posType = configClass.getField("manaBarPosType").getInt(config);
                    offsetX = configClass.getField("manaBarPosOffsetX").getInt(config);
                    offsetY = configClass.getField("manaBarPosOffsetY").getInt(config);
                }
            } catch (Exception e) {
                // Use defaults if reflection fails
            }

            // Using standard method name seen in SnowFoxSPManaBar, assuming it exists
            Pair<Integer, Integer> pos = UIPositionUtils.getCorrectPosition(
                posType, 
                offsetX, 
                offsetY
            );
            
            renderBar(context, tickDelta, pos.getLeft(), pos.getRight(), percent);
        }
    }

    private void renderBar(DrawContext context, float tickDelta, int x, int y, double percent) {
        // Assuming texture width is 80 (same as Snow Fox) or use actual width?
        // Since I can't check texture width, I'll use 80 as default for compatibility with Snow Fox style
        int fullWidth = 80; 
        int barWidth = (int) Math.ceil(fullWidth * percent);
        
        // Draw Empty
        context.drawTexture(BarTexEmptyID, x, y, 0, 0, fullWidth, 5, fullWidth, 5);
        // Draw Full (clipped)
        context.drawTexture(BarTexFullID, x, y, 0, 0, barWidth, 5, fullWidth, 5);
    }
}
