package net.ssca.neomagic;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.util.ScreenshotRecorder;
import net.ssca.neomagic.client.*;
import net.jackcooper.shapeShifterCurseAddon.client.hud.NeoStabilityBar;
import net.jackcooper.shapeShifterCurseAddon.client.hud.NeoPixelHudPreviewScreen;
import java.nio.file.*;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

public final class NeoVisualProbe {
    private static int tick,step;
    private static net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig originalHudConfig;
    private static net.onixary.shapeShifterCurseFabric.config.ClientConfig originalBaseHudConfig;
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
                } else if(step==17){
                    mc.options.getGuiScale().setValue(2);mc.onResolutionChanged();mc.setScreen(new NeoPixelHudPreviewScreen());
                } else if(step==18){
                    save(mc,"09-pixel-stability-all-states.png");((NeoPixelHudPreviewScreen)mc.currentScreen).verify();
                } else if(step==19){
                    mc.options.getGuiScale().setValue(4);mc.onResolutionChanged();
                } else if(step==20){
                    save(mc,"10-pixel-stability-narrow.png");((NeoPixelHudPreviewScreen)mc.currentScreen).verify();
                } else if(step==21){
                    mc.options.getGuiScale().setValue(2);mc.onResolutionChanged();
                    var cfg=net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig.client();
                    var base=net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric.clientConfig;
                    originalHudConfig=NeoState.JSON.fromJson(NeoState.JSON.toJson(cfg),net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig.class);
                    originalBaseHudConfig=NeoState.JSON.fromJson(NeoState.JSON.toJson(base),net.onixary.shapeShifterCurseFabric.config.ClientConfig.class);
                    cfg.neoStabilityBarPosType=7;cfg.neoStabilityBarPosOffsetX=16;cfg.neoStabilityBarPosOffsetY=-88;cfg.showNeoStabilityBar=true;
                    base.manaBarPosType=1;base.manaBarPosOffsetX=300;base.manaBarPosOffsetY=100;
                    base.instinctBarPosType=1;base.instinctBarPosOffsetX=300;base.instinctBarPosOffsetY=130;
                    base.magicGUIPosType=1;base.magicGUIPosOffsetX=400;base.magicGUIPosOffsetY=170;
                    mc.setScreen(new net.jackcooper.shapeShifterCurseAddon.compat.SSCAddonConfigMenuScreen(null));
                } else if(step==22){
                    save(mc,"11-unified-hud-entry.png");clickButton(mc.currentScreen,net.minecraft.text.Text.translatable("text.ssc_addon.bar_editor.open"));
                    expectInputs(mc.currentScreen,0);expectEditorChrome(mc.currentScreen);
                } else if(step==23){
                    save(mc,"12-hud-editor-unselected.png");selectStability(mc);expectInputs(mc.currentScreen,2);expectTarget(mc.currentScreen,"stability");
                } else if(step==24){
                    save(mc,"13-hud-editor-stability.png");var fields=inputs(mc.currentScreen);fields.get(0).setText("35");fields.get(1).setText("-110");
                    var cfg=net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig.client();var base=net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric.clientConfig;
                    if(cfg.neoStabilityBarPosOffsetX!=35||base.manaBarPosOffsetX!=300)throw new AssertionError("Selected stability controls modified another HUD");
                    clickPreview(mc.currentScreen,330,102);expectTarget(mc.currentScreen,"mana");expectInputs(mc.currentScreen,2);inputs(mc.currentScreen).get(0).setText("280");
                    if(cfg.neoStabilityBarPosOffsetX!=35||base.manaBarPosOffsetX!=280)throw new AssertionError("Selected mana controls modified stability position");
                } else if(step==25){
                    save(mc,"14-hud-editor-mana.png");clickPreview(mc.currentScreen,mc.currentScreen.width-90,mc.currentScreen.height-90);expectInputs(mc.currentScreen,0);
                } else if(step==26){
                    save(mc,"15-hud-editor-cleared.png");selectStability(mc);var screen=mc.currentScreen;
                    var cfg=net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig.client();int[] p=NeoStabilityBar.position(7,cfg.neoStabilityBarPosOffsetX,cfg.neoStabilityBarPosOffsetY,screen.width,screen.height);
                    screen.mouseClicked(p[0]+10,p[1]+16,0);screen.mouseDragged(p[0]+30,p[1]+31,0,20,15);screen.mouseReleased(p[0]+30,p[1]+31,0);screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT,0,0);
                    if(cfg.neoStabilityBarPosOffsetX!=56||cfg.neoStabilityBarPosOffsetY!=-95)throw new AssertionError("Unified drag/arrow position mismatch: "+cfg.neoStabilityBarPosOffsetX+","+cfg.neoStabilityBarPosOffsetY);
                    openExitMenu(screen);expectExitMenu(screen);
                } else if(step==27){
                    save(mc,"18-hud-editor-exit-menu.png");var editor=mc.currentScreen;
                    // 弹窗期间禁止透传点击、拖动或方向键；取消后保留工作值与当前目标。
                    clickPreview(editor,20,editor.height-72);editor.mouseDragged(50,editor.height-60,0,30,12);editor.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT,0,0);
                    expectExitMenu(editor);
                    clickButton(editor,net.minecraft.text.Text.translatable("gui.cancel"));expectTarget(editor,"stability");expectInputs(editor,2);
                    if(net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig.client().neoStabilityBarPosOffsetX!=56)throw new AssertionError("Exit popup changed pending HUD positions");
                    editor.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);expectExitMenu(editor);
                    clickButton(editor,net.minecraft.text.Text.translatable("text.ssc_addon.bar_editor.exit.save"));
                    if(!(mc.currentScreen instanceof net.jackcooper.shapeShifterCurseAddon.compat.SSCAddonConfigMenuScreen))throw new AssertionError("Save did not return to the editor parent");
                    var saved=NeoState.JSON.fromJson(Files.readString(Path.of("config/ssc_addon_client.json")),net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig.class);
                    if(saved.neoStabilityBarPosOffsetX!=56||saved.neoStabilityBarPosOffsetY!=-95)throw new AssertionError("Unified HUD position not written to disk");
                    mc.setScreen(new net.jackcooper.shapeShifterCurseAddon.client.screen.BarPositionEditorScreen(null));selectStability(mc);inputs(mc.currentScreen).get(0).setText("999");mc.currentScreen.close();
                    editor=mc.currentScreen;expectExitMenu(editor);
                    editor.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);expectInputs(editor,2);
                    if(net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig.client().neoStabilityBarPosOffsetX!=500)throw new AssertionError("Escape from popup discarded pending changes");
                    openExitMenu(editor);clickButton(editor,net.minecraft.text.Text.translatable("text.ssc_addon.bar_editor.exit.discard"));
                    if(mc.currentScreen==editor)throw new AssertionError("Discard did not exit the editor");
                    if(net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig.client().neoStabilityBarPosOffsetX!=56)throw new AssertionError("Cancel did not roll back the unified HUD working copy");
                    saved=NeoState.JSON.fromJson(Files.readString(Path.of("config/ssc_addon_client.json")),net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig.class);
                    if(saved.neoStabilityBarPosOffsetX!=56)throw new AssertionError("Discard overwrote the saved HUD positions");
                } else if(step==28){
                    var cfg=net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig.client();cfg.showNeoStabilityBar=false;
                    mc.setScreen(new net.jackcooper.shapeShifterCurseAddon.client.screen.BarPositionEditorScreen(null));selectStability(mc);expectTarget(mc.currentScreen,"stability");
                    clickButton(mc.currentScreen,net.minecraft.text.Text.translatable("text.ssc_addon.bar_editor.hidden"));
                    if(!cfg.showNeoStabilityBar)throw new AssertionError("A hidden stability HUD cannot be selected and enabled");
                    openExitMenu(mc.currentScreen);clickButton(mc.currentScreen,net.minecraft.text.Text.translatable("text.ssc_addon.bar_editor.exit.save"));
                } else if(step==29){
                    mc.setScreen(new net.jackcooper.shapeShifterCurseAddon.client.screen.BarPositionEditorScreen(null));
                    clickPreview(mc.currentScreen,320,132);expectTarget(mc.currentScreen,"instinct");
                    var cfg=net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig.client();var a=net.onixary.shapeShifterCurseFabric.util.UIPositionUtils.getCorrectPosition(cfg.cdBarPosType,cfg.cdBarPosOffsetX,cfg.cdBarPosOffsetY);
                    var layout=net.jackcooper.shapeShifterCurseAddon.client.hud.SkillCooldownBarRenderer.panelLayout(a.getLeft(),a.getRight(),cfg.cdMirrorRight,mc.currentScreen.width,mc.currentScreen.height);
                    clickPreview(mc.currentScreen,layout.primaryX()+10,layout.primaryY()+10);expectTarget(mc.currentScreen,"cd");
                    clickPreview(mc.currentScreen,mc.currentScreen.width-8,mc.currentScreen.height/2-20);expectTarget(mc.currentScreen,"charge");
                    clickPreview(mc.currentScreen,420,180);expectTarget(mc.currentScreen,"ssc_magic");
                    clickPreview(mc.currentScreen,40,mc.currentScreen.height-40);expectTarget(mc.currentScreen,"spellbook");expectInputs(mc.currentScreen,2);
                } else if(step==30){
                    mc.options.getGuiScale().setValue(4);mc.onResolutionChanged();mc.setScreen(new net.jackcooper.shapeShifterCurseAddon.client.screen.BarPositionEditorScreen(null));
                    expectInputs(mc.currentScreen,0);selectStability(mc);expectInputs(mc.currentScreen,2);
                } else if(step==31){
                    save(mc,"16-hud-editor-stability-narrow.png");
                    for(var child:mc.currentScreen.children())if(child instanceof ClickableWidget w)if(w.getX()<0||w.getY()<0||w.getX()+w.getWidth()>mc.currentScreen.width||w.getY()+w.getHeight()>mc.currentScreen.height)throw new AssertionError("Contextual HUD controls clipped at 320x240");
                    clickPreview(mc.currentScreen,240,160);expectInputs(mc.currentScreen,0);
                } else if(step==32){
                    save(mc,"17-hud-editor-unselected-narrow.png");expectEditorChrome(mc.currentScreen);
                    openExitMenu(mc.currentScreen);expectExitMenu(mc.currentScreen);
                } else if(step==33){
                    save(mc,"19-hud-editor-exit-menu-narrow.png");var editor=mc.currentScreen;
                    for(var child:editor.children())if(child instanceof ClickableWidget w)if(w.getX()<0||w.getY()<0||w.getX()+w.getWidth()>editor.width||w.getY()+w.getHeight()>editor.height)throw new AssertionError("Exit popup clipped at 320x240");
                    editor.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);expectEditorChrome(editor);expectInputs(editor,0);
                    openExitMenu(editor);expectExitMenu(editor);clickButton(editor,net.minecraft.text.Text.translatable("gui.cancel"));expectEditorChrome(editor);
                    restoreHudFixtures();
                    mc.options.getGuiScale().setValue(2);mc.onResolutionChanged();mc.setScreen(new NeoPixelHudPreviewScreen(true));
                } else if(step==34){
                    save(mc,"20-pixel-stability-bright.png");((NeoPixelHudPreviewScreen)mc.currentScreen).verify();
                    mc.options.getGuiScale().setValue(4);mc.onResolutionChanged();
                } else if(step==35){
                    save(mc,"21-pixel-stability-bright-narrow.png");((NeoPixelHudPreviewScreen)mc.currentScreen).verify();
                    mc.options.getGuiScale().setValue(2);mc.onResolutionChanged();openBookPreview(mc,100,100,0);
                } else if(step==36){
                    save(mc,"22-book-player-information.png");checkBookBounds(mc);
                    openBookPreview(mc,150,100,6000);
                } else if(step==37){
                    save(mc,"23-book-human-level-one.png");openBookPreview(mc,250,200,6000);
                    NeoClient.hud.putDouble("C",275);
                } else if(step==38){
                    save(mc,"24-book-familiar-danger.png");
                    mc.options.getGuiScale().setValue(4);mc.onResolutionChanged();
                } else if(step==39){
                    save(mc,"25-book-player-information-narrow.png");checkBookBounds(mc);
                    System.out.println("Neomagic native Minecraft UI: 25 screenshots; HUD editor regressions, bright pixel text, fixed form markers and book sidebar with synchronized player level/XP, 0-N safety, N-2N danger and six-second recovery; normal and 320x240 viewports verified. Fixture data, not live multiplayer acceptance.");
                    mc.scheduleStop();
                }
                step++;
            } catch(Throwable error){error.printStackTrace();System.exit(2);}
        });
    }
    private static void clickButton(net.minecraft.client.gui.screen.Screen screen,net.minecraft.text.Text text){for(var child:screen.children())if(child instanceof ButtonWidget b&&b.getMessage().getString().equals(text.getString())){b.onPress();return;}throw new AssertionError("Missing HUD setting "+text.getString());}
    private static void openBookPreview(net.minecraft.client.MinecraftClient mc,double safety,double body,int experience){
        var state=new net.minecraft.nbt.NbtCompound();state.putDouble("S",safety);state.putDouble("BodyS",body);state.putInt("MasteryExpTen",experience);state.putInt("RecoveryDelay",120);
        NeoClient.hud=state;
        var inventory=new net.minecraft.entity.player.PlayerInventory(null);
        var packet=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        packet.writeInt(3);packet.writeInt(1);packet.writeInt(0);packet.writeInt(0);packet.writeInt(100);packet.writeInt(0);packet.writeNbt(state);
        var handler=new net.jackcooper.shapeShifterCurseAddon.screen.SpellbookScreenHandler(1,inventory,packet);packet.release();
        if(handler.playerState.getDouble("S")!=safety)throw new AssertionError("Opening book snapshot lost player safety");
        mc.setScreen(new NeoPixelHudPreviewScreen.BookPreview(handler,inventory,net.minecraft.text.Text.translatable("item.ssc_addon.moon_dust_spellbook")));
    }
    private static void checkBookBounds(net.minecraft.client.MinecraftClient mc){
        var screen=((NeoPixelHudPreviewScreen.BookPreview)mc.currentScreen).book();
        int x=(screen.width-318)/2,y=(screen.height-166)/2;
        if(x<0||y<0||x+318>screen.width||y+166>screen.height)throw new AssertionError("Book player sidebar leaves viewport");
        for(var slot:screen.getScreenHandler().slots)if(x+slot.x<0||x+slot.x+16>screen.width||y+slot.y<0||y+slot.y+16>screen.height)throw new AssertionError("Book item slots leave viewport");
    }
    private static void expectEditorChrome(net.minecraft.client.gui.screen.Screen screen){
        var buttons=screen.children().stream().filter(e->e instanceof ButtonWidget).map(e->(ButtonWidget)e).toList();
        if(buttons.size()!=1)throw new AssertionError("Unselected editor must have only a close button");
        var close=buttons.get(0);
        if(!close.getMessage().getString().equals(net.minecraft.text.Text.translatable("text.ssc_addon.bar_editor.close").getString())||close.getX()!=screen.width-24||close.getY()!=4)throw new AssertionError("Close button is not at the top right");
    }
    private static void openExitMenu(net.minecraft.client.gui.screen.Screen screen){clickPreview(screen,screen.width-14,14);}
    private static void expectExitMenu(net.minecraft.client.gui.screen.Screen screen){
        expectInputs(screen,0);var buttons=screen.children().stream().filter(e->e instanceof ButtonWidget).map(e->(ButtonWidget)e).toList();
        var labels=java.util.List.of("text.ssc_addon.bar_editor.exit.save","text.ssc_addon.bar_editor.exit.discard","gui.cancel").stream().map(k->net.minecraft.text.Text.translatable(k).getString()).toList();
        if(buttons.size()!=3||!buttons.stream().map(b->b.getMessage().getString()).toList().equals(labels))throw new AssertionError("Exit popup must contain save, discard and cancel only");
    }
    private static java.util.List<TextFieldWidget> inputs(net.minecraft.client.gui.screen.Screen screen){return screen.children().stream().filter(e->e instanceof TextFieldWidget).map(e->(TextFieldWidget)e).toList();}
    private static void expectInputs(net.minecraft.client.gui.screen.Screen screen,int expected){if(inputs(screen).size()!=expected)throw new AssertionError("Expected "+expected+" contextual fields, got "+inputs(screen).size());}
    private static void clickPreview(net.minecraft.client.gui.screen.Screen screen,double x,double y){screen.mouseClicked(x,y,0);screen.mouseReleased(x,y,0);}
    private static void selectStability(net.minecraft.client.MinecraftClient mc){var cfg=net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig.client();int[] p=NeoStabilityBar.position(cfg.neoStabilityBarPosType,cfg.neoStabilityBarPosOffsetX,cfg.neoStabilityBarPosOffsetY,mc.currentScreen.width,mc.currentScreen.height);clickPreview(mc.currentScreen,p[0]+40,p[1]+16);}
    private static void expectTarget(net.minecraft.client.gui.screen.Screen screen,String key){String target=net.minecraft.text.Text.translatable("text.ssc_addon.bar_editor."+key).getString();if(screen.children().stream().filter(e->e instanceof ButtonWidget).map(e->((ButtonWidget)e).getMessage().getString()).noneMatch(s->s.contains(target)))throw new AssertionError("Missing selected HUD controls: "+key);}
    private static void restoreHudFixtures(){
        var cfg=net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig.client();var base=net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric.clientConfig;
        cfg.showNeoStabilityBar=originalHudConfig.showNeoStabilityBar;cfg.showDisintegrationDebugBar=originalHudConfig.showDisintegrationDebugBar;cfg.neoStabilityBarPosType=originalHudConfig.neoStabilityBarPosType;cfg.neoStabilityBarPosOffsetX=originalHudConfig.neoStabilityBarPosOffsetX;cfg.neoStabilityBarPosOffsetY=originalHudConfig.neoStabilityBarPosOffsetY;
        base.manaBarPosType=originalBaseHudConfig.manaBarPosType;base.manaBarPosOffsetX=originalBaseHudConfig.manaBarPosOffsetX;base.manaBarPosOffsetY=originalBaseHudConfig.manaBarPosOffsetY;
        base.instinctBarPosType=originalBaseHudConfig.instinctBarPosType;base.instinctBarPosOffsetX=originalBaseHudConfig.instinctBarPosOffsetX;base.instinctBarPosOffsetY=originalBaseHudConfig.instinctBarPosOffsetY;
        base.magicGUIPosType=originalBaseHudConfig.magicGUIPosType;base.magicGUIPosOffsetX=originalBaseHudConfig.magicGUIPosOffsetX;base.magicGUIPosOffsetY=originalBaseHudConfig.magicGUIPosOffsetY;
        me.shedaniel.autoconfig.AutoConfig.getConfigHolder(net.jackcooper.shapeShifterCurseAddon.config.SSCAddonClientConfig.class).save();me.shedaniel.autoconfig.AutoConfig.getConfigHolder(net.onixary.shapeShifterCurseFabric.config.ClientConfig.class).save();
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
