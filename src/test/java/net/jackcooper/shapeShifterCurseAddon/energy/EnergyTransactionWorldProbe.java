package net.jackcooper.shapeShifterCurseAddon.energy;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.jackcooper.shapeShifterCurseAddon.block.EnergyConverterBlock;
import net.jackcooper.shapeShifterCurseAddon.block.EnergyConverterBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.block.EnergyExtractorBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.block.EnergyStorageTankBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.block.RegAddonBlocks;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.base.SimpleEnergyStorage;

/** Real Fabric transactions and block entities; each scenario gets a fresh source and budget. */
public final class EnergyTransactionWorldProbe {
    private static final List<String> FAILURES = new ArrayList<>();
    private static int checks;
    private static ServerWorld world;
    private static BlockPos origin;
    private static EnergyStorage destination;
    private static boolean lookupRegistered;

    private record Fixture(EnergyConverterBlockEntity converter, EnergyStorageTankBlockEntity tank) {
        EnergyStorage storage() { return converter.getExposedStorage(); }
    }

    private static void check(boolean condition, String message) {
        checks++;
        System.out.println("ENERGY_AUDIT " + (condition ? "PASS " : "FAIL ") + message);
        if (!condition) FAILURES.add(message);
    }

    private static void clearFixture() {
        destination = null;
        for (BlockPos pos : List.of(origin.east(), origin, origin.down(), origin.down(2),
                origin.down().south(), origin.down().south(2)))
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
    }

    private static Fixture fresh() {
        clearFixture();
        world.setBlockState(origin.down(), RegAddonBlocks.ENERGY_STORAGE_TANK.getDefaultState());
        world.setBlockState(origin, RegAddonBlocks.ENERGY_CONVERTER.getDefaultState().with(EnergyConverterBlock.ACTIVE, true));
        var fixture = new Fixture((EnergyConverterBlockEntity) world.getBlockEntity(origin),
                (EnergyStorageTankBlockEntity) world.getBlockEntity(origin.down()));
        fixture.tank.setStoredEnergy(1000);
        return fixture;
    }

    public static void start(TestContext context) {
        checks = 0; FAILURES.clear();
        world = context.getWorld();
        origin = context.getAbsolutePos(new BlockPos(3, 2, 3));
        if (!lookupRegistered) {
            EnergyStorage.SIDED.registerForBlocks((w, pos, state, be, side) ->
                    w == world && origin != null && pos.equals(origin.east()) ? destination : null, Blocks.GOLD_BLOCK);
            lookupRegistered = true;
        }
        try {
            networkTransfers();
            nestedRollback();
            childRollback();
            deepTransactions(false);
            deepTransactions(true);
            siblingTransactions();
            repeatedExtraction();
            sourceBoundaries();
            multipleMembers();
            pushAndPull();
            partialPushRollback();
            delayedClose(false);
            delayedClose(true);
        } finally {
            clearFixture(); world = null; origin = null;
        }
        if (!FAILURES.isEmpty()) throw new AssertionError(String.join("; ", FAILURES));
        System.out.println("ENERGY_AUDIT_COMPLETE " + checks + " checks passed");
        context.complete();
    }

    private static void networkTransfers() {
        var f = fresh();
        world.setBlockState(origin.down(2), RegAddonBlocks.ENERGY_STORAGE_TANK.getDefaultState());
        world.setBlockState(origin.down().south(), RegAddonBlocks.ENERGY_EXTRACTOR.getDefaultState());
        world.setBlockState(origin.down().south(2), RegAddonBlocks.CREATIVE_ENERGY_TANK.getDefaultState());
        var second = (EnergyStorageTankBlockEntity) world.getBlockEntity(origin.down(2));
        var extractor = (EnergyExtractorBlockEntity) world.getBlockEntity(origin.down().south());
        var creative = (EnergyNetworkMember) world.getBlockEntity(origin.down().south(2));
        List<EnergyNetworkMember> network = List.of(creative, extractor, f.tank, second);
        check(EnergyNetwork.collect(world, origin.down().south(2)).size() == 4,
                "creative connector, extractor and both tanks form one network");
        check(EnergyNetwork.getTotalCapacity(network) == 2200,
                "network includes the extractor buffer without counting creative storage");
        f.tank.setStoredEnergy(0); second.setStoredEnergy(0); extractor.setStoredEnergy(0);

        check(EnergyNetwork.insert(network, Integer.MAX_VALUE) == 2200,
                "oversized insertion accepts only the combined storage capacity");
        check(extractor.getStoredEnergy() == 200 && f.tank.getStoredEnergy() == 1000
                && second.getStoredEnergy() == 1000 && creative.getStoredEnergy() == 0,
                "insertion fills the extractor and tanks while leaving the creative connector empty");
        check(EnergyNetwork.insert(network, 1) == 0, "full network accepts no extra energy");
        check(EnergyNetwork.extract(network, Integer.MAX_VALUE) == 2200,
                "oversized extraction returns exactly the stored energy");
        check(EnergyNetwork.getTotalEnergy(network) == 0 && EnergyNetwork.extract(network, 1) == 0,
                "empty network cannot supply energy or underflow");

        check(EnergyNetwork.insert(network, 1201) == 1201, "partial insertion conserves its entire accepted amount");
        check(extractor.getStoredEnergy() == 200 && f.tank.getStoredEnergy() == 501
                && second.getStoredEnergy() == 500,
                "only tanks share the odd remainder; extractor keeps its separate buffer");
        check(EnergyNetwork.extract(network, 201) == 201, "extraction can span the buffer and a tank");
        check(extractor.getStoredEnergy() == 0 && f.tank.getStoredEnergy() == 500
                && second.getStoredEnergy() == 500,
                "extraction preserves member order and equalizes the remaining tank energy");
        check(Math.abs(f.tank.getClientFillRatio() - 1000f / 2200) < 0.000001f
                && f.tank.getClientFillRatio() == second.getClientFillRatio(),
                "successful transfers refresh every tank to the same network fill ratio");

        // Deliberately unequal balances reveal accidental equalization during a rejected request.
        f.tank.setStoredEnergy(17); second.setStoredEnergy(0);
        float previousRatio = f.tank.getClientFillRatio();
        for (int amount : new int[]{0, -1, Integer.MIN_VALUE}) {
            check(EnergyNetwork.insert(network, amount) == 0 && EnergyNetwork.extract(network, amount) == 0,
                    "nonpositive requests transfer no energy: " + amount);
        }
        check(f.tank.getStoredEnergy() == 17 && second.getStoredEnergy() == 0
                && f.tank.getClientFillRatio() == previousRatio,
                "nonpositive requests leave balances and liquid displays untouched");
        check(EnergyNetwork.insert(List.of(), Integer.MAX_VALUE) == 0
                && EnergyNetwork.extract(List.of(), Integer.MAX_VALUE) == 0,
                "empty member lists transfer no energy");
    }

    private static void nestedRollback() {
        var f = fresh();
        try (var outer = Transaction.openOuter()) {
            try (var inner = outer.openNested()) {
                check(f.storage().extract(160, inner) == 160, "nested extraction supplies the requested amount");
                inner.commit();
            }
            check(f.tank.getStoredEnergy() == 990, "child commit remains provisionally debited");
        }
        check(f.tank.getStoredEnergy() == 1000, "outer abort restores committed child energy");
        try (var tx = Transaction.openOuter()) {
            check(f.storage().extract(512, tx) == 512, "outer abort releases the child's budget");
        }
    }

    private static void childRollback() {
        var f = fresh();
        try (var outer = Transaction.openOuter()) {
            try (var inner = outer.openNested()) {
                check(f.storage().extract(160, inner) == 160, "child to abort reserves source energy");
            }
            check(f.tank.getStoredEnergy() == 1000, "child abort independently restores source energy");
            check(f.storage().extract(512, outer) == 512, "child abort immediately releases the full budget");
            outer.commit();
        }
        check(f.tank.getStoredEnergy() == 968, "parent commits only its own subsequent extraction");
    }

    private static void deepTransactions(boolean commit) {
        var f = fresh();
        String label = commit ? "deep commit" : "deep abort";
        try (var outer = Transaction.openOuter()) {
            check(f.storage().extract(128, outer) == 128, label + ": outer reserves 128 E");
            try (var middle = outer.openNested()) {
                check(f.storage().extract(160, middle) == 160, label + ": middle reserves 160 E");
                try (var inner = middle.openNested()) {
                    check(f.storage().extract(224, inner) == 224, label + ": inner reserves remaining 224 E");
                    inner.commit();
                }
                middle.commit();
            }
            check(f.storage().extract(1, outer) == 0, label + ": all levels share one 512 E budget");
            check(f.tank.getStoredEnergy() == 968, label + ": provisional debit is exactly 32 SSCA");
            if (commit) outer.commit();
        }
        check(f.tank.getStoredEnergy() == (commit ? 968 : 1000), label + ": final source balance is exact");
        try (var tx = Transaction.openOuter()) {
            check(f.storage().extract(512, tx) == (commit ? 0 : 512), label + ": final budget settles exactly once");
        }
    }

    private static void siblingTransactions() {
        var f = fresh();
        try (var outer = Transaction.openOuter()) {
            try (var first = outer.openNested()) {
                check(f.storage().extract(128, first) == 128, "first sibling reserves 128 E");
                first.commit();
            }
            try (var cancelled = outer.openNested()) {
                check(f.storage().extract(384, cancelled) == 384, "second sibling reserves the remaining budget");
            }
            check(f.tank.getStoredEnergy() == 992, "cancelled sibling preserves earlier committed provisional debit");
            try (var third = outer.openNested()) {
                check(f.storage().extract(384, third) == 384, "third sibling reuses only the cancelled reservation");
                third.commit();
            }
        }
        check(f.tank.getStoredEnergy() == 1000, "parent abort restores both committed siblings without double refunds");
    }

    private static void repeatedExtraction() {
        var f = fresh();
        try (var tx = Transaction.openOuter()) {
            check(f.storage().extract(512, tx) == 512, "first extraction reserves 512 E immediately");
            check(f.storage().extract(512, tx) == 0, "repeated extraction cannot bypass the uncommitted tick limit");
        }
        check(f.tank.getStoredEnergy() == 1000, "aborted repeated extraction conserves source energy");
        try (var tx = Transaction.openOuter()) {
            check(f.storage().extract(512, tx) == 512, "aborted reservation can be retried in the same tick");
            tx.commit();
        }
        check(f.tank.getStoredEnergy() == 968, "committed 512 E costs exactly 32 SSCA");
        try (var tx = Transaction.openOuter()) {
            check(f.storage().extract(1, tx) == 0, "successful commit retains its tick budget occupation");
        }
    }

    private static void sourceBoundaries() {
        var f = fresh();
        try (var tx = Transaction.openOuter()) {
            check(f.storage().extract(0, tx) == 0, "zero extraction changes no source or budget");
            f.tank.setStoredEnergy(0);
            check(f.storage().extract(160, tx) == 0, "empty source cannot grant energy");
            f.tank.setStoredEnergy(1000);
            check(f.storage().extract(1, tx) == 1, "sub-ratio extraction still supplies one E");
            check(f.tank.getStoredEnergy() == 999, "existing per-extraction integer rounding is preserved");
        }
        check(f.tank.getStoredEnergy() == 1000, "rounded sub-ratio debit is fully refundable");
        world.setBlockState(origin, f.converter.getCachedState().with(EnergyConverterBlock.ACTIVE, false));
        try (var tx = Transaction.openOuter()) {
            check(!f.storage().supportsExtraction() && f.storage().extract(512, tx) == 0, "inactive converter grants no energy");
        }
    }

    private static void multipleMembers() {
        var f = fresh();
        world.setBlockState(origin.down(2), RegAddonBlocks.ENERGY_STORAGE_TANK.getDefaultState());
        var second = (EnergyStorageTankBlockEntity) world.getBlockEntity(origin.down(2));
        f.tank.setStoredEnergy(10); second.setStoredEnergy(100);
        f.converter.markNetworkDirty();
        try (var outer = Transaction.openOuter()) {
            try (var child = outer.openNested()) {
                check(f.storage().extract(512, child) == 512, "one extraction can debit multiple network members");
                child.commit();
            }
            check(f.tank.getStoredEnergy() + second.getStoredEnergy() == 78, "multi-member debit totals exactly 32 SSCA");
        }
        check(f.tank.getStoredEnergy() == 10 && second.getStoredEnergy() == 100,
                "ancestor abort restores every member's original contribution");
    }

    private static void pushAndPull() {
        var f = fresh();
        var target = new SimpleEnergyStorage(10000, 10000, 0);
        destination = target;
        world.setBlockState(origin.east(), Blocks.GOLD_BLOCK.getDefaultState());
        EnergyConverterBlockEntity.tick(world, origin, f.converter.getCachedState(), f.converter);
        check(target.amount == 512 && f.tank.getStoredEnergy() == 968, "push atomically credits 512 E and debits 32 SSCA");
        try (var tx = Transaction.openOuter()) {
            check(f.storage().extract(16, tx) == 0, "push and pull share the same tick budget");
        }
        f = fresh();
        target = new SimpleEnergyStorage(128, 128, 0);
        destination = target;
        world.setBlockState(origin.east(), Blocks.GOLD_BLOCK.getDefaultState());
        EnergyConverterBlockEntity.tick(world, origin, f.converter.getCachedState(), f.converter);
        check(target.amount == 128 && f.tank.getStoredEnergy() == 992, "partially accepting destination pays for its accepted amount");
        try (var tx = Transaction.openOuter()) {
            check(f.storage().extract(512, tx) == 384, "pull can consume exactly the unspent push budget");
            tx.commit();
        }
        check(f.tank.getStoredEnergy() == 968, "combined partial push and pull costs exactly 32 SSCA");
    }

    private static void partialPushRollback() {
        var f = fresh();
        var source = f.storage();
        var target = new SimpleEnergyStorage(10000, 10000, 0) {
            @Override public long insert(long amount, TransactionContext tx) {
                long inserted = super.insert(amount, tx);
                source.extract(16, tx);
                return inserted;
            }
        };
        destination = target;
        world.setBlockState(origin.east(), Blocks.GOLD_BLOCK.getDefaultState());
        EnergyConverterBlockEntity.tick(world, origin, f.converter.getCachedState(), f.converter);
        check(target.amount == 0, "partial source payment rolls back destination credit");
        check(f.tank.getStoredEnergy() == 1000, "partial payment restores every source debit");
        try (var tx = Transaction.openOuter()) {
            check(source.extract(512, tx) == 512, "partial payment restores the full tick budget");
        }
    }

    private static void delayedClose(boolean commit) {
        var f = fresh();
        var properties = world.getServer().getSaveProperties().getMainWorldProperties();
        long originalTime = world.getTime();
        String label = commit ? "delayed commit" : "delayed abort";
        // Simulate a tick boundary synchronously; do not leave a global Fabric transaction open between game ticks.
        try {
            try (var outer = Transaction.openOuter()) {
                check(f.storage().extract(128, outer) == 128, label + ": old tick reserves 128 E");
                properties.setTime(originalTime + 1);
                try (var child = outer.openNested()) {
                    check(f.storage().extract(256, child) == 256, label + ": new tick has its own budget");
                    child.commit();
                }
                if (commit) outer.commit();
            }
            check(f.tank.getStoredEnergy() == (commit ? 976 : 1000), label + ": source balance includes only committed debits");
            try (var tx = Transaction.openOuter()) {
                check(f.storage().extract(Long.MAX_VALUE, tx) == (commit ? 256 : 512),
                        label + ": old callbacks neither inflate nor consume the new tick budget");
            }
        } finally {
            properties.setTime(originalTime);
        }
    }
}
