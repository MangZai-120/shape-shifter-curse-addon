package net.jackcooper.shapeShifterCurseAddon.evolution;

import com.mojang.authlib.GameProfile;
import dev.onyxstudios.cca.api.v3.component.ComponentContainer;
import dev.onyxstudios.cca.api.v3.component.ComponentProvider;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon;
import net.fabricmc.fabric.impl.networking.NetworkHandlerExtensions;
import net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.client.evolution.EvolutionScreen;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.network.JsonSnapshotSync;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.config.CommonConfig;
import net.onixary.shapeShifterCurseFabric.player_form.NormalForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.IFormWithCondition;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.TransformManager;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class EvolutionSystemIntegrationProbe {
    private static sun.misc.Unsafe unsafe;
    private static TestWorld world;
    private static int checks;
    private static Map<String, String> rawRoutes;

    public static void run() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (sun.misc.Unsafe) field.get(null);
        world = allocate(TestWorld.class);
        rawRoutes = new LinkedHashMap<>();
        for (String route : List.of("familiar_fox", "axolotl")) {
            try (var input = EvolutionSystemIntegrationProbe.class.getResourceAsStream(
                    "/data/my_addon/ssca_evolution/routes/" + route + ".json")) {
                rawRoutes.put(route, new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        loadServerRoutes(rawRoutes);
        for (EvolutionRoute route : EvolutionRegistry.INSTANCE.all().values()) {
            RegPlayerForms.registerPlayerForm(new NormalForm(route.startForm));
        }
        if (!Registries.ITEM.containsId(new Identifier("ssc_addon", "psionic_orb"))) {
            Registry.register(Registries.ITEM, new Identifier("ssc_addon", "psionic_orb"), SscAddon.PSIONIC_ORB);
        }
        ShapeShifterCurseFabric.commonConfig = new CommonConfig();
        ShapeShifterCurseFabric.commonConfig.immediatelyTransform = false;
        TransformManager.onServerInit();
        normalProgression();
        batchSynchronization();
        prerequisitesAndSelections();
        migration();
        routeValidation();
        interruptedJobChanges();
        rejectedAndInterruptedAnimations();
        entryAndPaymentValidation();
        synchronization();
        failedReloadPreservesProgress();
        auditBoundaryRegressions();
        auditRegistryAndRequests();
        jobReloadBoundaries();
        EvolutionTalentsIntegrationProbe.run(rawRoutes);
        net.jackcooper.shapeShifterCurseAddon.AuditServerRegressionProbe.run();
        System.out.println("Evolution system integration: " + checks + " checks passed. Live gameplay NOT tested.");
    }

    private static void normalProgression() throws Exception {
        for (EvolutionRoute route : EvolutionRegistry.INSTANCE.all().values()) {
            TestPlayer player = player(route);
            EvolutionManager.tickPlayer(player);
            check(player.evolution.hasMigrated() && player.evolution.hasStarted(), route.routeId + " initializes fresh progression");
            player.totalExperience = EvolutionComponent.levelToTotalExp(50) * 2;
            EvolutionManager.tickPlayer(player);
            check(player.evolution.getEvoLevel() == 50, route.routeId + " reaches level 50");
            check(player.evolution.getPoints() == route.getLevelMilestones().length, route.routeId + " awards exact budget");
            List<String> paid = route.nodes.stream().filter(n -> !n.autoUnlock).map(n -> n.id).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            Collections.reverse(paid);
            check(EvolutionManager.tryUnlockBatch(player, paid) == paid.size(), route.routeId + " unlocks reverse-ordered complete tree");
            EvolutionManager.tickPlayer(player);
            check(player.evolution.getPoints() == 0 && EvolutionManager.canUpgradeFoxEvolve(player), route.routeId + " complete tree enables SP evolution");
            check(EvolutionManager.tryUnlockBatch(player, paid) == 0, route.routeId + " duplicate requests do not spend points");
            int exp = player.evolution.getExp();
            player.totalExperience -= 10;
            EvolutionManager.tickPlayer(player);
            check(player.evolution.getExp() == exp, route.routeId + " spending vanilla experience preserves evolution experience");
            reload(player);
            EvolutionManager.tickPlayer(player);
            check(player.evolution.getEvoLevel() == 50 && player.evolution.getPoints() == 0, route.routeId + " ordinary reload preserves progression");
        }
        EvolutionRoute axolotl = EvolutionRegistry.INSTANCE.getRoute("axolotl");
        check(axolotl.nextMilestone(39) == 40, "axolotl last point is at level 40");
        check(axolotl.nextMilestone(40) == -1 && axolotl.nextMilestone(44) == -1, "axolotl does not advertise a level-45 point");
        check(EvolutionRegistry.INSTANCE.getRoute("familiar_fox").nextMilestone(40) == 45, "familiar fox retains level-45 point");
    }

    private static void batchSynchronization() throws Exception {
        for (EvolutionRoute route : EvolutionRegistry.INSTANCE.all().values()) {
            TestPlayer player = player(route);
            EvolutionManager.tickPlayer(player);
            player.totalExperience = EvolutionComponent.levelToTotalExp(50) * 2;
            EvolutionManager.tickPlayer(player);
            var requested = route.nodes.stream().filter(node -> !node.autoUnlock).map(node -> node.id).toList();
            var order = EvolutionSelection.resolve(route, player.evolution, requested);
            check(order.size() > 1, "Fixture includes a real dependent batch");
            int before = player.syncLookups;
            check(EvolutionManager.tryUnlock(player, order.get(0)), "A standalone unlock still succeeds");
            check(player.syncLookups - before == 1, "A standalone unlock still synchronizes immediately");
            before = player.syncLookups;
            check(EvolutionManager.tryUnlockBatch(player, requested) == requested.size() - 1,
                    "Batch retains prerequisite order, point budget and already-unlocked filtering");
            check(player.syncLookups - before == 1, "A multi-node batch publishes exactly one final component snapshot");
            check(player.evolution.getPoints() == 0 && requested.stream().allMatch(player.evolution::isUnlocked),
                    "One published final state contains every accepted unlock and exact point cost");
            before = player.syncLookups;
            check(EvolutionManager.tryUnlockBatch(player, requested) == 0 && player.syncLookups == before,
                    "A duplicate batch has no state change or redundant synchronization");
        }
    }

    private static void prerequisitesAndSelections() throws Exception {
        EvolutionScreen screen = allocate(EvolutionScreen.class);
        Method client = EvolutionScreen.class.getDeclaredMethod("preMet", EvolutionComponent.class, EvolutionNode.class);
        client.setAccessible(true);
        for (EvolutionRoute route : EvolutionRegistry.INSTANCE.all().values()) {
            for (EvolutionNode node : route.nodes) {
                if (node.prereqs.size() < 2) continue;
                TestPlayer player = player(route);
                player.evolution.setRoute(route.routeId);
                player.evolution.setPoints(10);
                player.evolution.unlock(node.prereqs.get(0));
                check(!(boolean) client.invoke(screen, player.evolution, node), node.id + " client requires both prerequisites");
                check(!EvolutionManager.tryUnlock(player, node.id), node.id + " server rejects incomplete prerequisites");
                player.evolution.unlock(node.prereqs.get(1));
                check((boolean) client.invoke(screen, player.evolution, node), node.id + " client allows complete prerequisites");
                check(EvolutionManager.tryUnlock(player, node.id), node.id + " server accepts complete prerequisites");
            }
        }
        EvolutionRoute route = EvolutionRegistry.INSTANCE.getRoute("familiar_fox");
        TestPlayer player = player(route);
        EvolutionManager.tickPlayer(player);
        player.evolution.setPoints(3);
        LinkedHashSet<String> pending = new LinkedHashSet<>(List.of("mana_system", "spark", "rocket"));
        pending.remove("mana_system");
        pending.retainAll(EvolutionSelection.resolve(route, player.evolution, pending));
        check(pending.isEmpty(), "canceling prerequisite clears dependent staging");
        check(EvolutionManager.tryUnlockBatch(player, List.of("spark", "rocket", "mana_system")) == 3,
                "server orders re-added prerequisite before its dependents");
        check(player.evolution.getPoints() == 0, "batch charges exactly three points");
        TestPlayer invalid = player(route);
        EvolutionManager.tickPlayer(invalid);
        invalid.evolution.setPoints(1);
        check(EvolutionManager.tryUnlockBatch(invalid, List.of("unknown", "spark", "spark")) == 0
                && invalid.evolution.getPoints() == 1, "invalid and missing-prerequisite nodes never spend points");
        check(EvolutionManager.tryUnlockBatch(invalid, List.of("spark", "mana_system", "mana_system")) == 1,
                "budget and duplicates are checked during batch ordering");
    }

    private static TestPlayer legacy(int experience) throws Exception {
        EvolutionRoute route = EvolutionRegistry.INSTANCE.getRoute("familiar_fox");
        TestPlayer player = player(route);
        player.evolution.setRoute(route.routeId);
        player.evolution.markStarted();
        player.evolution.setExp(experience);
        player.evolution.setPoints(3);
        player.evolution.unlock("mana_system");
        for (int i = 0; i < 5; i++) player.evolution.markGrantedLevel(route.getLevelMilestones()[i]);
        return player;
    }

    private static void migration() throws Exception {
        for (int gained : List.of(0, 2)) {
            TestPlayer player = legacy(gained);
            EvolutionManager.tickPlayer(player);
            check(player.evolution.hasMigrated() && player.evolution.getEvoLevel() == 30, "legacy migration before UI with prior XP=" + gained);
            check(player.evolution.getPoints() == 3 && player.evolution.isUnlocked("mana_system"), "migration preserves spent and unspent points");
            player.totalExperience += 2;
            EvolutionManager.tickPlayer(player);
            check(player.evolution.getExp() == EvolutionComponent.levelToTotalExp(30) * 2 + 2, "new XP accumulates after legacy restoration");
            EvolutionManager.checkAndMigrate(player);
            check(player.evolution.getExp() == EvolutionComponent.levelToTotalExp(30) * 2 + 2, "reopening UI does not repeat migration");
        }
        TestPlayer skipped = legacy(2);
        NbtCompound saved = save(skipped);
        saved.putBoolean("migrated", true);
        saved.remove("expMigrationVersion");
        skipped.evolution.readFromNbt(saved);
        EvolutionManager.tickPlayer(skipped);
        check(skipped.evolution.getEvoLevel() == 30, "repairs old migrated=true flag that previously skipped restoration");
        TestPlayer genuine = legacy(EvolutionComponent.levelToTotalExp(46) * 2 + 7);
        for (int milestone : EvolutionRegistry.INSTANCE.getRoute("familiar_fox").getLevelMilestones()) genuine.evolution.markGrantedLevel(milestone);
        int exp = genuine.evolution.getExp();
        EvolutionManager.checkAndMigrate(genuine);
        check(genuine.evolution.getExp() == exp, "valid new-style experience is neither lowered nor promoted to level 50");
    }

    private static void routeValidation() throws Exception {
        EvolutionRoute fox = EvolutionRegistry.INSTANCE.getRoute("familiar_fox");
        TestPlayer player = player(fox);
        EvolutionManager.selectRoute(player, "nonexistent");
        check(!player.evolution.isOnSscaRoute(), "unknown route is rejected before state mutation");
        EvolutionManager.selectRoute(player, "axolotl");
        check(!player.evolution.isOnSscaRoute(), "route selection cannot transform an unrelated form");
        EvolutionManager.selectRoute(player, fox.routeId);
        check(player.evolution.getRoute().equals(fox.routeId) && player.evolution.isUnlocked(fox.getBaseNodeId()), "matching initial route can still be attached");
        player.evolution.setExp(10000);
        player.evolution.setPoints(7);
        player.evolution.unlock("fire_ring");
        player.form.nowForm = new NormalForm(EvolutionRegistry.INSTANCE.getRoute("axolotl").startForm);
        EvolutionManager.selectRoute(player, "axolotl");
        check(player.evolution.getRoute().equals(fox.routeId) && player.evolution.getExp() == 10000
                && player.evolution.getPoints() == 7, "forged route switch preserves original state rather than bypassing job cost");
        check(!EvolutionManager.tryUnlock(player, "mana_system") && !EvolutionManager.canUpgradeFoxEvolve(player), "mismatched stored route cannot unlock or evolve");
    }

    static TestPlayer trained(EvolutionRoute route) throws Exception {
        TestPlayer player = player(route);
        EvolutionManager.tickPlayer(player);
        player.evolution.setExp(EvolutionComponent.levelToTotalExp(50) * 2);
        EvolutionManager.tickPlayer(player);
        EvolutionManager.tryUnlockBatch(player, route.nodes.stream().filter(n -> !n.autoUnlock).map(n -> n.id).toList());
        ItemStack orbs = new ItemStack(SscAddon.PSIONIC_ORB, 2);
        orbs.getOrCreateNbt().putString("test_payment", "original");
        player.inv.setStack(0, orbs);
        return player;
    }

    private static void interruptedJobChanges() throws Exception {
        for (String sourceId : List.of("familiar_fox", "axolotl")) {
            EvolutionRoute source = EvolutionRegistry.INSTANCE.getRoute(sourceId);
            EvolutionRoute target = EvolutionRegistry.INSTANCE.getRoute(sourceId.equals("familiar_fox") ? "axolotl" : "familiar_fox");
            TestPlayer player = trained(source);
            EvolutionManager.startJobChange(player, target.startForm.toString());
            check(player.evolution.isJobChanging() && player.evolution.isJobAnimationActive(), sourceId + " starts job with all points already spent");
            check(player.inv.getStack(0).getCount() == 1, sourceId + " consumes one orb");
            String transaction = player.evolution.getJobChangeId();
            int expectedLevel = sourceId.equals("familiar_fox") ? 20 : 15;
            check(player.evolution.getJobChangeLevel() == expectedLevel, sourceId + " freezes normal three-milestone cost");
            EvolutionManager.startJobChange(player, target.startForm.toString());
            check(player.inv.getStack(0).getCount() == 1, sourceId + " duplicate job request consumes nothing");
            reload(player);
            check(player.evolution.isJobChanging() && !player.evolution.isJobAnimationActive(), "saved job survives while old callback is discarded");
            check(player.evolution.getJobChangePayment().getNbt().getString("test_payment").equals("original"), "reserved payment keeps NBT across reload");
            EvolutionManager.tickPlayer(player);
            check(player.evolution.isJobAnimationActive() && player.inv.getStack(0).getCount() == 1, "reload before midpoint resumes without charging twice");
            check(!EvolutionManager.tryUnlock(player, target.getBaseNodeId()), "job in progress rejects talent mutation");
            player.form.nowForm = new NormalForm(target.startForm);
            player.form.transformTargetForm = null;
            reload(player);
            EvolutionManager.tickPlayer(player);
            check(!player.evolution.isJobChanging() && player.evolution.getRoute().equals(target.routeId), "reload after midpoint completes the pending job");
            check(player.evolution.getEvoLevel() == expectedLevel && player.evolution.getPoints() == (sourceId.equals("familiar_fox") ? 4 : 3), "recovered job retains normal remaining level and points");
            check(player.evolution.isUnlocked(target.getBaseNodeId()) && !player.evolution.isUnlocked(source.getBaseNodeId()), "new tree has only its own base node");
            NbtCompound settled = save(player);
            complete(player, transaction);
            check(save(player).equals(settled), "late callback cannot settle a job twice");
            player.evolution.beginJobChange(sourceId, 0, ItemStack.EMPTY);
            complete(player, transaction);
            check(player.evolution.isJobChanging(), "old callback cannot settle a later job");
            player.evolution.clearJobChange();
            NbtCompound reused = save(player);
            reused.putString("jobChangeRoute", "stale");
            player.evolution.writeToNbt(reused);
            check(!reused.contains("jobChangeRoute"), "settled job is removed from reused NBT compounds");
        }
    }

    private static void rejectedAndInterruptedAnimations() throws Exception {
        EvolutionRoute source = EvolutionRegistry.INSTANCE.getRoute("familiar_fox");
        EvolutionRoute target = EvolutionRegistry.INSTANCE.getRoute("axolotl");
        RegPlayerForms.registerPlayerForm(new RejectedForm(target.startForm));
        TestPlayer denied = trained(source);
        EvolutionManager.startJobChange(denied, target.startForm.toString());
        check(!denied.evolution.isJobChanging() && denied.inv.getStack(0).getCount() == 2, "rejected target consumes no orb and leaves no stuck flag");
        ItemStack refund = denied.inv.getStack(0).split(1);
        denied.evolution.beginJobChange(target.routeId, 20, refund);
        reload(denied);
        EvolutionManager.tickPlayer(denied);
        check(!denied.evolution.isJobChanging() && denied.evolution.getEvoLevel() == 50, "failed job recovery preserves old progression");
        check(denied.inv.getStack(0).getCount() == 2 && denied.inv.getStack(0).getNbt().getString("test_payment").equals("original"), "failed recovery refunds the reserved orb with original NBT");
        RegPlayerForms.registerPlayerForm(new NormalForm(target.startForm));
        TestPlayer timeout = trained(source);
        timeout.evolution.beginJobChange(target.routeId, 20, ItemStack.EMPTY);
        timeout.evolution.startJobAnimation(1);
        timeout.form.nowForm = new NormalForm(target.startForm);
        EvolutionManager.tickPlayer(timeout);
        check(!timeout.evolution.isJobChanging() && timeout.evolution.getEvoLevel() == 20, "lost callback settles once animation timeout observes target form");
        TestPlayer interrupted = trained(source);
        EvolutionManager.startJobChange(interrupted, target.startForm.toString());
        interrupted.form.transformTargetForm = new NormalForm(new Identifier("audit", "other"));
        EvolutionManager.tickPlayer(interrupted);
        check(!interrupted.evolution.isJobChanging() && interrupted.inv.getStack(0).getCount() == 2, "replacement transformation cancels job and refunds payment");
    }

    private static void synchronization() throws Exception {
        TestPlayer player = player(EvolutionRegistry.INSTANCE.getRoute("axolotl"));
        SscAddonNetworking.registerEvolutionRouteSync();
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.invoker().onSyncDataPackContents(player, true);
        Map<String,String> joined = receiveRoutes(player);
        check(joined.equals(rawRoutes), "join lifecycle event sends full production routes");
        int sent = ((TestNetworkHandler)player.networkHandler).packets.size();
        SscAddonNetworking.sendEvolutionRoutes(player);
        SscAddonNetworking.sendEvolutionRoutes(player);
        check(((TestNetworkHandler)player.networkHandler).packets.size() == sent,
                "duplicate join paths send identical JSON only once per connection");
        Map<Identifier, Resource> resources = new LinkedHashMap<>();
        for (var entry : rawRoutes.entrySet()) {
            String content = entry.getKey().equals("axolotl") ? entry.getValue().replace("\"cost\": 1", "\"cost\": 2") : entry.getValue();
            resources.put(new Identifier("my_addon", "ssca_evolution/routes/" + entry.getKey() + ".json"),
                    new Resource(null, () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
        }
        ResourceManager manager = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class[]{ResourceManager.class},
                (proxy, method, args) -> method.getName().equals("findResources") ? resources : null);
        EvolutionRegistry.INSTANCE.reload(manager);
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.invoker().onSyncDataPackContents(player, false);
        Map<String,String> reloaded = receiveRoutes(player);
        check(!reloaded.get("axolotl").equals(joined.get("axolotl")), "reload lifecycle sends changed route to already connected player");
        EvolutionRegistry.CLIENT.applyClientSync(reloaded);
        check(EvolutionRegistry.CLIENT.getNode("axolotl", "aquatic_adapt").cost == 2, "client rebuild uses reloaded cost without reconnecting");
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.invoker().onSyncDataPackContents(player, false);
        check(receiveRoutes(player).equals(reloaded), "explicit reload repairs unchanged JSON as well");
        JsonSnapshotSync.send(player, SscAddonNetworking.PACKET_EVO_ROUTES_SYNC,
                EvolutionRegistry.INSTANCE.getRawJson(), true);
        check(receiveRoutes(player).equals(reloaded), "forced repair bypasses the content baseline");
        TestNetworkHandler oldConnection = (TestNetworkHandler)player.networkHandler;
        int oldPacketCount = oldConnection.packets.size();
        player.networkHandler = network();
        SscAddonNetworking.sendEvolutionRoutes(player);
        check(receiveRoutes(player).equals(reloaded) && oldConnection.packets.size() == oldPacketCount,
                "a new connection for the same player receives a fresh JSON baseline");
        loadServerRoutes(rawRoutes);
    }

    private static void entryAndPaymentValidation() throws Exception {
        EvolutionRoute source = EvolutionRegistry.INSTANCE.getRoute("familiar_fox");
        EvolutionRoute target = EvolutionRegistry.INSTANCE.getRoute("axolotl");
        TestPlayer initial = player(source);
        initial.form.nowForm = RegPlayerForms.ORIGINAL_BEFORE_ENABLE;
        EvolutionManager.startSscaRoute(initial, source.startForm.toString());
        check(initial.evolution.getRoute().equals(source.routeId) && initial.form.transformTargetForm.getFormID().equals(source.startForm), "initial book still starts the requested evolution route");
        EvolutionManager.startSscaRoute(initial, target.startForm.toString());
        check(initial.evolution.getRoute().equals(source.routeId), "repeated initial packet cannot overwrite a route during the opening animation");
        TestPlayer noOrb = trained(source);
        noOrb.inv.setStack(0, ItemStack.EMPTY);
        EvolutionManager.startJobChange(noOrb, target.startForm.toString());
        check(!noOrb.evolution.isJobChanging(), "survival job requires an orb");
        TestPlayer tooFew = player(source);
        EvolutionManager.tickPlayer(tooFew);
        tooFew.inv.setStack(0, new ItemStack(SscAddon.PSIONIC_ORB));
        EvolutionManager.startJobChange(tooFew, target.startForm.toString());
        check(!tooFew.evolution.isJobChanging() && tooFew.inv.getStack(0).getCount() == 1, "insufficient total milestones never consumes an orb");
        for (int slot : List.of(5, 40)) {
            TestPlayer otherSlot = trained(source);
            otherSlot.inv.setStack(slot, otherSlot.inv.removeStack(0));
            EvolutionManager.startJobChange(otherSlot, target.startForm.toString());
            check(otherSlot.evolution.isJobChanging() && otherSlot.inv.getStack(slot).getCount() == 1, "job accepts exactly one orb from inventory slot " + slot);
        }
        TestPlayer creative = trained(source);
        creative.abilities.creativeMode = true;
        creative.inv.setStack(0, ItemStack.EMPTY);
        EvolutionManager.startJobChange(creative, target.startForm.toString());
        check(creative.evolution.isJobChanging() && creative.evolution.getJobChangePayment().isEmpty(), "creative mode reserves no payment");
    }

    private static void failedReloadPreservesProgress() throws Exception {
        EvolutionRegistry registry = EvolutionRegistry.INSTANCE;
        EvolutionRoute route = registry.getRoute("familiar_fox");
        TestPlayer player = player(route);
        EvolutionManager.tickPlayer(player);
        player.evolution.setExp(4000);
        EvolutionManager.tickPlayer(player); // Settle legitimate milestone grants before comparing NBT.
        player.evolution.setPoints(3);
        player.evolution.unlock("mana_system");
        NbtCompound before = save(player);
        var original = com.google.gson.JsonParser.parseString(rawRoutes.get("familiar_fox")).getAsJsonObject();
        List<String> invalid = new ArrayList<>();
        invalid.add("{");
        var missing = original.deepCopy();
        missing.getAsJsonArray("nodes").get(0).getAsJsonObject().remove("id");
        invalid.add(missing.toString());
        var duplicate = original.deepCopy();
        duplicate.getAsJsonArray("nodes").add(duplicate.getAsJsonArray("nodes").get(0).deepCopy());
        invalid.add(duplicate.toString());
        var prerequisite = original.deepCopy();
        prerequisite.getAsJsonArray("nodes").get(1).getAsJsonObject().getAsJsonArray("prereqs").add("missing_node");
        invalid.add(prerequisite.toString());
        var cycle = original.deepCopy();
        cycle.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("prereqs").add("mana_system");
        invalid.add(cycle.toString());
        var missingStart = original.deepCopy();
        missingStart.remove("start_form");
        invalid.add(missingStart.toString());
        for (String json : invalid) {
            var resources = Map.of(new Identifier("my_addon", "ssca_evolution/routes/familiar_fox.json"),
                    new Resource(null, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
            ResourceManager manager = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class[]{ResourceManager.class},
                    (proxy, method, args) -> method.getName().equals("findResources") ? resources : null);
            boolean rejected = false;
            try { registry.reload(manager); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected && registry.getRawJson().equals(rawRoutes), "failed reload retains the whole previous route registry");
            EvolutionManager.tickPlayer(player);
            check(before.equals(save(player)), "failed reload preserves experience, points, nodes and migration NBT");
        }
        EvolutionRegistry.CLIENT.applyClientSync(rawRoutes);
        EvolutionRegistry.CLIENT.applyClientSync(Map.of("familiar_fox", invalid.get(1)));
        check(EvolutionRegistry.CLIENT.getRawJson().equals(rawRoutes), "bad client snapshot preserves complete previous mirror");
        loadServerRoutes(Map.of());
        EvolutionManager.tickPlayer(player);
        check(before.equals(save(player)), "temporarily missing route does not reset saved progress");
        check(!EvolutionManager.canUpgradeFoxEvolve(player), "missing route cannot bypass evolution requirements");
        loadServerRoutes(rawRoutes);
        EvolutionManager.tickPlayer(player);
        check(before.equals(save(player)), "restoring route resumes existing progression without reset");
        var changedStart = original.deepCopy();
        changedStart.addProperty("start_form", "audit:changed_start_form");
        var changed = new LinkedHashMap<>(rawRoutes);
        changed.put("familiar_fox", changedStart.toString());
        loadServerRoutes(changed);
        EvolutionManager.tickPlayer(player);
        check(before.equals(save(player)), "editing start_form cannot impersonate a real form departure");
        loadServerRoutes(Map.of());
        player.form.nowForm = new NormalForm(new Identifier("audit", "different_form"));
        EvolutionManager.tickPlayer(player);
        check(!player.evolution.isOnSscaRoute(), "actual form departure still resets progress while config is missing");
        loadServerRoutes(rawRoutes);
    }

    private static void loadServerRoutes(Map<String, String> raw) {
        Map<Identifier, Resource> resources = new LinkedHashMap<>();
        raw.forEach((id, json) -> resources.put(new Identifier("my_addon", "ssca_evolution/routes/" + id + ".json"),
                new Resource(null, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))));
        ResourceManager manager = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class[]{ResourceManager.class},
                (proxy, method, args) -> method.getName().equals("findResources") ? resources : null);
        EvolutionRegistry.INSTANCE.reload(manager);
    }

    private static void auditBoundaryRegressions() throws Exception {
        List<String> failures = new ArrayList<>();
        EvolutionComponent comp = new EvolutionComponent();
        comp.setExp(Integer.MAX_VALUE);
        comp.addExp(1);
        auditCheck(failures, comp.getExp() == Integer.MAX_VALUE && comp.getEvoLevel() == 50,
                "experience overflow cannot wipe earned progression");
        comp.setPoints(Integer.MAX_VALUE);
        comp.addPoints(1);
        auditCheck(failures, comp.getPoints() == Integer.MAX_VALUE, "point overflow cannot erase the balance");
        auditCheck(failures, !comp.spendPoints(-1), "negative purchases are rejected at the component boundary");
        NbtCompound corrupt = new NbtCompound();
        corrupt.putInt("exp", -50);
        corrupt.putInt("points", -3);
        corrupt.putIntArray("grantedLevels", new int[]{-1, 0, 5, 51});
        comp.readFromNbt(corrupt);
        auditCheck(failures, comp.getExp() == 0 && comp.getPoints() == 0 && comp.getGrantedLevelCount() == 1,
                "invalid saved counters cannot create debt or fake milestone history");
        EvolutionRoute source = EvolutionRegistry.INSTANCE.getRoute("familiar_fox");
        EvolutionRoute target = EvolutionRegistry.INSTANCE.getRoute("axolotl");
        TestPlayer changing = trained(source);
        EvolutionManager.startJobChange(changing, target.startForm.toString());
        NbtCompound reserved = save(changing);
        EvolutionManager.reset(changing);
        auditCheck(failures, reserved.equals(save(changing)) && changing.inv.getStack(0).getCount() == 1,
                "reset during an active transformation preserves its transaction and reserved payment");
        TestPlayer initial = player(source);
        initial.form.nowForm = RegPlayerForms.ORIGINAL_BEFORE_ENABLE;
        EvolutionManager.startSscaRoute(initial, source.startForm.toString());
        initial.form.transformTargetForm = null;
        TransformManager.onServerInit();
        EvolutionManager.tickPlayer(initial);
        auditCheck(failures, !initial.evolution.isOnSscaRoute(), "interrupted initial entry permits choosing a form again");
        for (String failure : failures) System.out.println("AUDIT REGRESSION: " + failure);
        check(failures.isEmpty(), "evolution boundary regressions: " + failures);
        TestPlayer legacy = trained(source);
        NbtCompound old = save(legacy);
        old.remove("started");
        old.remove("routeStartForm");
        legacy.evolution.readFromNbt(old);
        loadServerRoutes(Map.of());
        EvolutionManager.tickPlayer(legacy);
        check(legacy.evolution.getExp() == old.getInt("exp") && legacy.evolution.getRoute().equals(source.routeId),
                "missing route and old entry fields cannot be mistaken for an interrupted initial selection");
        loadServerRoutes(rawRoutes);
    }

    private static void auditCheck(List<String> failures, boolean passed, String description) {
        checks++;
        if (passed) System.out.println("PASS " + description);
        else failures.add(description);
    }

    private static void auditRegistryAndRequests() throws Exception {
        EvolutionRegistry.CLIENT.applyClientSync(rawRoutes);
        var serverRoute = EvolutionRegistry.INSTANCE.getRoute("axolotl");
        check(serverRoute != EvolutionRegistry.CLIENT.getRoute("axolotl"), "integrated client and server own separate route objects");
        var stale = new LinkedHashMap<>(rawRoutes);
        stale.put("axolotl", stale.get("axolotl").replace("\"cost\": 1", "\"cost\": 2"));
        EvolutionRegistry.CLIENT.applyClientSync(stale);
        check(EvolutionRegistry.INSTANCE.getNode("axolotl", "aquatic_adapt").cost == 1,
                "a delayed client snapshot cannot overwrite server purchase costs");
        EvolutionRegistry.clearClient();
        check(EvolutionRegistry.CLIENT.all().isEmpty() && EvolutionRegistry.INSTANCE.getRoute("axolotl") == serverRoute,
                "disconnect clears only the client mirror");
        var duplicate = new LinkedHashMap<>(rawRoutes);
        duplicate.put("duplicate", rawRoutes.get("axolotl"));
        boolean rejected = false;
        try { loadServerRoutes(duplicate); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected && EvolutionRegistry.INSTANCE.getRoute("axolotl") == serverRoute,
                "ambiguous start forms reject the entire reload");
        EvolutionRegistry.CLIENT.applyClientSync(rawRoutes);
        EvolutionRegistry.CLIENT.applyClientSync(duplicate);
        check(EvolutionRegistry.CLIENT.getRawJson().equals(rawRoutes), "ambiguous client snapshots retain the previous mirror");
        try { serverRoute.nodes.clear(); throw new AssertionError("mutable node list"); }
        catch (UnsupportedOperationException expected) { checks++; }
        try { serverRoute.getNode("water_spurt").prereqs.clear(); throw new AssertionError("mutable dependencies"); }
        catch (UnsupportedOperationException expected) { checks++; }
        int[] milestones = serverRoute.getLevelMilestones();
        milestones[0] = 49;
        check(serverRoute.getLevelMilestones()[0] == 5, "milestone arrays cannot mutate the validated snapshot");
        for (String field : List.of("level", "auto", "base", "fraction", "overflow", "layout")) {
            var json = com.google.gson.JsonParser.parseString(rawRoutes.get("axolotl")).getAsJsonObject();
            switch (field) {
                case "level" -> json.getAsJsonArray("level_milestones").add(51);
                case "auto" -> json.addProperty("auto_branch_level", 51);
                case "base" -> json.addProperty("base_node", "water_spear");
                case "fraction" -> json.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("cost", 0.5);
                case "overflow" -> json.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("cost", 4294967296L);
                case "layout" -> json.getAsJsonArray("nodes").get(1).getAsJsonObject().addProperty("row", Integer.MAX_VALUE);
            }
            rejected = false;
            try { EvolutionRoute.fromJson("axolotl", json); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid route " + field + " is rejected before publication");
        }
        var automatic = new LinkedHashMap<>(rawRoutes);
        automatic.put("axolotl", rawRoutes.get("axolotl").replace("\"cost\": 1", "\"cost\": 0, \"auto_unlock\": true"));
        loadServerRoutes(automatic);
        TestPlayer free = player(EvolutionRegistry.INSTANCE.getRoute("axolotl"));
        EvolutionManager.tickPlayer(free);
        check(free.evolution.isUnlocked("become_water") && !free.evolution.isUnlocked("branch_stone")
                && free.evolution.getPoints() == 0, "automatic main-line chains unlock without bypassing branch levels");
        loadServerRoutes(rawRoutes);

        var budget = new net.jackcooper.shapeShifterCurseAddon.network.EvolutionRequestBudget();
        var open = net.jackcooper.shapeShifterCurseAddon.network.EvolutionRequestBudget.Action.OPEN;
        var buy = net.jackcooper.shapeShifterCurseAddon.network.EvolutionRequestBudget.Action.PURCHASE;
        Object connection = new Object();
        check(budget.accept(connection, open, 10) && budget.accept(connection, buy, 10), "opening cannot consume the purchase budget");
        check(!budget.accept(connection, buy, 10) && !budget.accept(connection, buy, 11)
                && budget.accept(connection, buy, 12), "rejected repeats do not extend the purchase lockout");
        check(budget.accept(new Object(), buy, 12), "a new connection has its own request budget");
        TestPlayer buyer = player(EvolutionRegistry.INSTANCE.getRoute("axolotl"));
        EvolutionManager.tickPlayer(buyer);
        buyer.evolution.setPoints(2);
        SscAddonNetworking.handleEvolutionPurchase(buyer, buyer.networkHandler, 10, 7, "familiar_fox", "mana_system", 1);
        check(receivePurchase(buyer, 7) == 2 && buyer.evolution.getPoints() == 2, "stale-route request rejects with a correlated result and no charge");
        SscAddonNetworking.handleEvolutionPurchase(buyer, buyer.networkHandler, 12, 8, "axolotl", "aquatic_adapt", 1);
        check(receivePurchase(buyer, 8) == 0 && buyer.evolution.getPoints() == 1 && buyer.evolution.isUnlocked("aquatic_adapt"), "successful result follows the authoritative purchase");
        SscAddonNetworking.handleEvolutionPurchase(buyer, buyer.networkHandler, 12, 9, "axolotl", "water_propel", 1);
        check(receivePurchase(buyer, 9) == 1 && buyer.evolution.getPoints() == 1, "rate-limited request returns immediately without charging");
        SscAddonNetworking.handleEvolutionPurchase(buyer, buyer.networkHandler, 14, 10, "axolotl", "water_spear", 1);
        check(receivePurchase(buyer, 10) == 3 && buyer.evolution.getPoints() == 1, "missing prerequisites return a denial without charging");
        SscAddonNetworking.handleEvolutionPurchase(buyer, network(), 16, 11, "axolotl", "water_propel", 1);
        check(!buyer.evolution.isUnlocked("water_propel"), "stale connections cannot mutate progression");
        SscAddonNetworking.handleEvolutionPurchase(buyer, buyer.networkHandler, 18, 12, "axolotl", "water_propel", 0);
        check(receivePurchase(buyer, 12) == 2 && buyer.evolution.getPoints() == 1,
                "a stale displayed price never spends a different cost");
    }

    private static int receivePurchase(TestPlayer player, int request) {
        var packets = ((TestNetworkHandler) player.networkHandler).packets;
        var packet = (CustomPayloadS2CPacket) packets.remove(packets.size() - 1);
        check(packet.getChannel().equals(SscAddonNetworking.PACKET_EVO_RESULT), "purchase uses its result channel");
        var data = packet.getData();
        check(data.readVarInt() == request, "purchase result retains request identity");
        int status = data.readUnsignedByte();
        check(data.readableBytes() == 0, "purchase result has no trailing bytes");
        return status;
    }

    private static void jobReloadBoundaries() throws Exception {
        var source = EvolutionRegistry.INSTANCE.getRoute("familiar_fox");
        var target = EvolutionRegistry.INSTANCE.getRoute("axolotl");
        TestPlayer before = trained(source);
        EvolutionManager.startJobChange(before, target.startForm.toString());
        check(before.evolution.getJobChangeForm().equals(target.startForm.toString()), "new job pins the accepted form");
        var withoutTarget = Map.of("familiar_fox", rawRoutes.get("familiar_fox"));
        loadServerRoutes(withoutTarget);
        EvolutionManager.tickPlayer(before);
        check(!before.evolution.isJobChanging() && before.inv.getStack(0).getCount() == 2
                && before.form.transformTargetForm == null
                && !net.jackcooper.shapeShifterCurseAddon.mixin.TransformManagerAccessor.ssca$playerData().containsKey(before.id),
                "reload before midpoint cancels the owned SSC animation before refunding");
        check(before.evolution.getEvoLevel() == 50, "pre-midpoint cancellation preserves earned progression");
        loadServerRoutes(rawRoutes);
        TestPlayer after = trained(source);
        EvolutionManager.startJobChange(after, target.startForm.toString());
        after.form.nowForm = new NormalForm(target.startForm);
        after.form.transformTargetForm = null;
        loadServerRoutes(withoutTarget);
        EvolutionManager.tickPlayer(after);
        check(after.evolution.isJobChanging() && !after.evolution.isJobAnimationActive()
                && after.evolution.getEvoLevel() == 50 && after.inv.getStack(0).getCount() == 1,
                "reload after midpoint preserves progression and payment until the route returns");
        reload(after);
        EvolutionManager.tickPlayer(after);
        check(after.evolution.isJobChanging(), "paused transaction survives restart without losing payment");
        loadServerRoutes(rawRoutes);
        EvolutionManager.tickPlayer(after);
        check(!after.evolution.isJobChanging() && after.evolution.getRoute().equals("axolotl")
                && after.evolution.getEvoLevel() == 20, "restored route settles the paused job exactly once");
        TestPlayer redirected = trained(source);
        EvolutionManager.startJobChange(redirected, target.startForm.toString());
        reload(redirected);
        var changed = new LinkedHashMap<>(rawRoutes);
        var json = com.google.gson.JsonParser.parseString(changed.get("axolotl")).getAsJsonObject();
        json.addProperty("start_form", "audit:redirected");
        changed.put("axolotl", json.toString());
        loadServerRoutes(changed);
        EvolutionManager.tickPlayer(redirected);
        check(!redirected.evolution.isJobChanging() && redirected.inv.getStack(0).getCount() == 2
                && redirected.form.transformTargetForm == null, "resumed jobs cannot silently change their accepted target");
        loadServerRoutes(rawRoutes);
        TestPlayer reset = trained(source);
        reset.evolution.beginJobChange(target.routeId, target.startForm.toString(), 20, reset.inv.getStack(0).split(1));
        check(EvolutionManager.reset(reset) && !reset.evolution.isOnSscaRoute() && reset.inv.getStack(0).getCount() == 2
                && reset.inv.getStack(0).getNbt().getString("test_payment").equals("original"),
                "explicit reset of a paused transaction returns the exact reserved orb");
        TestPlayer stone = trained(source);
        EvolutionManager.tickPlayer(stone);
        check(EvolutionManager.canUpgradeFoxEvolve(stone), "stone regression reaches its transform call");
        Identifier sp = new Identifier("my_addon", "familiar_fox_mancianima");
        var originalSp = RegPlayerForms.getPlayerForm(sp);
        RegPlayerForms.registerPlayerForm(new RejectedForm(sp));
        var stack = new ItemStack(net.minecraft.item.Items.DIAMOND, 2);
        new net.jackcooper.shapeShifterCurseAddon.EvolutionStoneItem(new net.minecraft.item.Item.Settings()).finishUsing(stack, world, stone);
        check(stack.getCount() == 2 && stone.form.transformTargetForm == null, "rejected evolution never consumes a stone");
        RegPlayerForms.registerPlayerForm(originalSp == null ? new NormalForm(sp) : originalSp);
    }

    private static Map<String,String> receiveRoutes(TestPlayer player) {
        var packets = ((TestNetworkHandler)player.networkHandler).packets;
        CustomPayloadS2CPacket packet = (CustomPayloadS2CPacket) packets.remove(packets.size() - 1);
        check(packet.getChannel().equals(SscAddonNetworking.PACKET_EVO_ROUTES_SYNC), "packet uses evolution route channel");
        var data = packet.getData();
        Map<String,String> routes = new LinkedHashMap<>();
        int count = data.readInt();
        for (int i = 0; i < count; i++) routes.put(data.readString(256), data.readString(2000000));
        check(data.readableBytes() == 0, "route packet decodes without trailing bytes");
        data.release();
        return routes;
    }

    static void complete(TestPlayer player, String transaction) throws Exception {
        Method method = EvolutionManager.class.getDeclaredMethod("completeJobChange", ServerPlayerEntity.class, String.class);
        method.setAccessible(true);
        method.invoke(null, player, transaction);
    }

    static NbtCompound save(TestPlayer player) {
        NbtCompound nbt = new NbtCompound();
        player.evolution.writeToNbt(nbt);
        return nbt;
    }

    private static void reload(TestPlayer player) {
        NbtCompound nbt = save(player);
        player.evolution = new EvolutionComponent();
        player.evolution.readFromNbt(nbt);
        container(player);
    }

    static TestPlayer player(EvolutionRoute route) throws Exception {
        TestPlayer player = allocate(TestPlayer.class);
        player.id = UUID.randomUUID();
        player.inv = new PlayerInventory(player);
        player.abilities = new PlayerAbilities();
        player.evolution = new EvolutionComponent();
        player.form = allocate(PlayerFormComponent.class);
        player.form.nowForm = new NormalForm(route.startForm);
        player.age = 20;
        var pos = Entity.class.getDeclaredField("pos");
        pos.setAccessible(true);
        pos.set(player, Vec3d.ZERO);
        player.networkHandler = network();
        container(player);
        return player;
    }

    private static TestNetworkHandler network() throws Exception {
        TestNetworkHandler network = allocate(TestNetworkHandler.class);
        network.packets = new ArrayList<>();
        network.addon = allocate(ServerPlayNetworkAddon.class);
        // Unsafe deliberately bypasses Fabric's constructor hook. Supply its negotiated channels
        // explicitly rather than weakening production canSend checks to accommodate a fake handler.
        var channels = AbstractChanneledNetworkAddon.class.getDeclaredField("sendableChannelsView");
        channels.setAccessible(true);
        channels.set(network.addon, Set.of(SscAddonNetworking.PACKET_EVO_ROUTES_SYNC, SscAddonNetworking.PACKET_EVO_RESULT,
                SscAddonNetworking.PACKET_SPELL_CONFIG_SYNC));
        return network;
    }

    private static void container(TestPlayer player) {
        player.components = ComponentContainer.Factory.builder(TestPlayer.class)
                .component(RegEvolutionComponent.EVOLUTION, p -> p.evolution)
                .component(RegPlayerFormComponent.PLAYER_FORM, p -> p.form).build().createContainer(player);
    }

    @SuppressWarnings("unchecked") private static <T> T allocate(Class<T> type) throws Exception { return (T) unsafe.allocateInstance(type); }
    private static void check(boolean value, String description) {
        checks++;
        if (!value) throw new AssertionError(description);
        System.out.println("PASS " + description);
    }

    public static final class RejectedForm extends NormalForm implements IFormWithCondition {
        RejectedForm(Identifier id) { super(id); }
        @Override public boolean checkCanUse(PlayerEntity player) { return false; }
    }

    public static final class TestPlayer extends ServerPlayerEntity implements ComponentProvider {
        UUID id;
        PlayerInventory inv;
        PlayerAbilities abilities;
        EvolutionComponent evolution;
        PlayerFormComponent form;
        ComponentContainer components;
        int syncLookups;
        private TestPlayer() { super(null, null, new GameProfile(UUID.randomUUID(), "evolution-system")); }
        @Override public World getWorld() { return world; }
        @Override public ServerWorld getServerWorld() { return world; }
        @Override public UUID getUuid() { return id; }
        @Override public String getUuidAsString() { return id.toString(); }
        @Override public PlayerInventory getInventory() { return inv; }
        @Override public PlayerAbilities getAbilities() { return abilities; }
        @Override public ItemStack getMainHandStack() { return inv.getMainHandStack(); }
        @Override public ItemStack getOffHandStack() { return inv.getStack(40); }
        @Override public ComponentContainer getComponentContainer() { return components; }
        @Override public Iterable<ServerPlayerEntity> getRecipientsForComponentSync() { syncLookups++; return List.of(); }
        @Override public void sendMessage(Text message, boolean overlay) { }
        @Override public void playSound(SoundEvent sound, SoundCategory category, float volume, float pitch) { }
        @Override public boolean addStatusEffect(StatusEffectInstance effect, Entity source) { return true; }
        @Override public boolean removeStatusEffect(net.minecraft.entity.effect.StatusEffect effect) { return true; }
    }

    public static final class TestWorld extends ServerWorld {
        private TestWorld() { super(null, null, null, null, World.OVERWORLD, null, null, false, 0, List.of(), false, null); }
        @Override public List<ServerPlayerEntity> getPlayers() { return List.of(); }
        @Override public void playSound(PlayerEntity player, double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch) { }
    }

    public static final class TestNetworkHandler extends ServerPlayNetworkHandler implements NetworkHandlerExtensions {
        List<Packet<?>> packets;
        ServerPlayNetworkAddon addon;
        private TestNetworkHandler() { super(null, null, null); }
        @Override public ServerPlayNetworkAddon getAddon() { return addon; }
        @Override public void sendPacket(Packet<?> packet) { packets.add(packet); }
    }
}
