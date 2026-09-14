package net.jackcooper.shapeShifterCurseAddon.spell.pocket;

/**
 * 口袋空间布局：网格从 (0,0) 起、每 512 格一个槽位、方形螺旋向外编号（越靠近原点编号越小），
 * 全部房间同一水平高度（地面 Y=64），永不垂直叠放。
 */
public record PocketSpaceLayout(int index, int level) {
	public static final int FLOOR_Y = 64;
	public static final int SPACING = 512;
	/** 螺旋最大圈数（±1024 圈），槽位数 = (2*1024+1)^2。 */
	private static final int MAX_RING = 1024;
	public static final int MAX_ROOMS = (2 * MAX_RING + 1) * (2 * MAX_RING + 1);

	public PocketSpaceLayout {
		if (index < 0 || index >= MAX_ROOMS || level < 1 || level > 5) {
			throw new IllegalArgumentException("Invalid pocket space layout");
		}
	}

	/** 螺旋编号 → 网格坐标 {列, 行}。index 0 = (0,0)，之后按圈向外。 */
	static int[] spiral(int index) {
		if (index == 0) return new int[] {0, 0};
		int ring = (int) Math.ceil((Math.sqrt(index + 1) - 1) / 2);
		int ringStart = (2 * ring - 1) * (2 * ring - 1);
		int offset = index - ringStart;
		int side = 2 * ring;
		int leg = offset / side;
		int step = offset % side;
		return switch (leg) {
			case 0 -> new int[] {ring, -ring + 1 + step};
			case 1 -> new int[] {ring - 1 - step, ring};
			case 2 -> new int[] {-ring, ring - 1 - step};
			default -> new int[] {-ring + 1 + step, -ring};
		};
	}

	/** 世界坐标 → 所在网格槽位编号（超出螺旋范围返回 -1）。 */
	public static int indexAt(int blockX, int blockZ) {
		int column = Math.floorDiv(blockX + SPACING / 2, SPACING);
		int row = Math.floorDiv(blockZ + SPACING / 2, SPACING);
		int ring = Math.max(Math.abs(column), Math.abs(row));
		if (ring > MAX_RING) return -1;
		if (ring == 0) return 0;
		int ringStart = (2 * ring - 1) * (2 * ring - 1);
		int side = 2 * ring;
		int offset;
		if (column == ring && row > -ring) offset = row + ring - 1;
		else if (row == ring) offset = side + (ring - 1 - column);
		else if (column == -ring) offset = 2 * side + (ring - 1 - row);
		else offset = 3 * side + (column + ring - 1);
		return ringStart + offset;
	}

	public int size() {
		return 16 + (level - 1) * 6;
	}

	/** 房间内部起点 X：网格点为房间中心。 */
	public int minX() {
		return spiral(index)[0] * SPACING - size() / 2;
	}

	public int minZ() {
		return spiral(index)[1] * SPACING - size() / 2;
	}

	public int centerX() {
		return minX() + size() / 2;
	}

	public boolean isInterior(int blockX, int blockY, int blockZ) {
		int height = blockY - FLOOR_Y - 1;
		boolean room = blockX >= minX() && blockX < minX() + size()
				&& blockZ >= minZ() && blockZ < minZ() + size()
				&& height >= 0 && height < size();
		boolean alcove = blockX >= centerX() - 2 && blockX < centerX() + 2
				&& blockZ >= minZ() - 4 && blockZ < minZ()
				&& height >= 0 && height < 4;
		return room || alcove;
	}

	public boolean isShell(int blockX, int blockY, int blockZ) {
		return !isInterior(blockX, blockY, blockZ)
				&& (isInterior(blockX - 1, blockY, blockZ) || isInterior(blockX + 1, blockY, blockZ)
				|| isInterior(blockX, blockY - 1, blockZ) || isInterior(blockX, blockY + 1, blockZ)
				|| isInterior(blockX, blockY, blockZ - 1) || isInterior(blockX, blockY, blockZ + 1));
	}

	public boolean isPortal(int blockX, int blockY, int blockZ) {
		return blockY == FLOOR_Y + 1 && blockX >= centerX() - 1 && blockX < centerX() + 1
				&& blockZ >= minZ() - 3 && blockZ < minZ() - 1;
	}

	public boolean isAlcove(int blockX, int blockY, int blockZ) {
		return blockZ < minZ() && isInterior(blockX, blockY, blockZ);
	}

	/** 真实外包围盒（按等级）：完全覆盖外壳 + 凹槽 + 顶盖的实际生成范围，用于区块过滤。 */
	public int boundsMinX() { return minX() - 1; }
	public int boundsMaxX() { return minX() + size(); }
	public int boundsMinZ() { return minZ() - 5; }
	public int boundsMaxZ() { return minZ() + size(); }
	public int boundsMaxY() { return FLOOR_Y + size() + 1; }

	/** 该槽位按 Lv5 尺寸算的最大外包围盒（含外壳与凹槽）：用于消除时清空与逐玩家区块过滤。 */
	public static int slotBoundsMinX(int index) { return spiral(index)[0] * SPACING - 20 - 1; }
	public static int slotBoundsMinZ(int index) { return spiral(index)[1] * SPACING - 20 - 5; }
	public static int slotBoundsMaxX(int index) { return spiral(index)[0] * SPACING + 20; }
	public static int slotBoundsMaxZ(int index) { return spiral(index)[1] * SPACING + 20; }
	public static int slotBoundsMaxY() { return FLOOR_Y + 40 + 1; }
}