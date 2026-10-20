package net.jackcooper.shapeShifterCurseAddon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.onyxstudios.cca.api.v3.component.ComponentContainer;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import net.jackcooper.shapeShifterCurseAddon.ability.AllaySPPortableBeacon;
import net.jackcooper.shapeShifterCurseAddon.entity.TidalOrbEntity;
import net.jackcooper.shapeShifterCurseAddon.power.ParasiticFruitSeedPower;
import net.jackcooper.shapeShifterCurseAddon.screen.SpellbookScreenHandler;
import net.jackcooper.shapeShifterCurseAddon.spell.ScrollData;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryUtils;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

/** Actual transformed container/effect/targeting paths; world and equipment I/O are fixtures. */
public final class Issue38IntegrationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path", Files.readString(Path.of(System.getProperty("issue38.testClasspathFile"))));
        var knot = new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);
        var loader = knot.init(new String[]{"--version", "issue38-test", "--accessToken", "0"});
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.minecraft.SharedConstants", true, loader).getMethod("createGameVersion").invoke(null);
        Class.forName("net.minecraft.Bootstrap", true, loader).getMethod("initialize").invoke(null);
        Class.forName(Issue38IntegrationTest.class.getName() + "$Probe", true, loader).getMethod("run").invoke(null);
    }

    public static final class Probe {
        private static sun.misc.Unsafe unsafe;
        private static int checks;
        private static TestWorld world;

        public static void run() throws Exception {
            var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
            unsafe = (sun.misc.Unsafe) f.get(null);
            world = alloc(TestWorld.class); world.entities = new ArrayList<>();
            Registry.register(Registries.ITEM, new Identifier("ssc_addon", "moon_dust_spellbook"), SscAddon.MOON_DUST_SPELLBOOK);
            Registry.register(Registries.ITEM, new Identifier("ssc_addon", "magic_scroll"), SscAddon.MAGIC_SCROLL);
            net.jackcooper.shapeShifterCurseAddon.spell.SpellRegistry.init();
            bookTransactions();
            coral();
            fearBlindnessOwnership();
            twinTargets();
            waterTether();
            descriptions();
            tooltipLayout();
            portableBeacon();
            System.out.println("ISSUE38_CHECKS=" + checks + " PASS; actual container/effect/orb paths. Live game, visuals and multiplayer NOT tested by this automated run.");
        }

        private static void bookTransactions() throws Exception {
            for (int anchor : new int[]{0, 4, 15, 40}) {
                TestPlayer p = player();
                ItemStack book = new ItemStack(SscAddon.MOON_DUST_SPELLBOOK);
                SpellbookData.setScroll(book, 0, ScrollData.create("fire_bolt", 1));
                SpellbookData.setScroll(book, 1, ScrollData.create("fire_bolt", 1));
                p.inv.setStack(anchor, book);
                var before = book.getNbt().copy();
                var handler = new SpellbookScreenHandler(1, p.inv, book);
                check(book.getNbt().equals(before), "opening does not mutate or partially overwrite scroll NBT");
                int anchorGui = anchor < 9 ? handler.slotCount + 27 + anchor
                        : anchor < 36 ? handler.slotCount + anchor - 9 : -1;
                if (anchorGui >= 0) {
                    for (var action : new SlotActionType[]{SlotActionType.PICKUP, SlotActionType.QUICK_MOVE,
                            SlotActionType.THROW, SlotActionType.CLONE}) {
                        handler.onSlotClick(anchorGui, 0, action, p);
                        check(p.inv.getStack(anchor) == book, "open book cannot move through " + action);
                    }
                    check(handler.quickMove(p, anchorGui).isEmpty(), "quickMove API also rejects open book");
                }
                if (anchor < 9 || anchor == 40) {
                    handler.onSlotClick(0, anchor, SlotActionType.SWAP, p);
                    check(p.inv.getStack(anchor) == book && handler.getSlot(0).hasStack(), "number/F swap cannot detach book");
                }
                handler.onSlotClick(0, 0, SlotActionType.PICKUP, p);
                check(handler.getCursorStack().isOf(SscAddon.MAGIC_SCROLL), "normal extraction still works");
                check(SpellbookData.getScroll(book, 0).isEmpty(), "extraction removes authoritative serialized scroll");
                check(!SpellbookData.getScroll(book, 1).isEmpty(), "other stored scroll is preserved");
                handler.onSlotClick(0, 0, SlotActionType.PICKUP, p);
                check(handler.getCursorStack().isEmpty() && !SpellbookData.getScroll(book, 0).isEmpty(), "normal insertion restores exactly one scroll");
                SpellbookData.setMana(book, 17);
                handler.quickMove(p, 0);
                check(SpellbookData.getScroll(book, 0).isEmpty(), "shift extraction saves removal immediately");
                check(SpellbookData.getMana(book) == 17, "editing scrolls preserves live mana changes");
                p.inv.setStack(anchor, book.copy()); // Another mod replaces the reference.
                check(!handler.canUse(p), "external replacement invalidates open container");
                book.setCount(0);
                check(!handler.canUse(p), "emptied server book is never mistaken for client placeholder");
                handler.onSlotClick(1, 0, SlotActionType.PICKUP, p);
                check(!handler.getSlot(1).getStack().isEmpty(), "invalid container cannot extract stale scrolls");
                check(handler.quickMove(p, -1).isEmpty() && handler.quickMove(p, 999).isEmpty(), "invalid indices are harmless");
            }
        }

        private static void portableBeacon() throws Exception {
            me.shedaniel.autoconfig.AutoConfig.register(
                    net.jackcooper.shapeShifterCurseAddon.config.SSCAddonServerConfig.class,
                    me.shedaniel.autoconfig.serializer.GsonConfigSerializer::new);
            net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig.server().whitelistEnabled = true;
            io.github.apace100.apoli.power.factory.condition.EntityConditions.register();
            net.jackcooper.shapeShifterCurseAddon.condition.SscAddonConditions.register();
            Method tick = AllaySPPortableBeacon.class.getDeclaredMethod("tick", ServerPlayerEntity.class);
            tick.setAccessible(true);
            Method clear = AllaySPPortableBeacon.class.getDeclaredMethod("clearPlayer", ServerPlayerEntity.class);
            clear.setAccessible(true);
            Identifier mana = new Identifier("my_addon", "form_allay_sp_mana_resource");
            Identifier active = new Identifier("my_addon", "form_allay_sp_beacon_active");
            TestPlayer owner = player(), ally = player(), outsider = player(), diagonal = player(), spectator = player();
            VariableIntPower energy = resource(owner, mana, 200, 10);
            VariableIntPower flag = resource(owner, active, 1, 0);
            position(ally, new Vec3d(20, 0, 0)); position(outsider, new Vec3d(2, 0, 0));
            position(diagonal, new Vec3d(15, 0, 15)); spectator.spectator = true;
            owner.tags.add(net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils.WHITELIST_MOB_TAG_PREFIX + ally.id);
            owner.tags.add(net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils.WHITELIST_MOB_TAG_PREFIX + diagonal.id);
            owner.tags.add(net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils.WHITELIST_MOB_TAG_PREFIX + spectator.id);
            world.entities = new ArrayList<>(List.of(owner, ally, outsider, diagonal, spectator));
            ItemStack beacon = new ItemStack(Items.BEACON); owner.inv.setStack(0, beacon);
            check(!beacon.hasGlint(), "ordinary beacon has no activation glint");
            AllaySPPortableBeacon.toggleBeacon(owner);
            check(flag.getValue() == 1 && beacon.hasGlint(), "activation synchronizes flag and actual Mixin glint");
            check(owner.effects.get(net.minecraft.entity.effect.StatusEffects.HASTE).getAmplifier() == 1,
                    "beacon grants owner Haste II as documented");
            check(ally.effects.get(net.minecraft.entity.effect.StatusEffects.HASTE).getAmplifier() == 1
                    && ally.effects.get(net.minecraft.entity.effect.StatusEffects.HASTE).getDuration() == 100,
                    "whitelisted ally exactly 20 blocks away receives Haste II");
            check(outsider.effects.isEmpty() && diagonal.effects.isEmpty() && spectator.effects.isEmpty(),
                    "Haste aura excludes unlisted, outside-sphere and spectator targets");
            check(!owner.effects.containsKey(net.minecraft.entity.effect.StatusEffects.REGENERATION)
                    && !ally.effects.containsKey(net.minecraft.entity.effect.StatusEffects.REGENERATION),
                    "beacon does not grant the erroneous regeneration effect");
            for (int i = 1; i < 60; i++) {
                tick.invoke(null, owner);
                check(energy.getValue() == 10, "beacon does not drain every tick: " + i);
            }
            tick.invoke(null, owner);
            check(energy.getValue() == 9, "one energy after exactly 60 active ticks");
            ItemStack moved = beacon.copy();
            owner.inv.setStack(0, ItemStack.EMPTY);
            new net.minecraft.screen.slot.Slot(owner.inv, 17, 0, 0).setStack(moved);
            tick.invoke(null, owner);
            check(flag.getValue() == 1 && moved.hasGlint(), "moving activated beacon into own backpack preserves activation");
            owner.effects.clear(); ally.effects.clear();
            for (int i = 0; i < 19; i++) tick.invoke(null, owner);
            check(owner.effects.containsKey(net.minecraft.entity.effect.StatusEffects.HASTE)
                    && ally.effects.containsKey(net.minecraft.entity.effect.StatusEffects.HASTE)
                    && !owner.effects.containsKey(net.minecraft.entity.effect.StatusEffects.REGENERATION),
                    "backpack beacon refreshes whitelisted Haste II without healing");
            var chest = new net.minecraft.inventory.SimpleInventory(1);
            new net.minecraft.screen.slot.Slot(chest, 0, 0, 0).setStackNoCallbacks(moved.copy());
            check(!chest.getStack(0).hasGlint(), "placing beacon into chest clears only activation glint");
            owner.inv.setStack(17, ItemStack.EMPTY); tick.invoke(null, owner);
            check(flag.getValue() == 0 && !moved.hasGlint(), "leaving own inventory disables owner beacon");
            int after = energy.getValue(); ally.effects.clear();
            for (int i = 0; i < 160; i++) tick.invoke(null, owner);
            check(energy.getValue() == after && ally.effects.isEmpty(), "inactive beacon neither drains nor refreshes buffs");

            beacon = new ItemStack(Items.BEACON); owner.inv.setStack(0, beacon);
            energy.setValue(1); AllaySPPortableBeacon.toggleBeacon(owner);
            for (int i = 0; i < 60; i++) tick.invoke(null, owner);
            check(energy.getValue() == 0 && flag.getValue() == 0 && !beacon.hasGlint(), "last energy point turns beacon fully off");
            owner.effects.clear(); AllaySPPortableBeacon.toggleBeacon(owner);
            check(flag.getValue() == 0 && owner.effects.isEmpty() && !beacon.hasGlint(), "zero energy cannot activate or grant buffs");
            energy.setValue(10); AllaySPPortableBeacon.toggleBeacon(owner); energy.setValue(0);
            tick.invoke(null, owner);
            check(flag.getValue() == 0, "energy exhausted by another skill also disables beacon immediately");
            energy.setValue(10); AllaySPPortableBeacon.toggleBeacon(owner);
            ItemStack transfer = beacon.copy();
            new net.minecraft.screen.slot.Slot(ally.inv, 0, 0, 0).setStack(transfer);
            check(!transfer.hasGlint(), "direct transfer never passes active glint to another owner");
            owner.inv.setStack(0, ItemStack.EMPTY); tick.invoke(null, owner);
            check(flag.getValue() == 0, "transferred beacon disables original owner");
            owner.inv.setStack(0, beacon = new ItemStack(Items.BEACON)); AllaySPPortableBeacon.toggleBeacon(owner);
            owner.powerMap.remove(PowerTypeRegistry.get(mana)); owner.powerMap.remove(PowerTypeRegistry.get(active));
            tick.invoke(null, owner);
            check(!beacon.hasGlint(), "form change clears activation and glint");
            resource(owner, mana, 200, 10); flag = resource(owner, active, 1, 0);
            AllaySPPortableBeacon.toggleBeacon(owner); clear.invoke(null, owner);
            check(flag.getValue() == 0 && !beacon.hasGlint(), "disconnect cleanup clears owned session and glint");
            flag.setValue(1); tick.invoke(null, owner);
            check(flag.getValue() == 0, "legacy active flag without a session cannot persist");
            owner.inv.setStack(0, ItemStack.EMPTY);
            beacon = new ItemStack(Items.BEACON);
            beacon.getOrCreateNbt().putString("custom_data", "preserved");
            beacon.addEnchantment(net.minecraft.enchantment.Enchantments.UNBREAKING, 1);
            var originalNbt = beacon.getNbt().copy();
            owner.inv.setStack(40, beacon);
            AllaySPPortableBeacon.toggleBeacon(owner);
            check(flag.getValue() == 1 && beacon.hasGlint(), "offhand beacon can activate");
            AllaySPPortableBeacon.deactivateBeacon(owner);
            check(beacon.getNbt().equals(originalNbt) && beacon.hasGlint(), "deactivation preserves real enchantments and custom NBT");
            AllaySPPortableBeacon.toggleBeacon(owner); owner.alive = false; tick.invoke(null, owner);
            check(flag.getValue() == 0 && !AllaySPPortableBeacon.isActiveStack(beacon), "death ends activation even with keep-inventory");
            owner.alive = true;
            int energyBeforeToggles = net.jackcooper.shapeShifterCurseAddon.util.PowerUtils.getResourceValue(owner, mana);
            for (int burst = 0; burst < 3; burst++) {
                AllaySPPortableBeacon.toggleBeacon(owner);
                for (int i = 0; i < 20; i++) tick.invoke(null, owner);
                AllaySPPortableBeacon.deactivateBeacon(owner);
                check(net.jackcooper.shapeShifterCurseAddon.util.PowerUtils.getResourceValue(owner, mana)
                        == energyBeforeToggles - (burst == 2 ? 1 : 0), "toggle cannot reset accumulated drain time: " + burst);
            }
            VariableIntPower cooldown = resource(owner, new Identifier("my_addon", "form_allay_sp_mana_cooldown_resource"), 100, 0);
            var manaJson = JsonParser.parseString(new String(Issue38IntegrationTest.class.getResourceAsStream(
                    "/data/my_addon/powers/form_allay_sp_mana.json").readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            var regenJson = manaJson.getAsJsonObject("regen").getAsJsonObject("condition");
            var regenFactory = io.github.apace100.apoli.registry.ApoliRegistries.ENTITY_CONDITION.get(
                    new Identifier(regenJson.get("type").getAsString()));
            var canRegenerate = regenFactory.read(regenJson);
            check(canRegenerate.test(owner), "ordinary passive energy recovery is allowed while beacon is off");
            AllaySPPortableBeacon.toggleBeacon(owner);
            check(!canRegenerate.test(owner), "actual Apoli regeneration condition blocks passive recovery while beacon is active");
            AllaySPPortableBeacon.deactivateBeacon(owner);
            check(canRegenerate.test(owner), "turning beacon off restores passive energy recovery eligibility");
            cooldown.setValue(5);
            check(!canRegenerate.test(owner), "existing recovery cooldown still blocks regeneration after beacon turns off");
            cooldown.setValue(0);
            net.jackcooper.shapeShifterCurseAddon.util.PowerUtils.setResourceValue(owner, mana, 200);
            check(!canRegenerate.test(owner), "existing full-energy limit remains effective");
            for (String path : List.of("form_allay_sp_beacon_effect", "form_allay_sp_beacon_mana_tick", "form_allay_sp_mana")) {
                var text = new String(Issue38IntegrationTest.class.getResourceAsStream("/data/my_addon/powers/" + path + ".json").readAllBytes(), StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(text).getAsJsonObject();
                if (path.equals("form_allay_sp_mana")) json = json.getAsJsonObject("beacon_consume");
                check(json.get("type").getAsString().equals("apoli:simple"), "legacy duplicate action disabled: " + path);
            }
            world.entities.clear();
        }

        private static VariableIntPower resource(TestPlayer player, Identifier id, int max, int value) {
            if (!PowerTypeRegistry.contains(id)) PowerTypeRegistry.register(id, new PowerType<>(id, null));
            PowerType<?> type = PowerTypeRegistry.get(id);
            VariableIntPower power = new VariableIntPower(type, player, value, 0, max);
            player.powerMap.put(type, power);
            return power;
        }

        private static void coral() throws Exception {
            var oldIo = AccessoryUtils.nowAccessoryMod;
            AccessoryUtils.nowAccessoryMod = (AccessoryUtils.AccessoryIO) java.lang.reflect.Proxy.newProxyInstance(
                    AccessoryUtils.class.getClassLoader(), new Class<?>[]{AccessoryUtils.AccessoryIO.class},
                    (proxy, method, args) -> method.getName().equals("getEntitySlots")
                            ? Map.of(new Pair<>("chest", "necklace"), ((TestPlayer) args[0]).worn) : null);
            try {
                TestPlayer p = player(); p.age = 10;
                SscAddon.PLAYING_DEAD.applyUpdateEffect(p, 0);
                near(p.healed, 2.5, "without necklace, original healing is retained");
                p.worn.add(new ItemStack(SscAddon.ACTIVE_CORAL_NECKLACE)); p.healed = 0;
                for (int i = 0; i < 12; i++) SscAddon.PLAYING_DEAD.applyUpdateEffect(p, 0);
                near(p.healed, 10, "full necklace Play Dead grants 10 HP healing");
                near(p.absorption, 40, "full necklace Play Dead grants 40 absorption HP");
                p.absorption = 60;
                SscAddon.PLAYING_DEAD.applyUpdateEffect(p, 0);
                near(p.absorption, 60, "necklace never truncates stronger absorption from another source");
                p.worn.clear(); p.absorption = 0; p.healed = 0;
                SscAddon.PLAYING_DEAD.applyUpdateEffect(p, 0);
                near(p.healed, 2.5, "unequipping restores normal healing immediately");
                near(p.absorption, 0, "unequipped necklace grants no absorption");
            } finally { AccessoryUtils.nowAccessoryMod = oldIo; }
        }

        private static void twinTargets() throws Exception {
            TestPlayer caster = player(), primary = player(), extra = player(), diagonal = player();
            position(caster, new Vec3d(-2, 0, 0)); position(primary, Vec3d.ZERO);
            position(extra, new Vec3d(3, 0, 0)); position(diagonal, new Vec3d(3, 3, 3));
            ArmorStandEntity marker = new ArmorStandEntity(world, 0.1, 0, 0);
            world.entities = new ArrayList<>(List.of(caster, primary, marker, extra, diagonal));
            Method find = ParasiticFruitSeedPower.class.getDeclaredMethod("findNearbyOther", ServerPlayerEntity.class, LivingEntity.class);
            find.setAccessible(true);
            check(find.invoke(null, caster, primary) == extra, "twin ignores nearest invisible/armor-stand marker");
            extra.spectator = true;
            check(find.invoke(null, caster, primary) == null, "spectator and diagonal outside 4-block sphere are excluded");
            extra.spectator = false; extra.alive = false;
            check(find.invoke(null, caster, primary) == null, "dead extra target is excluded");
            check(!ParasiticFruitSeedPower.isValidSeedHost(caster, marker), "marker never consumes planted seed");
            check(ParasiticFruitSeedPower.isValidSeedHost(caster, caster), "caster can still pick up own ground seed");
            world.entities.clear();
        }

        private static void fearBlindnessOwnership() throws Exception {
            Class<?> client = net.jackcooper.shapeShifterCurseAddon.client.NightmareFearClient.class;
            Method update = client.getDeclaredMethod("updateBlindness", Map.class, boolean.class);
            Method clear = client.getDeclaredMethod("clearFearBlindness");
            update.setAccessible(true); clear.setAccessible(true);
            StatusEffect blindness = net.minecraft.entity.effect.StatusEffects.BLINDNESS;
            Map<StatusEffect, net.minecraft.entity.effect.StatusEffectInstance> effects = new HashMap<>();
            TestPlayer p = player();
            clear.invoke(null);
            try {
                var playDead = new net.minecraft.entity.effect.StatusEffectInstance(blindness, 120, 0, false, false, false);
                effects.put(blindness, playDead);
                for (int tick = 0; tick < 120; tick++) {
                    update.invoke(null, effects, false);
                    check(effects.get(blindness) == playDead, "no-fear tick preserves Play Dead blindness at tick " + tick);
                    check(playDead.getDuration() == 120 - tick, "Play Dead retains its full six-second countdown");
                    if (!playDead.update(p, () -> {})) effects.remove(blindness);
                }
                update.invoke(null, effects, false);
                check(effects.isEmpty(), "Play Dead blindness expires naturally after 120 ticks");

                var shortOther = new net.minecraft.entity.effect.StatusEffectInstance(blindness, 5);
                effects.put(blindness, shortOther);
                update.invoke(null, effects, true);
                check(effects.get(blindness) == shortOther && shortOther.getDuration() == 5,
                        "fear does not overwrite even a short external blindness");
                update.invoke(null, effects, false);
                check(effects.get(blindness) == shortOther, "fear ending does not clear external blindness");

                effects.clear();
                for (int tick = 0; tick < 80; tick++) {
                    update.invoke(null, effects, true);
                    var carrier = effects.get(blindness);
                    check(carrier != null && carrier.getDuration() >= 30 && carrier.getDuration() <= 40,
                            "fear maintains its own local carrier at tick " + tick);
                    carrier.update(p, () -> {});
                }
                update.invoke(null, effects, false);
                check(!effects.containsKey(blindness), "ending fear removes its own carrier immediately");

                update.invoke(null, effects, true);
                // Vanilla's incoming status packet installs a NEW instance through setStatusEffect.
                // Use identical values too: cleanup must compare identity, not effect/duration equality.
                var replacement = new net.minecraft.entity.effect.StatusEffectInstance(blindness, 40, 0, false, false, false);
                effects.put(blindness, replacement);
                update.invoke(null, effects, false);
                check(effects.get(blindness) == replacement, "server replacement arriving just before fear ends is retained");

                effects.clear(); update.invoke(null, effects, true);
                var longer = new net.minecraft.entity.effect.StatusEffectInstance(blindness, 600);
                effects.put(blindness, longer);
                update.invoke(null, effects, true);
                update.invoke(null, effects, false);
                check(effects.get(blindness) == longer && longer.getDuration() == 600,
                        "server blindness arriving during fear keeps its duration after fear ends");

                effects.clear(); update.invoke(null, effects, true);
                Map<StatusEffect, net.minecraft.entity.effect.StatusEffectInstance> respawn = new HashMap<>();
                var respawnBlindness = new net.minecraft.entity.effect.StatusEffectInstance(blindness, 120);
                respawn.put(blindness, respawnBlindness);
                update.invoke(null, respawn, false);
                check(effects.isEmpty(), "switching player/status map cleans only the old local carrier");
                check(respawn.get(blindness) == respawnBlindness, "respawn map keeps its independent blindness");
                clear.invoke(null);
                check(respawn.get(blindness) == respawnBlindness, "disconnect cleanup does not remove an external effect");

                effects.clear(); update.invoke(null, effects, true); clear.invoke(null);
                check(effects.isEmpty(), "disconnect cleanup removes an owned carrier");
                var infinite = new net.minecraft.entity.effect.StatusEffectInstance(blindness, -1);
                effects.put(blindness, infinite);
                update.invoke(null, effects, true); update.invoke(null, effects, false);
                check(effects.get(blindness) == infinite, "indefinite external blindness is preserved");
            } finally { clear.invoke(null); }
        }

        private static void waterTether() throws Exception {
            Method move = TidalOrbEntity.class.getDeclaredMethod("moveWithWallCheck", ServerWorld.class, double.class, double.class, double.class);
            Method enter = TidalOrbEntity.class.getDeclaredMethod("enterAttractPhase", ServerWorld.class);
            Method tether = TidalOrbEntity.class.getDeclaredMethod("applyTether", ServerWorld.class);
            move.setAccessible(true); enter.setAccessible(true); tether.setAccessible(true);
            for (boolean wet : new boolean[]{false, true}) {
                world.wet = wet; world.wall = false;
                TidalOrbEntity orb = new TidalOrbEntity(SscAddon.TIDAL_ORB_ENTITY, world);
                orb.setPosition(0.5, 2, 0.5);
                check(!(boolean) move.invoke(orb, world, 1.0, 0.0, 0.0), "orb moves in " + (wet ? "water" : "air"));
                near(orb.getX(), 1.5, "liquids do not stop orb movement");
                TestPlayer target = player(); position(target, orb.getPos().add(2, 0, 0));
                world.entities = new ArrayList<>(List.of(target));
                enter.invoke(orb, world);
                check(orb.isTetherActive(), "tether activates in " + (wet ? "water" : "air"));
                position(target, orb.getPos().add(6.5, 0, 0)); target.setVelocity(Vec3d.ZERO);
                tether.invoke(orb, world);
                check(target.getVelocity().x < 0, "captured target is pulled back in " + (wet ? "water" : "air"));
                world.wall = true;
                orb.setPosition(0.5, 2, 0.5);
                check((boolean) move.invoke(orb, world, 3.0, 0.0, 0.0), "solid wall still stops orb underwater/on land");
            }
            world.wet = false; world.wall = false; world.entities.clear();
        }

        private static void descriptions() throws Exception {
            for (String lang : new String[]{"zh_cn", "en_us"}) {
                JsonObject texts = json("assets/my_addon/lang/" + lang + ".json");
                for (String key : new String[]{"item.ssc_addon.blue_fire_amulet.tooltip_2", "item.ssc_addon.frost_amulet.tooltip.2",
                        "item.ssc_addon.active_coral_necklace.desc", "item.ssc_addon.active_coral_necklace.tooltip_decay",
                        "power.my_addon.form_snow_fox_frostspine_frost_spike.description", "power.my_addon.form_snow_fox_frostspine_frost_forge.description"}) {
                    check(texts.has(key) && !texts.get(key).getAsString().contains("???"), "translated gameplay description exists: " + lang + "/" + key);
                }
                String pros = texts.get("codex.form.my_addon.snow_fox_frostspine.pros").getAsString();
                check(pros.contains("1.2") && pros.contains("0.2") && pros.contains("10%"), "book explains both Frostspine actions");
            }
            JsonObject origin = json("data/my_addon/origins/form_snow_fox_frostspine.json");
            check(origin.get("powers").toString().contains("form_snow_fox_frostspine_frost_forge"), "secondary explanation is attached to actual form");
            check(!json("data/my_addon/powers/form_snow_fox_frostspine_frost_spike.json").get("hidden").getAsBoolean(), "main ability is visible in ability menu");
        }

        private static JsonObject json(String path) throws Exception {
            try (var stream = Probe.class.getClassLoader().getResourceAsStream(path)) {
                if (stream == null) throw new AssertionError(path);
                return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }

        private static void tooltipLayout() throws Exception {
            var font = alloc(TestFont.class);
            var renderer = new net.minecraft.client.font.TextRenderer(ignored -> font, false);
            for (int width : new int[]{96, 160, 320, 640}) {
                var lines = new ArrayList<net.minecraft.text.Text>();
                lines.add(net.minecraft.text.Text.literal("Title"));
                lines.add(net.minecraft.text.Text.literal("A long accessory description with conditions, benefits, and costs that must fit the available GUI width.")
                        .formatted(net.minecraft.util.Formatting.GOLD));
                lines.add(net.minecraft.text.Text.literal("饰品效果、触发条件与使用限制必须完整显示，中文说明也需要根据当前界面宽度换行。")
                        .formatted(net.minecraft.util.Formatting.AQUA));
                lines.add(net.minecraft.text.Text.literal("line1\nline2"));
                net.jackcooper.shapeShifterCurseAddon.client.tooltip.AddonTooltipFormatter.wrap(renderer, lines, width);
                int max = Math.min(260, width - 24);
                check(lines.size() > 4, "long descriptions are wrapped at GUI width " + width);
                for (var line : lines) check(renderer.getWidth(line) <= max, "every tooltip row fits available width");
                check(lines.stream().noneMatch(line -> line.getString().contains("\n")), "embedded line breaks are expanded");
                check(lines.stream().anyMatch(line -> line.getSiblings().stream().anyMatch(part ->
                        part.getStyle().getColor() != null)), "wrapping retains text colors");
            }
        }

        public static final class TestFont extends net.minecraft.client.font.FontStorage {
            private TestFont() { super(null, new Identifier("issue38", "metrics")); }
            @Override public net.minecraft.client.font.Glyph getGlyph(int codePoint, boolean validate) {
                return new net.minecraft.client.font.Glyph() {
                    @Override public float getAdvance() { return 6; }
                    @Override public net.minecraft.client.font.GlyphRenderer bake(java.util.function.Function<net.minecraft.client.font.RenderableGlyph, net.minecraft.client.font.GlyphRenderer> bake) { return null; }
                };
            }
        }
        private static TestPlayer player() throws Exception {
            TestPlayer p = alloc(TestPlayer.class); p.id = UUID.randomUUID(); p.alive = true;
            p.inv = new PlayerInventory(p); p.abilities = new PlayerAbilities();
            p.tags = new HashSet<>(); p.effects = new HashMap<>(); p.powerMap = new HashMap<>();
            PowerHolderComponent powers = (PowerHolderComponent) java.lang.reflect.Proxy.newProxyInstance(
                    PowerHolderComponent.class.getClassLoader(), new Class<?>[]{PowerHolderComponent.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getPower" -> p.powerMap.get(args[0]);
                        case "getPowers" -> List.copyOf(p.powerMap.values());
                        // Packet delivery is fixture I/O; keep resource access real without creating connections.
                        case "hasPower" -> false;
                        default -> null;
                    });
            p.components = ComponentContainer.Factory.builder(TestPlayer.class)
                    .component(PowerHolderComponent.KEY, x -> powers).build().createContainer(p);
            p.worn = new ArrayList<>(); p.tracker = new DataTracker(p);
            var field = Entity.class.getDeclaredField("dataTracker"); field.setAccessible(true); field.set(p, p.tracker);
            field = Entity.class.getDeclaredField("dimensions"); field.setAccessible(true);
            field.set(p, net.minecraft.entity.EntityDimensions.changing(0.6f, 1.8f));
            startTracking(p, "FLAGS", (byte) 0);
            startTracking(p, "AIR", 300);
            startTracking(p, "CUSTOM_NAME", Optional.empty());
            startTracking(p, "NAME_VISIBLE", false);
            startTracking(p, "SILENT", false);
            startTracking(p, "NO_GRAVITY", false);
            startTracking(p, "POSE", net.minecraft.entity.EntityPose.STANDING);
            startTracking(p, "FROZEN_TICKS", 0);
            p.initializeTracker(); p.setVelocity(Vec3d.ZERO); position(p, Vec3d.ZERO);
            return p;
        }
        @SuppressWarnings("unchecked") private static <T> void startTracking(TestPlayer p, String name, T value) throws Exception {
            var field = Entity.class.getDeclaredField(name); field.setAccessible(true);
            p.tracker.startTracking((net.minecraft.entity.data.TrackedData<T>) field.get(null), value);
        }
        private static void position(Entity entity, Vec3d pos) throws Exception {
            var field = Entity.class.getDeclaredField("pos"); field.setAccessible(true); field.set(entity, pos);
            entity.setBoundingBox(new Box(pos.x - 0.3, pos.y, pos.z - 0.3, pos.x + 0.3, pos.y + 1.8, pos.z + 0.3));
        }
        @SuppressWarnings("unchecked") private static <T> T alloc(Class<T> type) throws Exception { return (T) unsafe.allocateInstance(type); }
        private static void near(double actual, double expected, String message) { check(Math.abs(actual - expected) < 0.0001, message + " actual=" + actual); }
        private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }

        public static final class TestPlayer extends ServerPlayerEntity implements dev.onyxstudios.cca.api.v3.component.ComponentProvider {
            UUID id; PlayerInventory inv; PlayerAbilities abilities; DataTracker tracker;
            ComponentContainer components; Set<String> tags;
            Map<PowerType<?>, Power> powerMap; Map<StatusEffect, net.minecraft.entity.effect.StatusEffectInstance> effects;
            boolean spectator, alive; float healed, absorption; List<ItemStack> worn;
            private TestPlayer() { super(null, null, new GameProfile(UUID.randomUUID(), "issue38")); }
            void initializeTracker() { super.initDataTracker(); }
            @Override public DataTracker getDataTracker() { return tracker; }
            @Override public World getWorld() { return world; }
            @Override public ServerWorld getServerWorld() { return world; }
            @Override public UUID getUuid() { return id; }
            @Override public String getUuidAsString() { return id.toString(); }
            @Override public PlayerInventory getInventory() { return inv; }
            @Override public ComponentContainer getComponentContainer() { return components; }
            @Override public ItemStack getMainHandStack() { return inv.getMainHandStack(); }
            @Override public ItemStack getOffHandStack() { return inv.getStack(40); }
            @Override public void sendMessage(net.minecraft.text.Text message, boolean overlay) { }
            @Override public void playSound(SoundEvent sound, SoundCategory category, float volume, float pitch) { }
            @Override public boolean addStatusEffect(net.minecraft.entity.effect.StatusEffectInstance effect, Entity source) { effects.put(effect.getEffectType(), effect); return true; }
            @Override public PlayerAbilities getAbilities() { return abilities; }
            @Override public void onTrackedDataSet(net.minecraft.entity.data.TrackedData<?> data) { }
            @Override public boolean isSpectator() { return spectator; }
            @Override public boolean isAlive() { return alive; }
            @Override public boolean isDead() { return !alive; }
            @Override public boolean isSleeping() { return false; }
            @Override public boolean isTouchingWater() { return world.wet; }
            @Override public boolean hasStatusEffect(StatusEffect effect) { return false; }
            @Override public Set<String> getCommandTags() { return tags; }
            @Override public void heal(float amount) { healed += amount; }
            @Override public float getAbsorptionAmount() { return absorption; }
            @Override public void setAbsorptionAmount(float amount) { absorption = amount; }
        }

        public static final class TestWorld extends ServerWorld {
            List<LivingEntity> entities; boolean wet, wall;
            private TestWorld() { super(null, null, null, null, World.OVERWORLD, null, null, false, 0, List.of(), false, null); }
            @Override public long getTime() { return 100; }
            @Override public net.minecraft.resource.featuretoggle.FeatureSet getEnabledFeatures() { return net.minecraft.resource.featuretoggle.FeatureFlags.VANILLA_FEATURES; }
            @Override public BlockState getBlockState(BlockPos pos) { return wall && pos.getX() >= 2 ? Blocks.STONE.getDefaultState() : wet ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState(); }
            @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
            @Override public Entity getEntity(UUID id) { return entities.stream().filter(e -> e.getUuid().equals(id)).findFirst().orElse(null); }
            @Override public Entity getEntityById(int id) { return entities.stream().filter(e -> e.getId() == id).findFirst().orElse(null); }
            @Override public ServerPlayerEntity getPlayerByUuid(UUID id) { return null; }
            @Override public <T extends Entity> List<T> getEntitiesByClass(Class<T> type, Box box, Predicate<? super T> predicate) {
                return entities.stream().filter(type::isInstance).map(type::cast).filter(e -> box.intersects(e.getBoundingBox())).filter(predicate).toList();
            }
            @Override public void playSound(PlayerEntity player, double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch) { }
            @Override public <T extends ParticleEffect> int spawnParticles(T particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed) { return count; }
        }
    }
}
