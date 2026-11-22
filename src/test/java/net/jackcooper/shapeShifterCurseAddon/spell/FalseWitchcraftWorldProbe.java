package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;

public final class FalseWitchcraftWorldProbe {
    private static final String WITCH = "false_witchcraft";
    private static final String HEAL = "lunar_mend";

    public static void start(TestContext test) {
        test.waitAndRun(70, () -> {
            try {
                run(test);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static void run(TestContext t) throws Exception {
        var witch = SpellRegistry.get(WITCH);
        var p = NeoBookWorldProbe.player(t);
        var book = NeoBookWorldProbe.book(p);
        SpellbookData.setLevel(book, 3);
        SpellbookData.setScroll(book, 0, ScrollData.create(WITCH, 4));
        SpellbookData.setScroll(book, 1, ArcaneAnalysis.markUnanalyzed(ScrollData.create("void_devour")));
        String[] spells = {HEAL, HEAL, "lunar_veil", "void_erosion", "fire_bolt"};
        for (int i = 0; i < spells.length; i++) SpellbookData.setScroll(book, i+2, ScrollData.create(spells[i]));
        long now = p.getWorld().getTime();
        long later = now+500;
        setEnd(book, 1, later);
        setEnd(book, 2, now);
        SpellCastManager.cast(p, 0);
        require(!SpellChannelManager.isCasting(p) && NeoRuntime.data(p).records.isEmpty(), "ready, expired and unidentified spells cannot start a paid cast");
        setEnd(book, 0, later);
        int[] distinctSlots = {2, 4, 5, 6};
        for (int level = 1; level <= 4; level++) {
            for (int slot : new int[]{2,3,5,6}) setEnd(book, slot, later);
            SharedSpellCooldowns.record(p, SpellRegistry.get(HEAL), later);
            SharedSpellCooldowns.record(p, SpellRegistry.get("lunar_veil"), later);
            witch.cast(p, 0, false, level);
            int shortened = 0;
            for (int slot : distinctSlots) {
                long reduction = later - now - remaining(p, book, slot);
                require(reduction == 0 || reduction == 200, "equal shares when targets have long cooldowns");
                if (reduction > 0) shortened++;
            }
            require(shortened == level, "each tier shares its 200/400/600/800 ticks among up to one spell per level");
            require(remaining(p, book, 2) == remaining(p, book, 3), "duplicate scrolls share one draw");
            require(remaining(p, book, 0) == later - now && remaining(p, book, 1) == later - now,
                    "self and unidentified scrolls never receive a share");
        }
        SharedSpellCooldowns.clearAllFor(p);
        setEnd(book, 5, 0);
        setEnd(book, 6, 0);
        setEnd(book, 2, now + 10);
        setEnd(book, 3, now + 100);
        SharedSpellCooldowns.record(p, SpellRegistry.get("lunar_veil"), now + 1600);
        witch.cast(p, 0, false, 4);
        require(remaining(p, book, 2) <= 0 && remaining(p, book, 3) <= 0,
                "the longer duplicate determines the spell's share even if the shorter duplicate comes first");
        require(remaining(p, book, 4) == 900, "two targets with 5s and 80s left end at 0s and 45s");

        // Three long targets split 800 indivisible ticks to within a single tick.
        setEnd(book, 2, now + 1600);
        setEnd(book, 3, now + 1600);
        setEnd(book, 5, now + 1600);
        SharedSpellCooldowns.record(p, SpellRegistry.get("lunar_veil"), now + 1600);
        witch.cast(p, 0, false, 4);
        long usedTicks = 0;
        for (int slot : new int[]{2, 4, 5}) {
            long used = 1600 - remaining(p, book, slot);
            require(used == 266 || used == 267, "three-way split rounds evenly");
            usedTicks += used;
        }
        require(usedTicks == 800, "no tick lost or created by rounding");

        SharedSpellCooldowns.clearAllFor(p);
        setEnd(book, 5, 0);
        setEnd(book, 0, 0);
        setEnd(book, 2, later);
        setEnd(book, 3, later);
        setEnd(book, 4, now+5); // Expires before witchcraft releases.
        SharedSpellCooldowns.record(p, SpellRegistry.get(HEAL), later);
        var other = NeoBookWorldProbe.player(t);
        SharedSpellCooldowns.record(other, SpellRegistry.get(HEAL), later);
        SpellCastManager.cast(p, 0);
        var witchRecord = latest(p);
        require(witchRecord.spell.endsWith(WITCH), "book starts witchcraft");

        var solo = NeoBookWorldProbe.player(t);
        var soloBook = NeoBookWorldProbe.book(solo);
        SpellbookData.setScroll(soloBook, 0, ScrollData.create(HEAL));
        setEnd(soloBook, 0, later);
        var loose = ScrollData.create(WITCH);
        solo.setStackInHand(Hand.MAIN_HAND, loose);
        int uses = ScrollData.getUses(loose);
        loose.use(solo.getWorld(), solo, Hand.MAIN_HAND);

        t.waitAndRun(57, () -> {
            require(witchRecord.result.equals("released") && witchRecord.paid > 0
                    && Math.abs(witchRecord.actual-witchRecord.paid)<1e-7, "witchcraft pays normal collapse");
            require(remaining(p,book,0)>0 && remaining(p,book,2)<=0 && remaining(p,book,3)<=0
                    && SharedSpellCooldowns.getCooldownEndOf(p,SpellRegistry.get(HEAL))==0,
                    "selected spell immediately loses its scroll and shared cooldown while witchcraft retains its own");
            require(ScrollData.getCooldownEnd(SpellbookData.getScroll(book,4)) == now+5, "a cooldown expiring during preparation is not selected at release");
            require(SharedSpellCooldowns.getCooldownEndOf(other,SpellRegistry.get(HEAL))==later, "other player's cooldown is isolated");
            require(SpellChannelManager.getNextCastAt(p)>p.getWorld().getTime(), "global cast interval remains active");
            int before = NeoRuntime.data(p).records.size();
            SpellCastManager.cast(p, 2);
            require(NeoRuntime.data(p).records.size()==before, "refresh cannot bypass global cast interval");
        });
        t.waitAndRun(73, () -> SpellCastManager.cast(p, 2));
        t.waitAndRun(112, () -> {
            var cast = latest(p);
            require(cast.spell.endsWith(HEAL) && cast.result.equals("released") && cast.paid>0
                    && Math.abs(cast.actual-cast.paid)<1e-7, "next cast pays its full collapse quote");
            require(remaining(p,book,2)>0 && remaining(p,book,3)>0, "next cast starts ordinary scroll and shared cooldowns");
            require(ScrollData.getUses(loose) == uses - 1 && latest(solo).paid > 0
                    && ScrollData.getCooldownEnd(SpellbookData.getScroll(soloBook, 0)) == later - 200,
                    "standalone green witchcraft shortens one long cooldown by precisely ten seconds and pays its costs");
            System.out.println("FALSE_WITCHCRAFT_SHARED_REDUCTION_WORLD_PASS");
            t.complete();
        });
    }

    private static void setEnd(ItemStack book,int slot,long end) {
        var scroll=SpellbookData.getScroll(book,slot);
        ScrollData.setCooldownEnd(scroll,end);
        SpellbookData.setScroll(book,slot,scroll);
    }

    private static NeoState.Experiment latest(ServerPlayerEntity p) { return NeoRuntime.data(p).records.get(0); }
    private static long remaining(ServerPlayerEntity p, ItemStack book, int slot) {
        return SharedSpellCooldowns.getEffectiveCooldownEnd(p,SpellbookData.getScroll(book,slot))-p.getWorld().getTime();
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
