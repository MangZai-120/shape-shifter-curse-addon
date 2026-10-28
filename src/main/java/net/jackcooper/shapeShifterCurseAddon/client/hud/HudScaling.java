package net.jackcooper.shapeShifterCurseAddon.client.hud;

import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.util.UIPositionUtils;
import java.util.HashMap;
import static net.jackcooper.shapeShifterCurseAddon.client.hud.HudScaleGeometry.*;

/** Per-HUD local settings. All visual pieces share the same matrix, including text. */
public final class HudScaling {
    public static final String[] KEYS={"instinct","mana","cooldown","spellbook","charge","ssc_magic","stability","item_store"};
    private static final ThreadLocal<Integer> ACTIVE=ThreadLocal.withInitial(()->-1);
    private HudScaling() {}
    public static int get(SSCAddonClientConfig config,int section) {
        if(section<0 || section>=KEYS.length || config.hudScalePercent==null)return DEFAULT;
        Integer value=config.hudScalePercent.get(KEYS[section]);return value==null?DEFAULT:percent(value);
    }
    public static boolean isVisible(SSCAddonClientConfig config,int section) {
        return switch(KEYS[section]) {
            case "instinct" -> config.showInstinctBar;
            case "mana" -> config.showManaBar;
            case "cooldown" -> config.showCdBar;
            case "spellbook" -> config.showSpellbookHud;
            case "charge" -> config.showChargeBar;
            case "ssc_magic" -> config.showSscMagicHud;
            case "item_store" -> config.showSscItemStoreHud;
            case "stability" -> config.showNeoStabilityBar;
            default -> throw new IllegalArgumentException("Unknown HUD section "+section);
        };
    }
    public static void set(SSCAddonClientConfig config,int section,int value) {
        if(config.hudScalePercent==null)config.hudScalePercent=new HashMap<>();
        config.hudScalePercent.put(KEYS[section],percent(value));
    }
    public static Box raw(int section) {
        var mc=MinecraftClient.getInstance();int w=mc.getWindow().getScaledWidth(),h=mc.getWindow().getScaledHeight();
        var c=SSCAddonConfig.client();var base=ShapeShifterCurseFabric.clientConfig;
        if(section==2 || section==4)c.migrateSkillHudLayout();
        switch(section) {
            case 0: {var p=UIPositionUtils.getCorrectPosition(base.instinctBarPosType,base.instinctBarPosOffsetX,base.instinctBarPosOffsetY);return new Box(p.getLeft(),p.getRight()-2,80,9);}
            case 1: {var p=UIPositionUtils.getCorrectPosition(base.manaBarPosType,base.manaBarPosOffsetX,base.manaBarPosOffsetY);return new Box(p.getLeft()-6,p.getRight()-2,92,9);}
            case 2: {var p=UIPositionUtils.getCorrectPosition(c.cdBarPosType,0,0);var b=SkillCooldownBarRenderer.panelLayout(p.getLeft()+c.cdBarPosOffsetX,p.getRight()+c.cdBarPosOffsetY,c.cdMirrorRight,w,h);return new Box(b.primaryX(),b.primaryY(),33,68);}
            case 3: {return SpellbookHudRenderer.scaleBounds();}
            case 4: {var p=UIPositionUtils.getCorrectPosition(c.chargeBarPosType,c.chargeBarPosOffsetX,c.chargeBarPosOffsetY);int x=clamp(p.getLeft(),w,14);if(c.chargeMirrorRight)x=w-x-14;return new Box(x,clamp(p.getRight(),h,68),14,68);}
            case 5: {var p=UIPositionUtils.getCorrectPosition(base.magicGUIPosType,base.magicGUIPosOffsetX,base.magicGUIPosOffsetY);return new Box(p.getLeft()+6,p.getRight(),52,24);}
            case 6: {int[] p=NeoStabilityBar.position(c.neoStabilityBarPosType,c.neoStabilityBarPosOffsetX,c.neoStabilityBarPosOffsetY,w,h);return new Box(p[0],p[1],97,24);}
            case 7: {return itemStoreBounds();}
            default: throw new IllegalArgumentException("Unknown HUD section "+section);
        }
    }
    public static boolean rightAligned(int section) {
        var c=SSCAddonConfig.client();return section==2?c.cdMirrorRight:section==4&&c.chargeMirrorRight;
    }
    public static View view(int section) {
        var mc=MinecraftClient.getInstance();return place(raw(section),get(SSCAddonConfig.client(),section),mc.getWindow().getScaledWidth(),mc.getWindow().getScaledHeight(),rightAligned(section));
    }
    public static Box bounds(int section) {return view(section).bounds();}
    public static boolean hit(int section,double x,double y) {return bounds(section).contains(x,y,2);}
    /** Workbench tooltips stay in screen coordinates while their preview is scaled. */
    public static void tooltip(DrawContext context,net.minecraft.client.font.TextRenderer font,net.minecraft.text.Text text,int x,int y) {
        int section=ACTIVE.get();if(section<0){context.drawTooltip(font,text,x,y);return;}
        var v=view(section);context.draw();context.getMatrices().push();
        try {
            context.getMatrices().translate(v.source().x(),v.source().y(),0);
            context.getMatrices().scale(1/v.scale(),1/v.scale(),1);
            context.getMatrices().translate(-v.bounds().x(),-v.bounds().y(),0);
            context.drawTooltip(font,text,x,y);context.draw();
        } finally {context.getMatrices().pop();}
    }
    public static void render(DrawContext context,int section,Runnable draw) {
        // Charge rendering also advances its exit watchdog; its own gate runs after cleanup.
        if(section!=4 && !isVisible(SSCAddonConfig.client(),section))return;
        // Reentrant calls from a shared resource renderer must not apply the same scale twice.
        if(ACTIVE.get()==section){draw.run();return;}
        var mc=MinecraftClient.getInstance();
        if(mc==null || mc.getWindow()==null){draw.run();return;}
        View v;
        try{v=view(section);}catch(IllegalStateException|NullPointerException unavailable){draw.run();return;}
        var source=v.source();var target=v.bounds();
        if(v.scale()==1 && source.x()==target.x() && source.y()==target.y()){draw.run();return;}
        context.draw();context.getMatrices().push();int previous=ACTIVE.get();ACTIVE.set(section);
        try {
            context.getMatrices().translate(target.x(),target.y(),0);
            context.getMatrices().scale(v.scale(),v.scale(),1);
            context.getMatrices().translate(-source.x(),-source.y(),0);
            draw.run();
        } finally {
            try{context.draw();}finally{context.getMatrices().pop();ACTIVE.set(previous);}
        }
    }
    /** Convert a displayed box back into the existing anchor/offset format. */
    public static int[] offsets(int section,int left,int top) {
        var mc=MinecraftClient.getInstance();int w=mc.getWindow().getScaledWidth();var c=SSCAddonConfig.client();var base=ShapeShifterCurseFabric.clientConfig;
        var v=view(section);int rawX=rightAligned(section)?left+v.bounds().width()-v.source().width():left;
        int type=switch(section){case 0->base.instinctBarPosType;case 1->base.manaBarPosType;case 2->c.cdBarPosType;case 3->c.spellbookHudPosType;case 4->c.chargeBarPosType;case 5->base.magicGUIPosType;case 7->base.itemStorePowerPosType;case 6->c.neoStabilityBarPosType; default->7;};
        var anchor=UIPositionUtils.getCorrectPosition(type,0,0);
        int x=rawX-anchor.getLeft(),y=top-anchor.getRight();
        if(section==2 && c.cdMirrorRight)x=w-rawX-33-anchor.getLeft();
        if(section==4 && c.chargeMirrorRight)x=w-rawX-14-anchor.getLeft();
        if(section==3){int[] p=SpellbookHudRenderer.hudAnchor();x-=v.source().x()-p[0];y-=v.source().y()-p[1];}
        if(section==0)y+=2;
        if(section==1){x+=6;y+=2;}
        if(section==5)x-=6;
        if(section==7){
            x+=Math.max(1,net.jackcooper.shapeShifterCurseAddon.mixin.client.SscItemStoreLayoutAccessor.ssca$columns())*20+3;
            y+=Math.max(1,net.jackcooper.shapeShifterCurseAddon.mixin.client.SscItemStoreLayoutAccessor.ssca$rows())*20+4;
        }
        return new int[]{x,y};
    }
    public static Box itemStoreBounds(){
        var c=ShapeShifterCurseFabric.clientConfig;
        int cols=Math.max(1,net.jackcooper.shapeShifterCurseAddon.mixin.client.SscItemStoreLayoutAccessor.ssca$columns());
        int rows=Math.max(1,net.jackcooper.shapeShifterCurseAddon.mixin.client.SscItemStoreLayoutAccessor.ssca$rows());
        var p=UIPositionUtils.getCorrectPosition(c.itemStorePowerPosType,c.itemStorePowerPosOffsetX-cols*20,c.itemStorePowerPosOffsetY-rows*20);
        return new Box(p.getLeft()-3,p.getRight()-4,cols*20+9,rows*20+4);
    }
    private static int clamp(int p,int size,int extent){return Math.max(0,Math.min(p,Math.max(0,size-extent)));}
}
