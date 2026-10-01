package net.jackcooper.shapeShifterCurseAddon.spell.pocket;

import com.google.gson.JsonParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** 口袋空间自检（pocketSpaceTest 任务运行；不打进发行 jar）。 */
public final class PocketSpaceLayoutTest {
	public static void main(String[] args) throws Exception {
		for (int level = 1; level <= 5; level++) {
			PocketSpaceLayout layout = new PocketSpaceLayout(7, level);
			int interiorCount = 0;
			int portalCount = 0;
			for (int blockX = layout.minX() - 1; blockX <= layout.minX() + layout.size(); blockX++) {
				for (int blockY = PocketSpaceLayout.FLOOR_Y; blockY <= PocketSpaceLayout.FLOOR_Y + layout.size() + 1; blockY++) {
					for (int blockZ = layout.minZ() - 5; blockZ <= layout.minZ() + layout.size(); blockZ++) {
						if (layout.isInterior(blockX, blockY, blockZ)) interiorCount++;
						if (layout.isPortal(blockX, blockY, blockZ)) portalCount++;
						check(!(layout.isInterior(blockX, blockY, blockZ) && layout.isShell(blockX, blockY, blockZ)));
						// 房间全部方块（含外壳/凹槽）都必须落在同一槽位包围盒内，供区块过滤与清空使用
						check(PocketSpaceLayout.indexAt(blockX, blockZ) == layout.index());
					}
				}
			}
			check(layout.size() == 16 + (level - 1) * 6);
			check(interiorCount == layout.size() * layout.size() * layout.size() + 64);
			check(portalCount == 4);
			check(layout.portalRotation(layout.centerX() - 1, layout.minZ() - 3) == 0);
			check(layout.portalRotation(layout.centerX(), layout.minZ() - 3) == 90);
			check(layout.portalRotation(layout.centerX(), layout.minZ() - 2) == 180);
			check(layout.portalRotation(layout.centerX() - 1, layout.minZ() - 2) == 270);
			check(layout.centerX() * 2 == layout.minX() * 2 + layout.size());
			// 真实包围盒必须完全覆盖实际生成的全部方块（外壳/凹槽/顶盖），区块过滤按它算
			check(layout.boundsMinX() <= layout.minX() - 1 && layout.boundsMaxX() >= layout.minX() + layout.size());
			check(layout.boundsMinZ() <= layout.minZ() - 5 && layout.boundsMaxZ() >= layout.minZ() + layout.size());
			check(layout.boundsMaxY() >= PocketSpaceLayout.FLOOR_Y + layout.size() + 1);
			// 真实包围盒自身也必须完整落在同一槽位内（跨槽会污染相邻房间的区块过滤）
			check(PocketSpaceLayout.indexAt(layout.boundsMinX(), layout.boundsMinZ()) == layout.index());
			check(PocketSpaceLayout.indexAt(layout.boundsMaxX(), layout.boundsMaxZ()) == layout.index());
			check(layout.isInterior(layout.centerX(), 65, layout.minZ() - 1));
			check(!layout.isShell(layout.centerX(), 65, layout.minZ() - 1));
			check(layout.isShell(layout.centerX(), 69, layout.minZ() - 1));
			check(layout.isShell(layout.centerX(), 64, layout.minZ() - 3));
		}
		// 网格：index 0 = 原点、512 间距、同一高度；螺旋编号与坐标反查互逆；相邻槽位净距 ≥ 466
		PocketSpaceLayout origin = new PocketSpaceLayout(0, 5);
		check(origin.centerX() == 0 && origin.minZ() + origin.size() / 2 == 0);
		for (int index = 0; index < 5000; index++) {
			int[] grid = PocketSpaceLayout.spiral(index);
			check(PocketSpaceLayout.indexAt(grid[0] * PocketSpaceLayout.SPACING, grid[1] * PocketSpaceLayout.SPACING) == index);
			int ringNow = Math.max(Math.abs(grid[0]), Math.abs(grid[1]));
			int[] prev = PocketSpaceLayout.spiral(Math.max(0, index - 1));
			check(ringNow >= Math.max(Math.abs(prev[0]), Math.abs(prev[1]))); // 编号越大离原点越远（不会先远后近）
		}
		java.util.Set<Long> seenGrid = new java.util.HashSet<>();
		for (int index = 0; index < 5000; index++) {
			int[] grid = PocketSpaceLayout.spiral(index);
			check(seenGrid.add(((long) grid[0] << 32) ^ (grid[1] & 0xffffffffL))); // 无两个槽位共坐标
		}
		check(PocketSpaceLayout.SPACING - 2 * (20 + 1) - 4 >= 256);
		check(PocketSpaceLayout.slotBoundsMaxY() == PocketSpaceLayout.FLOOR_Y + 41);
		PocketSpaceLayout last = new PocketSpaceLayout(PocketSpaceLayout.MAX_ROOMS - 1, 5);
		check(Math.abs(last.minX()) < 29_999_984 && Math.abs(last.minZ()) < 29_999_984);
		PocketPortalGate gate = new PocketPortalGate();
		for (int tick = 0; tick < 120; tick++) check(!gate.tick(true, false));
		for (int tick = 0; tick < 120; tick++) check(!gate.tick(true, true));
		check(!gate.tick(true, false));
		for (int tick = 0; tick < 120; tick++) check(!gate.tick(true, true));
		check(!gate.tick(false, false));
		for (int tick = 0; tick < 59; tick++) check(!gate.tick(true, true));
		check(gate.tick(true, true));
		check(!gate.tick(false, false));
		for (int tick = 0; tick < 30; tick++) check(!gate.tick(true, true));
		check(!gate.tick(false, false));
		for (int tick = 0; tick < 30; tick++) check(!gate.tick(true, true));
		check(!gate.tick(true, false));
		for (int tick = 0; tick < 59; tick++) check(!gate.tick(true, true));
		check(gate.tick(true, true));
		System.out.println("Pocket space layout checks passed (5 levels, alcoves, portals, isolation).");
		System.out.println("Portal checks passed (arrival lock, landing, jumping, 60 ticks, interrupted countdown).");
		check(Math.abs(PocketChannelRules.SPEED_MULTIPLIER - 0.2) < 1e-9); // 引导期减速 80%
		check(PocketChannelRules.refundCooldownEnd(1100, 2200, 1200) == 1960);
		check(PocketChannelRules.refundCooldownEnd(1100, 3400, 2400) == 2920);
		check(PocketChannelRules.refundCooldownEnd(1100, 1150, 1200) == 1100);
		check(PocketChannelRules.refundCooldownEnd(1100, 1100, 0) == 1100);
		System.out.println("Channel checks passed (80% slow, 20% original cooldown refund, elapsed time, zero clamp).");
		UUID roomId = UUID.randomUUID();
		NbtCompound scrollTag = new NbtCompound();
		scrollTag.putUuid(PocketSpaceStorage.SCROLL_ID, roomId);
		check(bytes(scrollTag) < 64);
		// 登记表往返：两个房间、槽位 0/1、心跳与已建标记；重复槽位条目被丢弃；损坏条目跳过
		PocketSpaceStorage.Registry registry = new PocketSpaceStorage.Registry();
		NbtCompound saved = registry.writeNbt(new NbtCompound());
		net.minecraft.nbt.NbtList rooms = new net.minecraft.nbt.NbtList();
		for (int slot = 0; slot < 2; slot++) {
			NbtCompound tag = new NbtCompound();
			tag.putUuid("Scroll", UUID.randomUUID());
			tag.putInt("Slot", slot);
			tag.putInt("Level", slot + 1);
			tag.putLong("LastSeen", 1234L + slot);
			tag.putBoolean("Built", slot == 0);
			rooms.add(tag);
		}
		NbtCompound duplicate = rooms.getCompound(1).copy();
		duplicate.putUuid("Scroll", UUID.randomUUID());
		rooms.add(duplicate);
		NbtCompound broken = new NbtCompound();
		broken.putUuid("Scroll", UUID.randomUUID());
		broken.putInt("Slot", -1);
		broken.putInt("Level", 9);
		rooms.add(broken);
		saved.put("Rooms", rooms);
		PocketSpaceStorage.Registry restored = PocketSpaceStorage.Registry.read(saved);
		check(restored.rooms().size() == 2);
		check(bytes(restored.writeNbt(new NbtCompound())) < 256);
		PocketSpaceStorage.Room first = restored.rooms().stream().filter(r -> r.layout().index() == 0).findFirst().orElseThrow();
		check(first.initialized() && first.lastSeen() == 1234L && first.layout().level() == 1);
		first.touch(1000L);
		check(first.lastSeen() == 1234L); // 心跳只前进不后退
		first.touch(5000L);
		check(first.lastSeen() == 5000L);
		try (InputStreamReader reader = new InputStreamReader(PocketSpaceLayoutTest.class.getResourceAsStream(
				"/data/ssc_addon/dimension_type/pocket_space.json"), StandardCharsets.UTF_8)) {
			var dimension = JsonParser.parseReader(reader).getAsJsonObject();
			check(dimension.get("bed_works").getAsBoolean());
			check(dimension.get("height").getAsInt() == 256);
			check(dimension.get("min_y").getAsInt() == 0);
		}
		try (InputStreamReader reader = new InputStreamReader(PocketSpaceLayoutTest.class.getResourceAsStream(
				"/data/ssc_addon/dimension/pocket_space.json"), StandardCharsets.UTF_8)) {
			var dimension = JsonParser.parseReader(reader).getAsJsonObject();
			check(dimension.get("type").getAsString().equals("ssc_addon:pocket_space"));
			var generator = dimension.getAsJsonObject("generator");
			check(generator.get("type").getAsString().equals("minecraft:flat"));
			check(generator.getAsJsonObject("settings").getAsJsonArray("layers").isEmpty());
		}
		System.out.println("NBT checks passed: scroll binding=" + bytes(scrollTag)
				+ " bytes; registry(2 rooms)=" + bytes(restored.writeNbt(new NbtCompound())) + " bytes.");
		System.out.println("Grid checks passed (origin slot 0, 512 spacing, spiral<->coords, no shared slots, >=256 gap).");
		System.out.println("Dimension JSON structure checks passed (runtime loading requires Fabric).");
		try (InputStreamReader reader = new InputStreamReader(PocketSpaceLayoutTest.class.getResourceAsStream(
				"/assets/ssc_addon/blockstates/pocket_space_portal.json"), StandardCharsets.UTF_8)) {
			var variants = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("variants");
			String[] directions = {"north", "east", "south", "west"};
			check(variants.size() == 4);
			for (int turn = 0; turn < directions.length; turn++) {
				var variant = variants.getAsJsonObject("facing=" + directions[turn]);
				check(variant.get("y").getAsInt() == turn * 90);
				check(variant.get("model").getAsString().equals("ssc_addon:block/pocket_space_portal"));
				check(!variant.has("uvlock") || !variant.get("uvlock").getAsBoolean());
			}
		}
		var top = javax.imageio.ImageIO.read(PocketSpaceLayoutTest.class.getResourceAsStream(
				"/assets/ssc_addon/textures/block/pocket_space_portal_top.png"));
		try (InputStreamReader reader = new InputStreamReader(PocketSpaceLayoutTest.class.getResourceAsStream(
				"/assets/ssc_addon/models/block/pocket_space_portal.json"), StandardCharsets.UTF_8)) {
			var elements = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("elements");
			check(elements.size() == 2);
			var lower = elements.get(0).getAsJsonObject();
			var upper = elements.get(1).getAsJsonObject();
			check(lower.getAsJsonArray("to").get(1).getAsDouble() == PocketSpaceLayout.PORTAL_BASE_HEIGHT * 16);
			check(upper.getAsJsonArray("from").get(1).getAsDouble() == PocketSpaceLayout.PORTAL_BASE_HEIGHT * 16);
			check(upper.getAsJsonArray("from").get(0).getAsDouble() == PocketSpaceLayout.PORTAL_INSET * 16);
			check(upper.getAsJsonArray("from").get(2).getAsDouble() == PocketSpaceLayout.PORTAL_INSET * 16);
			check(upper.getAsJsonArray("to").get(1).getAsDouble() == PocketSpaceLayout.PORTAL_TOP_HEIGHT * 16);
			check(upper.getAsJsonArray("to").get(0).getAsDouble() == 16);
			check(upper.getAsJsonArray("to").get(2).getAsDouble() == 16);
			var upperUv = upper.getAsJsonObject("faces").getAsJsonObject("up").getAsJsonArray("uv");
			check(upperUv.get(0).getAsInt() == 2 && upperUv.get(1).getAsInt() == 2);
			check(upperUv.get(2).getAsInt() == 16 && upperUv.get(3).getAsInt() == 16);
		}
		var emission = javax.imageio.ImageIO.read(PocketSpaceLayoutTest.class.getResourceAsStream(
				"/assets/ssc_addon/textures/block/pocket_space_portal_emission.png"));
		var side = javax.imageio.ImageIO.read(PocketSpaceLayoutTest.class.getResourceAsStream(
				"/assets/ssc_addon/textures/block/pocket_space_portal_side.png"));
		check(top.getWidth() == 16 && top.getHeight() == 16);
		check(side.getWidth() == 16 && side.getHeight() == 16);
		check((side.getRGB(4, 4) & 255) > (top.getRGB(4, 4) & 255));
		check(emission.getWidth() == top.getWidth() && emission.getHeight() == top.getHeight());
		int transparent = 0;
		int purple = 0;
		for (int row = 0; row < emission.getHeight(); row++) {
			for (int column = 0; column < emission.getWidth(); column++) {
				int color = emission.getRGB(column, row);
				int alpha = color >>> 24;
				if (alpha == 0) transparent++;
				if (alpha >= 200) {
					int red = color >> 16 & 255;
					int green = color >> 8 & 255;
					int blue = color & 255;
					check(blue > green && red > green);
					check(color == top.getRGB(column, row));
					purple++;
				}
				check(top.getRGB(column, row) >>> 24 == 255);
			}
		}
		int pixelCount = emission.getWidth() * emission.getHeight();
		check(transparent > pixelCount / 2 && purple > pixelCount / 32);
		int[][] assembled = new int[32][32];
		for (int quadrantZ = 0; quadrantZ < 2; quadrantZ++) {
			for (int quadrantX = 0; quadrantX < 2; quadrantX++) {
				int turns = origin.portalRotation(origin.centerX() - 1 + quadrantX, origin.minZ() - 3 + quadrantZ) / 90;
				int innerX = 16;
				int innerZ = 16;
				for (int turn = 0; turn < turns; turn++) {
					int previousX = innerX;
					innerX = 16 - innerZ;
					innerZ = previousX;
				}
				check(quadrantX * 16 + innerX == 16 && quadrantZ * 16 + innerZ == 16);
				for (int pixelZ = 0; pixelZ < 16; pixelZ++) {
					for (int pixelX = 0; pixelX < 16; pixelX++) {
						int rotatedX = pixelX;
						int rotatedZ = pixelZ;
						for (int turn = 0; turn < turns; turn++) {
							int previousX = rotatedX;
							rotatedX = 15 - rotatedZ;
							rotatedZ = previousX;
						}
						assembled[quadrantZ * 16 + rotatedZ][quadrantX * 16 + rotatedX] =
								pixelX < 2 || pixelZ < 2 ? side.getRGB(pixelX, pixelZ) : top.getRGB(pixelX, pixelZ);
					}
				}
			}
		}
		for (int pixelZ = 0; pixelZ < 32; pixelZ++) {
			for (int pixelX = 0; pixelX < 32; pixelX++) {
				check(assembled[pixelZ][pixelX] == assembled[31 - pixelZ][31 - pixelX]);
				check(assembled[pixelZ][pixelX] == assembled[pixelX][31 - pixelZ]);
			}
		}
		check(Math.abs(PocketSpaceLayout.PORTAL_BASE_HEIGHT - PocketSpaceLayout.PORTAL_TOP_HEIGHT) < 0.08);
		check(PocketSpaceLayout.PORTAL_TOP_HEIGHT > 0.08);
		System.out.println("Portal assembly checks passed (4 inward corners, rotational symmetry, two-level landing heights).");
		System.out.println("Portal material checks passed (4 rotated variants, opaque stone, transparent purple emission).");
	}

	private static int bytes(NbtCompound nbt) throws Exception {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (DataOutputStream output = new DataOutputStream(buffer)) {
			NbtIo.write(nbt, output);
		}
		return buffer.size();
	}

	private static void check(boolean condition) {
		if (!condition) throw new AssertionError("Pocket space check failed");
	}
}
