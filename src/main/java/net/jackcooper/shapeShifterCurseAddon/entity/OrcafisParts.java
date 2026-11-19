package net.jackcooper.shapeShifterCurseAddon.entity;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

final class OrcafisParts {
    private final OrcafisPartEntity[] parts = new OrcafisPartEntity[OrcafisCollisionRig.Section.values().length];

    void tick(OrcafisEntity boss) {
        if (boss.belowGround()) {
            remove();
            return;
        }
        ServerWorld world = (ServerWorld)boss.getWorld();
        Box[] boxes = boss.bodyPose().boxes();
        for (int index = 0; index < boxes.length; index++) {
            Box box = boxes[index];
            if (box == null) continue;
            OrcafisPartEntity part = parts[index];
            if (part != null && !part.isRemoved()) {
                part.align(box);
            } else {
                BlockPos block = BlockPos.ofFloored(box.getCenter());
                if (!world.isChunkLoaded(block.getX() >> 4,block.getZ() >> 4)) continue;
                part = RegAddonEntities.ORCAFIS_PART.create(world);
                if (part == null) continue;
                part.attach(boss);
                part.align(box);
                if (world.spawnEntity(part)) parts[index] = part;
            }
        }
    }

    void remove() {
        for (int index = 0; index < parts.length; index++) {
            if (parts[index] != null) parts[index].discard();
            parts[index] = null;
        }
    }
}
