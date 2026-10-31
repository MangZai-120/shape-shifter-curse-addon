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
                check(!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.getGlobalReceivers().contains(NeoMagic.id("action")),"Retired research action channel is not registered");
                var root=t.getWorld().getServer().getCommandManager().getDispatcher().getRoot().getChild("neomagic");
                check(root!=null&&root.getChild("reset_burden")!=null&&root.getChild("kit")==null&&root.getChild("research_all")==null,"Retired kit/research commands are absent; live collapse debug command remains");
                check(t.getWorld().getServer().getRecipeManager().get(new net.minecraft.util.Identifier("ssc_addon","test_tube")).isPresent()&&t.getWorld().getServer().getRecipeManager().get(new net.minecraft.util.Identifier("ssc_addon","test_tube_filling")).isPresent(),"Tube crafting and potion dividing use the main addon namespace");
                var audit=player(t);NeoFormationAudit.run(t,audit,book(audit));
                NeoFailureWindowAudit.start(t,player(t),player(t));
                var p=player(t);var book=book(p);var scroll=ScrollData.create("lunar_mend",1);
                SpellbookData.setScroll(book,0,scroll);p.setHealth(5);
                double safe=NeoRuntime.profile(p).safety();SpellbookData.setLevel(book,3);
                check(NeoRuntime.profile(p).safety()==safe,"Book upgrades retain slots and recovery without raising player safety");
                int unit=PlayerSpellMastery.EXPERIENCE_PER_LEVEL_TEN,perTier=50;
                SpellbookData.setExpTen(book,unit+100);var oldCopy=book.copy();
                check(NeoRuntime.profile(p).safety()==safe+perTier,"Old mastery migrates into player casting levels");
                check(SpellbookData.getExpTen(book)==0&&NeoRuntime.data(p).spellbookMasteryExpTen==unit+100,"Migration consumes the old book ledger");
                PlayerSpellMastery.migrate(p,oldCopy);
                check(NeoRuntime.data(p).spellbookMasteryExpTen==unit+100,"Copies of an old book do not stack mastery");
                var slot=TrinketsApi.getTrinketComponent(p).orElseThrow().getInventory().get("moonlit").get("spellbook");
                slot.setStack(0,ItemStack.EMPTY);
                check(NeoRuntime.profile(p).safety()==safe+perTier,"Player mastery remains with no book equipped");
                p.setStackInHand(Hand.MAIN_HAND,ScrollData.create("fire_bolt"));
                check(NeoNetwork.hudState(p).getDouble("S")==safe+perTier,"Solo scroll HUD includes player mastery");
                check(NeoNetwork.hudState(p).getInt("MasteryExpTen")==unit+100,"Personal mastery is synchronized independently of the book");
                p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
                var replacement=new ItemStack(SscAddon.MOON_DUST_SPELLBOOK);slot.setStack(0,replacement);
                check(NeoRuntime.profile(p).safety()==safe+perTier,"Switching to a new low-level book preserves mastery");
                int personal=NeoRuntime.data(p).spellbookMasteryExpTen;
                PlayerSpellMastery.grant(p,replacement,20);
                check(SpellbookData.getExpTen(replacement)==SpellbookData.boostedExpTen(20)&&NeoRuntime.data(p).spellbookMasteryExpTen==personal+SpellbookData.boostedExpTen(20),"A successful cast trains the player and preserves low-level book upgrading");
                slot.setStack(0,book);PlayerSpellMastery.grant(p,book,20);
                check(SpellbookData.getExpTen(book)==0&&NeoRuntime.data(p).spellbookMasteryExpTen==personal+2*SpellbookData.boostedExpTen(20),"Full-level book awards only the player and no book mastery");
                var other=player(t);var traded=book(other);SpellbookData.setLevel(traded,3);
                check(PlayerSpellMastery.bonus(NeoRuntime.data(other))==0,"Another player does not inherit the owner's mastery");
                slot.setStack(0,ItemStack.EMPTY);
                var otherSlot=TrinketsApi.getTrinketComponent(other).orElseThrow().getInventory().get("moonlit").get("spellbook");
                otherSlot.setStack(0,book);NeoRuntime.profile(other);
                check(PlayerSpellMastery.bonus(NeoRuntime.data(other))==0&&PlayerSpellMastery.bonus(NeoRuntime.data(p))==perTier,"Trading the migrated book does not transfer its former owner's mastery");
                otherSlot.setStack(0,traded);slot.setStack(0,book);
                int beforeCommand=NeoRuntime.data(p).spellbookMasteryExpTen;
                int command=p.getServer().getCommandManager().executeWithPrefix(p.getCommandSource().withLevel(4),"ssc_addon moon_dust_spellbook exp addon 2 equipped");
                check(command==1&&NeoRuntime.data(p).spellbookMasteryExpTen==beforeCommand+20&&SpellbookData.getExpTen(book)==0,"Max-level book EXP command updates player without the gameplay multiplier");
                // 后续原有容量法阵断言以含玩家精通的基础阈值为准。
                safe+=perTier;
                check(NeoLegacyCasting.recoveryMultiplier(book)>1,"Book regeneration becomes recovery multiplier");
                double plain=NeoLegacyCasting.recoveryMultiplier(book);
                SpellbookData.setFormation(book,0,FormationData.create(FormationElement.UNIVERSAL,1,FormationData.VARIANT_RECOVERY));
                check(Math.abs(NeoLegacyCasting.recoveryMultiplier(book)/plain-1.2)<1e-6,"Recovery formation retains exact 20 percent bonus");
                SpellbookData.setFormation(book,0,FormationData.create(FormationElement.UNIVERSAL,2,FormationData.VARIANT_MANA));
                check(NeoRuntime.profile(p).safety()==safe+20,"Level two capacity formation adds twenty safety independently of book level");
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
                var upgradeBook=new ItemStack(SscAddon.MOON_DUST_SPELLBOOK);SpellbookData.setExpTen(upgradeBook,6000);
                altar.setStack(0,upgradeBook);altar.setStack(1,new ItemStack(net.onixary.shapeShifterCurseFabric.items.RegCustomItem.MOONDUST_CRYSTAL_SHARD));
                altar.setStack(2,new ItemStack(net.onixary.shapeShifterCurseFabric.items.RegCustomItem.MORPHSCALE_CORE));
                double personalSafety=NeoRuntime.profile(p).safety();altar.tryUpgrade(p);
                check(SpellbookData.getLevel(upgradeBook)==2&&SpellbookData.getSlotCount(upgradeBook)==5&&SpellbookData.getFormationSlotCount(upgradeBook)==3,"Book upgrading and slot unlocking remain available");
                check(altar.getStack(1).isEmpty()&&altar.getStack(2).isEmpty()&&NeoRuntime.profile(p).safety()==personalSafety,"Book upgrade consumes only its original materials and cannot raise player safety");
                var menu=new net.jackcooper.shapeShifterCurseAddon.screen.SpellbookScreenHandler(9,p.getInventory(),book);
                check(menu.playerState.getDouble("S")==personalSafety&&menu.playerState.getDouble("DangerMax")==personalSafety*2,"Real server book opening captures dynamic player ranges");
                check(menu.playerState.getInt("RecoveryDelay")==120,"Book opening reports the six-second recovery delay");
                var detached=new ItemStack(SscAddon.MOON_DUST_SPELLBOOK);SpellbookData.setMana(detached,0);SpellbookData.setLevel(detached,3);
                altar.setStack(0,detached);altar.setStack(1,new ItemStack(net.onixary.shapeShifterCurseFabric.items.RegCustomItem.UNTREATED_MOONDUST,3));
                t.waitAndRun(22,()->check(altar.getStack(1).getCount()==3&&SpellbookData.getMana(detached)==0,"Altar no longer burns fuel charging retired book mana"));
                SpellbookData.setLevel(book,1);
                var previewWand=new ItemStack(net.ssca.neomagic.NeoTestFixtures.WAND);previewWand.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(NeoModel.Blueprint.starter()));p.setStackInHand(Hand.OFF_HAND,previewWand);
                var wandHud=NeoNetwork.hudState(p);
                check(wandHud.getBoolean("Visible")&&wandHud.getString("Spell").equals("ssc_addon:lunar_mend"),"Retired blueprint NBT on an ordinary item cannot override the equipped book preview");
                p.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);p.setStackInHand(Hand.MAIN_HAND,ScrollData.create("fire_bolt"));
                check(NeoNetwork.hudState(p).getString("Spell").equals("ssc_addon:fire_bolt"),"HUD previews held solo scroll before book selection");
                p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
                check(NeoNetwork.hudState(p).getString("Spell").equals("ssc_addon:lunar_mend"),"HUD returns to equipped book spell when hands are empty");
                SpellCastManager.cast(p,0,123);var cast=NeoLegacyCasting.active(p);
                check(cast!=null,"Normal book/key entry starts with EMPTY old mana");
                check(cast.session.checkpoints.size()==1&&cast.session.checkpoints.get(0).tick()==0,"Preparation rolls at tick zero");
                check(cast.session.paid==0&&NeoRuntime.data(p).burden==0,"Noninstant start does not prepay");
                var wand=new ItemStack(net.ssca.neomagic.NeoTestFixtures.WAND);wand.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(NeoModel.Blueprint.starter()));p.setStackInHand(Hand.OFF_HAND,wand);
                net.ssca.neomagic.NeoTestFixtures.start(p,Hand.OFF_HAND);check(!NeoRuntime.ACTIVE.containsKey(p.getUuid()),"Book and loaded wand cannot prepare in parallel");
                var bad=player(t);var badBook=book(bad);SpellbookData.setScroll(badBook,0,ScrollData.create("lunar_mend",1));
                NeoRuntime.data(bad).burden=10000;
                SpellCastManager.cast(bad,0,321);var interrupted=NeoLegacyCasting.active(bad);
                var badRecord=NeoRuntime.data(bad).records.get(0);
                var changing=player(t);var changingBook=book(changing);SpellbookData.setScroll(changingBook,0,ScrollData.create("lunar_mend",1));
                SpellCastManager.cast(changing,0,322);var changingCast=NeoLegacyCasting.active(changing);
                check(changingCast!=null&&changingCast.session.failureProbability==0,"Projected safe cast passes its one initial failure draw");NeoRuntime.data(changing).burden=10000;
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
                t.waitAndRun(3,()->{
                    check(cast.session.paid>0&&cast.session.paid<cast.session.quote.actual(),"Preparation adds C progressively");check(SpellbookData.getMana(book)==0,"Payment never touches old mana");
                    var hud=NeoNetwork.hudState(changing);
                    check(hud.getDouble("Failure")==0&&hud.getDouble("Offset")>0,"HUD keeps initial whole-cast failure chance while deviation follows live C");
                    check(hud.getDouble("FailureBasis")==changingCast.session.quote.actual(),"HUD retains the initial projected payment basis");
                });
                var early=player(t);NeoRuntime.data(early).burden=10000;int[] lockedEffect={0},lockedCd={0};long began=early.getWorld().getTime();
                var lockingSpell=new Spell(real.getId(),SpellRarity.WHITE) {
                    @Override public SpellCastingRules.Profile getCastingProfile(ServerPlayerEntity c,int l,boolean s){return new SpellCastingRules.Profile(30,1,false);}
                    @Override public int getLockInTick(){return 5;}
                    @Override public boolean isLockedIn(ServerPlayerEntity c){return c.getWorld().getTime()-began>=5;}
                    @Override public void cast(ServerPlayerEntity c,float power,boolean s){}
                };
                check(SpellChannelManager.start(early,lockingSpell,ScrollData.create("fire_bolt"),1,false,323,15,100,()->true,v->lockedEffect[0]++,c->lockedCd[0]=c,()->{}),"Pre-lock failure fixture starts");
                var lockRecord=NeoRuntime.data(early).records.get(0);
                t.waitAndRun(8,()->{
                    check(!SpellChannelManager.isCasting(early)&&lockRecord.result.equals("failed"),"Failure fires before the spell becomes locked");
                    check(lockRecord.checks.get(lockRecord.checks.size()-1).tick()<5&&lockedEffect[0]==0&&lockedCd[0]==100,"No effect or lock bypass; full cooldown settles once");
                    check(!NeoState.JSON.toJson(NeoRuntime.data(early)).contains("failureAt"),"Research snapshots never reveal the scheduled failure tick");
                });
                t.waitAndRun(34,()->{
                    check(!SpellChannelManager.isCasting(bad)&&badRecord.result.equals("failed")&&badRecord.failureDraws==1,"One initial failure result eventually interrupts preparation");
                    check(badRecord.checks.get(badRecord.checks.size()-1).tick()<30,"Normal spell fails strictly before completion");
                    check(badRecord.paid>=0&&badRecord.paid<badRecord.actual,"Failure retains paid C without charging remainder");
                    long remaining=SharedSpellCooldowns.getCooldownEndOf(bad,SpellRegistry.get("lunar_mend"))-bad.getWorld().getTime();
                    check(remaining>200,"Scheduled failure receives full quoted cooldown");
                    check(!changingCast.session.failed&&changingCast.session.failureDraws==1,"Rising live C never rerolls the initial failure result");
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
                    var allocationBean=java.lang.management.ManagementFactory.getThreadMXBean();
                    var allocations=allocationBean instanceof com.sun.management.ThreadMXBean bean&&bean.isThreadAllocatedMemorySupported()?bean:null;
                    if(allocations!=null&&!allocations.isThreadAllocatedMemoryEnabled())allocations.setThreadAllocatedMemoryEnabled(true);
                    long thread=Thread.currentThread().getId(),beforeBytes=allocations==null?0:allocations.getThreadAllocatedBytes(thread),beforeTime=System.nanoTime();
                    for(int i=701;i<=5200;i++)probe.advance(i);
                    long elapsed=System.nanoTime()-beforeTime,allocated=allocations==null?-1:allocations.getThreadAllocatedBytes(thread)-beforeBytes;
                    try{Files.writeString(Path.of("cast-advance-allocation.json"),NeoState.JSON.toJson(Map.of("ticks",4500,"allocated_bytes",allocated,"elapsed_ns",elapsed)));}catch(Exception e){throw new RuntimeException(e);}
                    System.out.println("NEOMAGIC CAST ADVANCE: 4500 held ticks; allocated_bytes="+allocated+" elapsed_ns="+elapsed);
                    check(probe.record.checks.size()==512&&probe.record.omittedChecks==9&&probe.record.checks.get(0).tick()==0,"Indefinite holds have bounded evidence retaining tick zero");
                    check(Math.abs(probe.session.paid-1)<1e-9,"Prepared holding never repays the quote");
                    float cameraYaw=p.getYaw(),cameraPitch=p.getPitch();probe.session.yaw=12;probe.session.pitch=-4;
                    probe.effect(()->check(p.getYaw()==cameraYaw+12,"Directional spell constructs with final accumulated deviation"));
                    check(p.getYaw()==cameraYaw&&p.getPitch()==cameraPitch,"Direction deviation restores camera angles after release");
                    probe.finish(false,"cancelled");NeoState.validate(NeoRuntime.data(p));
                    net.ssca.neomagic.NeoTestFixtures.start(p,Hand.OFF_HAND);
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
