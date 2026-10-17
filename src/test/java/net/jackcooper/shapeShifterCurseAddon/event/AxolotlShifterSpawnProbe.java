package net.jackcooper.shapeShifterCurseAddon.event;

import com.google.common.collect.ImmutableSet;
import dev.onyxstudios.cca.api.v3.component.ComponentContainer;
import dev.onyxstudios.cca.api.v3.component.ComponentProvider;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.jackcooper.shapeShifterCurseAddon.entity.AxolotlShifterEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.passive.AxolotlEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.EntityBucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Real bucket NBT/FromBucket ordering and registered lifecycle callbacks, with isolated world I/O. */
public final class AxolotlShifterSpawnProbe {
    private static final String CHECKED_TAG = "ssc_axolotl_shifter_checked";
    private static sun.misc.Unsafe unsafe;
    private static int checks;
    private static int nextEntityId = 1;

    public static void run() throws Exception {
        Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        unsafe = (sun.misc.Unsafe) singleton.get(null);
        AxolotlShifterSpawnHandler.register();
        TestWorld world = allocate(TestWorld.class);
        world.neighbor = allocate(AxolotlShifterEntity.class);

        // Keep EntityBucketItem.onEmptied/spawnEntity, copyDataFromNbt and setFromBucket real.
        // Only EntityType's world insertion is isolated; it fires ENTITY_LOAD before returning.
        EntityBucketItem bucket = new EntityBucketItem(new BucketAxolotlType(), Fluids.WATER,
                SoundEvents.ITEM_BUCKET_EMPTY_AXOLOTL, new Item.Settings());
        for (int i = 0; i < 3; i++) {
            ItemStack stack = new ItemStack(Items.AXOLOTL_BUCKET);
            stack.getOrCreateNbt().putInt("Variant", AxolotlEntity.Variant.BLUE.getId());
            bucket.onEmptied(null, world, stack, BlockPos.ORIGIN);
            TestAxolotl released = world.lastLoaded;
            check(released.isFromBucket(), "vanilla bucket marks released axolotl after ENTITY_LOAD");
            check(released.getVariant() == AxolotlEntity.Variant.BLUE, "bucket still restores its variant");
            check(world.nearbyQueries == 0, "bucket insertion does not immediately check for a shifter");
            endTick(world);
            check(world.nearbyQueries == 0 && !released.tags.contains(CHECKED_TAG),
                    "completed bucket release never enters the companion spawn path");
            load(released, world);
            endTick(world);
            check(world.nearbyQueries == 0, "reloaded bucket axolotl stays excluded");
        }

        TestAxolotl wild = axolotl(world);
        load(wild, world);
        load(wild, world);
        check(!wild.tags.contains(CHECKED_TAG) && world.nearbyQueries == 0,
                "wild companion checks wait until end of world tick");
        endTick(world);
        check(wild.tags.contains(CHECKED_TAG) && world.nearbyQueries == 1,
                "wild axolotl still checks its original companion rule exactly once");
        check(world.spawns == 0, "existing nearby shifter prevents an extra companion");
        load(wild, world);
        endTick(world);
        check(world.nearbyQueries == 1, "persisted checked tag prevents repeat checks on reload");

        TestAxolotl unloaded = axolotl(world);
        load(unloaded, world);
        ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(unloaded, world);
        endTick(world);
        check(!unloaded.tags.contains(CHECKED_TAG), "unloaded entity is removed from pending checks");
        load(unloaded, world);
        endTick(world);
        check(unloaded.tags.contains(CHECKED_TAG) && world.nearbyQueries == 2,
                "an unloaded unchecked wild axolotl is checked when loaded again");

        TestAxolotl removed = axolotl(world);
        load(removed, world);
        Field reason = Entity.class.getDeclaredField("removalReason");
        reason.setAccessible(true);
        reason.set(removed, Entity.RemovalReason.DISCARDED);
        TestAxolotl dead = axolotl(world);
        load(dead, world);
        dead.alive = false;
        TestAxolotl moved = axolotl(world);
        load(moved, world);
        moved.terrain = allocate(TestWorld.class);
        endTick(world);
        check(world.nearbyQueries == 2, "removed, dead and dimension-changed axolotls do not spawn companions");

        TestWorld otherWorld = allocate(TestWorld.class);
        otherWorld.neighbor = world.neighbor;
        TestAxolotl otherWild = axolotl(otherWorld);
        load(otherWild, otherWorld);
        TestAxolotl pending = axolotl(world);
        load(pending, world);
        ServerWorldEvents.UNLOAD.invoker().onWorldUnload(null, world);
        endTick(world);
        check(!pending.tags.contains(CHECKED_TAG), "world unload clears pending references");
        check(!otherWild.tags.contains(CHECKED_TAG), "processing one world does not drain another world");
        endTick(otherWorld);
        check(otherWild.tags.contains(CHECKED_TAG), "another world's pending check remains intact");
        System.out.println("Axolotl companion spawning: " + checks
                + " bucket-ordering, reload and lifecycle checks passed. Live gameplay NOT tested.");
    }

    private static TestAxolotl axolotl(TestWorld world) throws Exception {
        TestAxolotl axolotl = allocate(TestAxolotl.class);
        axolotl.setId(nextEntityId++);
        axolotl.terrain = world;
        axolotl.tags = new HashSet<>();
        axolotl.alive = true;
        axolotl.components = ComponentContainer.Factory.builder(TestAxolotl.class).build().createContainer(axolotl);
        Field tracker = Entity.class.getDeclaredField("dataTracker");
        tracker.setAccessible(true);
        tracker.set(axolotl, new DataTracker(axolotl));
        Field bounds = Entity.class.getDeclaredField("boundingBox");
        bounds.setAccessible(true);
        bounds.set(axolotl, new Box(0, 0, 0, 0.75, 0.42, 0.75));
        axolotl.initDataTracker();
        return axolotl;
    }

    private static void load(TestAxolotl axolotl, TestWorld world) {
        world.lastLoaded = axolotl;
        ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(axolotl, world);
    }

    private static void endTick(TestWorld world) {
        ServerTickEvents.END_WORLD_TICK.invoker().onEndTick(world);
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws InstantiationException {
        return (T) unsafe.allocateInstance(type);
    }

    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }

    public static final class TestAxolotl extends AxolotlEntity implements ComponentProvider {
        TestWorld terrain;
        Set<String> tags;
        ComponentContainer components;
        boolean alive;
        private TestAxolotl() { super(null, null); }
        @Override protected void initDataTracker() { super.initDataTracker(); }
        @Override public void onTrackedDataSet(TrackedData<?> data) { }
        @Override public ComponentContainer getComponentContainer() { return components; }
        @Override public World getWorld() { return terrain; }
        @Override public Set<String> getCommandTags() { return tags; }
        @Override public boolean addCommandTag(String tag) { return tags.add(tag); }
        @Override public boolean isAlive() { return alive; }
    }

    public static final class BucketAxolotlType extends EntityType<AxolotlEntity> {
        BucketAxolotlType() {
            super(null, SpawnGroup.AXOLOTLS, true, true, false, false, ImmutableSet.of(),
                    EntityDimensions.fixed(0.75f, 0.42f), 5, 3, FeatureFlags.VANILLA_FEATURES);
        }

        @Override public AxolotlEntity spawnFromItemStack(ServerWorld world, ItemStack stack,
                PlayerEntity player, BlockPos pos, SpawnReason reason, boolean align, boolean invert) {
            check(reason == SpawnReason.BUCKET, "vanilla bucket uses the bucket spawn reason");
            try {
                TestAxolotl axolotl = axolotl((TestWorld) world);
                check(!axolotl.isFromBucket(), "FromBucket is initially false at world insertion");
                load(axolotl, (TestWorld) world);
                return axolotl;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static final class TestWorld extends ServerWorld {
        int nearbyQueries;
        int spawns;
        TestAxolotl lastLoaded;
        AxolotlShifterEntity neighbor;
        private TestWorld() { super(null, null, null, null, World.OVERWORLD, null, null, false, 0, List.of(), false, null); }
        @Override public <T extends Entity> List<T> getEntitiesByClass(Class<T> type, Box box,
                Predicate<? super T> predicate) {
            nearbyQueries++;
            return List.of(type.cast(neighbor));
        }
        @Override public boolean spawnEntity(Entity entity) { spawns++; return true; }
        @Override public void emitGameEvent(Entity entity, GameEvent event, BlockPos pos) { }
    }
}
