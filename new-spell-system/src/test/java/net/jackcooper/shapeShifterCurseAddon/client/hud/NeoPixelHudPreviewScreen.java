package net.jackcooper.shapeShifterCurseAddon.client.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import java.util.*;

/** Explicit fixtures painted by the production pixel HUD, at two real GUI scales. */
public final class NeoPixelHudPreviewScreen extends Screen {
    /** 主菜单没有玩家实体：容器逻辑另在真实世界验证，此处仅委托正式书页完成原生绘制。 */
    public static final class BookPreview extends Screen {
        private final net.jackcooper.shapeShifterCurseAddon.client.screen.SpellbookScreen book;
        public BookPreview(net.jackcooper.shapeShifterCurseAddon.screen.SpellbookScreenHandler handler,net.minecraft.entity.player.PlayerInventory inventory,Text title){super(title);book=new net.jackcooper.shapeShifterCurseAddon.client.screen.SpellbookScreen(handler,inventory,title);}
        @Override protected void init(){book.init(net.minecraft.client.MinecraftClient.getInstance(),width,height);}
        @Override public void render(DrawContext context,int x,int y,float delta){book.render(context,x,y,delta);}
        public net.jackcooper.shapeShifterCurseAddon.client.screen.SpellbookScreen book(){return book;}
    }
    private final List<NbtCompound> examples=new ArrayList<>();
    private final List<int[]> bounds=new ArrayList<>();
    private final boolean bright;
    private static final String[] LABELS={"未选法术","安全施法","预计恰好安全","预计越线","当前等于阈值","超额5","超额20","超额40","显示末端","超出显示范围","逐步支付","不可打断","失败已发生","等待恢复","恢复中","变形归零","形态S200","玩家成长","卸书保留上限","熟练减耗"};
    public NeoPixelHudPreviewScreen(){
        this(false);
    }
    public NeoPixelHudPreviewScreen(boolean bright){
        super(Text.literal("独立稳定度条 · 20状态原生像素预览"));
        this.bright=bright;
        double[][] data={{0,80,0},{20,80,12},{70,80,10},{70,80,25},{80,80,0},{85,80,10},{100,80,15},{120,80,15},{160,80,0},{165,80,20},{82,80,25},{92,80,25},{83,80,0},{72,80,0},{68,80,0},{0,200,0},{70,200,25},{95,130,20},{95,130,20},{70,80,10}};
        for(double[] v:data){var n=new NbtCompound();n.putDouble("C",v[0]);n.putDouble("S",v[1]);n.putDouble("Q",v[2]);n.putInt("Age",-1);n.putBoolean("HasSpell",v[2]>0);examples.add(n);}
        examples.get(10).putInt("Age",20);examples.get(10).putInt("Prep",40);examples.get(10).putDouble("Paid",12);
        examples.get(11).putInt("Age",36);examples.get(11).putInt("Prep",40);examples.get(11).putInt("LockBoundary",30);examples.get(11).putDouble("Paid",22);
        examples.get(12).putBoolean("RecentFailure",true);examples.get(13).putInt("Recovery",40);examples.get(15).putBoolean("RecentReset",true);examples.get(17).putDouble("BodyS",80);examples.get(18).putDouble("BodyS",80);
    }
    @Override public void render(DrawContext c,int mx,int my,float delta){
        c.fill(0,0,width,height,bright?0xFFF8F1D0:0xFF1E2023);c.drawText(textRenderer,title,20,15,bright?0xFF28333A:0xFFE2E8E7,false);
        c.drawText(textRenderer,Text.literal("玩家左侧原图 · 实际C / 安全S · ＋未付部分"),20,32,0xFF9FB4BE,false);
        bounds.clear();boolean wide=width>=560;int columns=wide?4:2,rowHeight=wide?77:56;
        int[] indices=wide?java.util.stream.IntStream.range(0,20).toArray():new int[]{1,3,4,9,16,17};
        for(int i=0;i<indices.length;i++){
            int index=indices[i],x=20+(i%columns)*((width-28)/columns),y=61+(i/columns)*rowHeight;
            if(bright)c.fill(x-3,y-2,x+101,y+26,switch(i%4){case 0->0xFFFFFFFF;case 1->0xFFFFE880;case 2->0xFFB5F1FF;default->0xFFA3D45C;});
            c.drawText(textRenderer,Text.literal((index+1)+" "+LABELS[index]),x,y-12,bright?0xFF28333A:0xFFCAD6DB,false);
            NeoStabilityBar.draw(c,examples.get(index),x,y);bounds.add(new int[]{x,y,97,24});
        }
        c.drawText(textRenderer,Text.literal("测试夹具；完整外框与游戏使用同一渲染器。"),20,height-15,0xFF7D959F,false);
    }
    public void verify(){for(int[] b:bounds)if(b[0]<0||b[1]<0||b[0]+b[2]>width||b[1]+b[3]>height)throw new AssertionError("Pixel stability bar leaves viewport");if(bounds.isEmpty())throw new AssertionError("No pixel HUD rendered");}
}
