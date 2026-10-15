package net.jackcooper.shapeShifterCurseAddon.command;

import com.mojang.brigadier.CommandDispatcher;
import net.jackcooper.shapeShifterCurseAddon.item.MoonDustSpellbookItem;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

/** Real transformed Minecraft command tree and NBT; no client-side write or live world. */
public final class SpellbookExperienceIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path", java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("spellbookExp.testClasspath"))));
        var knot = new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);
        var loader = knot.init(new String[0]);
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.minecraft.SharedConstants", true, loader).getMethod("createGameVersion").invoke(null);
        Class.forName("net.minecraft.Bootstrap", true, loader).getMethod("initialize").invoke(null);
        Class.forName(SpellbookExperienceIntegrationTest.class.getName()+"$Probe", true, loader).getMethod("run").invoke(null);
    }
    public static final class Probe {
        private static int checks;
        private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
        public static void run() {
            Item item = Registry.register(Registries.ITEM, new Identifier("test", "experience_book"), new MoonDustSpellbookItem(new Item.Settings()));
            ItemStack main = new ItemStack(item), off = new ItemStack(item), worn = new ItemStack(item);
            check(SpellbookExperience.select(true, main, off, () -> worn) == worn, "equipped never chooses a held book");
            check(SpellbookExperience.select(true, main, off, () -> null) == null, "missing equipped never falls back to held");
            check(SpellbookExperience.select(false, main, off, () -> { throw new AssertionError("held queried worn"); }) == main, "old command keeps main hand priority");
            check(SpellbookExperience.select(false, new ItemStack(Items.STONE), off, () -> worn) == off, "old command keeps offhand fallback");
            check(SpellbookExperience.select(false, ItemStack.EMPTY, ItemStack.EMPTY, () -> worn) == null, "old command never picks equipped");
            worn.getOrCreateNbt().putInt("Level", 2); worn.getOrCreateNbt().putInt("Mana", 17);
            worn.getOrCreateNbt().putString("Unrelated", "preserve");
            var result = SpellbookExperience.add(worn, 1000);
            check(result.success() && result.totalTen() == 10000 && SpellbookData.getExpTen(worn.copy()) == 10000, "1000 points means exactly 10000 ExpTen");
            check(main.getNbt() == null && off.getNbt() == null, "held books untouched");
            check(worn.getNbt().getInt("Level") == 2 && worn.getNbt().getInt("Mana") == 17 && worn.getNbt().getString("Unrelated").equals("preserve"), "level mana and other NBT retained");
            ItemStack fractional = new ItemStack(item); SpellbookData.setExpTen(fractional, 17);
            check(SpellbookExperience.add(fractional, 1000).totalTen() == 10017, "fractional experience retained");
            ItemStack legacy = new ItemStack(item); legacy.getOrCreateNbt().putInt("Exp", 11);
            check(SpellbookExperience.add(legacy, 1).totalTen() == 120, "legacy integer experience migrates accurately");
            ItemStack capped = new ItemStack(item); SpellbookData.setExpTen(capped, Integer.MAX_VALUE - 17);
            var before = capped.getNbt().copy(); result = SpellbookExperience.add(capped, 2);
            check(!result.success() && result.maximumAdditional() == 1 && before.equals(capped.getNbt()), "overflow refuses without NBT mutation");
            check(SpellbookExperience.add(capped, 1).totalTen() == Integer.MAX_VALUE - 7, "last legal whole point allowed");
            legacy = new ItemStack(item); legacy.getOrCreateNbt().putInt("Exp", Integer.MAX_VALUE);
            before = legacy.getNbt().copy();
            check(!SpellbookExperience.add(legacy, 1).success() && before.equals(legacy.getNbt()), "rejected legacy request does not migrate NBT");
            for (int invalid : new int[]{0, -1, Integer.MIN_VALUE}) {
                before = worn.getNbt().copy();
                try { SpellbookExperience.add(worn, invalid); throw new AssertionError("invalid accepted"); }
                catch (IllegalArgumentException expected) { check(before.equals(worn.getNbt()), "invalid amount cannot write"); }
            }
            var dispatcher = new CommandDispatcher<ServerCommandSource>(); SscAddonCommands.register(dispatcher);
            var op = source(2); var normal = source(0);
            for (String command : new String[]{"ssc_addon moon_dust_spellbook exp addon 1000", "ssc_addon moon_dust_spellbook exp addon 1000 equipped", "ssc_addon moon_dust_spellbook cd reset", "ssc_addon reset_spell_cd"}) {
                var parsed = dispatcher.parse(command, op);
                check(!parsed.getReader().canRead() && parsed.getContext().getCommand() != null, "registered executable command: " + command);
            }
            for (String command : new String[]{"ssc_addon moon_dust_spellbook exp addon 1000 equipped", "ssc_addon moon_dust_spellbook exp addon 0 equipped", "ssc_addon moon_dust_spellbook exp addon -1 equipped"}) {
                var parsed = dispatcher.parse(command, command.contains("1000") ? normal : op);
                check(parsed.getReader().canRead() || parsed.getContext().getCommand() == null, "permission or positive integer enforced: " + command);
            }
            System.out.println("Spellbook experience integration PASS: " + checks + " checks; command permissions, exact equipment target, legacy migration, fractional EXP, overflow and NBT preservation. Live server sync remains untested.");
        }
        private static ServerCommandSource source(int level) {
            return new ServerCommandSource(CommandOutput.DUMMY, Vec3d.ZERO, Vec2f.ZERO, null, level, "test", Text.literal("test"), null, null);
        }
    }
}
