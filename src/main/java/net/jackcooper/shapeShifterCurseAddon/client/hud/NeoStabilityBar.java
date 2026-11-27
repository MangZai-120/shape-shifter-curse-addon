package net.jackcooper.shapeShifterCurseAddon.client.hud;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.jackcooper.shapeShifterCurseAddon.spell.NeoMagic;
import org.joml.Matrix4f;
import java.util.ArrayList;

/** 保留玩家原始像素图，使用独立布局和服务端同步数值。 */
public final class NeoStabilityBar {
    public static final int WIDTH=97, HEIGHT=24, FRAME_HEIGHT=16, FRAME_Y=8;
    private static final Identifier SAFE=texture("frame-safe"),OVER=texture("frame-over"),NEUTRAL=texture("frame-neutral");
    private static final Identifier FILL=texture("fill-full"),FORECAST=texture("forecast-full"),MASK=texture("interior-mask");
    private static final Identifier MAPPED_FILL=NeoMagic.id("stability_bar_fill");
    private static final int FILL_START=12,FILL_END=96,ORANGE_COLUMN=64;
    private static final int FIRST_MARKER=FILL_START+markerOffset(FILL_END-FILL_START,0);
    private static final int LAST_MARKER=FILL_START+markerOffset(FILL_END-FILL_START,Double.MAX_VALUE);
    private static final int ATLAS_HEIGHT=(LAST_MARKER-FIRST_MARKER+1)*FRAME_HEIGHT*2;
    private static volatile int[] columns=new int[WIDTH];
    private static final String[] DIGITS={"111101101101111","010110010010111","111001111100111","111001111001111","101101111001001","111100111001111","111100111101111","111001010010010","111101111101111","111101111001111"};
    private static final String GLYPH_CHARACTERS = "0123456789/+-.kme";
    private static final Glyph UNKNOWN_GLYPH = bakeGlyph(glyph('?'));
    private static final Glyph[] GLYPHS = new Glyph['m' + 1];
    static {
        java.util.Arrays.fill(GLYPHS, UNKNOWN_GLYPH);
        for (int i = 0; i < GLYPH_CHARACTERS.length(); i++) {
            char ch = GLYPH_CHARACTERS.charAt(i);
            GLYPHS[ch] = bakeGlyph(glyph(ch));
        }
    }
    // 只缓存显示文本；数值和状态仍读取当前服务端快照，编辑器预览同样即时更新。
    private static final double[] numberValues={Double.NaN,Double.NaN,Double.NaN};
    private static final String[] numberLabels=new String[3];
    private static final int[][] OVERFLOW_ARROW={{0,0},{1,1},{2,2},{1,3},{0,4}};
    private static final String[] WAITING={"111","101","010","101","111"},RECOVERING={"00100","00100","11111","01110","00100"},
            CASTING={"000","000","101","000","000"},LOCKED={"01110","01010","11111","11011","11111"},
            FAILED={"101","101","010","101","101"},BLOCKED={"010","010","010","000","010"},RESET={"01110","10001","10000","10011","01101"};
    private NeoStabilityBar(){}
    private static Identifier texture(String name){return NeoMagic.id("textures/gui/stability/"+name+".png");}
    public static double positive(double v){return Double.isFinite(v)?Math.max(0,v):0;}
    public record View(double current,double safety,double quote,double remaining,double projected,double bodySafety,boolean selected,String phase) {
        public boolean safe(){return projected<=safety;}
        public double extent(){return Math.min(Double.MAX_VALUE,safety*2);}
        public double fraction(){return (marker()-FILL_START)/(double)(FILL_END-FILL_START);}
        public int edge(double n){return FILL_START+(int)Math.round((FILL_END-FILL_START)*coordinate(n,safety,fraction()));}
        public int marker(){return FILL_START+markerOffset(FILL_END-FILL_START,bodySafety);}
        public boolean overflow(){return current>extent();}
    }
    /** 白线限定在轨道的 35%～65%，像素取整后也不会越界；玩家成长只改变数值比例。 */
    public static int markerOffset(int width,double bodySafety){
        double fraction=Math.max(.35,Math.min(.65,.2+.003*positive(bodySafety)));
        return Math.max((int)Math.ceil(width*.35),Math.min((int)Math.floor(width*.65),(int)Math.round(width*fraction)));
    }
    /** 安全段与危险段分别缩放，白线按形态基础值固定，玩家成长不会推动白线。 */
    public static double coordinate(double value,double safety,double fraction){
        if(safety<=0)return value>0?1:0;
        double ratio=positive(value)/safety;
        return Math.min(1,ratio<=1?ratio*fraction:fraction+(ratio-1)*(1-fraction));
    }
    public static View view(NbtCompound n){
        double current=positive(n.getDouble("C")),safety=positive(n.getDouble("S")),quote=positive(n.getDouble("Q"));
        int age=n.contains("Age")?n.getInt("Age"):-1;
        boolean stored=n.getBoolean("Stored");
        boolean selected=(n.contains("HasSpell")?n.getBoolean("HasSpell"):quote>0)||age>=0;
        double remaining=stored?0:age>=0?Math.max(0,quote-positive(n.getDouble("Paid"))):quote;
        String phase=n.getBoolean("RecentFailure")?"failed":n.getBoolean("RecentReset")?"reset":n.getInt("Blocked")>0?"blocked":stored?"locked":age>=0?(age>=Math.max(0,n.contains("LockBoundary")?n.getInt("LockBoundary"):n.getInt("Prep"))?"locked":"casting"):n.getInt("Recovery")>0?"waiting":current>0?"recovering":"idle";
        return new View(current,safety,quote,remaining,Math.min(Double.MAX_VALUE,current+(selected?remaining:0)),n.contains("BodyS")?positive(n.getDouble("BodyS")):safety,selected,phase);
    }
    /** 九宫格使用整屏坐标，与统一HUD编辑器保持一致。 */
    public static int[] anchor(int type,int width,int height){int t=Math.max(1,Math.min(9,type))-1;return new int[]{(t%3)*width/2,(t/3)*height/2};}
    public static int[] position(int type,int offsetX,int offsetY,int width,int height){
        int[] a=anchor(type,width,height);return new int[]{(int)Math.max(0,Math.min(Math.max(0,width-WIDTH),(long)a[0]+offsetX)),(int)Math.max(0,Math.min(Math.max(0,height-HEIGHT),(long)a[1]+offsetY))};
    }
    public static void registerResources(){ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener(){
        @Override public Identifier getFabricId(){return NeoMagic.id("stability_bar_mask");}
        @Override public void reload(ResourceManager manager){
            int[] next=new int[WIDTH];
            try(var input=manager.getResource(MASK).orElseThrow().getInputStream();var image=NativeImage.read(input);
                var fillInput=manager.getResource(FILL).orElseThrow().getInputStream();var fill=NativeImage.read(fillInput);
                var forecastInput=manager.getResource(FORECAST).orElseThrow().getInputStream();var forecast=NativeImage.read(forecastInput)){
                if(image.getWidth()!=WIDTH||image.getHeight()!=FRAME_HEIGHT)throw new java.io.IOException("Expected 97x16 stability mask");
                if(fill.getWidth()!=WIDTH||fill.getHeight()!=FRAME_HEIGHT||forecast.getWidth()!=WIDTH||forecast.getHeight()!=FRAME_HEIGHT)throw new java.io.IOException("Expected 97x16 stability fills");
                for(int x=0;x<WIDTH;x++)for(int y=0;y<FRAME_HEIGHT;y++)if((image.getColor(x,y)>>>24)!=0)next[x]|=1<<y;
                int[] fillPixels=new int[WIDTH*FRAME_HEIGHT],forecastPixels=new int[WIDTH*FRAME_HEIGHT];
                for(int y=0;y<FRAME_HEIGHT;y++)for(int x=0;x<WIDTH;x++){
                    fillPixels[y*WIDTH+x]=fill.getColor(x,y);forecastPixels[y*WIDTH+x]=forecast.getColor(x,y);
                }
                // 一次资源加载生成所有白线位置，逐帧仍只裁剪两张贴图，不缩放外框或额外上传纹理。
                var atlas=new NativeImage(WIDTH,ATLAS_HEIGHT,true);
                for(int marker=FIRST_MARKER;marker<=LAST_MARKER;marker++)for(int layer=0;layer<2;layer++){
                    int[] pixels=remapFill(layer==0?fillPixels:forecastPixels,next,marker);
                    int top=((marker-FIRST_MARKER)*2+layer)*FRAME_HEIGHT;
                    for(int y=0;y<FRAME_HEIGHT;y++)for(int x=0;x<WIDTH;x++)atlas.setColor(x,top+y,pixels[y*WIDTH+x]);
                }
                MinecraftClient.getInstance().getTextureManager().registerTexture(MAPPED_FILL,new NativeImageBackedTexture(atlas));
                columns=next;
            }catch(Exception error){NeoMagic.LOG.error("Unable to load stability bar mask",error);}
        }
    });}
    /** 原图蓝至橙色压入白线左侧，橙至红色铺满右侧；轮廓始终使用目标位置的遮罩。 */
    static int[] remapFill(int[] source,int[] mask,int marker){
        int[] result=new int[WIDTH*FRAME_HEIGHT];
        for(int x=FILL_START;x<FILL_END;x++){
            int sample=x<marker?FILL_START+(x-FILL_START)*(ORANGE_COLUMN-FILL_START-1)/Math.max(1,marker-FILL_START-1)
                    :ORANGE_COLUMN+(x-marker)*(FILL_END-ORANGE_COLUMN-1)/Math.max(1,FILL_END-marker-1);
            for(int y=0;y<FRAME_HEIGHT;y++)if((mask[x]&(1<<y))!=0){
                // 原图两端及高光有镂空：只取同一行最近的有效颜色，避免缩放后在条内产生空洞。
                int sx=sample;
                for(int distance=0;distance<WIDTH;distance++){
                    int left=sample-distance,right=sample+distance;
                    if(left>=FILL_START&&(source[y*WIDTH+left]>>>24)!=0){sx=left;break;}
                    if(right<FILL_END&&(source[y*WIDTH+right]>>>24)!=0){sx=right;break;}
                }
                result[y*WIDTH+x]=source[y*WIDTH+sx];
            }
        }
        return result;
    }
    /** 像素四边形共用原生 GUI 顶点缓冲，由 draw 末尾统一提交。 */
    private record Pixels(Matrix4f matrix, VertexConsumer buffer) {
        void fill(int x1, int y1, int x2, int y2, int color) {
            buffer.vertex(matrix,x2,y2,0).color(color).next();
            buffer.vertex(matrix,x2,y1,0).color(color).next();
            buffer.vertex(matrix,x1,y1,0).color(color).next();
            buffer.vertex(matrix,x1,y2,0).color(color).next();
        }
    }
    private static void pixel(Pixels pixels,int originX,int originY,int x,int y,int color){if(x>=0&&x<WIDTH&&y>=0&&y<FRAME_HEIGHT&&(columns[x]&(1<<y))!=0)pixels.fill(originX+x,originY+FRAME_Y+y,originX+x+1,originY+FRAME_Y+y+1,color);}
    private static void pattern(Pixels pixels,String[] rows,int x,int y,int color){for(int yy=0;yy<rows.length;yy++)for(int xx=0;xx<rows[yy].length();xx++)if(rows[yy].charAt(xx)=='1')pixels.fill(x+xx,y+yy,x+xx+1,y+yy+1,color);}
    private static String glyph(char ch){if(ch>='0'&&ch<='9')return DIGITS[ch-'0'];return switch(ch){case '/'->"001001010100100";case '+'->"000010111010000";case '-'->"000000111000000";case '.'->"000000000000010";case 'k'->"100101110101101";case 'm'->"000111111101101";case 'e'->"111100110100111";default->"111001010000010";};}
    private record Rect(int left, int top, int right, int bottom) {}
    private record Glyph(Rect[] outline, Rect[] face) {}

    private static Glyph bakeGlyph(String bits) {
        var outline = new ArrayList<Rect>();
        var face = new ArrayList<Rect>();
        // Join edge-adjacent strokes across a row or column. Overlapping translucent strokes
        // still produce separate quads and blend exactly as many times as the original pixels.
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 3;) {
                int start = x++;
                if (bits.charAt(3 * y + start) != '1') continue;
                while (x < 3 && bits.charAt(3 * y + x) == '1') x++;
                face.add(new Rect(start, y, x, y + 1));
                outline.add(new Rect(start, y - 1, x, y + 2));
            }
        }
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 5;) {
                int start = y++;
                if (bits.charAt(3 * start + x) != '1') continue;
                while (y < 5 && bits.charAt(3 * y + x) == '1') y++;
                outline.add(new Rect(x - 1, start, x + 2, y));
            }
        }
        return new Glyph(outline.toArray(Rect[]::new), face.toArray(Rect[]::new));
    }

    private static int tiny(Pixels pixels, String text, int x, int y, int color) {
        for (int pass = 0; pass < 2; pass++) {
            int ink = pass == 0 ? 0xF010151B : color;
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                Glyph shape = ch < GLYPHS.length ? GLYPHS[ch] : UNKNOWN_GLYPH;
                int left = x + i * 4;
                for (Rect r : pass == 0 ? shape.outline() : shape.face()) {
                    pixels.fill(left + r.left(), y + r.top(), left + r.right(), y + r.bottom(), ink);
                }
            }
        }
        return x + text.length() * 4;
    }
    private static String number(double n){n=positive(n);if(n>=1e9)return String.format(java.util.Locale.ROOT,"%.0e",n).replace("e+0","e").replace("e+","e");if(n>=1e6)return shortNumber(n/1e6)+"m";if(n>=1000)return shortNumber(n/1000)+"k";return shortNumber(n);}
    private static String shortNumber(double n){String value=String.format(java.util.Locale.ROOT,"%.1f",n);return value.endsWith(".0")?value.substring(0,value.length()-2):value;}
    private static String number(int field,double value){
        value=positive(value);
        if(numberLabels[field]==null||numberValues[field]!=value){numberValues[field]=value;numberLabels[field]=number(value);}
        return numberLabels[field];
    }
    public static void draw(DrawContext c,NbtCompound state,int x,int y){
        c.draw();float[] color=com.mojang.blaze3d.systems.RenderSystem.getShaderColor().clone();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);
        try{paint(c,state,x,y);}finally{c.draw();com.mojang.blaze3d.systems.RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);}
    }
    private static void paint(DrawContext c,NbtCompound state,int x,int y){
        View v=view(state);Identifier frame=!v.selected()?NEUTRAL:v.safe()?SAFE:OVER;
        c.drawTexture(frame,x,y+FRAME_Y,0,0,WIDTH,FRAME_HEIGHT,WIDTH,FRAME_HEIGHT);
        int paid=v.edge(v.current()),future=v.edge(v.projected());
        int fillRow=(v.marker()-FIRST_MARKER)*FRAME_HEIGHT*2;
        if(paid>12)c.drawTexture(MAPPED_FILL,x+12,y+FRAME_Y,12,fillRow,paid-12,FRAME_HEIGHT,WIDTH,ATLAS_HEIGHT);
        if(v.selected()&&future>paid)c.drawTexture(MAPPED_FILL,x+paid,y+FRAME_Y,paid,fillRow+FRAME_HEIGHT,future-paid,FRAME_HEIGHT,WIDTH,ATLAS_HEIGHT);
        var pixels = new Pixels(c.getMatrices().peek().getPositionMatrix(), c.getVertexConsumers().getBuffer(RenderLayer.getGui()));
        for(int yy=3;yy<=13;yy++)pixel(pixels,x,y,v.marker(),yy,0xFFE9F5F2);
        if(v.overflow()||v.projected()>v.extent()){
            int color=v.overflow()?0xFFFFE0D2:0x73FFE0D2;
            for(int[] p:OVERFLOW_ARROW)pixel(pixels,x,y,90+p[0],6+p[1],color);
        }
        int tx=tiny(pixels,number(0,v.current()),x+12,y+1,v.current()>v.safety()?0xFFF29D81:0xFFE5E9E7);
        tx=tiny(pixels,"/",tx,y+1,0xFFB7C2CA);tiny(pixels,number(1,v.safety()),tx,y+1,0xFF90CCD5);
        String cost=v.selected()?"+"+number(2,v.remaining()):"-";tiny(pixels,cost,x+96-(cost.length()*4-1),y+1,!v.selected()?0xFF87979C:v.safe()?0xFF7CD4E5:0xFFF07D75);
        String[] icon=switch(v.phase()){
            case "waiting"->WAITING;
            case "recovering"->RECOVERING;
            case "casting"->CASTING;
            case "locked"->LOCKED;
            case "failed"->FAILED;
            case "blocked"->BLOCKED;
            case "reset"->RESET;
            default->null;
        };
        if(icon!=null)pattern(pixels,icon,x+2,y,v.phase().equals("failed")||v.phase().equals("blocked")?0xFFF17D73:v.phase().equals("recovering")?0xFF7CD4E5:0xFFC4CECE);
    }
}
