package net.jackcooper.shapeShifterCurseAddon.block;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.EnumMap;

final class HorizontalBlockShapes {
	private HorizontalBlockShapes() {}

	static EnumMap<Direction, VoxelShape> fromNorth(VoxelShape north) {
		var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
		shapes.put(Direction.NORTH, north);
		VoxelShape shape = north;
		for (Direction facing = Direction.EAST; facing != Direction.NORTH; facing = facing.rotateYClockwise()) {
			VoxelShape rotated = VoxelShapes.empty();
			for (Box box : shape.getBoundingBoxes()) {
				rotated = VoxelShapes.union(rotated, VoxelShapes.cuboid(
						1 - box.maxZ, box.minY, box.minX, 1 - box.minZ, box.maxY, box.maxX));
			}
			shapes.put(facing, rotated);
			shape = rotated;
		}
		return shapes;
	}
}
