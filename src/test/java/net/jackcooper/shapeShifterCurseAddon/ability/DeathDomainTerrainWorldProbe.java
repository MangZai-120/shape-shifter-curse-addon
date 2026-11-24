package net.jackcooper.shapeShifterCurseAddon.ability;

import io.github.apace100.apoli.Apoli;
import io.github.apace100.apoli.util.ApoliConfig;
import net.jackcooper.shapeShifterCurseAddon.spell.NeoFormCastingProbe;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;

import java.util.Map;
import java.util.UUID;

public final class DeathDomainTerrainWorldProbe {
    private enum Ending { DISCONNECT, RELOAD, RETRACTION }

    public static void start(TestContext test) {
        test.waitAndRun(70, () -> {
            try {
                if (Apoli.config == null) Apoli.config = new ApoliConfig();
                var origin = test.getAbsolutePos(new BlockPos(8, 48, 8));
                exercise(test, origin, Ending.DISCONNECT);
                exercise(test, origin.east(36), Ending.RELOAD);
                exercise(test, origin.east(72), Ending.RETRACTION);
                sealed(test, origin.south(48));
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
    }

    private static void exercise(TestContext test, BlockPos center, Ending ending) throws Exception {
        var world = test.getWorld();
        var caster = NeoFormCastingProbe.player(test, FormIdentifiers.ANUBIS_WOLF_SP, 0);
        FormUtils.setForm(caster, RegPlayerForms.playerForms.get(FormIdentifiers.ANUBIS_WOLF_SP));
        AnubisWolfSpSoulEnergy.setEnergy(caster, 0);
        for (var p : BlockPos.iterate(center.add(-7,-1,-7), center.add(7,-1,7))) world.setBlockState(p, Blocks.STONE.getDefaultState());
        var cake = center.east(4);
        var rod = center.south(4);
        world.setBlockState(cake, Blocks.CAKE.getDefaultState());
        world.setBlockState(rod, Blocks.END_ROD.getDefaultState());
        for (var p : BlockPos.iterate(cake.add(-2,2,-2), cake.add(2,2,2))) world.setBlockState(p, Blocks.BEDROCK.getDefaultState());
        var skipped = center.west(3);
        world.setBlockState(skipped, Blocks.CAKE.getDefaultState());
        var at = Vec3d.ofBottomCenter(center);
        caster.refreshPositionAndAngles(at.x,at.y,at.z,0,0);
        check(AnubisWolfSpDeathDomain.execute(caster), "domain accepted");
        for (int tick=0;tick<69;tick++) AnubisWolfSpDeathDomain.tick(caster);
        check(world.getBlockState(center.down()).isOf(Blocks.SOUL_SAND) && world.getBlockState(cake).isAir()
                && world.getBlockState(rod).isAir(), "domain actually converted test terrain");
        caster.refreshPositionAndAngles(at.x,at.y-0.125,at.z,0,0);
        var trapped = test.createMockCreativeServerPlayerInWorld();
        var start = Vec3d.ofBottomCenter(cake);
        trapped.refreshPositionAndAngles(start.x,start.y,start.z,0,0);
        var large = mob(world, EntityType.RAVAGER, Vec3d.ofBottomCenter(rod).add(-0.85,0,0));
        var largeStart = large.getPos();
        check(!large.getBlockPos().equals(rod), "large mob's feet are outside the restored block");
        world.setBlockState(skipped, Blocks.BEDROCK.getDefaultState());
        var bystander = mob(world, EntityType.PIG, Vec3d.ofBottomCenter(skipped));
        var clear = mob(world, EntityType.PIG, Vec3d.ofBottomCenter(center.north(3)));
        var bystanderStart = bystander.getPos();
        var clearStart = clear.getPos();
        switch (ending) {
            case DISCONNECT -> world.getServer().getPlayerManager().remove(caster);
            case RELOAD -> AnubisWolfSpDeathDomain.forceRestoreAll(true);
            case RETRACTION -> {
                for (int tick = 0; tick < 600; tick++) AnubisWolfSpDeathDomain.tick(caster);
            }
        }
        check(world.getBlockState(cake).isOf(Blocks.CAKE) && world.getBlockState(rod).isOf(Blocks.END_ROD)
                && world.getBlockState(center.down()).isOf(Blocks.STONE), "original terrain restored");
        check(caster.getPos().squaredDistanceTo(at)<1e-8, "caster rises only the missing eighth of a block");
        check(Math.abs(trapped.getY()-start.y)<1e-6 && trapped.getPos().distanceTo(start)<1.01
                && world.isSpaceEmpty(trapped,trapped.getBoundingBox()), "player moved sideways below low ceiling");
        check(large.getPos().distanceTo(largeStart)>0 && world.isSpaceEmpty(large,large.getBoundingBox()),
                "large mob's whole body clears restored rod");
        check(bystander.getPos().equals(bystanderStart) && world.getBlockState(skipped).isOf(Blocks.BEDROCK),
                "unrelated collision in a player-edited block was preserved");
        check(clear.getPos().equals(clearStart), "unobstructed mob stayed put");
        if (ending == Ending.DISCONNECT) {
            var file = world.getServer().getSavePath(WorldSavePath.PLAYERDATA).resolve(caster.getUuid()+".dat");
            var pos = NbtIo.readCompressed(file.toFile()).getList("Pos", NbtElement.DOUBLE_TYPE);
            check(Math.abs(pos.getDouble(1)-at.y)<1e-7, "vanilla logout saved the corrected height");
        }
        System.out.println("TERRAIN_RESTORE_PATH "+ending+" passed");
    }

    private static void sealed(TestContext test, BlockPos cell) {
        var world = test.getWorld();
        for (var p : BlockPos.iterate(cell.add(-10,-10,-10), cell.add(10,11,10)))
            world.setBlockState(p, Blocks.BEDROCK.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(cell.up(), Blocks.AIR.getDefaultState());
        world.setBlockState(cell, Blocks.AIR.getDefaultState());
        var id = UUID.randomUUID();
        var change = new DeathDomainRecovery.Change(Blocks.STONE.getDefaultState(), Blocks.AIR.getDefaultState());
        check(DeathDomainRecovery.record(world,id,Map.of(cell,change)), "sealed-space recovery logged");
        var occupant = mob(world, EntityType.COW, Vec3d.ofBottomCenter(cell));
        DeathDomainRecovery.finished(world,id);
        check(world.getBlockState(cell).isAir() && world.isSpaceEmpty(occupant,occupant.getBoundingBox()),
                "sealed room keeps occupied block pending");
        occupant.discard();
        test.waitAndRun(24, () -> {
            check(world.getBlockState(cell).isOf(Blocks.STONE), "deferred original restored after occupant left");
            System.out.println("DEATH_DOMAIN_TERRAIN_PASS");
            test.complete();
        });
    }

    private static <T extends MobEntity> T mob(ServerWorld world, EntityType<T> type, Vec3d at) {
        var entity = type.create(world);
        entity.setAiDisabled(true);
        entity.setPersistent();
        entity.refreshPositionAndAngles(at.x,at.y,at.z,0,0);
        check(world.spawnEntity(entity),"spawn "+type);
        return entity;
    }

    private static void check(boolean ok,String message) {
        if (!ok) throw new AssertionError(message);
        System.out.println("TERRAIN_RESTORE_CHECK "+message);
    }
}
