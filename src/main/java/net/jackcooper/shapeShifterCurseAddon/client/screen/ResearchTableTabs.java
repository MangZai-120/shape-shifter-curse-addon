package net.jackcooper.shapeShifterCurseAddon.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.util.function.BooleanSupplier;

public final class ResearchTableTabs {
    public static final int ANALYSIS=0, RESEARCH=1, WORKSHOP=2, SCRIBE=3, LEARN=4, COUNT=5;
    private static final Identifier TABS=new Identifier("minecraft","textures/gui/container/creative_inventory/tabs.png");
    private static final Item[] ICONS={Items.WRITABLE_BOOK,Items.ENCHANTED_BOOK,Items.CRAFTING_TABLE,Items.PAPER,Items.BOOK};
    private ResearchTableTabs(){}
    public static ButtonWidget create(int left,int top,int index,BooleanSupplier selected,Runnable action){
        Text title=Text.translatable("research.ssc_addon.slotted.tab."+index);
        var button=new ButtonWidget(left+index*27,top-28,26,28,title,ignored->action.run(),supplier->supplier.get()){
            @Override public void renderButton(DrawContext context,int mouseX,int mouseY,float delta){
                RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();context.setShaderColor(1,1,1,1);
                context.drawTexture(TABS,getX(),getY(),index*26,selected.getAsBoolean()?32:0,26,selected.getAsBoolean()?32:28,256,256);
                context.drawItem(new ItemStack(ICONS[index]),getX()+5,getY()+9);
            }
        };
        button.setTooltip(Tooltip.of(title));return button;
    }
}