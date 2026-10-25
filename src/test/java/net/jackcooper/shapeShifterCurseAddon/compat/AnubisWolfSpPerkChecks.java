package net.jackcooper.shapeShifterCurseAddon.compat;

import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.ability.AnubisWolfSpSummonWolves;
import net.jackcooper.shapeShifterCurseAddon.action.SscAddonActions;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.additional_power.WolfMinionPower;
import net.onixary.shapeShifterCurseFabric.minion.mobs.AnubisWolfMinionEntity;
import net.onixary.shapeShifterCurseFabric.perk.NormalPerk;
import net.onixary.shapeShifterCurseFabric.perk.PerkUtils;
import net.onixary.shapeShifterCurseFabric.perk.RegPerks;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.jackcooper.shapeShifterCurseAddon.compat.AxolotlSpPerkIntegrationProbe.*;

/** Real Perk loading/ownership with isolated worlds and no live entity/network I/O. */
final class AnubisWolfSpPerkChecks {
    private static int checks;

    static int run() throws Exception {
        var tree = RegPerks.T_AnubisWolf3PerkTree;
        var all = RegPerks.getPerkTree(tree).getAllPerks();
        var form = RegPlayerForms.getPlayerForm(FormIdentifiers.ANUBIS_WOLF_SP);
        check(all.size() == 8 && form.getPerkTreeID().equals(tree), "SP wolf binds the original eight-node tree");
        SscAddonActions.register();
        for (Identifier id : all) {
            var perk = (NormalPerk) RegPerks.getPerk(id);
            for (Identifier power : perk.powerAdd) register(power, json("shape-shifter-curse", power.getPath()));
            for (Identifier power : perk.powerRemove) if (!PowerTypeRegistry.contains(power)) register(power, null);
        }
        for (Identifier id : List.of(AnubisWolfSpPerks.SUMMONING_1, AnubisWolfSpPerks.SUMMONING_2,
                AnubisWolfSpPerks.SOUL_EXCITATION, AnubisWolfSpPerks.SOUL_RECALL)) register(id, json("my_addon", id.getPath()));
        var player = player();
        player.totalExperience = 1234;
        player.form.formPerkMap.put(tree, new ArrayList<>(all));
        select(player, RegPlayerForms.ANUBIS_WOLF_3);
        check(has(player, original("perks/anubis_wolf_soul_excitation")), "SSC wolf retains original active excitation");
        check(!AnubisWolfSpPerks.hasExtraCapacity(player), "SSC wolf never receives SP's extra slot");
        select(player, form);
        check(player.form.formPerkMap.get(tree).equals(all) && player.totalExperience == 1234, "shared purchases and experience survive evolution");
        check(AnubisWolfSpPerks.capacity(player, 6) == 7 && AnubisWolfSpPerks.capacity(player, 8) == 9, "passive adds exactly one active slot with/without crystal");
        check(player.minions.isEmpty() && player.effects.isEmpty(), "learning the passive neither summons nor applies wither");
        check(has(player, AnubisWolfSpPerks.SUMMONING_2) && !has(player, AnubisWolfSpPerks.SUMMONING_1), "rank II replaces rank I summon marker");
        check(has(player, original("perks/anubis_wolf_pack_response_1")), "rank II retains rank I death healing");
        check(!has(player, original("perks/anubis_wolf_summoning_2"))
                && !has(player, original("perks/anubis_wolf_soul_excitation"))
                && !has(player, original("perks/anubis_wolf_soul_recall")), "original extra actives/passive triggers are not duplicated");
        check(!has(player, original("perks/anubis_wolf_wither_tolerance_1"))
                && has(player, original("perks/anubis_wolf_wither_tolerance_2")), "wither interval ranks do not stack");
        check(!has(player, original("form_anubis_wolf_3_undead_damage_down")), "undead outgoing-damage penalty is removed");
        var reverse = new ArrayList<>(all); java.util.Collections.reverse(reverse);
        player.form.formPerkMap.put(tree, reverse);
        select(player, form);
        PerkUtils.loadAllPerk(player, tree);
        check(AnubisWolfSpPerks.capacity(player, 6) == 7 && !has(player, AnubisWolfSpPerks.SUMMONING_1)
                && has(player, original("perks/anubis_wolf_pack_response_1")), "reverse and repeated loading keeps one slot and death healing");
        NbtCompound saved = new NbtCompound(); player.form.writeToNbt(saved);
        var restored = player(); restored.form.readFromNbt(saved); select(restored, form);
        check(restored.form.formPerkMap.get(tree).equals(reverse) && AnubisWolfSpPerks.capacity(restored, 6) == 7, "real component NBT restores wolf purchases");
        var fresh = player(); select(fresh, form);
        check(AnubisWolfSpPerks.capacity(fresh, 6) == 6 && !has(fresh, AnubisWolfSpPerks.SOUL_RECALL), "unlearned SP gets no free perk");
        check(RegPlayerForms.getPlayerForm(FormIdentifiers.GOLDEN_SANDSTORM_SP).getPerkTreeID().equals(RegPerks.EMPTY_PERK_TREE), "unapproved Sandstorm remains unbound");

        int[][][] expected = {{{2, 2, 2, 600}, {3, 3, 3, 600}}, {{2, 2, 2, 420}, {3, 3, 3, 420}}, {{3, 2, 3, 420}, {4, 3, 4, 420}}};
        for (int rank = 0; rank < 3; rank++) for (int crystal = 0; crystal < 2; crystal++)
            for (int extra = 0; extra < 2; extra++) for (int hit = 0; hit < 2; hit++) {
                var spec = AnubisWolfSpPerks.passiveSummon(rank, crystal == 1, extra == 1, hit == 1);
                check(spec.count() == expected[rank][crystal][hit] && spec.maximum() == expected[rank][crystal][2] + extra
                        && spec.cooldown() == expected[rank][crystal][3], "passive summon matrix " + rank + "/" + crystal + "/" + extra + "/" + hit);
            }
        var excitation = json("my_addon", AnubisWolfSpPerks.SOUL_EXCITATION.getPath());
        check(excitation.get("type").getAsString().equals("apoli:simple") && !excitation.has("key") && !excitation.has("entity_action"), "excitation is exclusively passive");
        var recall = json("my_addon", AnubisWolfSpPerks.SOUL_RECALL.getPath());
        check(recall.getAsJsonObject("key").get("key").getAsString().equals("key.shape-shifter-curse.active_skill_3")
                && recall.get("cooldown").getAsInt() == 200 && !recall.has("condition"), "recall is key 3 in both sneak states, cooldown 10s");

        setupWorlds();
        TestMinion wolf = minion(player, world);
        WolfMinionPower.onSpawn(player, wolf);
        check(wolf.effects.get(StatusEffects.STRENGTH).getDuration() == 600 && wolf.effects.get(StatusEffects.STRENGTH).getAmplifier() == 0, "real wolf bonus action supplies Strength I for 30s");
        TestWorld otherWorld = allocate(TestWorld.class); otherWorld.entities = new HashMap<>(); otherWorld.server = world.server;
        set(MinecraftServer.class, world.server, "worlds", new HashMap<>(Map.of(World.OVERWORLD, world, World.NETHER, otherWorld)));
        TestMinion second = minion(player, otherWorld);
        var other = player(); select(other, form); other.form.formPerkMap.put(tree, new ArrayList<>(all)); select(other, form);
        TestMinion foreign = minion(other, world);
        player.minions.get(AnubisWolfMinionEntity.MinionID).add(foreign.id);
        player.minions.get(AnubisWolfMinionEntity.MinionID).add(UUID.randomUUID()); // unloaded entry
        var attack = ApoliDataTypes.BIENTITY_ACTION.read(json("my_addon", "form_anubis_wolf_sp_summon_wolf_on_hit").getAsJsonObject("bientity_action"));
        var struck = ApoliDataTypes.BIENTITY_ACTION.read(json("my_addon", "form_anubis_wolf_sp_summon_wolf_when_hit").getAsJsonObject("bientity_action"));
        attack.accept(new net.minecraft.util.Pair<>(player, wolf));
        struck.accept(new net.minecraft.util.Pair<>(wolf, player));
        check(player.minions.get(AnubisWolfMinionEntity.MinionID).size() == 4, "both real passive actions use the shared cap and normalize owner direction");
        for (int i = 0; i < 3; i++) player.minions.get(AnubisWolfMinionEntity.MinionID).add(UUID.randomUUID());
        Object pending = pendingSummon();
        var spawn = AnubisWolfSpSummonWolves.class.getDeclaredMethod("spawnMinionWolf", net.minecraft.server.network.ServerPlayerEntity.class, pending.getClass());
        spawn.setAccessible(true); spawn.invoke(null, player, pending);
        check(player.minions.get(AnubisWolfMinionEntity.MinionID).size() == 7, "active spawn rechecks the exact seven-wolf cap before entity creation");
        Identifier unrelated = new Identifier("my_addon", "test_other_minion");
        player.minions.put(unrelated, new ArrayList<>(List.of(UUID.randomUUID())));
        player.effects.put(StatusEffects.WITHER, new StatusEffectInstance(StatusEffects.WITHER, 200));
        var active = activeSummons();
        var manager = net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager.get(world);
        String skill = AnubisWolfSpSummonWolves.SKILL_ID;
        long castId = manager.begin(player.id, skill,
                new net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager.ResolvedConfig(600, 100, "on_release"), world.clock);
        set(pending.getClass(), pending, "castId", castId);
        active.put(player.id, pending); active.put(other.id, pendingSummon());
        var action = ApoliDataTypes.ENTITY_ACTION.read(recall.getAsJsonObject("entity_action"));
        action.accept(player);
        check(!player.hasStatusEffect(StatusEffects.WITHER) && !active.containsKey(player.id), "recall clears wither and pending summon state");
        check(active.containsKey(other.id), "recall leaves another player's pending summons alone");
        check(wolf.kills == 1 && second.kills == 1 && foreign.kills == 0, "recall kills only owned living wolves across loaded worlds");
        check(player.healed == 4 && other.healed == 0, "actual SSC death-heal power restores two per recalled wolf exactly once");
        check(manager.cooldown(player.id, skill).remaining(world.clock) == 100 && manager.control(player.id, skill) == null,
                "recall interrupts charging through the original five-second failure cooldown");
        check(!player.minions.containsKey(AnubisWolfMinionEntity.MinionID) && player.minions.containsKey(unrelated), "recall retires stale wolf entries without removing other minion types");
        action.accept(player); AnubisWolfSpSummonWolves.tick(player);
        check(player.healed == 4 && !active.containsKey(player.id), "repeated recall/tick cannot heal twice or restart summoning");
        world.clock += 5;
        action.accept(player);
        check(manager.cooldown(player.id, skill).remaining(world.clock) == 95, "repeated recall does not reset the interrupted cast cooldown");
        manager.reset(player.id, skill);
        long released = manager.begin(player.id, skill,
                new net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager.ResolvedConfig(600, 100, "on_release"), world.clock);
        manager.complete(released, world.clock);
        Object completed = pendingSummon();
        var phase = completed.getClass().getDeclaredField("phase"); phase.setAccessible(true);
        phase.set(completed, java.util.Arrays.stream(phase.getType().getEnumConstants()).filter(e -> e.toString().equals("ACTIVE")).findFirst().orElseThrow());
        active.put(player.id, completed);
        world.clock += 10;
        action.accept(player);
        check(manager.cooldown(player.id, skill).remaining(world.clock) == 590, "recalling an already released batch preserves its running cooldown");
        TestMinion blocked = minion(player, world);
        player.effects.put(SscAddon.STUN, new StatusEffectInstance(SscAddon.STUN, 40));
        action.accept(player);
        check(blocked.kills == 0, "full control blocks the registered recall action");
        player.effects.clear();
        select(player, RegPlayerForms.ANUBIS_WOLF_3);
        action.accept(player);
        check(blocked.kills == 0 && !AnubisWolfSpPerks.hasExtraCapacity(player)
                && has(player, original("perks/anubis_wolf_soul_excitation")), "switching back restores SSC effects and isolates SP recall");
        active.clear();

        ClassNode node = new ClassNode();
        try (var in = AnubisWolfSpPerkChecks.class.getResourceAsStream("/net/jackcooper/shapeShifterCurseAddon/ability/AnubisWolfSpSummonWolves.class")) {
            new ClassReader(in).accept(node, 0);
        }
        long hooks = node.methods.stream().filter(m -> m.name.equals("spawnMinionWolf"))
                .flatMap(m -> java.util.stream.StreamSupport.stream(m.instructions.spliterator(), false))
                .filter(i -> i instanceof MethodInsnNode call && call.owner.endsWith("/WolfMinionPower") && call.name.equals("onSpawn")).count();
        check(hooks == 1, "actual active/domain spawn path invokes wolf bonus exactly once");
        System.out.println("Anubis SP Perks: " + checks + " inheritance/cap/key/recall checks passed; simulated worlds and deaths, no live gameplay.");
        return checks;
    }

    private static Identifier original(String path) { return new Identifier("shape-shifter-curse", path); }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void setupWorlds() throws Exception {
        world.server = allocate(MinecraftDedicatedServer.class);
        world.states = new TestStates();
        set(MinecraftServer.class, world.server, "worlds", new HashMap<>(Map.of(World.OVERWORLD, world)));
    }

    public static final class TestStates extends net.minecraft.world.PersistentStateManager {
        private final Map<String, net.minecraft.world.PersistentState> entries = new HashMap<>();
        TestStates() { super(null, null); }
        @Override @SuppressWarnings("unchecked") public <T extends net.minecraft.world.PersistentState> T getOrCreate(
                java.util.function.Function<NbtCompound, T> read, java.util.function.Supplier<T> create, String id) {
            return (T) entries.computeIfAbsent(id, ignored -> create.get());
        }
    }
    @SuppressWarnings("unchecked") private static Map<UUID, Object> activeSummons() throws Exception {
        var field = AnubisWolfSpSummonWolves.class.getDeclaredField("ACTIVE_SUMMONS"); field.setAccessible(true);
        return (Map<UUID, Object>) field.get(null);
    }
    private static Object pendingSummon() throws Exception {
        var type = Class.forName(AnubisWolfSpSummonWolves.class.getName() + "$SummonData");
        var ctor = type.getDeclaredConstructor(int.class, boolean.class); ctor.setAccessible(true);
        return ctor.newInstance(4, false);
    }
    private static TestMinion minion(TestPlayer owner, TestWorld location) throws Exception {
        TestMinion wolf = allocate(TestMinion.class); wolf.id = UUID.randomUUID(); wolf.owner = owner; wolf.location = location;
        wolf.alive = true; wolf.effects = new HashMap<>(); location.entities.put(wolf.id, wolf);
        owner.minions.computeIfAbsent(AnubisWolfMinionEntity.MinionID, ignored -> new ArrayList<>()).add(wolf.id);
        return wolf;
    }

    public static final class TestMinion extends AnubisWolfMinionEntity {
        UUID id; TestPlayer owner; TestWorld location; boolean alive; int kills;
        Map<StatusEffect, StatusEffectInstance> effects;
        private TestMinion() { super(null, null); }
        @Override public UUID getUuid() { return id; }
        @Override public UUID getOwnerUuid() { return owner.id; }
        @Override public LivingEntity getOwner() { return owner; }
        @Override public World getWorld() { return location; }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean addStatusEffect(StatusEffectInstance effect, Entity source) { effects.put(effect.getEffectType(), effect); return true; }
        @Override public void kill() {
            if (alive) { alive = false; kills++; WolfMinionPower.onDeath(owner); }
        }
    }
}
