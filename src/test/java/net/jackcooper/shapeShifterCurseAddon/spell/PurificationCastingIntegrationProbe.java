package net.jackcooper.shapeShifterCurseAddon.spell;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.jackcooper.shapeShifterCurseAddon.spell.config.SpellConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Real ItemStack/JSON/payment paths and compiled hooks; does not claim live multiplayer acceptance. */
public final class PurificationCastingIntegrationProbe {
    private static int checks;

    public static void run() throws Exception {
        SpellRegistry.init();
        check(SpellRegistry.all().size() == 26, "all 26 registered spell behaviors are covered");
        for (Spell spell : SpellRegistry.all()) {
            spell.ssc_addon$applyConfig(SpellConfig.fromJson(json("ssc_addon/spells/" + spell.getId().getPath())));
            for (int level = 1; level <= spell.getMaxLevel(); level++) {
                ItemStack book = book();
                int cost = SpellNumbers.finalManaCost(spell, book, null, level);
                check(cost >= 0, "configured full mana quote exists: " + spell.getId());
                SpellbookData.setMana(book, cost - 1);
                var before = book.getNbt().copy();
                if (cost > 0) {
                    check(!SpellbookData.canPayMana(book, cost), "cost-1 cannot begin " + spell.getId());
                    check(!SpellbookData.consumeMana(book, cost) && book.getNbt().equals(before), "rejection does not mutate real ItemStack");
                }
                SpellbookData.setMana(book, cost);
                check(SpellbookData.canPayMana(book, cost), "exact quote can begin");
                int duration = spell.getCastingProfile(null, level, false).ticks();
                SpellManaPayment payment = new SpellManaPayment(cost, duration);
                for (int tick = 0; tick <= duration + 2; tick++) {
                    check(payment.advance(tick, due -> SpellbookData.consumeMana(book, due)), "actual ItemStack progressive payment succeeds");
                    int billed = Math.min(duration, tick >= duration ? duration : tick / 2 * 2);
                    check(SpellbookData.getMana(book) == cost - SpellCastingRules.cumulativeMana(cost, billed, duration),
                            "actual HUD mana matches the 2-tick ledger");
                }
                check(payment.paid() == cost && SpellbookData.getMana(book) == 0, "real book pays exactly once, including held release");
                int cooldown = SpellNumbers.finalCooldownTicks(spell, level, 1, 1, 1);
                check(SpellCastingRules.cooldownAfterStop(spell, cooldown, true, true) == cooldown,
                        "purification gives full quoted CD for " + spell.getId());
                check(SpellCastingRules.cooldownAfterStop(spell, cooldown, true, false) == spell.getInterruptedCooldown(cooldown),
                        "other interruption rules remain intact");
            }
        }
        checkExample();
        checkWiring();
        System.out.println("Purification/casting integration: " + checks
                + " real ItemStack/JSON/payment and compiled-hook checks passed. Live multiplayer/visual acceptance NOT tested.");
    }

    private static void checkExample() {
        ItemStack book = book();
        SpellbookData.setMana(book, 60);
        check(!SpellbookData.canPayMana(book, 100) && SpellbookData.getMana(book) == 60, "60 mana cannot begin a 100-mana spell");
        SpellbookData.setMana(book, 100);
        SpellManaPayment payment = new SpellManaPayment(100, 100);
        for (int tick = 0; tick <= 40; tick++) {
            check(payment.advance(tick, due -> SpellbookData.consumeMana(book, due)), "example payment succeeds");
            check(SpellbookData.getMana(book) == 100 - tick / 2 * 2, "5s/100 mana deducts 2 mana every 2 ticks");
        }
        check(payment.paid() == 40 && SpellbookData.getMana(book) == 60, "interruption at 2s leaves the unpaid 60 mana");
    }

    private static void checkWiring() throws Exception {
        ClassNode channel = node(SpellChannelManager.class.getName());
        MethodNode purify = method(channel, "onPurified", null);
        int stops = 0;
        for (var instruction : purify.instructions) if (instruction instanceof MethodInsnNode call && call.name.equals("stop")) {
            var flag = instruction.getPrevious();
            while (flag != null && flag.getOpcode() < 0) flag = flag.getPrevious();
            check(flag != null && flag.getOpcode() == Opcodes.ICONST_1 && call.desc.endsWith("Text;Z)V"), "purification explicitly requests full CD");
            stops++;
        }
        check(stops == 1 && callIndex(purify, "isLockedIn") < 0 && callIndex(purify, "allowsExternal") < 0,
                "purification overrides all interrupt modes and release locks");
        MethodNode advance = method(channel, "advance", null);
        check(callIndex(advance, "ensureValid") < callIndex(advance, "advance")
                && callIndex(advance, "advance") < callIndex(advance, "beginEffect"), "validation and payment precede effect release");
        check(callIndex(method(channel, "tick", null), "ensureValid") >= 0
                && callIndex(method(channel, "release", null), "ensureValid") >= 0, "tick and key release both honor purification");
        check(callIndex(method(node(SpellCastManager.class.getName()), "castInternal", null), "consumeMana") < 0,
                "book entry no longer deducts the entire cost after starting");
        ClassNode effect = node("net.jackcooper.shapeShifterCurseAddon.effect.PurifiedEffect");
        check(callIndex(method(effect, "onApplied", null), "interruptCasting") >= 0
                && callIndex(method(effect, "applyUpdateEffect", null), "interruptCasting") >= 0,
                "first application and repeated effect ticks both interrupt immediately");
        int hooks = 0;
        for (var instruction : method(effect, "interruptCasting", null).instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals("onPurified")) hooks++;
        check(hooks == 3, "purification connects spell casting, vortex and group heal");
        for (String owner : new String[]{"VortexChargeManager", "AllaySPGroupHeal"}) {
            ClassNode skill = node("net.jackcooper.shapeShifterCurseAddon.ability." + owner);
            check(callIndex(method(skill, "onPurified", null), "interruptedFull") >= 0, owner + " uses full skill CD");
        }
        MethodNode vortexRelease = method(node("net.jackcooper.shapeShifterCurseAddon.ability.VortexChargeManager"), "release", null);
        check(callIndex(vortexRelease, "onPurified") >= 0
                && callIndex(vortexRelease, "onPurified") < callIndex(vortexRelease, "remove"),
                "vortex release checks purification before consuming its charge or dealing damage");
        JsonObject purifyJson = json("my_addon/powers/form_allay_sp_purify");
        String release = purifyJson.get("charging_tick").toString();
        check(!release.contains("power resource set") && !purifyJson.has("interrupt_on_purified"), "other Allays' purification charge has no stop action");
        JsonObject heal = json("my_addon/powers/form_allay_sp_group_heal");
        check(heal.getAsJsonObject("key_activation").get("condition").toString().contains("ssc_addon:purified")
                && heal.getAsJsonObject("charging_tick").get("condition").toString().contains("ssc_addon:purified"), "group-heal input and charge advancement reject purification");
    }

    private static ItemStack book() {
        ItemStack book = new ItemStack(Items.BOOK);
        book.getOrCreateNbt().putInt(SpellbookData.NBT_LEVEL, 3);
        book.getOrCreateNbt().putInt(SpellbookData.NBT_EXP, SpellbookData.MASTERY_EXP_PER_TIER * 12);
        return book;
    }

    private static JsonObject json(String path) throws Exception {
        try (var input = PurificationCastingIntegrationProbe.class.getResourceAsStream("/data/" + path + ".json")) {
            if (input == null) throw new AssertionError("Missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static ClassNode node(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var input = PurificationCastingIntegrationProbe.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (input == null) throw new AssertionError("Missing class " + name);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(m -> m.name.equals(name) && (descriptor == null || m.desc.equals(descriptor)))
                .findFirst().orElseThrow(() -> new AssertionError("Missing method " + name));
    }

    private static int callIndex(MethodNode method, String name) {
        for (int i = 0; i < method.instructions.size(); i++)
            if (method.instructions.get(i) instanceof MethodInsnNode call && call.name.equals(name)) return i;
        return -1;
    }

    private static void check(boolean passed, String reason) {
        checks++;
        if (!passed) throw new AssertionError(reason);
    }
}
