package net.jackcooper.shapeShifterCurseAddon.client.tutorial;

import java.util.List;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlocks;
import net.jackcooper.shapeShifterCurseAddon.client.SpellcastKeybindings;
import net.jackcooper.shapeShifterCurseAddon.spell.ScrollData;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneRole;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.item.MoonDustReliefItem;
import org.lwjgl.glfw.GLFW;

/** 独立画面中的自动演示；只绘制示意物，不操作真实物品、研究进度或施法。 */
public final class SpellTutorialScreen extends Screen {
    public static final int PAGE_COUNT=9;
    private static final Identifier PANEL=new Identifier("minecraft","textures/gui/demo_background.png");
    private static final Identifier RUNES=new Identifier("ssc_addon","textures/gui/formation_research/runes.png");
    private static final String[] PAGES={"overview","analysis","identify","runes","upgrade","enhance","casting","collapse","growth"};
    private final int[] glyphs;
    private int page,elapsed,ticks,bodyScroll,left,top,panelWidth,panelHeight;
    private boolean paused;
    private ButtonWidget previous,next,pause;
    public SpellTutorialScreen(int[] glyphs){super(text("title"));this.glyphs=glyphs.clone();}
    private static Text text(String key,Object...args){return Text.translatable("tutorial.ssc_addon."+key,args);}
    @Override protected void init(){
        panelWidth=Math.min(390,width-16);panelHeight=Math.min(232,height-16);
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        int buttonWidth=(panelWidth-32)/4,buttonTop=top+panelHeight-28;
        previous=addDrawableChild(ButtonWidget.builder(text("previous"),b->move(-1)).dimensions(left+10,buttonTop,buttonWidth,20).build());
        pause=addDrawableChild(ButtonWidget.builder(text(paused?"play":"pause"),b->{paused=!paused;refresh();})
                .dimensions(left+14+buttonWidth,buttonTop,buttonWidth,20).build());
        next=addDrawableChild(ButtonWidget.builder(text("next"),b->move(1)).dimensions(left+18+buttonWidth*2,buttonTop,buttonWidth,20).build());
        addDrawableChild(ButtonWidget.builder(text("skip"),b->close()).dimensions(left+22+buttonWidth*3,buttonTop,buttonWidth,20).build());
        refresh();
    }
    private void refresh(){
        previous.active=page>0;next.setMessage(text(page==PAGE_COUNT-1?"finish":"next"));
        pause.setMessage(text(paused?"play":"pause"));
    }
    private void move(int offset){
        if(page+offset>=PAGE_COUNT){close();return;}
        page=Math.max(0,page+offset);elapsed=0;bodyScroll=0;refresh();
    }
    @Override public void tick(){ticks++;if(!paused&&++elapsed>=duration())move(1);}
    private int duration(){return 240;}
    private Text body(){
        if(page==6){
            Text cast=SpellcastKeybindings.KEY_CAST==null||SpellcastKeybindings.KEY_CAST.isUnbound()?text("unbound"):SpellcastKeybindings.KEY_CAST.getBoundKeyLocalizedText();
            Text change=SpellcastKeybindings.KEY_SWITCH==null||SpellcastKeybindings.KEY_SWITCH.isUnbound()?text("unbound"):SpellcastKeybindings.KEY_SWITCH.getBoundKeyLocalizedText();
            return text("casting.body",cast,change);
        }
        return text(PAGES[page]+".body");
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        renderBackground(context);
        context.drawTexture(PANEL,left,top,panelWidth,panelHeight,0,0,248,166,256,256);
        context.drawText(textRenderer,textRenderer.trimToWidth(text(PAGES[page]+".title").getString(),panelWidth-72),left+12,top+12,0xff292929,false);
        Text counter=text("page",page+1,PAGE_COUNT);
        context.drawText(textRenderer,counter,left+panelWidth-12-textRenderer.getWidth(counter),top+12,0xff555555,false);
        context.fill(left+10,top+30,left+panelWidth-10,top+98,0xff12212b);
        context.drawBorder(left+10,top+30,panelWidth-20,68,0xff526c75);
        renderDiagram(context);
        List<OrderedText> lines=textRenderer.wrapLines(body(),panelWidth-28);
        int visible=Math.max(1,(panelHeight-150)/11);
        bodyScroll=Math.max(0,Math.min(bodyScroll,Math.max(0,lines.size()-visible)));
        for(int line=0;line<visible&&line+bodyScroll<lines.size();line++)
            context.drawText(textRenderer,lines.get(line+bodyScroll),left+14,top+106+line*11,0xff2e2e2e,false);
        if(lines.size()>visible){
            context.drawText(textRenderer,Text.literal(bodyScroll>0?"↑":"↓"),left+panelWidth-14,top+106,0xff555555,false);
        }
        int progressWidth=(int)((panelWidth-24)*Math.min(1,(elapsed+delta)/duration()));
        context.fill(left+12,top+panelHeight-36,left+panelWidth-12,top+panelHeight-34,0xff7c7c7c);
        context.fill(left+12,top+panelHeight-36,left+12+progressWidth,top+panelHeight-34,0xff3d9d9a);
        super.render(context,mouseX,mouseY,delta);
    }
    private void renderDiagram(DrawContext context){
        switch(page){
            case 0->flow(context,new ItemStack[]{new ItemStack(Items.CHEST),ScrollData.create("fire_bolt",1),
                    new ItemStack(RegAddonBlocks.SPELL_RESEARCH_TABLE),new ItemStack(SscAddon.MOON_DUST_SPELLBOOK)},
                    new String[]{"explore","scroll","research","book"});
            case 1->{flow(context,new ItemStack[]{ScrollData.create("fire_bolt",1),new ItemStack(SscAddon.BLANK_FORMATION_PAPER),
                    new ItemStack(RegAddonBlocks.SPELL_RESEARCH_TABLE),new ItemStack(SscAddon.ANALYZED_SPELL_DIAGRAM)},
                    new String[]{"scroll","paper","analysis.short","diagram"});}
            case 2->{
                RuneRole[] roles={RuneRole.SOURCE,RuneRole.GAIN,RuneRole.STABLE};
                for(int i=0;i<3;i++){
                    int cx=left+panelWidth*(i+1)/4;
                    glyph(context,glyphs[i],cx-12,top+38,24,1);
                    Text role=Text.translatable("research.ssc_addon.slotted.role."+roles[i].ordinal());
                    context.drawCenteredTextWithShadow(textRenderer,role,cx,top+76,0xffd9dfd7);
                }
            }
            case 3->{
                int cx=left+panelWidth/2,cy=top+63;
                int[][] points={{cx,cy-21},{cx-34,cy+19},{cx+34,cy+19}};
                for(int i=0;i<3;i++){
                    int[] a=points[i],b=points[(i+1)%3];line(context,a[0],a[1],b[0],b[1],0xffb6a06b);
                    context.drawBorder(a[0]-10,a[1]-10,20,20,0xffb6a06b);
                }
                glyph(context,glyphs[0],points[0][0]-8,points[0][1]-8,16,.35f);
                double phase=(ticks%100)/100.0;
                int handX=(int)((left+35)*(1-phase)+(points[0][0]-8)*phase);
                int handY=(int)((top+58)*(1-phase)+(points[0][1]-8)*phase);
                glyph(context,glyphs[0],handX,handY,16,1);
                context.drawCenteredTextWithShadow(textRenderer,text("manual"),left+panelWidth-54,top+61,0xffd9dfd7);
            }
            case 4->flow(context,new ItemStack[]{ScrollData.create("fire_bolt",1),new ItemStack(RegAddonBlocks.SPELL_RESEARCH_TABLE),
                    new ItemStack(SscAddon.FORMATION_INK_FIRE),ScrollData.create("fire_bolt",2)},
                    new String[]{"lower_scroll","runes.short","materials","higher_scroll"});
            case 5->flow(context,new ItemStack[]{new ItemStack(SscAddon.SPELL_FORMATION),ScrollData.create("fire_bolt",1),
                    new ItemStack(RegAddonBlocks.INFUSION_ALTAR),ScrollData.create("fire_bolt",1)},
                    new String[]{"scheme","scroll","preparation","imprint"});
            case 6->flow(context,new ItemStack[]{ScrollData.create("fire_bolt",1),new ItemStack(SscAddon.MOON_DUST_SPELLBOOK),
                    new ItemStack(SscAddon.MOON_DUST_SPELLBOOK),new ItemStack(Items.FIRE_CHARGE)},
                    new String[]{"scroll","insert","equip","cast"});
            case 7->{
                int bx=left+26,bw=panelWidth-52,by=top+61;
                double value=(ticks%180)/180.0*1.4;
                context.fill(bx,by,bx+bw,by+12,0xff3b3c3c);
                int safe=bw/2;context.fill(bx,by,bx+Math.min(safe,(int)(value*safe)),by+12,0xff45bec0);
                if(value>1)context.fill(bx+safe,by,bx+(int)(value*safe),by+12,0xffcb7956);
                context.drawBorder(bx-1,by-1,bw+2,14,0xffa8bac1);context.fill(bx+safe,by-3,bx+safe+1,by+15,0xfff2f2e9);
                context.drawCenteredTextWithShadow(textRenderer,text("collapse.demo"),left+panelWidth/2,top+39,0xffd9dfd7);
                context.drawText(textRenderer,text("safe"),bx,by+19,0xff8bd4cd,false);
                Text danger=text("danger");context.drawText(textRenderer,danger,bx+bw-textRenderer.getWidth(danger),by+19,0xffffba88,false);
            }
            case 8->flow(context,new ItemStack[]{new ItemStack(Items.ARMOR_STAND),new ItemStack(Items.EXPERIENCE_BOTTLE),
                    new ItemStack(MoonDustReliefItem.BOTTLE),new ItemStack(SscAddon.MOON_DUST_SPELLBOOK)},
                    new String[]{"player","experience","recovery","replay.short"});
        }
    }
    private void flow(DrawContext context,ItemStack[] stacks,String[] captions){
        int spacing=(panelWidth-28)/stacks.length;
        for(int i=0;i<stacks.length;i++){
            int cx=left+14+spacing*i+spacing/2;
            context.getMatrices().push();context.getMatrices().translate(cx-12,top+38,0);context.getMatrices().scale(1.5f,1.5f,1);
            context.drawItem(stacks[i],0,0);context.getMatrices().pop();
            String label=textRenderer.trimToWidth(text(captions[i]).getString(),spacing-7);
            context.drawCenteredTextWithShadow(textRenderer,Text.literal(label),cx,top+76,0xffd9dfd7);
            if(i+1<stacks.length)context.drawText(textRenderer,Text.literal("→"),cx+spacing/2-4,top+47,0xffc7bd88,false);
        }
    }
    private static void glyph(DrawContext context,int glyph,int x,int y,int size,float alpha){
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();context.setShaderColor(1,1,1,alpha);
        context.drawTexture(RUNES,x,y,size,size,glyph*64,0,64,64,1152,64);context.setShaderColor(1,1,1,1);
    }
    private static void line(DrawContext context,int ax,int ay,int bx,int by,int color){
        int steps=Math.max(Math.abs(bx-ax),Math.abs(by-ay));
        for(int i=0;i<=steps;i++){int x=ax+(bx-ax)*i/Math.max(1,steps),y=ay+(by-ay)*i/Math.max(1,steps);context.fill(x,y,x+1,y+1,color);}
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double amount){bodyScroll-=Math.round((float)amount);return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_SPACE){paused=!paused;refresh();return true;}
        if(key==GLFW.GLFW_KEY_RIGHT||key==GLFW.GLFW_KEY_ENTER){move(1);return true;}
        if(key==GLFW.GLFW_KEY_LEFT){move(-1);return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean shouldPause(){return true;}
    @Override public void close(){client.setScreen(null);}
}
