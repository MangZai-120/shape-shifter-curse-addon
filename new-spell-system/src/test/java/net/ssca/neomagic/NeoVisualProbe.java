package net.ssca.neomagic;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.util.ScreenshotRecorder;
import net.ssca.neomagic.client.*;
import java.nio.file.*;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

public final class NeoVisualProbe {
    private static int tick,step;
    public static void install() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(mc->org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getHandle()));
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            try {
                if(mc.getOverlay()!=null)return;
                if(tick++%15!=0)return;
                if(step==0) {
                    NeoClient.data=new NeoState.PlayerData();NeoClient.data.known.addAll(NeoMagic.SAMPLES.keySet());NeoClient.table=true;
                    NeoClient.profile=new Profile(80,1.15,.85,1,1,false);NeoClient.data.form="my_addon:axolotl_fluorescent";
                    NeoClient.data.burden=95;NeoClient.data.hypothesis="分流分配已有作用量；保持其他条件不变。";
                    for(int i=0;i<2;i++) {
                        var b=new Blueprint("视觉检查样本",Core.THERMAL,Carrier.PROJECTILE,i==0?List.of():List.of(Control.SPLIT),Core.FROST,1,40,32);
                        var e=new NeoState.Experiment();e.blueprint=b;e.form=NeoClient.data.form;e.initial=70;e.normal=20;e.actual=20;e.paid=20;e.result="released";e.version="UI-test-fixture";
                        var session=new Session(b,quote(b,0,coefficients(Core.THERMAL)));double c=70;for(int t=0;t<=40;t++)c+=session.advance(t,c,NeoClient.profile,new Random(1));e.checks=session.checkpoints;NeoClient.data.records.add(e);
                    }
                    mc.options.language="zh_cn";mc.options.getGuiScale().setValue(2);mc.onResolutionChanged();
                    mc.setScreen(new NeoScreen());
                    snapshotRegression(mc);
                } else if(step<=8) {
                    var screen=(NeoScreen)mc.currentScreen;
                    if(step%2==1) {
                        if(step==1)click(screen,"panel.1");else click(screen,"page."+(step==3?0:step==5?2:3));
                    } else save(mc,"0"+(step/2)+"-"+(step==2?"craft":step==4?"observe":step==6?"experiment":"notes")+".png");
                } else if(step==9) {
                    mc.getWindow().setWindowedSize(1280,960);
                    mc.options.getGuiScale().setValue(4);mc.onResolutionChanged();
                    var screen=(NeoScreen)mc.currentScreen;click(screen,"page.1");click(screen,"panel.3");
                } else if(step==10) {
                    save(mc,"05-narrow-burden.png");
                    checkLayout((NeoScreen)mc.currentScreen);
                } else if(step==11)click((NeoScreen)mc.currentScreen,"panel.0");
                else if(step==12) {
                    save(mc,"06-narrow-components.png");checkLayout((NeoScreen)mc.currentScreen);
                } else if(step==13) {
                    mc.options.getGuiScale().setValue(2);mc.onResolutionChanged();mc.setScreen(new NeoHudPreviewScreen());
                } else if(step==14) {
                    save(mc,"07-stability-hud.png");((NeoHudPreviewScreen)mc.currentScreen).verify();
                } else if(step==15) {
                    mc.options.getGuiScale().setValue(4);mc.onResolutionChanged();
                } else if(step==16) {
                    save(mc,"08-stability-hud-narrow.png");((NeoHudPreviewScreen)mc.currentScreen).verify();
                    System.out.println("Neomagic native Minecraft UI: 8 screenshots; stability HUD safe/forecast/casting/critical fixtures, book strip and 320x240 bounds checked. Fixture data, not live network acceptance.");
                    mc.scheduleStop();
                }
                step++;
            } catch(Throwable error){error.printStackTrace();System.exit(2);}
        });
    }
    private static void snapshotRegression(net.minecraft.client.MinecraftClient mc) {
        var n=new net.minecraft.nbt.NbtCompound();n.putUuid("Token",UUID.randomUUID());n.putBoolean("Table",true);
        n.putByteArray("DataJson",NeoPayload.pack(NeoState.JSON.toJson(NeoClient.data).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        n.putString("Profile",NeoState.JSON.toJson(NeoClient.profile));n.putString("Rules","UI-test-fixture");
        Map<Core,Coefficients> costs=new EnumMap<>(Core.class);for(Core core:Core.values())costs.put(core,coefficients(core));
        n.putString("Costs",NeoState.JSON.toJson(costs));n.putByteArray("Tunings",NeoPayload.pack("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var book=new EnumMap<Core,double[]>(Core.class);for(Core core:Core.values())book.put(core,new double[]{1.2,1.24});
        book.put(Core.FROST,new double[]{1.2,0}); // Stacked opposing formations legitimately suppress power to zero.
        n.putString("BookEffects",NeoState.JSON.toJson(book));
        NeoClient.acceptSnapshot(mc,n);
        if(NeoClient.data.known.size()!=18||NeoClient.COSTS.size()!=7||NeoClient.data.burden!=95)throw new AssertionError("Compressed snapshot did not reach the real client receiver");
        if(NeoClient.BOOK_EFFECTS.get(Core.FROST)[1]!=0)throw new AssertionError("Valid zero-power book formation was rejected");
        var before=NeoClient.data;var token=NeoClient.token;var broken=n.copy();broken.putByteArray("DataJson",new byte[]{1,2,3});NeoClient.acceptSnapshot(mc,broken);
        if(NeoClient.data!=before||!NeoClient.token.equals(token))throw new AssertionError("Malformed snapshot partially changed client state");
        var quote=quote(Blueprint.starter(),0,coefficients(Core.THERMAL));
        if(Math.abs(NeoClient.preview(Blueprint.starter()).actual()-quote.actual()*1.2)>1e-8)throw new AssertionError("Book cost bonus missing from research preview");
        var invalidBook=n.copy();invalidBook.putString("BookEffects","{\"THERMAL\":[\"NaN\",1]}");NeoClient.acceptSnapshot(mc,invalidBook);
        if(NeoClient.data!=before||!NeoClient.token.equals(token)||NeoClient.BOOK_EFFECTS.get(Core.THERMAL)[0]!=1.2)throw new AssertionError("Invalid book modifier partially changed client state");
        System.out.println("Neomagic native client snapshot: valid compressed data applied; malformed data rejected atomically.");
    }
    private static void checkLayout(NeoScreen screen) {
        var widgets=screen.children().stream().filter(e->e instanceof ClickableWidget).map(e->(ClickableWidget)e).toList();
        for(var widget:widgets)if(widget.getX()<0||widget.getY()<0||widget.getX()+widget.getWidth()>screen.width||widget.getY()+widget.getHeight()>screen.height)throw new AssertionError("Widget outside viewport");
        for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++) {
            var a=widgets.get(i);var b=widgets.get(j);
            if(a.getX()<b.getX()+b.getWidth()&&b.getX()<a.getX()+a.getWidth()&&a.getY()<b.getY()+b.getHeight()&&b.getY()<a.getY()+a.getHeight())throw new AssertionError("Overlapping controls: "+a.getMessage()+" / "+b.getMessage());
        }
    }
    private static void click(NeoScreen screen,String key) {
        for(var child:screen.children())if(child instanceof ButtonWidget button&&button.getMessage().getString().equals(NeoMagic.text(key).getString())){button.onPress();return;}
        throw new AssertionError("Missing button "+key);
    }
    private static void save(net.minecraft.client.MinecraftClient mc,String name)throws Exception {
        Path folder=Path.of("screenshots");Files.createDirectories(folder);
        try(var shot=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())){shot.writeTo(folder.resolve(name));}
    }
}
