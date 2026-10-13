package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.fluid.FluidState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.profiler.DummyProfiler;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Actual Minecraft voxel pathfinding and move controls against an isolated block world.
 * Does not run a live server, entity collisions with other mobs, or client rendering. */
public final class AxolotlShifterAiProbe {
    private static sun.misc.Unsafe unsafe;
    private static int checks;

    public static void run() throws Exception {
        Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        unsafe = (sun.misc.Unsafe) singleton.get(null);
        // Bootstrap alone does not apply datapack tags. Bind the vanilla fluid tags used by the AI.
        net.minecraft.registry.Registries.FLUID.populateTags(Map.of(
                net.minecraft.registry.tag.FluidTags.WATER, List.of(
                        net.minecraft.registry.Registries.FLUID.getEntry(net.minecraft.fluid.Fluids.WATER),
                        net.minecraft.registry.Registries.FLUID.getEntry(net.minecraft.fluid.Fluids.FLOWING_WATER)),
                net.minecraft.registry.tag.FluidTags.LAVA, List.of(
                        net.minecraft.registry.Registries.FLUID.getEntry(net.minecraft.fluid.Fluids.LAVA),
                        net.minecraft.registry.Registries.FLUID.getEntry(net.minecraft.fluid.Fluids.FLOWING_LAVA))));
        TestWorld world = allocate(TestWorld.class);
        world.blocks = new HashMap<>();
        world.boundary = new net.minecraft.world.border.WorldBorder();
        world.chunk = allocate(TestChunk.class);
        world.chunk.terrain = world;
        world.chunks = allocate(TestChunks.class);
        world.chunks.chunk = world.chunk;
        TestShifter shifter = allocate(TestShifter.class);
        shifter.terrain = world;
        shifter.attrs = new AttributeContainer(AxolotlShifterEntity.createAxolotlShifterAttributes().build());
        shifter.penalties = new HashMap<>();
        shifter.rng = Random.create(1);
        shifter.wetness = 300;
        shifter.velocity = Vec3d.ZERO;
        shifter.initializeTracker();
        shifter.powers = (io.github.apace100.apoli.component.PowerHolderComponent) java.lang.reflect.Proxy.newProxyInstance(
                io.github.apace100.apoli.component.PowerHolderComponent.class.getClassLoader(),
                new Class<?>[]{io.github.apace100.apoli.component.PowerHolderComponent.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getPowers" -> List.of();
                    case "getPowerTypes" -> Set.of();
                    case "hasPower" -> false;
                    default -> null;
                });
        shifter.components = dev.onyxstudios.cca.api.v3.component.ComponentContainer.Factory.builder(TestShifter.class)
                .component(io.github.apace100.apoli.component.PowerHolderComponent.KEY, entity -> entity.powers).build().createContainer(shifter);
        set(Entity.class, shifter, "type", EntityType.ZOMBIE);
        set(Entity.class, shifter, "dimensions", EntityDimensions.fixed(0.6f, 1.8f));
        shifter.setStepHeight(0.6f);
        position(shifter, new Vec3d(0.5, 1, 0.5));
        shifter.navigation = new AxolotlShifterEntity.ShifterNavigation(shifter, world);
        shifter.control = new AxolotlShifterEntity.ShifterMoveControl(shifter);
        shoreline(world, shifter);
        recovery(world, shifter);
        steering(world, shifter);
        System.out.println("Axolotl shifter AI: " + checks + " voxel-path, shore, swim-control and recovery checks passed. Live gameplay NOT tested.");
    }

    private static void shoreline(TestWorld world, TestShifter shifter) throws Exception {
        // A pond one block below the shore; target is on land beyond the bank.
        world.blocks.clear();
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            world.blocks.put(new BlockPos(x, 1, z), x < 2 ? Blocks.WATER.getDefaultState() : Blocks.STONE.getDefaultState());
        }
        shifter.inWater = true;
        position(shifter, new Vec3d(0.5, 1, 0.5));
        Path path = shifter.navigation.findPathTo(new BlockPos(3, 2, 0), 0);
        check(path != null && path.reachesTarget(), "a one-block bank has a complete exit path");
        check(path.getLength() <= 6, "shore chase takes a short route instead of circling in the pond");
        shifter.navigation.startMovingAlong(path, 1);
        shifter.velocity = Vec3d.ZERO;
        // Drive the production navigation, controller and water travel through real block collisions.
        for (int tick = 0; tick < 160 && shifter.inWater; tick++) {
            shifter.navigation.tick();
            shifter.control.tick();
            shifter.travel(new Vec3d(shifter.sidewaysSpeed, shifter.upwardSpeed, shifter.forwardSpeed));
            shifter.inWater = shifter.getY() < 2.0 && shifter.getX() < 2.0;
        }
        check(!shifter.inWater && shifter.getY() >= 2.0, "navigation and swimming can physically clear a one-block bank");

        // Enclose the closest water; an accessible pond farther away must still be chosen.
        world.blocks.clear();
        shifter.navigation.stop();
        shifter.inWater = false;
        position(shifter, new Vec3d(0.5, 1, 0.5));
        BlockPos sealedWater = new BlockPos(2, 1, 0);
        world.blocks.put(sealedWater, Blocks.WATER.getDefaultState());
        for (int x = 1; x <= 3; x++) for (int z = -1; z <= 1; z++) for (int y = 1; y <= 4; y++) {
            if (x != 2 || z != 0) world.blocks.put(new BlockPos(x, y, z), Blocks.STONE.getDefaultState());
        }
        BlockPos reachable = new BlockPos(-4, 1, 0);
        world.blocks.put(reachable, Blocks.WATER.getDefaultState());
        var findWater = AxolotlShifterEntity.class.getDeclaredMethod("findWaterPath", int.class, int.class, Set.class);
        findWater.setAccessible(true);
        Path waterPath = (Path) findWater.invoke(shifter, 6, 4, Set.of());
        check(waterPath != null && waterPath.reachesTarget() && waterPath.getTarget().equals(reachable),
                "unreachable closest water does not hide a reachable alternative: " + waterPath
                        + " target=" + (waterPath == null ? "none" : waterPath.getTarget()));
        world.blocks.remove(reachable);
        world.blocks.put(sealedWater, Blocks.OAK_SLAB.getDefaultState().with(net.minecraft.state.property.Properties.WATERLOGGED, true));
        shifter.navigation.stop();
        check(findWater.invoke(shifter, 6, 4, Set.of()) == null, "water inside solid geometry is not a usable destination");
    }

    private static void recovery(TestWorld world, TestShifter shifter) throws Exception {
        world.blocks.clear();
        world.blocks.put(new BlockPos(2, 1, 0), Blocks.WATER.getDefaultState());
        position(shifter, new Vec3d(0.5, 1, 0.5));
        shifter.inWater = false;
        shifter.wetness = 300;
        shifter.target = null;
        shifter.age = 100;
        var seek = new AxolotlShifterEntity.SeekWaterGoal(shifter);
        check(!seek.canStart(), "well-hydrated animal can remain on the shore");
        shifter.wetness = 179;
        shifter.age += 20;
        check(seek.canStart(), "dry animal returns to water even when already beside it");
        seek.start();
        seek.tick();
        Path original = shifter.navigation.getCurrentPath();
        check(original != null, "return-to-water actually starts a path");
        shifter.age += 20;
        position(shifter, new Vec3d(0.8, 1, 0.5));
        seek.tick();
        check(shifter.navigation.getCurrentPath() == original, "progressing return path is not replaced during travel");
        shifter.inWater = true;
        shifter.wetness = 200;
        check(seek.shouldContinue(), "entering water does not end recovery before its finish threshold");
        seek.tick();
        check(shifter.navigation.isIdle(), "animal rests in water during recovery");
        shifter.wetness = 240;
        check(!seek.shouldContinue(), "normal activity resumes after recovery");
        seek.stop();

        shifter.target = shifter; // Presence only: no target selectors or combat are invoked here.
        shifter.wetness = 80;
        var retreat = new AxolotlShifterEntity.CombatRetreatToWaterGoal(shifter);
        check(retreat.canStart(), "a very dry combatant also recovers when already in water");
        retreat.start();
        shifter.target = null;
        check(retreat.shouldContinue(), "losing a combat target does not interrupt water recovery");
        shifter.wetness = 240;
        check(!retreat.shouldContinue(), "combat recovery also ends at 240");
        retreat.stop();
    }

    private static void steering(TestWorld world, TestShifter shifter) throws Exception {
        world.blocks.clear();
        world.blocks.put(new BlockPos(0, 1, 0), Blocks.WATER.getDefaultState());
        world.blocks.put(new BlockPos(0, 2, 0), Blocks.WATER.getDefaultState());
        position(shifter, new Vec3d(0.5, 1, 0.5));
        shifter.inWater = true;
        var control = new AxolotlShifterEntity.ShifterMoveControl(shifter);
        Path route = new Path(List.of(new net.minecraft.entity.ai.pathing.PathNode(0, 1, 0),
                new net.minecraft.entity.ai.pathing.PathNode(0, 2, 0)), new BlockPos(0, 2, 0), true);
        shifter.navigation.startMovingAlong(route, 1);
        control.moveTo(0.5, 2.0, 0.5, 1);
        control.tick();
        check(shifter.upwardSpeed > 0, "swimming control can ascend");
        control.moveTo(0.5, 0.5, 0.5, 1);
        // Vanilla aquatic pitch turns by five degrees each tick, so give the reversal time to settle.
        for (int turn = 0; turn < 40; turn++) control.tick();
        check(shifter.upwardSpeed < 0, "swimming control can descend");
        shifter.horizontalCollision = true;
        shifter.velocity = Vec3d.ZERO;
        control.moveTo(0.5, 2, 0.5, 1);
        control.tick();
        check(shifter.velocity.y == 0, "colliding with an underwater wall does not launch a jump");
        control.moveTo(1.5, 2, 0.5, 1);
        control.tick();
        check(shifter.velocity.y >= 0.42, "a real shoreline step triggers an exit jump");
        world.blocks.put(new BlockPos(0, 3, 0), Blocks.STONE.getDefaultState());
        shifter.velocity = Vec3d.ZERO;
        control.tick();
        check(shifter.velocity.y == 0, "blocked headroom suppresses an exit jump");
        world.blocks.clear();
        shifter.horizontalCollision = false;
        shifter.inWater = false;
        control.moveTo(1.5, 1, 0.5, 1);
        control.tick();
        check(shifter.upwardSpeed == 0 && shifter.forwardSpeed > 0, "land walking clears swimming input");
    }

    private static <T> T allocate(Class<T> cls) throws InstantiationException { return cls.cast(unsafe.allocateInstance(cls)); }
    private static void set(Class<?> cls, Object object, String name, Object value) throws Exception {
        Field field = cls.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static void position(Entity entity, Vec3d pos) throws Exception {
        set(Entity.class, entity, "pos", pos);
        set(Entity.class, entity, "blockPos", BlockPos.ofFloored(pos));
        set(Entity.class, entity, "boundingBox", new Box(pos.x - 0.3, pos.y, pos.z - 0.3, pos.x + 0.3, pos.y + 1.8, pos.z + 0.3));
    }
    private static void check(boolean ok, String description) { checks++; if (!ok) throw new AssertionError(description); }

    public static final class TestShifter extends AxolotlShifterEntity implements dev.onyxstudios.cca.api.v3.component.ComponentProvider {
        TestWorld terrain; AttributeContainer attrs; Map<PathNodeType, Float> penalties;
        Random rng; EntityNavigation navigation; LivingEntity target; Vec3d velocity;
        AxolotlShifterEntity.ShifterMoveControl control;
        io.github.apace100.apoli.component.PowerHolderComponent powers;
        dev.onyxstudios.cca.api.v3.component.ComponentContainer components;
        boolean inWater; int wetness;
        private TestShifter() { super(null, null); }
        @Override public dev.onyxstudios.cca.api.v3.component.ComponentContainer getComponentContainer() { return components; }
        void initializeTracker() throws Exception {
            set(Entity.class, this, "dataTracker", new net.minecraft.entity.data.DataTracker(this));
            Field flags = Entity.class.getDeclaredField("FLAGS"); flags.setAccessible(true);
            @SuppressWarnings("unchecked") var flagKey = (net.minecraft.entity.data.TrackedData<Byte>) flags.get(null);
            this.dataTracker.startTracking(flagKey, (byte) 0);
            this.initDataTracker();
        }
        @Override public LivingEntity getControllingPassenger() { return null; }
        @Override public boolean hasPassengers() { return false; }
        @Override public net.minecraft.item.ItemStack getMainHandStack() { return net.minecraft.item.ItemStack.EMPTY; }
        @Override public World getWorld() { return terrain; }
        @Override public AttributeContainer getAttributes() { return attrs; }
        @Override public double getAttributeValue(net.minecraft.entity.attribute.EntityAttribute attr) { return attrs.getValue(attr); }
        @Override public EntityNavigation getNavigation() { return navigation; }
        @Override public net.minecraft.entity.ai.control.MoveControl getMoveControl() { return control; }
        @Override public LivingEntity getTarget() { return target; }
        @Override public Random getRandom() { return rng; }
        @Override public boolean isTouchingWater() { return inWater; }
        @Override public boolean isInLava() { return false; }
        @Override public boolean isPanicking() { return false; }
        @Override public boolean isOnGround() { return !inWater; }
        @Override public boolean isAlive() { return true; }
        @Override public boolean isInWalkTargetRange(BlockPos pos) { return true; }
        @Override public int getMoisture() { return wetness; }
        @Override public float getPathfindingPenalty(PathNodeType type) { return penalties.getOrDefault(type, type.getDefaultPenalty()); }
        @Override public void setPathfindingPenalty(PathNodeType type, float penalty) { penalties.put(type, penalty); }
        @Override public Vec3d getVelocity() { return velocity; }
        @Override public void setVelocity(Vec3d value) { velocity = value; }
        @Override public boolean isLogicalSideForUpdatingMovement() { return true; }
        @Override public void updateLimbs(boolean flutter) { }
        @Override public void move(net.minecraft.entity.MovementType type, Vec3d requested) {
            Vec3d allowed = Entity.adjustMovementForCollisions(this, requested, getBoundingBox(), terrain, List.of());
            this.horizontalCollision = Math.abs(allowed.x - requested.x) > 1e-7 || Math.abs(allowed.z - requested.z) > 1e-7;
            try { position(this, getPos().add(allowed)); } catch (Exception e) { throw new RuntimeException(e); }
            if (Math.abs(allowed.y - requested.y) > 1e-7) velocity = new Vec3d(velocity.x, 0, velocity.z);
        }
    }

    public static final class TestWorld extends ServerWorld {
        Map<BlockPos, BlockState> blocks; TestChunk chunk; TestChunks chunks;
        net.minecraft.world.border.WorldBorder boundary;
        private TestWorld() { super(null, null, null, null, World.OVERWORLD, null, null, false, 0, List.of(), false, null); }
        @Override public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, pos.getY() <= 0 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public WorldChunk getChunk(int x, int z) { return chunk; }
        @Override public Chunk getChunk(int x, int z, ChunkStatus status, boolean create) { return chunk; }
        @Override public net.minecraft.server.world.ServerChunkManager getChunkManager() { return chunks; }
        @Override public net.minecraft.world.BlockView getChunkAsView(int x, int z) { return chunk; }
        @Override public int getBottomY() { return -16; }
        @Override public int getHeight() { return 64; }
        @Override public Profiler getProfiler() { return DummyProfiler.INSTANCE; }
        @Override public net.minecraft.world.border.WorldBorder getWorldBorder() { return boundary; }
        @Override public List<net.minecraft.util.shape.VoxelShape> getEntityCollisions(Entity entity, Box box) { return List.of(); }
        @Override public long getTime() { return 0; }
    }

    public static final class TestChunks extends net.minecraft.server.world.ServerChunkManager {
        TestChunk chunk;
        private TestChunks() { super(null, null, null, null, null, null, 0, 0, false, null, null, null); }
        @Override public WorldChunk getWorldChunk(int x, int z) { return chunk; }
        @Override public Chunk getChunk(int x, int z, ChunkStatus status, boolean create) { return chunk; }
        @Override public boolean isChunkLoaded(int x, int z) { return true; }
    }

    public static final class TestChunk extends WorldChunk {
        TestWorld terrain;
        private TestChunk() { super(null, null); }
        @Override public BlockState getBlockState(BlockPos pos) { return terrain.getBlockState(pos); }
        @Override public FluidState getFluidState(BlockPos pos) { return terrain.getFluidState(pos); }
        @Override public boolean areSectionsEmptyBetween(int lower, int upper) { return false; }
        @Override public int getBottomY() { return -16; }
        @Override public int getHeight() { return 64; }
    }
}
