package net.jackcooper.shapeShifterCurseAddon.client.hud;

/** Shared uniform geometry for rendering, hit testing and drag bounds. No game state. */
public final class HudScaleGeometry {
    public static final int MIN = 50, MAX = 200, DEFAULT = 100;
    public record Box(int x,int y,int width,int height) {
        public boolean contains(double px,double py,int padding) {
            return px>=x-padding && px<=x+width+padding && py>=y-padding && py<=y+height+padding;
        }
    }
    public record View(Box source,Box bounds,float scale) {}
    private HudScaleGeometry() {}
    public static int percent(int value) { return Math.max(MIN,Math.min(MAX,value)); }
    public static View place(Box source,int percent,int screenW,int screenH,boolean rightAligned) {
        float scale=percent(percent)/100f;
        scale=Math.min(scale,Math.min(screenW/(float)Math.max(1,source.width),screenH/(float)Math.max(1,source.height)));
        scale=Math.max(.01f,scale);
        int w=Math.max(1,(int)Math.ceil(source.width*scale)),h=Math.max(1,(int)Math.ceil(source.height*scale));
        int x=rightAligned?source.x+source.width-w:source.x;
        int y=source.y;
        x=Math.max(0,Math.min(x,Math.max(0,screenW-w)));
        y=Math.max(0,Math.min(y,Math.max(0,screenH-h)));
        return new View(source,new Box(x,y,w,h),scale);
    }
}
