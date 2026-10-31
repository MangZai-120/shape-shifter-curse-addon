package net.jackcooper.shapeShifterCurseAddon.client;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jackcooper.shapeShifterCurseAddon.item.MoonDustReliefItem;
import net.jackcooper.shapeShifterCurseAddon.item.PotionTestTubes;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.effect.UniversalEnergyEffect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.*;
import net.minecraft.util.Hand;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import net.ssca.neomagic.*;
import net.ssca.neomagic.client.NeoClient;

/** 独立真实客户端通过正常右键包饮用、投掷，并核对同步崩解度与模型。 */
public final class MoonDustPotionClientProbe {
    private static boolean starting;
    private static volatile boolean ready,finished;
    private static volatile Throwable failure;
    private static int ticks,phase,checks;
    private static final long START=System.nanoTime();
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void install(){
        ClientLifecycleEvents.CLIENT_STARTED.register(mc->org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getHandle()));
        ServerTickEvents.END_SERVER_TICK.register(server->{try{
            if(finished||server.getPlayerManager().getPlayerList().isEmpty())return;
            if(++ticks==60){
                var p=server.getPlayerManager().getPlayerList().get(0);
                server.getOverworld().setTimeOfDay(6000);
                server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);
                p.changeGameMode(GameMode.SURVIVAL);
                net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils._setForm(p,
                    net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.getPlayerForm(new net.minecraft.util.Identifier("shape-shifter-curse","familiar_fox_1")));
                NeoRuntime.refreshForm(p);
                var d=NeoRuntime.data(p);d.burden=100;d.recovery=10000;
                var tube=PotionTestTubes.divide(new ItemStack(MoonDustReliefItem.BOTTLE));tube.setCount(1);
                check(tube.isOf(MoonDustReliefItem.VIAL),"服务端分装必须给专属月尘试管而非通用NBT试管");
                p.getInventory().setStack(0,tube);
                p.getInventory().setStack(1,new ItemStack(MoonDustReliefItem.BOTTLE));
                p.getInventory().setStack(2,new ItemStack(MoonDustReliefItem.SPLASH));
                p.getInventory().setStack(3,tube.copy());
                ready=true;
            }
        }catch(Throwable e){fail(e);}});
        ClientTickEvents.END_CLIENT_TICK.register(mc->{try{
            if(finished)return;
            if(System.nanoTime()-START>180_000_000_000L)throw new AssertionError("月尘客户端检查超时，阶段"+phase);
            if(mc.getOverlay()!=null)return;
            if(!starting&&mc.currentScreen instanceof TitleScreen){
                starting=true;mc.options.pauseOnLostFocus=false;
                mc.options.getViewDistance().setValue(4);mc.options.getMaxFps().setValue(60);
                mc.options.getGuiScale().setValue(3);mc.onResolutionChanged();
                var data=new DataConfiguration(new DataPackSettings(List.of("vanilla","fabric"),List.of()),DataConfiguration.SAFE_MODE.enabledFeatures());
                mc.createIntegratedServerLoader().createAndStart("MoonDust-"+System.currentTimeMillis(),
                    new LevelInfo("月尘药水检查",GameMode.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),data),
                    new GeneratorOptions(20261031L,false,false),
                    r->r.get(RegistryKeys.WORLD_PRESET).get(WorldPresets.FLAT).createDimensionsRegistryHolder());
            }
            if(!ready||mc.player==null||mc.world==null)return;
            if(phase<5&&mc.currentScreen!=null)mc.setScreen(null);
            double burden=NeoClient.hud.getDouble("C");
            if(phase==0&&burden==100&&mc.player.getInventory().getStack(0).isOf(MoonDustReliefItem.VIAL)){
                for(var item:List.of(MoonDustReliefItem.VIAL,MoonDustReliefItem.BOTTLE,MoonDustReliefItem.SPLASH,PotionTestTubes.EMPTY,PotionTestTubes.FILLED)){
                    var stack=item.getDefaultStack();
                    check(!mc.getItemRenderer().getModel(stack,mc.world,mc.player,0).getParticleSprite().getContents().getId().getPath().equals("missingno"),"客户端加载药水模型");
                    check(!stack.getName().getString().contains("item."),"药水中文名称不能出现item.*翻译键");
                    check(net.minecraft.registry.Registries.ITEM.getId(item).getNamespace().equals("ssc_addon"),"药水和试管全部归入ssc_addon");
                }
                check(net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.get(MoonDustReliefItem.BOTTLE).getColor(new ItemStack(MoonDustReliefItem.BOTTLE),0)==MoonDustReliefItem.COLOR,"瓶装药液为浅月光青蓝色");
                check(net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.get(MoonDustReliefItem.SPLASH).getColor(new ItemStack(MoonDustReliefItem.SPLASH),0)==MoonDustReliefItem.COLOR,"支援药液为浅月光青蓝色");
                var moonStack=mc.player.getInventory().getStack(0);
                var moonProvider=net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.get(MoonDustReliefItem.VIAL);
                check(moonProvider.getColor(moonStack,0)==MoonDustReliefItem.COLOR&&moonProvider.getColor(moonStack,1)==-1,"专属月尘试管仅药液染色，玻璃不染色");
                check(MoonDustReliefItem.COLOR!=UniversalEnergyEffect.POTION_COLOR,"月尘药液必须与通用能量药水有独立配色");
                var moonSprite=mc.getItemRenderer().getModel(moonStack,mc.world,mc.player,0).getParticleSprite().getContents().getId();
                var infiniteSprite=mc.getItemRenderer().getModel(new ItemStack(SscAddon.INFINITE_ENERGY_POTION),mc.world,mc.player,0).getParticleSprite().getContents().getId();
                check(moonSprite.getNamespace().equals("ssc_addon")&&!moonSprite.equals(infiniteSprite),"月尘试管必须使用ssc_addon独立材质而非无限能量药水模型");
                var normalDefault=PotionTestTubes.FILLED.getDefaultStack();
                check(normalDefault.isOf(PotionTestTubes.FILLED)&&net.minecraft.potion.PotionUtil.getPotion(normalDefault)==net.minecraft.potion.Potions.HEALING,"普通试管默认显示治疗药水");
                check(MoonDustReliefItem.VIAL.getMaxUseTime(moonStack)==16&&PotionTestTubes.FILLED.getMaxUseTime(normalDefault)==13,"月尘和原版药水的试管饮用时间分别为瓶装的40%");
                var colored=net.minecraft.potion.PotionUtil.setPotion(PotionTestTubes.FILLED.getDefaultStack(),net.minecraft.potion.Potions.HEALING);
                colored.getOrCreateNbt().putInt("CustomPotionColor",0xC040FF);
                var provider=net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.get(PotionTestTubes.FILLED);
                check(provider.getColor(colored,0)==0xC040FF&&provider.getColor(colored,1)==-1,"液体按NBT染色且玻璃不染色");
                check(!mc.getItemRenderer().getModel(colored,mc.world,mc.player,0).getQuads(null,null,net.minecraft.util.math.random.Random.create()).isEmpty(),"通用药水管具有真实烘焙模型");
                mc.player.getInventory().selectedSlot=0;use(mc);phase=1;
            }else if(phase==1&&burden==80&&mc.player.getInventory().getStack(0).isOf(PotionTestTubes.EMPTY)){
                mc.options.useKey.setPressed(false);check(true,"正常右键试管完成并同步100到80");save(mc,"vial-in-game.png");
                mc.player.getInventory().selectedSlot=1;use(mc);phase=2;
            }else if(phase==2&&burden==20&&mc.player.getInventory().getStack(1).isOf(Items.GLASS_BOTTLE)){
                mc.options.useKey.setPressed(false);check(true,"正常右键瓶装完成并同步80到20");save(mc,"bottle-in-game.png");
                mc.getServer().execute(()->{
                    var p=mc.getServer().getPlayerManager().getPlayerList().get(0);
                    NeoRuntime.data(p).burden=100;NeoRuntime.data(p).recovery=10000;NeoNetwork.hud(p);
                });phase=3;
            }else if(phase==3&&burden==100){
                mc.player.getInventory().selectedSlot=2;mc.player.setPitch(90);
                mc.interactionManager.interactItem(mc.player,Hand.MAIN_HAND);phase=4;
            }else if(phase==4&&burden<100&&mc.player.getInventory().getStack(2).isEmpty()){
                mc.options.useKey.setPressed(false);
                check(burden>=65&&burden<100,"真实投掷落地恢复崩解度并同步，范围衰减不超过35");
                mc.player.getInventory().selectedSlot=3;save(mc,"potions-in-game.png");
                mc.setScreen(new TubePreview());phase=5;ticks=0;
            }else if(phase==5&&ticks>20){
                save(mc,"test-tubes-rendered.png");
                Files.writeString(Path.of("moon-dust-client-result.json"),NeoState.JSON.toJson(Map.of("checks",checks,"real_client",true,"actual_use_packets",true,"splash_final_c",burden)));
                finished=true;System.out.println("MOON_DUST_POTION_CLIENT_PASS checks="+checks);mc.scheduleStop();
            }
        }catch(Throwable e){fail(e);}});
    }
    private static final class TubePreview extends net.minecraft.client.gui.screen.Screen {
        private TubePreview(){super(net.minecraft.text.Text.literal("Moon dust / ordinary / energy potion models"));}
        @Override public boolean shouldPause(){return false;}
        @Override public void render(net.minecraft.client.gui.DrawContext ctx,int mouseX,int mouseY,float delta){
            ctx.fill(0,0,width,height,0xFF30343A);
            ctx.drawCenteredTextWithShadow(textRenderer,title,width/2,40,0xFFFFFF);
            var moon=PotionTestTubes.divide(new ItemStack(MoonDustReliefItem.BOTTLE));
            var heal=PotionTestTubes.divide(net.minecraft.potion.PotionUtil.setPotion(new ItemStack(Items.POTION),net.minecraft.potion.Potions.HEALING));
            var custom=heal.copy();custom.getOrCreateNbt().putInt("CustomPotionColor",0xC040FF);
            custom.setCustomName(net.minecraft.text.Text.literal("NBT #C040FF"));
            var items=List.of(new ItemStack(PotionTestTubes.EMPTY),moon,heal,custom,new ItemStack(MoonDustReliefItem.BOTTLE),new ItemStack(MoonDustReliefItem.SPLASH),
                new ItemStack(SscAddon.INFINITE_ENERGY_POTION),new ItemStack(SscAddon.UNIVERSAL_ENERGY_POTION),PotionTestTubes.FILLED.getDefaultStack());
            int rows=(items.size()+2)/3, firstTop=Math.max(64,(height-rows*70)/2+12);
            for(int i=0;i<items.size();i++){
                var stack=items.get(i);stack.setCount(1);
                int centerX=width*(2*(i%3)+1)/6, top=firstTop+(i/3)*70;
                ctx.getMatrices().push();ctx.getMatrices().translate(centerX-24,top,0);ctx.getMatrices().scale(3,3,1);
                ctx.drawItem(stack,0,0);ctx.getMatrices().pop();
                ctx.drawCenteredTextWithShadow(textRenderer,stack.getName(),centerX,top+60,0xFFFFFF);
            }
        }
    }
    private static void use(MinecraftClient mc){mc.options.useKey.setPressed(true);mc.interactionManager.interactItem(mc.player,Hand.MAIN_HAND);}
    private static void save(MinecraftClient mc,String name)throws Exception{
        try(var shot=ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())){shot.writeTo(Path.of(name));}
    }
    private static void fail(Throwable e){
        if(failure!=null)return;failure=e;finished=true;e.printStackTrace();
        MinecraftClient.getInstance().execute(()->MinecraftClient.getInstance().scheduleStop());
    }
    public static void verifyResult(){
        if(failure!=null)throw new AssertionError("月尘药水客户端检查失败",failure);
        if(!finished)throw new AssertionError("客户端提前结束");
    }
}
