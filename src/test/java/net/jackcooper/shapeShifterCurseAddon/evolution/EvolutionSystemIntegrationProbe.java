package net.jackcooper.shapeShifterCurseAddon.evolution;

import com.mojang.authlib.GameProfile;
import dev.onyxstudios.cca.api.v3.component.ComponentContainer;
import dev.onyxstudios.cca.api.v3.component.ComponentProvider;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.client.evolution.EvolutionScreen;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
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
        EvolutionRegistry.INSTANCE.applyClientSync(rawRoutes);
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
        prerequisitesAndSelections();
        migration();
        routeValidation();
        interruptedJobChanges();
        rejectedAndInterruptedAnimations();
        entryAndPaymentValidation();
        synchronization();
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
            check(player.evolution.getPoints() == route.levelMilestones.length, route.routeId + " awards exact budget");
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

    private static void prerequisitesAndSelections() throws Exception {
        EvolutionScreen screen = allocate(EvolutionScreen.class);
        var pendingField = EvolutionScreen.class.getDeclaredField("pending");
        pendingField.setAccessible(true);
        pendingField.set(screen, new LinkedHashSet<String>());
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
        for (int i = 0; i < 5; i++) player.evolution.markGrantedLevel(route.levelMilestones[i]);
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
        for (int milestone : EvolutionRegistry.INSTANCE.getRoute("familiar_fox").levelMilestones) genuine.evolution.markGrantedLevel(milestone);
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

    private static TestPlayer trained(EvolutionRoute route) throws Exception {
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
        EvolutionRegistry.INSTANCE.applyClientSync(reloaded);
        check(EvolutionRegistry.INSTANCE.getNode("axolotl", "aquatic_adapt").cost == 2, "client rebuild uses reloaded cost without reconnecting");
        EvolutionRegistry.INSTANCE.applyClientSync(rawRoutes);
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

    private static void complete(TestPlayer player, String transaction) throws Exception {
        Method method = EvolutionManager.class.getDeclaredMethod("completeJobChange", ServerPlayerEntity.class, String.class);
        method.setAccessible(true);
        method.invoke(null, player, transaction);
    }

    private static NbtCompound save(TestPlayer player) {
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

    private static TestPlayer player(EvolutionRoute route) throws Exception {
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
        TestNetworkHandler network = allocate(TestNetworkHandler.class);
        network.packets = new ArrayList<>();
        player.networkHandler = network;
        container(player);
        return player;
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
        @Override public Iterable<ServerPlayerEntity> getRecipientsForComponentSync() { return List.of(); }
        @Override public void sendMessage(Text message, boolean overlay) { }
        @Override public void playSound(SoundEvent sound, SoundCategory category, float volume, float pitch) { }
        @Override public boolean addStatusEffect(StatusEffectInstance effect, Entity source) { return true; }
    }

    public static final class TestWorld extends ServerWorld {
        private TestWorld() { super(null, null, null, null, World.OVERWORLD, null, null, false, 0, List.of(), false, null); }
        @Override public void playSound(PlayerEntity player, double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch) { }
    }

    public static final class TestNetworkHandler extends ServerPlayNetworkHandler {
        List<Packet<?>> packets;
        private TestNetworkHandler() { super(null, null, null); }
        @Override public void sendPacket(Packet<?> packet) { packets.add(packet); }
    }
}
