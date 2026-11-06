package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.block.WebMembraneOwners;
import net.jackcooper.shapeShifterCurseAddon.entity.SpellMeteorEntity;
import net.jackcooper.shapeShifterCurseAddon.entity.SpellMoonlightArrowEntity;
import net.jackcooper.shapeShifterCurseAddon.screen.PotionStorageBoxScreenHandler;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.ssca.neomagic.NeoRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Real server-world regression; all disk writes use the generated GameTest save. */
public final class CodeAuditEntityWorldProbe {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }

    public static void start(TestContext context) {
        context.waitAndRun(70, () -> {
            try { run(context); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
    }

    private static void run(TestContext context) throws Exception {
        var player = context.createMockCreativeServerPlayerInWorld();
        player.refreshPositionAndAngles(context.getAbsolutePos(new BlockPos(3, 3, 3)), 0, 0);
        aoeExperience(player);
        projectilePersistence(player);
        soloExperience(player);
        inventoryMerge(player);
        worldOwners(player);
        pocketRetention(context, player);
        terrainJournal(context);
        GameplayAbsorptionWorldProbe.run(context.getWorld());
        System.out.println("CODE_AUDIT_ENTITY_WORLD_PASS: " + checks
                + " checks; actual AOE experience, projectile NBT/phase, inventory merge, world ownership and durable terrain recovery.");
        context.complete();
    }

    private static LivingEntity mob(ServerPlayerEntity player, double offset) {
        var mob = EntityType.PIG.create(player.getServerWorld());
        mob.setAiDisabled(true);
        mob.setNoGravity(true);
        mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);
        mob.setHealth(200);
        mob.refreshPositionAndAngles(player.getX() + offset, player.getY(), player.getZ(), 0, 0);
        player.getServerWorld().spawnEntity(mob);
        return mob;
    }

    private static void aoeExperience(ServerPlayerEntity player) {
        for (String id : new String[]{"frost_nova", "flame_nova", "void_devour"}) {
            Spell spell = SpellRegistry.get(id);
            LivingEntity a = mob(player, -.5), b = mob(player, .5);
            var order = player.getServerWorld().getEntitiesByClass(LivingEntity.class,
                    player.getBoundingBox().expand(4), e -> e != player && e.isAlive());
            check(order.size() == 2, "AOE fixture contains exactly two ordered targets");
            LivingEntity first = order.get(0), second = order.get(1);
            String firstTag = WhitelistUtils.WHITELIST_MOB_TAG_PREFIX + first.getUuid();
            String secondTag = WhitelistUtils.WHITELIST_MOB_TAG_PREFIX + second.getUuid();
            player.addCommandTag(firstTag);
            int before = NeoRuntime.data(player).spellbookMasteryExpTen;
            spell.ssc_addon$setPendingExp(80);
            spell.castAtTarget(player, 2, false, 1, ItemStack.EMPTY, player.getPos());
            check(first.getHealth() == 200 && second.getHealth() < 200, id + " protects the first target and damages the next");
            check(NeoRuntime.data(player).spellbookMasteryExpTen == before + SpellbookData.boostedExpTen(80),
                    id + " keeps the complete pending budget after a protected first target");
            player.addCommandTag(secondTag);
            before = NeoRuntime.data(player).spellbookMasteryExpTen;
            spell.ssc_addon$setPendingExp(80);
            spell.castAtTarget(player, 2, false, 1, ItemStack.EMPTY, player.getPos());
            check(NeoRuntime.data(player).spellbookMasteryExpTen == before, id + " awards no hit experience for all-protected targets");
            player.removeScoreboardTag(firstTag);
            player.removeScoreboardTag(secondTag);
            first.timeUntilRegen = second.timeUntilRegen = 0;
            before = NeoRuntime.data(player).spellbookMasteryExpTen;
            spell.ssc_addon$setPendingExp(80);
            spell.castAtTarget(player, 2, false, 1, ItemStack.EMPTY, player.getPos());
            check(NeoRuntime.data(player).spellbookMasteryExpTen == before + SpellbookData.boostedExpTen(80),
                    id + " awards the budget exactly once across two successful hits");
            spell.ssc_addon$clearPendingExp();
            a.discard(); b.discard();
        }
    }

    private static void projectilePersistence(ServerPlayerEntity player) throws Exception {
        Spell arrowSpell = SpellRegistry.get("moonlight_arrow");
        arrowSpell.ssc_addon$setPendingExp(80);
        arrowSpell.cast(player, 2, false, 1);
        var arrows = player.getServerWorld().getEntitiesByClass(SpellMoonlightArrowEntity.class,
                player.getBoundingBox().expand(4), e -> e.getOwner() == player);
        check(arrows.size() == 1 && arrows.get(0).getExpBountyTen() == 80, "Actual moonlight cast transfers its pending experience");
        var restoredArrow = new SpellMoonlightArrowEntity(player.getWorld(), player);
        restoredArrow.readNbt(arrows.get(0).writeNbt(new NbtCompound()));
        check(restoredArrow.getExpBountyTen() == 80, "Moonlight experience survives native entity NBT roundtrip");
        arrows.get(0).discard(); restoredArrow.discard();

        var meteor = new SpellMeteorEntity(player.getWorld(), player);
        meteor.setImpactTarget(player.getX() + 15, player.getY(), player.getZ());
        var ticks = SpellMeteorEntity.class.getDeclaredField("ticksAlive"); ticks.setAccessible(true);
        var falling = SpellMeteorEntity.class.getDeclaredField("falling"); falling.setAccessible(true);
        ticks.setInt(meteor, 14); falling.setBoolean(meteor, true);
        meteor.setPosition(meteor.getPos().add(0, -10, 0));
        var restoredMeteor = new SpellMeteorEntity(player.getWorld(), player);
        restoredMeteor.readNbt(meteor.writeNbt(new NbtCompound()));
        check(ticks.getInt(restoredMeteor) == 14, "Falling meteor restores its phase clock");
        for (int index = 0; index < 4; index++) restoredMeteor.tick();
        check(restoredMeteor.isRemoved() && Math.abs(restoredMeteor.getY() - player.getY()) < 1e-5,
                "Reloaded falling meteor resolves at its original impact height after the remaining four ticks");
        meteor.discard();
    }

    @SuppressWarnings("unchecked")
    private static void soloExperience(ServerPlayerEntity player) throws Exception {
        var configsField = SpellRegistry.class.getDeclaredField("serverConfigs"); configsField.setAccessible(true);
        Map<String, net.jackcooper.shapeShifterCurseAddon.spell.config.SpellConfig> saved =
                (Map<String, net.jackcooper.shapeShifterCurseAddon.spell.config.SpellConfig>) configsField.get(SpellRegistry.INSTANCE);
        String[] ids = {"fire_bolt", "frost_spike", "moonlight_arrow", "meteor", "ice_barrage",
                "frost_nova", "flame_nova", "void_devour", "death_finale"};
        try {
            for (String id : ids) for (int mode = 0; mode <= 2; mode++) {
                Spell spell = SpellRegistry.get(id);
                com.google.gson.JsonObject json;
                try (var input = CodeAuditEntityWorldProbe.class.getResourceAsStream("/data/ssc_addon/spells/" + id + ".json")) {
                    json = com.google.gson.JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                }
                // Seventeen is intentionally not divisible by the three-projectile volley.
                json.addProperty("mana_cost", 17); json.addProperty("exp_mode", mode);
                var configs = new java.util.HashMap<>(saved);
                configs.put(id, net.jackcooper.shapeShifterCurseAddon.spell.config.SpellConfig.fromJson(json));
                configsField.set(SpellRegistry.INSTANCE, Map.copyOf(configs));
                var victim = mob(player, .5);
                int total = Math.round(spell.getManaCost() * spell.getConfig().manaCostMultiplier(1));
                int pending = mode == 1 ? total : mode == 2 ? total * 8 / 10 : 0;
                int immediate = total - pending;
                int before = NeoRuntime.data(player).spellbookMasteryExpTen;
                if (id.equals("death_finale")) {
                    DeathFinaleManager.begin(player, 1);
                    DeathFinaleManager.advance(player, 1);
                }
                SpellCastManager.castSoloEffect(player, spell, ScrollData.create(id), 1, 2, victim.getPos());
                check(spell.ssc_addon$getRefundCastId() == null, id + " solo mode " + mode + " never opens a book refund cast");
                boolean projectile = java.util.Set.of("fire_bolt", "frost_spike", "moonlight_arrow", "meteor", "ice_barrage").contains(id);
                if (projectile) {
                    check(NeoRuntime.data(player).spellbookMasteryExpTen == before + SpellbookData.boostedExpTen(immediate),
                            id + " solo mode " + mode + " grants only its configured release portion before impact");
                    var missiles = player.getServerWorld().getEntitiesByClass(net.minecraft.entity.projectile.ProjectileEntity.class,
                            player.getBoundingBox().expand(64), entity -> entity.getOwner() == player && !entity.isRemoved());
                    check(missiles.size() == (id.equals("ice_barrage") ? 3 : 1), id + " creates the expected solo projectiles");
                    int transported = 0;
                    for (var missile : missiles) {
                        var tag = missile.writeNbt(new NbtCompound());
                        transported += tag.getInt("ExpBountyTen");
                        check(!tag.containsUuid("RefundCastId"), id + " solo projectile carries experience without a book refund ID");
                        victim.timeUntilRegen = 0;
                        if (missile instanceof SpellMeteorEntity meteor) {
                            meteor.setPosition(victim.getPos());
                            var explode = SpellMeteorEntity.class.getDeclaredMethod("explode", ServerWorld.class); explode.setAccessible(true);
                            explode.invoke(meteor, player.getServerWorld());
                        } else {
                            var collide = net.minecraft.entity.projectile.ProjectileEntity.class.getDeclaredMethod("onCollision", net.minecraft.util.hit.HitResult.class);
                            collide.setAccessible(true); collide.invoke(missile, new net.minecraft.util.hit.EntityHitResult(victim));
                        }
                        missile.discard();
                    }
                    check(transported == pending, id + " solo mode " + mode + " transports its complete pending budget");
                }
                check(victim.getHealth() < 200, id + " solo mode " + mode + " successfully damages its real target");
                int expected = SpellbookData.boostedExpTen(immediate) + SpellbookData.boostedExpTen(pending);
                if (id.equals("ice_barrage")) {
                    int each = pending / 3, remainder = pending % 3;
                    expected = SpellbookData.boostedExpTen(immediate);
                    for (int index = 0; index < 3; index++) expected += SpellbookData.boostedExpTen(each + (index < remainder ? 1 : 0));
                }
                check(NeoRuntime.data(player).spellbookMasteryExpTen == before + expected,
                        id + " solo mode " + mode + " completes its configured release and hit experience exactly once");
                check(spell.ssc_addon$takePendingExp() == 0, id + " solo entry leaves no budget for another cast");
                DeathFinaleManager.cancel(player);
                victim.discard();
            }
        } finally {
            configsField.set(SpellRegistry.INSTANCE, saved);
            DeathFinaleManager.cancel(player);
            for (String id : ids) SpellRegistry.get(id).ssc_addon$clearPendingExp();
        }
        System.out.println("SOLO_EXPERIENCE_MATRIX_PASS: nine native spell paths x three exp modes, real release/collision and volley conservation.");
    }

    @SuppressWarnings("unchecked")
    private static void pocketRetention(TestContext context, ServerPlayerEntity player) throws Exception {
        var storage = net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceStorage.class;
        var manager = net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceManager.class;
        ItemStack scroll = ScrollData.create("pocket_space");
        UUID id = net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceStorage.bind(player.getServer(), scroll);
        var registry = net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceStorage.registry(player.getServer());
        var room = net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceStorage.findRoom(player.getServer(), id);
        var seenField = room.getClass().getDeclaredField("lastSeen"); seenField.setAccessible(true);
        seenField.setLong(room, -20_000);
        var builtField = room.getClass().getDeclaredField("built"); builtField.setAccessible(true); builtField.setBoolean(room, true);
        BlockPos chestPos = context.getAbsolutePos(new BlockPos(3, 2, 8));
        context.getWorld().setBlockState(chestPos, Blocks.CHEST.getDefaultState());
        var chest = (net.minecraft.block.entity.ChestBlockEntity) context.getWorld().getBlockEntity(chestPos);
        chest.setStack(0, scroll);
        var census = manager.getDeclaredMethod("census", MinecraftServer.class, ServerWorld.class); census.setAccessible(true);
        census.invoke(null, player.getServer(), context.getWorld());
        check(net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceStorage.findRoom(player.getServer(), id) == room,
                "A bound scroll stored in a chest retains its original room after a stale census heartbeat");
        check(seenField.getLong(room) == -20_000, "The retention test really exercises an unobserved container scroll");
        chest.removeStack(0); // only the local saved item now holds the UUID, as with offline/unloaded inventory
        census.invoke(null, player.getServer(), context.getWorld());
        check(net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceStorage.findRoom(player.getServer(), id) == room,
                "An unobserved/offline scroll never triggers automatic room deletion");

        var collapsingField = room.getClass().getDeclaredField("collapsing"); collapsingField.setAccessible(true);
        collapsingField.setBoolean(room, true);
        var pendingField = registry.getClass().getDeclaredField("pendingWipes"); pendingField.setAccessible(true);
        Map<Integer, UUID> pending = (Map<Integer, UUID>) pendingField.get(registry);
        int reserved = room.layout().index() + 1; UUID legacyId = UUID.randomUUID(); pending.put(reserved, legacyId);
        var recoveredField = manager.getDeclaredField("recoveredTasks"); recoveredField.setAccessible(true); recoveredField.setBoolean(null, false);
        var recover = manager.getDeclaredMethod("recoverTasks", MinecraftServer.class); recover.setAccessible(true);
        recover.invoke(null, player.getServer());
        check(!collapsingField.getBoolean(room), "Legacy absence-based eviction intent is cancelled on recovery");
        check(net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceManager.prepareRoom(player, id),
                "A still-registered legacy collapsing room can be entered again");
        var wipesField = manager.getDeclaredField("WIPES"); wipesField.setAccessible(true);
        var evictionsField = manager.getDeclaredField("EVICTIONS"); evictionsField.setAccessible(true);
        var completedField = manager.getDeclaredField("COMPLETED_WIPES"); completedField.setAccessible(true);
        check(((Map<?, ?>) wipesField.get(null)).isEmpty() && ((Map<?, ?>) evictionsField.get(null)).isEmpty()
                        && ((Map<?, ?>) completedField.get(null)).isEmpty(),
                "Legacy cleanup journals enqueue neither deletion nor slot release after a flush");
        check(pending.get(reserved).equals(legacyId), "An incomplete legacy wipe keeps its remaining terrain reserved");
        ItemStack legacyScroll = ScrollData.create("pocket_space");
        legacyScroll.getOrCreateNbt().putUuid(net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceStorage.SCROLL_ID, legacyId);
        check(net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceStorage.bind(player.getServer(), legacyScroll) == null
                        && legacyScroll.getNbt().getUuid(net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceStorage.SCROLL_ID).equals(legacyId),
                "A legacy isolated binding retains its UUID rather than overwriting remaining terrain");
        ItemStack fresh = ScrollData.create("pocket_space");
        UUID freshId = net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceStorage.bind(player.getServer(), fresh);
        check(net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceStorage.findRoom(player.getServer(), freshId).layout().index() != reserved,
                "New rooms never reuse a protected legacy cleanup slot");
        net.jackcooper.shapeShifterCurseAddon.spell.pocket.PocketSpaceManager.afterFlushedSave(context.getWorld());
        check(pending.get(reserved).equals(legacyId), "Saving does not release an isolated legacy cleanup slot");
        check(chest.getStack(0).isEmpty(), "The retention test does not manufacture a replacement scroll");
        System.out.println("POCKET_RETENTION_WORLD_PASS: stored/offline scrolls, cancellation of legacy eviction and protected cleanup slots.");
    }

    private static void inventoryMerge(ServerPlayerEntity player) {
        class Inventory extends SimpleInventory {
            int dirty;
            Inventory() { super(8); }
            @Override public void markDirty() { super.markDirty(); dirty++; }
        }
        Inventory inventory = new Inventory();
        ItemStack bottles = new ItemStack(SscAddon.UNIVERSAL_ENERGY_POTION, 2);
        inventory.setStack(0, bottles);
        var screen = new PotionStorageBoxScreenHandler(1, player.getInventory(), inventory);
        player.getInventory().setStack(9, new ItemStack(SscAddon.UNIVERSAL_ENERGY_POTION, 3));
        inventory.dirty = 0;
        screen.quickMove(player, 8);
        check(inventory.getStack(0).getCount() == 5 && player.getInventory().getStack(9).isEmpty(),
                "Shift transfer merges all bottles into the existing stack");
        check(inventory.getStack(1).isEmpty() && inventory.dirty > 0,
                "A merge that uses no new slot still marks the destination inventory dirty");
    }

    private static void worldOwners(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        ServerWorld other = player.getServer().getWorld(World.NETHER);
        check(other != null, "Second actual dimension exists");
        BlockPos pos = player.getBlockPos();
        UUID ownerA = UUID.randomUUID(), ownerB = UUID.randomUUID();
        WebMembraneOwners.set(world, pos, ownerA);
        WebMembraneOwners.set(other, pos, ownerB);
        check(ownerA.equals(WebMembraneOwners.get(world, pos)) && ownerB.equals(WebMembraneOwners.get(other, pos)),
                "Same coordinates in separate worlds retain independent membrane owners");
        WebMembraneOwners.remove(world, pos);
        check(ownerB.equals(WebMembraneOwners.get(other, pos)), "Removing one world's membrane preserves the other owner");
        WebMembraneOwners.remove(other, pos);
    }

    @SuppressWarnings("unchecked")
    private static void terrainJournal(TestContext context) throws Exception {
        ServerWorld world = context.getWorld();
        Path directory = world.getServer().getSavePath(WorldSavePath.ROOT).resolve("data/ssc_addon_death_domain_recovery");
        BlockPos a = context.getAbsolutePos(new BlockPos(6, 2, 6));
        BlockPos b = a.east(), c = a.south();
        UUID domain = UUID.randomUUID();
        Map<BlockPos, DeathDomainRecovery.Change> plan = new LinkedHashMap<>();
        plan.put(a, new DeathDomainRecovery.Change(Blocks.STONE.getDefaultState(), Blocks.SOUL_SAND.getDefaultState()));
        plan.put(b, new DeathDomainRecovery.Change(Blocks.DIRT.getDefaultState(), Blocks.SOUL_SAND.getDefaultState()));
        plan.put(c, new DeathDomainRecovery.Change(Blocks.FERN.getDefaultState(), Blocks.AIR.getDefaultState()));
        check(DeathDomainRecovery.record(world, domain, plan), "Terrain rollback is durably recorded before conversion");
        check(count(directory) == 1, "Atomic ring log exists on disk");
        world.setBlockState(a, Blocks.SOUL_SAND.getDefaultState());
        world.setBlockState(b, Blocks.DIAMOND_BLOCK.getDefaultState()); // later construction must survive
        world.setBlockState(c, Blocks.AIR.getDefaultState());
        var serversField = DeathDomainRecovery.class.getDeclaredField("SERVERS"); serversField.setAccessible(true);
        var states = (Map<MinecraftServer, ?>) serversField.get(null);
        states.remove(world.getServer()); // discard process state; load the real disk log again
        DeathDomainRecovery.finished(world, domain);
        check(world.getBlockState(a).isOf(Blocks.STONE), "Disk-only recovery restores still-converted terrain");
        check(world.getBlockState(b).isOf(Blocks.DIAMOND_BLOCK), "Recovery preserves later construction");
        check(world.getBlockState(c).isOf(Blocks.FERN), "Recovery restores temporarily removed non-full blocks");
        check(count(directory) == 1, "Restoration retains its log until cleared chunks have flushed");
        world.save(null, true, false);
        check(count(directory) == 0, "Completed flushing world save releases its durable recovery log");

        BlockPos far = new BlockPos(20_000_000, 80, 20_000_000);
        check(!world.isChunkLoaded(far.getX() >> 4, far.getZ() >> 4), "Remote recovery fixture starts unloaded");
        UUID remote = UUID.randomUUID();
        check(DeathDomainRecovery.record(world, remote, Map.of(far, plan.get(a))), "Remote journal is recorded without touching its chunk");
        states.remove(world.getServer());
        DeathDomainRecovery.finished(world, remote);
        check(!world.isChunkLoaded(far.getX() >> 4, far.getZ() >> 4), "Recovery never force-loads distant chunks");
        world.save(null, true, false);
        check(count(directory) == 1, "Unresolved unloaded terrain remains journaled after a flush");

        // Force a real filesystem write failure in the disposable test save, then invoke
        // the actual domain conversion method. Failure must not change even its first block.
        Object state = states.get(world.getServer());
        var pathField = state.getClass().getDeclaredField("directory"); pathField.setAccessible(true);
        Path blocker = directory.resolve("write-blocker"); Files.writeString(blocker, "test");
        pathField.set(state, blocker);
        BlockPos center = context.getAbsolutePos(new BlockPos(9, 2, 9));
        world.setBlockState(center, Blocks.STONE.getDefaultState());
        Class<?> dataType = Class.forName(AnubisWolfSpDeathDomain.class.getName() + "$DomainData");
        var constructor = dataType.getDeclaredConstructor(ServerWorld.class, BlockPos.class, int.class); constructor.setAccessible(true);
        Object data = constructor.newInstance(world, center, center.getY());
        var convert = AnubisWolfSpDeathDomain.class.getDeclaredMethod("convertBlocksInRing", ServerWorld.class, dataType, int.class, int.class);
        convert.setAccessible(true); convert.invoke(null, world, data, 0, 0);
        check(world.getBlockState(center).isOf(Blocks.STONE), "A failed write-ahead log prevents actual domain terrain conversion");
        pathField.set(state, directory);
        states.remove(world.getServer()); // production lifecycle will reload the retained unresolved journal
    }

    private static long count(Path directory) throws Exception {
        try (var files = Files.list(directory)) { return files.filter(p -> p.getFileName().toString().endsWith(".nbt")).count(); }
    }
}
