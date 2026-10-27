package net.ssca.neomagic;

import dev.emi.trinkets.api.TrinketsApi;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.balance.*;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import java.nio.file.*;
import java.util.*;

/** Real book, solo scroll, key packet destination, server ticks, effects and cooldown settlement. */
public final class NeoBookWorldProbe {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static ServerPlayerEntity player(TestContext t) throws Exception {
        var p=t.createMockCreativeServerPlayerInWorld();p.setNoGravity(true);p.getAbilities().invulnerable=true;
        var f=BalanceIntegration.class.getDeclaredField("server");f.setAccessible(true);var state=(ServerBalanceState)f.get(null);
        state.buildSendSequence(p.getUuid(),"{}");state.playerAcked(p.getUuid(),state.revision());
        check(BalanceIntegration.isPlayerReady(p),"Mock client acknowledged server configuration");
        NeoRuntime.refreshForm(p);return p;
    }
    private static ItemStack book(ServerPlayerEntity p) {
        var b=new ItemStack(SscAddon.MOON_DUST_SPELLBOOK);SpellbookData.setMana(b,0);
        var slots=TrinketsApi.getTrinketComponent(p).orElseThrow().getInventory();var group=slots.get("moonlit");
        if(group==null||!group.containsKey("spellbook"))throw new AssertionError("Book trinket slot missing: "+slots.keySet());
        group.get("spellbook").setStack(0,b);
        check(SpellCastManager.getEquippedBook(p)==b,"Book equipped through real accessory integration");return b;
    }
    public static void start(TestContext t) {
        t.waitAndRun(70,()->{
            try {
                var p=player(t);var book=book(p);var scroll=ScrollData.create("lunar_mend",1);
                SpellbookData.setScroll(book,0,scroll);p.setHealth(5);
                double safe=NeoRuntime.profile(p).safety();SpellbookData.setLevel(book,3);
                check(NeoRuntime.profile(p).safety()==safe+200,"Book level capacity becomes +200 safety");
                check(NeoLegacyCasting.recoveryMultiplier(book)>1,"Book regeneration becomes recovery multiplier");
                double plain=NeoLegacyCasting.recoveryMultiplier(book);
                SpellbookData.setFormation(book,0,FormationData.create(FormationElement.UNIVERSAL,1,FormationData.VARIANT_RECOVERY));
                check(Math.abs(NeoLegacyCasting.recoveryMultiplier(book)/plain-1.2)<1e-6,"Recovery formation retains exact 20 percent bonus");
                SpellbookData.setFormation(book,0,FormationData.create(FormationElement.UNIVERSAL,2,FormationData.VARIANT_MANA));
                check(NeoRuntime.profile(p).safety()>safe+200,"Capacity formation also adds safety");
                var built=NeoModel.Blueprint.starter();var unmodified=NeoRules.quote(built,0);
                SpellbookData.setFormation(book,0,FormationData.create(FormationElement.FIRE,2));
                check(NeoLegacyCasting.blueprintPower(p,built)>1,"Existing damage formation applies to authored spells");
                check(NeoLegacyCasting.blueprintQuote(p,built).actual()>unmodified.actual(),"Formation cost increase becomes collapse cost");
                check(NeoLegacyCasting.blueprintCooldown(p,built)<NeoRules.cooldown(built),"Formation cooldown benefit applies to authored spells");
                SpellbookData.setFormation(book,0,ItemStack.EMPTY);
                var altarPos=t.getAbsolutePos(new net.minecraft.util.math.BlockPos(2,2,2));
                var altarBlock=net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlocks.INFUSION_ALTAR;
                t.getWorld().setBlockState(altarPos,altarBlock.getDefaultState());
                var altar=(net.jackcooper.shapeShifterCurseAddon.block.InfusionAltarBlockEntity)t.getWorld().getBlockEntity(altarPos);
                var detached=new ItemStack(SscAddon.MOON_DUST_SPELLBOOK);SpellbookData.setMana(detached,0);SpellbookData.setLevel(detached,3);
                altar.setStack(0,detached);altar.setStack(1,new ItemStack(net.onixary.shapeShifterCurseFabric.items.RegCustomItem.UNTREATED_MOONDUST,3));
                t.waitAndRun(22,()->check(altar.getStack(1).getCount()==3&&SpellbookData.getMana(detached)==0,"Altar no longer burns fuel charging retired book mana"));
                SpellbookData.setLevel(book,1);
                var previewWand=new ItemStack(NeoMagic.WAND);previewWand.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(NeoModel.Blueprint.starter()));p.setStackInHand(Hand.OFF_HAND,previewWand);
                var wandHud=NeoNetwork.hudState(p);
                check(wandHud.getBoolean("Visible")&&wandHud.getString("Spell").isEmpty()&&Math.abs(wandHud.getDouble("Q")-NeoLegacyCasting.blueprintQuote(p,NeoModel.Blueprint.starter()).actual())<1e-9,"HUD previews offhand loaded wand before equipped book");
                p.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);p.setStackInHand(Hand.MAIN_HAND,ScrollData.create("fire_bolt"));
                check(NeoNetwork.hudState(p).getString("Spell").equals("ssc_addon:fire_bolt"),"HUD previews held solo scroll before book selection");
                p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
                check(NeoNetwork.hudState(p).getString("Spell").equals("ssc_addon:lunar_mend"),"HUD returns to equipped book spell when hands are empty");
                SpellCastManager.cast(p,0,123);var cast=NeoLegacyCasting.active(p);
                check(cast!=null,"Normal book/key entry starts with EMPTY old mana");
                check(cast.session.checkpoints.size()==1&&cast.session.checkpoints.get(0).tick()==0,"Preparation rolls at tick zero");
                check(cast.session.paid==0&&NeoRuntime.data(p).burden==0,"Noninstant start does not prepay");
                var wand=new ItemStack(NeoMagic.WAND);wand.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(NeoModel.Blueprint.starter()));p.setStackInHand(Hand.OFF_HAND,wand);
                NeoRuntime.start(p,Hand.OFF_HAND);check(!NeoRuntime.ACTIVE.containsKey(p.getUuid()),"Book and loaded wand cannot prepare in parallel");
                var bad=player(t);var badBook=book(bad);SpellbookData.setScroll(badBook,0,ScrollData.create("lunar_mend",1));
                SpellCastManager.cast(bad,0,321);var interrupted=NeoLegacyCasting.active(bad);
                check(interrupted!=null,"Second player starts independently");NeoRuntime.data(bad).burden=10000;
                var solo=player(t);solo.setHealth(5);var soloScroll=ScrollData.create("lunar_mend",1);solo.setStackInHand(Hand.MAIN_HAND,soloScroll);
                int uses=ScrollData.getUses(soloScroll);soloScroll.use(t.getWorld(),solo,Hand.MAIN_HAND);var soloCast=NeoLegacyCasting.active(solo);
                check(soloCast!=null&&soloCast.session.quote.actual()>0,"Standalone scroll pays collapse");
                var instant=player(t);var real=SpellRegistry.get("fire_bolt");int[] effect={0},cd={0};
                var spell=new Spell(real.getId(),SpellRarity.WHITE) {
                    @Override public SpellCastingRules.Profile getCastingProfile(ServerPlayerEntity c,int l,boolean s){return new SpellCastingRules.Profile(0,1,false);}
                    @Override public void cast(ServerPlayerEntity c,float power,boolean s){}
                };
                NeoRuntime.data(instant).burden=10000;
                check(SpellChannelManager.start(instant,spell,ScrollData.create("fire_bolt"),1,false,1,15,100,()->true,v->effect[0]++,c->cd[0]=c,()->{}),"Instant cast enters authoritative channel");
                var failure=NeoRuntime.data(instant).records.get(0);
                check(failure.paid==15&&failure.checks.size()==1&&failure.checks.get(0).tick()==0,"Instant failure pays full at zero exactly once");
                check(effect[0]==0&&cd[0]==100&&!SpellChannelManager.isCasting(instant),"Guaranteed failure interrupts effect with full cooldown");
                check(!NeoRuntime.data(instant).progress.containsKey("spell:"+real.getId()),"Failure grants no adaptation");
                t.waitAndRun(3,()->{check(cast.session.paid>0&&cast.session.paid<cast.session.quote.actual(),"Preparation adds C progressively");check(SpellbookData.getMana(book)==0,"Payment never touches old mana");});
                t.waitAndRun(14,()->{
                    check(!SpellChannelManager.isCasting(bad)&&interrupted.session.failed,"Rising LIVE C fails next checkpoint");
                    check(interrupted.session.checkpoints.stream().map(NeoModel.Checkpoint::tick).toList().equals(List.of(0,10)),"Checks run at 0 and 10 exactly once");
                    check(interrupted.session.paid>0&&interrupted.session.paid<interrupted.session.quote.actual(),"Failure retains paid part without charging remainder");
                    long remaining=SharedSpellCooldowns.getCooldownEndOf(bad,SpellRegistry.get("lunar_mend"))-bad.getWorld().getTime();
                    check(remaining>220,"Live failure receives full 240 tick cooldown");
                });
                t.waitAndRun(34,()->{
                    check(!SpellChannelManager.isCasting(p)&&p.getHealth()>5,"Existing book healing effect releases");
                    check(Math.abs(NeoRuntime.data(p).burden-cast.session.quote.actual())<1e-7,"Book payment equals final quote");
                    check(cast.session.checkpoints.stream().map(NeoModel.Checkpoint::tick).toList().equals(List.of(0,10,20,30)),"30 tick cast includes start and endpoint");
                    check(SpellbookData.getMana(book)==0&&SpellbookData.getExpTen(book)>0,"Book experience preserved with empty mana");
                    check(NeoRuntime.data(p).progress.get(cast.key)[0]==1,"Adaptation belongs to single named spell");
                    check(solo.getHealth()>5&&ScrollData.getUses(soloScroll)==uses-1,"Solo healing and charge consumption preserved");
                    check(NeoRuntime.data(solo).burden>0,"Standalone scroll uses same collapse state");
                    for(Spell known:SpellRegistry.all())for(int lv=1;lv<=known.getMaxLevel();lv++)check(NeoModel.errors(NeoLegacyCasting.descriptor(known,lv,known.getCastingProfile(p,lv,false).ticks())).isEmpty(),"Valid risk descriptor: "+known.getId());
                    // Long legacy preparation and held release must survive the same saved-state validator.
                    var probe=new NeoLegacyCasting(p,SpellRegistry.get("explosion"),1,700,1);
                    for(int i=0;i<=700;i++)check(probe.advance(i),"Safe long preparation remains exact");
                    check(probe.record.checks.size()==71&&Math.abs(probe.record.paid-1)<1e-9,"700 tick spell records all 71 checks and pays once");
                    for(int i=701;i<=5200;i++)probe.advance(i);
                    check(probe.record.checks.size()==512&&probe.record.omittedChecks==9&&probe.record.checks.get(0).tick()==0,"Indefinite holds have bounded evidence retaining tick zero");
                    check(Math.abs(probe.session.paid-1)<1e-9,"Prepared holding never repays the quote");
                    float cameraYaw=p.getYaw(),cameraPitch=p.getPitch();probe.session.yaw=12;probe.session.pitch=-4;
                    probe.effect(()->check(p.getYaw()==cameraYaw+12,"Directional spell constructs with final accumulated deviation"));
                    check(p.getYaw()==cameraYaw&&p.getPitch()==cameraPitch,"Direction deviation restores camera angles after release");
                    probe.finish(false,"cancelled");NeoState.validate(NeoRuntime.data(p));
                    NeoRuntime.start(p,Hand.OFF_HAND);
                    check(NeoRuntime.ACTIVE.containsKey(p.getUuid()),"Loaded wand starts after book settlement");
                    check(!SpellChannelManager.start(p,spell,ScrollData.create("fire_bolt"),1,false,1,15,100,()->true,v->{},c->{},()->{}),"Wand preparation blocks legacy channel creation");
                    NeoRuntime.cancel(p,"test_end");
                    try{Files.writeString(Path.of("book-cutover-result.json"),"{\"checks\":"+checks+",\"result\":\"passed\"}");}catch(Exception e){throw new RuntimeException(e);}
                    System.out.println("NEOMAGIC BOOK CUTOVER: "+checks+" real-world checks passed");t.complete();
                });
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
}
