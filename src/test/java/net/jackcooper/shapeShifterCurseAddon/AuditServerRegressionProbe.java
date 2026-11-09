package net.jackcooper.shapeShifterCurseAddon;

import io.netty.buffer.Unpooled;
import net.jackcooper.shapeShifterCurseAddon.block.EnergyStorageTankBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.energy.EnergyNetwork;
import net.jackcooper.shapeShifterCurseAddon.network.FormRepairBudget;
import net.jackcooper.shapeShifterCurseAddon.screen.SplitIntPropertyDelegate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ScreenHandlerPropertyUpdateS2CPacket;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Actual screen packet codecs and loaded-BE replacement/unload lifecycle; no game window. */
public final class AuditServerRegressionProbe {
    private static int checks;

    public static void run() throws Exception {
        screenProperties();
        repairBudget();
        energyLifecycle();
        net.jackcooper.shapeShifterCurseAddon.ability.GameplayAuditRegressionProbe.run();
        System.out.println("Audit server regressions: " + checks + " checks passed.");
    }

    private static void screenProperties() {
        var server = new ArrayPropertyDelegate(6);
        var client = new ArrayPropertyDelegate(6);
        var outgoing = new SplitIntPropertyDelegate(server, 2);
        var incoming = new SplitIntPropertyDelegate(client, 2);
        for (int value : new int[]{0, 25, 32767, 32768, 33000, 65535, 65536, 256000, Integer.MAX_VALUE}) {
            server.set(0, value);
            server.set(1, value);
            server.set(2, 59);
            server.set(5, 1);
            for (int index = 0; index < outgoing.size(); index++) {
                var wire = new PacketByteBuf(Unpooled.buffer());
                try {
                    new ScreenHandlerPropertyUpdateS2CPacket(1, index, outgoing.get(index)).write(wire);
                    var decoded = new ScreenHandlerPropertyUpdateS2CPacket(wire);
                    incoming.set(decoded.getPropertyId(), decoded.getValue());
                    check(wire.readableBytes() == 0, "property packet fully decoded");
                } finally { wire.release(); }
            }
            check(client.get(0) == value && client.get(1) == value, "32-bit energy/capacity survives signed short wire: " + value);
            check(client.get(2) == 59 && client.get(5) == 1, "progress and mode keep original indices");
        }
    }

    private static void repairBudget() {
        var budget = new FormRepairBudget();
        Object first = new Object(), second = new Object();
        check(budget.accept(first, 100), "first repair accepted");
        check(!budget.accept(first, 100) && !budget.accept(first, 119), "shared channels coalesce same tick and throttle retries");
        check(budget.accept(second, 100), "other connections have independent budgets");
        check(budget.accept(first, 120), "legitimate later repair accepted");
        budget.remove(first);
        check(budget.accept(first, 120), "disconnect releases connection budget");
        budget.clear();
        check(budget.accept(second, 0), "new server starts with an empty request budget");
    }

    private static void energyLifecycle() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) field.get(null);
        TestWorld world = (TestWorld) unsafe.allocateInstance(TestWorld.class);
        world.entities = new HashMap<>();
        world.loaded = new HashSet<>(Set.of(ChunkPos.toLong(0, 0), ChunkPos.toLong(1, 0)));
        world.chunks = (TestChunks) unsafe.allocateInstance(TestChunks.class);
        world.chunks.loaded = world.loaded;
        var a = tank(world, new BlockPos(15, 64, 0), 10);
        var oldB = tank(world, new BlockPos(16, 64, 0), 100);
        check(EnergyNetwork.getTotalEnergy(a.getNetwork()) == 110, "network spans two loaded chunks");
        var newB = tank(world, oldB.getPos(), 200);
        check(EnergyNetwork.getTotalEnergy(a.getNetwork()) == 210, "cached coordinates resolve the replacement BE");
        EnergyNetwork.extract(a.getNetwork(), 20);
        check(oldB.getStoredEnergy() == 100 && newB.getStoredEnergy() == 95 && a.getStoredEnergy() == 95,
                "energy mutations use current instances and preserve the removed object's inventory");
        EnergyNetwork.init();
        world.loaded.remove(ChunkPos.toLong(1, 0));
        ServerChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(world, null);
        check(a.getNetwork().size() == 1, "chunk unload rebuild excludes disconnected/unloaded members");
        world.loaded.add(ChunkPos.toLong(1, 0));
        ServerChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(world, null);
        check(a.getNetwork().size() == 2, "chunk reload discovers the network again without block placement");
        var cache = new EnergyNetwork.Cache(world, a.getNetwork());
        TestWorld other = (TestWorld) unsafe.allocateInstance(TestWorld.class);
        check(cache.resolve(other) == null, "same coordinates in another world cannot reuse a network cache");
    }

    private static EnergyStorageTankBlockEntity tank(TestWorld world, BlockPos pos, int energy) {
        var tank = new EnergyStorageTankBlockEntity(pos, Blocks.STONE.getDefaultState());
        tank.setWorld(world);
        // Test fixture supplies stored NBT without dirtying a real chunk.
        var nbt = new net.minecraft.nbt.NbtCompound();
        nbt.putInt("Energy", energy);
        tank.readNbt(nbt);
        world.entities.put(pos, tank);
        return tank;
    }

    private static void check(boolean value, String description) {
        checks++;
        if (!value) throw new AssertionError(description);
    }

    public static final class TestWorld extends ServerWorld {
        Map<BlockPos, BlockEntity> entities;
        Set<Long> loaded;
        TestChunks chunks;
        private TestWorld() { super(null, null, null, null, World.OVERWORLD, null, null, false, 0, List.of(), false, null); }
        @Override public net.minecraft.server.world.ServerChunkManager getChunkManager() { return chunks; }
        @Override public boolean isChunkLoaded(BlockPos pos) { return chunks.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            if (!isChunkLoaded(pos)) throw new AssertionError("Network attempted to access an unloaded chunk");
            return entities.get(pos);
        }
        @Override public void markDirty(BlockPos pos) { }
        @Override public void updateListeners(BlockPos pos, net.minecraft.block.BlockState old, net.minecraft.block.BlockState next, int flags) { }
        @Override public net.minecraft.block.BlockState getBlockState(BlockPos pos) { return Blocks.STONE.getDefaultState(); }
        @Override public void updateComparators(BlockPos pos, net.minecraft.block.Block block) { }
    }

    public static final class TestChunks extends net.minecraft.server.world.ServerChunkManager {
        Set<Long> loaded;
        private TestChunks() { super(null, null, null, null, null, null, 0, 0, false, null, null, null); }
        @Override public boolean isChunkLoaded(int x, int z) { return loaded.contains(ChunkPos.toLong(x, z)); }
    }
}
