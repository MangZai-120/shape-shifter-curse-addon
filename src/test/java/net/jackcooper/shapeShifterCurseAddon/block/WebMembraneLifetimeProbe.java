package net.jackcooper.shapeShifterCurseAddon.block;

import net.minecraft.block.Blocks;
import net.minecraft.block.ConnectingBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/** Real block placement/replacement, stale callbacks, and saved deadline round-trips. */
public final class WebMembraneLifetimeProbe {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void run(TestContext context) {
        var world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(100, 7, 100));
        world.getChunk(pos);
        world.setBlockState(pos.down(), Blocks.STONE.getDefaultState());
        var block = RegAddonBlocks.WEB_MEMBRANE;
        var membrane = block.getDefaultState().with(ConnectingBlock.DOWN, true);
        world.setBlockState(pos, membrane);
        long remaining = WebMembraneLifetimes.remaining(world, pos);
        check(remaining >= 1200 && remaining <= 1800, "Actual placement records a sixty-to-ninety-second deadline");
        world.setBlockState(pos, Blocks.AIR.getDefaultState());
        check(WebMembraneLifetimes.remaining(world, pos) == 0, "Removing a membrane clears its placement deadline");
        world.setBlockState(pos, membrane);
        long replacementDeadline = WebMembraneLifetimes.remaining(world, pos);
        block.scheduledTick(membrane, world, pos, world.getRandom());
        check(world.getBlockState(pos).isOf(block), "Stale scheduled callback cannot remove a new membrane");
        check(WebMembraneLifetimes.remaining(world, pos) == replacementDeadline, "Stale callback never rerolls or extends lifetime");

        var saved = WebMembraneLifetimes.get(world).writeNbt(new NbtCompound());
        var loaded = WebMembraneLifetimes.read(saved);
        check(saved.equals(loaded.writeNbt(new NbtCompound())), "Placement deadlines survive actual NBT codec round-trip");
        world.getPersistentStateManager().set(WebMembraneLifetimes.ID, loaded);
        block.scheduledTick(membrane, world, pos, world.getRandom());
        check(world.getBlockState(pos).isOf(block), "Reloaded deadline still protects replacement from an old callback");

        var expired = loaded.writeNbt(new NbtCompound());
        var entries = expired.getList("Entries", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            if (entry.getLong("Pos") == pos.asLong()) entry.putLong("Until", world.getTime() - 1);
        }
        world.getPersistentStateManager().set(WebMembraneLifetimes.ID, WebMembraneLifetimes.read(expired));
        block.scheduledTick(membrane, world, pos, world.getRandom());
        check(world.getBlockState(pos).isAir(), "An actually expired membrane disappears");
        check(WebMembraneLifetimes.remaining(world, pos) == 0, "Expiry clears its saved deadline");
        world.setBlockState(pos, membrane);
        WebMembraneLifetimes.removed(world, pos);
        block.scheduledTick(membrane, world, pos, world.getRandom());
        check(world.getBlockState(pos).isAir(), "Legacy membranes without new deadline data retain their original scheduled expiry");
        System.out.println("Web membrane lifetime checks passed: " + checks);
    }
}
