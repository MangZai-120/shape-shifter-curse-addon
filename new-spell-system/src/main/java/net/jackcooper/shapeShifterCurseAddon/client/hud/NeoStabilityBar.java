package net.jackcooper.shapeShifterCurseAddon.client.hud;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.ssca.neomagic.NeoMagic;

/** 保留玩家原始像素图，使用独立布局和服务端同步数值。 */
public final class NeoStabilityBar {
    public static final int WIDTH=97, HEIGHT=24, FRAME_HEIGHT=16, FRAME_Y=8;
    private static final Identifier SAFE=texture("frame-safe"),OVER=texture("frame-over"),NEUTRAL=texture("frame-neutral");
    private static final Identifier FILL=texture("fill-full"),FORECAST=texture("forecast-full"),MASK=texture("interior-mask");
    private static volatile int[] columns=new int[WIDTH];
    private static final String[] DIGITS={"111101101101111","010110010010111","111001111100111","111001111001111","101101111001001","111100111001111","111100111101111","111001010010010","111101111101111","111101111001111"};
    private NeoStabilityBar(){}
    private static Identifier texture(String name){return NeoMagic.id("textures/gui/stability/"+name+".png");}
    public static double positive(double v){return Double.isFinite(v)?Math.max(0,v):0;}
    public record View(double current,double safety,double quote,double remaining,double projected,double bodySafety,boolean selected,String phase) {
        public boolean safe(){return projected<=safety;}
        public double extent(){return Math.min(Double.MAX_VALUE,safety*2);}
        public double fraction(){return safety<=0?0:Math.max(.2,Math.min(.9,.2+.003*bodySafety));}
        public int edge(double n){return 12+(int)Math.round(84*coordinate(n,safety,fraction()));}
        public int marker(){return Math.min(95,edge(safety));}
        public boolean overflow(){return current>extent();}
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
            try(var input=manager.getResource(MASK).orElseThrow().getInputStream();var image=NativeImage.read(input)){
                if(image.getWidth()!=WIDTH||image.getHeight()!=FRAME_HEIGHT)throw new java.io.IOException("Expected 97x16 stability mask");
                for(int x=0;x<WIDTH;x++)for(int y=0;y<FRAME_HEIGHT;y++)if((image.getColor(x,y)>>>24)!=0)next[x]|=1<<y;
                columns=next;
            }catch(Exception error){NeoMagic.LOG.error("Unable to load stability bar mask",error);}
        }
    });}
    private static void pixel(DrawContext c,int originX,int originY,int x,int y,int color){if(x>=0&&x<WIDTH&&y>=0&&y<FRAME_HEIGHT&&(columns[x]&(1<<y))!=0)c.fill(originX+x,originY+FRAME_Y+y,originX+x+1,originY+FRAME_Y+y+1,color);}
    private static void pattern(DrawContext c,String[] rows,int x,int y,int color){for(int yy=0;yy<rows.length;yy++)for(int xx=0;xx<rows[yy].length();xx++)if(rows[yy].charAt(xx)=='1')c.fill(x+xx,y+yy,x+xx+1,y+yy+1,color);}
    private static String glyph(char ch){if(ch>='0'&&ch<='9')return DIGITS[ch-'0'];return switch(ch){case '/'->"001001010100100";case '+'->"000010111010000";case '-'->"000000111000000";case '.'->"000000000000010";case 'k'->"100101110101101";case 'm'->"000111111101101";case 'e'->"111100110100111";default->"111001010000010";};}
    private static int tiny(DrawContext c,String text,int x,int y,int color){
        // 先画一像素深色描边再画字面，高亮草地、天空和发光粒子下仍保持像素字形清晰。
        int start=x;
        for(int pass=0;pass<2;pass++){
            x=start;
            for(char ch:text.toCharArray()){
                String bits=glyph(ch);
                for(int i=0;i<15;i++)if(bits.charAt(i)=='1'){
                    int px=x+i%3,py=y+i/3;
                    if(pass==0){
                        c.fill(px-1,py,px+2,py+1,0xF010151B);
                        c.fill(px,py-1,px+1,py+2,0xF010151B);
                    }else c.fill(px,py,px+1,py+1,color);
                }
                x+=4;
            }
        }
        return x;
    }
    private static String number(double n){n=positive(n);if(n>=1e9)return String.format(java.util.Locale.ROOT,"%.0e",n).replace("e+0","e").replace("e+","e");if(n>=1e6)return shortNumber(n/1e6)+"m";if(n>=1000)return shortNumber(n/1000)+"k";return shortNumber(n);}
    private static String shortNumber(double n){String value=String.format(java.util.Locale.ROOT,"%.1f",n);return value.endsWith(".0")?value.substring(0,value.length()-2):value;}
    public static void draw(DrawContext c,NbtCompound state,int x,int y){
        c.draw();float[] color=com.mojang.blaze3d.systems.RenderSystem.getShaderColor().clone();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);
        // 原生批量绘制回调避免每个数字像素单独提交并分配缓冲区。
        try{c.draw(()->paint(c,state,x,y));}finally{c.draw();com.mojang.blaze3d.systems.RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);}
    }
    private static void paint(DrawContext c,NbtCompound state,int x,int y){
        View v=view(state);Identifier frame=!v.selected()?NEUTRAL:v.safe()?SAFE:OVER;
        c.drawTexture(frame,x,y+FRAME_Y,0,0,WIDTH,FRAME_HEIGHT,WIDTH,FRAME_HEIGHT);
        int paid=v.edge(v.current()),future=v.edge(v.projected());
        if(paid>12)c.drawTexture(FILL,x+12,y+FRAME_Y,12,0,paid-12,FRAME_HEIGHT,WIDTH,FRAME_HEIGHT);
        if(v.selected()&&future>paid)c.drawTexture(FORECAST,x+paid,y+FRAME_Y,paid,0,future-paid,FRAME_HEIGHT,WIDTH,FRAME_HEIGHT);
        for(int yy=3;yy<=13;yy++)pixel(c,x,y,v.marker(),yy,0xFFE9F5F2);
        if(v.overflow()||v.projected()>v.extent()){
            int color=v.overflow()?0xFFFFE0D2:0x73FFE0D2;
            int[][] arrow={{0,0},{1,1},{2,2},{1,3},{0,4}};for(int[] p:arrow)pixel(c,x,y,90+p[0],6+p[1],color);
        }
        int tx=tiny(c,number(v.current()),x+12,y+1,v.current()>v.safety()?0xFFF29D81:0xFFE5E9E7);
        tx=tiny(c,"/",tx,y+1,0xFFB7C2CA);tiny(c,number(v.safety()),tx,y+1,0xFF90CCD5);
        String cost=v.selected()?"+"+number(v.remaining()):"-";tiny(c,cost,x+96-(cost.length()*4-1),y+1,!v.selected()?0xFF87979C:v.safe()?0xFF7CD4E5:0xFFF07D75);
        String[] icon=switch(v.phase()){
            case "waiting"->new String[]{"111","101","010","101","111"};
            case "recovering"->new String[]{"00100","00100","11111","01110","00100"};
            case "casting"->new String[]{"000","000","101","000","000"};
            case "locked"->new String[]{"01110","01010","11111","11011","11111"};
            case "failed"->new String[]{"101","101","010","101","101"};
            case "blocked"->new String[]{"010","010","010","000","010"};
            case "reset"->new String[]{"01110","10001","10000","10011","01101"};
            default->null;
        };
        if(icon!=null)pattern(c,icon,x+2,y,v.phase().equals("failed")||v.phase().equals("blocked")?0xFFF17D73:v.phase().equals("recovering")?0xFF7CD4E5:0xFFC4CECE);
    }
}
