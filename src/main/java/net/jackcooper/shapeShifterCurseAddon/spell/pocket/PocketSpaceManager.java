package net.jackcooper.shapeShifterCurseAddon.spell.pocket;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class PocketSpaceManager {
	public static final RegistryKey<World> WORLD_KEY = RegistryKey.of(RegistryKeys.WORLD,
			new Identifier("ssc_addon", "pocket_space"));
	private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
	private static final Map<Integer, Generation> GENERATIONS = new HashMap<>();
	private static final Map<UUID, PocketPortalGate> GATES = new HashMap<>();
	/** 房间消除倒计时（槽位 → 剩余 tick）：房内有人时先屏幕中央提示 5 秒再送回。 */
	private static final Map<Integer, Integer> EVICTIONS = new HashMap<>();
	/** 已排队清空的槽位（逐 tick 分批清）。 */
	private static final Map<Integer, Wipe> WIPES = new HashMap<>();
	private static final UUID CHANNEL_SLOW_ID = UUID.fromString("9d17b640-476a-4bdc-a396-8a729fbd5ced");
	/** 卷轴“消失”宽限：连续 5 分钟未在任何在线玩家背包/书内/已加载掉落物中被看到。 */
	private static final long UNSEEN_LIMIT = 6000;
	private static final int EVICTION_TICKS = 100;
	private static boolean generating;

	private PocketSpaceManager() {}

	public static boolean isPocket(World world) {
		return WORLD_KEY.equals(world.getRegistryKey());
	}

	public static boolean canEnter(ServerPlayerEntity player) {
		return player.isAlive() && !player.isSpectator() && !player.hasVehicle() && !player.hasPassengers()
				&& !isPocket(player.getWorld()) && !CHANNELS.containsKey(player.getUuid())
				&& player.getServer().getWorld(WORLD_KEY) != null;
	}

	public static void start(ServerPlayerEntity player, UUID scrollId, int ticks) {
		PocketSpaceStorage.Room room = PocketSpaceStorage.findRoom(player.getServer(), scrollId);
		if (room == null) return;
		CHANNELS.put(player.getUuid(), new Channel(player, scrollId, Math.max(1, ticks)));
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(CHANNEL_SLOW_ID);
			speed.addTemporaryModifier(new EntityAttributeModifier(CHANNEL_SLOW_ID, "Pocket Space Channel",
					PocketChannelRules.SPEED_MULTIPLIER - 1.0, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
		}
		if (!room.initialized()) GENERATIONS.computeIfAbsent(room.layout().index(), ignored -> new Generation(room));
		player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE,
				SoundCategory.PLAYERS, 0.6f, 1.6f);
	}

	public static boolean prepareRoom(ServerPlayerEntity player, UUID scrollId) {
		PocketSpaceStorage.Room room = PocketSpaceStorage.findRoom(player.getServer(), scrollId);
		if (room == null) return false;
		if (!room.initialized()) GENERATIONS.computeIfAbsent(room.layout().index(), ignored -> new Generation(room));
		return room.initialized() && !WIPES.containsKey(room.layout().index());
	}

	public static void enterNow(ServerPlayerEntity player, UUID scrollId) {
		ServerWorld pocket = player.getServer().getWorld(WORLD_KEY);
		PocketSpaceStorage.Room room = PocketSpaceStorage.findRoom(player.getServer(), scrollId);
		if (pocket != null && room != null && canEnter(player) && prepareRoom(player, scrollId)) {
			enter(player, new Channel(player, scrollId, 0), pocket, room);
		}
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(PocketSpaceManager::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (amount > 0 && entity instanceof ServerPlayerEntity player) interrupt(player);
			return true;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			CHANNELS.remove(handler.player.getUuid());
			clearSlow(handler.player);
			GATES.remove(handler.player.getUuid());
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> clearSlow(handler.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			CHANNELS.remove(newPlayer.getUuid());
			clearSlow(oldPlayer);
			clearSlow(newPlayer);
			GATES.remove(newPlayer.getUuid());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server ->
				server.getPlayerManager().getPlayerList().forEach(PocketSpaceManager::clearSlow));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CHANNELS.clear();
			GENERATIONS.clear();
			GATES.clear();
		});
		EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) ->
				isPocket(player.getWorld()) ? PlayerEntity.SleepFailureReason.OTHER_PROBLEM : null);
		EntitySleepEvents.ALLOW_SETTING_SPAWN.register((player, pos) -> !isPocket(player.getWorld()));
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (isPocket(world) && world.getBlockState(hit.getBlockPos()).getBlock() instanceof BedBlock
					&& !player.shouldCancelInteraction()) {
				if (!world.isClient) message(player, "no_sleep");
				return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
	}

	private static void tick(MinecraftServer server) {
		ServerWorld pocket = server.getWorld(WORLD_KEY);
		if (pocket == null) return;
		pocket.setTimeOfDay(6000); // 口袋维度无时间概念：永远正午
		// 口袋维度观察距离压到 3：服务器只为玩家加载周围 7×7 区块（足以覆盖任意等级房间盒），
		// 而非全视距 441 个区块——这是“进入卡很久”的主源。周期重设防全局视距变更把它改回去。
		if (pocket.getTime() % 100 == 0) {
			((net.jackcooper.shapeShifterCurseAddon.mixin.ThreadedAnvilChunkStorageAccessor)
					pocket.getChunkManager().threadedAnvilChunkStorage).sscAddon$setViewDistance(3);
		}
		// census 每 5s 一次（原每 1s）：驱逐宽限 UNSEEN_LIMIT=5min，5s 粒度误差 0.08% 无感，
		// 但全服背包+全维度掉落物扫描量降 80%（空闲服 CPU 收益）
		if (server.getOverworld().getTime() % 100 == 0) census(server, pocket);
		tickEvictions(server, pocket);
		int budget = 4096;
		Iterator<Wipe> wipes = WIPES.values().iterator();
		while (wipes.hasNext() && budget > 0) {
			Wipe wipe = wipes.next();
			budget -= wipe.advance(pocket, budget);
			if (wipe.done()) wipes.remove();
		}
		Iterator<Generation> builds = GENERATIONS.values().iterator();
		while (builds.hasNext() && budget > 0) {
			Generation generation = builds.next();
			budget -= generation.advance(pocket, budget);
			if (generation.done()) {
				generation.room.finishGeneration();
				PocketSpaceStorage.registry(server).markDirty();
				builds.remove();
			}
		}
		Iterator<Map.Entry<UUID, Channel>> channels = CHANNELS.entrySet().iterator();
		while (channels.hasNext()) {
			Map.Entry<UUID, Channel> entry = channels.next();
			Channel channel = entry.getValue();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			PocketSpaceStorage.Room room = PocketSpaceStorage.findRoom(server, channel.scrollId);
			if (player == null || !player.isAlive() || player.hasVehicle() || player.hasPassengers()
					|| player.isSpectator()
					|| !player.getWorld().getRegistryKey().equals(channel.dimension)
					|| room == null) {
				if (player != null) {
					clearSlow(player);
					message(player, "interrupted");
				}
				channels.remove();
				continue;
			}
			if (!room.initialized()) {
				GENERATIONS.computeIfAbsent(room.layout().index(), ignored -> new Generation(room));
			}
			if (channel.ticks % 5 == 0) {
				player.getServerWorld().spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getBodyY(0.7),
						player.getZ(), 5, 0.4, 0.5, 0.4, 0.05);
				message(player, "channel", (Math.max(0, channel.ticks) + 19) / 20);
			}
			if (--channel.ticks <= 0 && room.initialized() && !WIPES.containsKey(room.layout().index())) {
				clearSlow(player);
				enter(player, channel, pocket, room);
				channels.remove();
			}
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!isPocket(player.getWorld())) {
				GATES.remove(player.getUuid());
				continue;
			}
			if (!player.isAlive() || player.isSpectator()) continue;
			PocketSpaceStorage.Visit visit = PocketSpaceStorage.visit(player);
			if (!visit.active()) continue;
			PocketSpaceStorage.Room room = PocketSpaceStorage.findRoom(server, visit.roomId());
			if (room == null) {
				// 房间已消除而玩家仍在维度内（如消除时离线）：直接送回原位置
				leave(player, visit);
				continue;
			}
			if (EVICTIONS.containsKey(room.layout().index())) continue; // 倒计时中，由 tickEvictions 处理
			if (!room.initialized()) {
				GENERATIONS.computeIfAbsent(room.layout().index(), ignored -> new Generation(room));
				continue;
			}
			PocketSpaceLayout layout = room.layout();
			PocketPortalGate gate = GATES.computeIfAbsent(player.getUuid(), ignored -> {
				repairPortal(pocket, layout);
				return new PocketPortalGate();
			});
			BlockPos feet = player.getBlockPos();
			if (!layout.isInterior(feet.getX(), feet.getY(), feet.getZ())) {
				teleportToPortal(player, pocket, layout);
				GATES.put(player.getUuid(), new PocketPortalGate());
				continue;
			}
			boolean insidePortal = player.getX() >= layout.centerX() - 1 && player.getX() < layout.centerX() + 1
					&& player.getZ() >= layout.minZ() - 3 && player.getZ() < layout.minZ() - 1;
			boolean onPortal = insidePortal && Math.abs(player.getY()
					- (PocketSpaceLayout.FLOOR_Y + 1 + PocketSpaceLayout.PORTAL_TOP_HEIGHT)) < 0.08
					&& player.isOnGround();
			if (gate.tick(insidePortal, onPortal)) {
				leave(player, visit);
				GATES.put(player.getUuid(), new PocketPortalGate());
			} else if (gate.counting() && player.age % 20 == 0) {
				message(player, "exit_countdown", gate.remainingSeconds());
			}
			// 出口倒计时期间：传送台法阵冒紫色上升粒子流（仿施法演出，客户端本地渲染）
			if (gate.counting() && player.age % 5 == 0) {
				spawnExitFx(pocket, layout);
			}
		}
	}

	/**
	 * 卷轴存在普查（每秒一次）：在线玩家背包/佩戴魔法书内、以及已加载的掉落物中出现即视为存在并刷新心跳；
	 * 连续超过宽限期未见 → 判定卷轴已物理消失 → 启动房间消除。
	 */
	private static void census(MinecraftServer server, ServerWorld pocket) {
		PocketSpaceStorage.Registry registry = PocketSpaceStorage.registry(server);
		if (registry.rooms().isEmpty()) return;
		long now = server.getOverworld().getTime();
		java.util.Set<UUID> seen = new java.util.HashSet<>();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			for (int i = 0; i < player.getInventory().size(); i++) collectScroll(player.getInventory().getStack(i), seen);
			ItemStack book = net.jackcooper.shapeShifterCurseAddon.spell.SpellCastManager.getEquippedBook(player);
			if (book != null) {
				for (int slot = 0; slot < net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData.getSlotCount(book); slot++) {
					collectScroll(net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData.getScroll(book, slot), seen);
				}
			}
		}
		for (ServerWorld world : server.getWorlds()) {
			for (net.minecraft.entity.Entity entity : world.iterateEntities()) {
				if (entity instanceof net.minecraft.entity.ItemEntity item) collectScroll(item.getStack(), seen);
			}
		}
		for (PocketSpaceStorage.Room room : new java.util.ArrayList<>(registry.rooms())) {
			if (seen.contains(room.scrollId)) {
				room.touch(now);
				registry.markDirty();
			} else if (now - room.lastSeen() > UNSEEN_LIMIT && !EVICTIONS.containsKey(room.layout().index())) {
				beginEviction(server, pocket, room);
			}
		}
	}

	private static void collectScroll(ItemStack stack, java.util.Set<UUID> seen) {
		if (stack.isEmpty()) return;
		net.minecraft.nbt.NbtCompound nbt = stack.getNbt();
		if (nbt != null && nbt.containsUuid(PocketSpaceStorage.SCROLL_ID)) seen.add(nbt.getUuid(PocketSpaceStorage.SCROLL_ID));
		// 卷轴装在别的容器物品里（如魔法书在背包而非佩戴）：递归扫 Items 列表
		if (nbt != null && nbt.contains(net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData.NBT_ITEMS, 9)) {
			net.minecraft.nbt.NbtList list = nbt.getList(net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData.NBT_ITEMS, 10);
			for (int i = 0; i < list.size(); i++) collectScroll(ItemStack.fromNbt(list.getCompound(i)), seen);
		}
	}

	/** 开始消除：房内无人立即清空；有人则屏幕中央提示 5 秒后送回再清空。 */
	private static void beginEviction(MinecraftServer server, ServerWorld pocket, PocketSpaceStorage.Room room) {
		java.util.List<ServerPlayerEntity> inside = playersInRoom(server, room);
		if (inside.isEmpty()) {
			wipe(server, pocket, room);
			return;
		}
		EVICTIONS.put(room.layout().index(), EVICTION_TICKS);
		for (ServerPlayerEntity player : inside) {
			player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(5, 90, 10));
			player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
					Text.translatable("message.ssc_addon.pocket_space.collapse_title")));
			player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
					Text.translatable("message.ssc_addon.pocket_space.collapse_subtitle", EVICTION_TICKS / 20)));
		}
	}

	private static void tickEvictions(MinecraftServer server, ServerWorld pocket) {
		Iterator<Map.Entry<Integer, Integer>> it = EVICTIONS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Integer> entry = it.next();
			PocketSpaceStorage.Room room = PocketSpaceStorage.registry(server).bySlot.get(entry.getKey());
			if (room == null) { it.remove(); continue; }
			int remaining = entry.getValue() - 1;
			if (remaining > 0) {
				entry.setValue(remaining);
				continue;
			}
			for (ServerPlayerEntity player : playersInRoom(server, room)) {
				leave(player, PocketSpaceStorage.visit(player));
			}
			it.remove();
			wipe(server, pocket, room);
		}
	}

	private static java.util.List<ServerPlayerEntity> playersInRoom(MinecraftServer server, PocketSpaceStorage.Room room) {
		java.util.List<ServerPlayerEntity> list = new java.util.ArrayList<>();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (isPocket(player.getWorld())
					&& PocketSpaceLayout.indexAt(player.getBlockX(), player.getBlockZ()) == room.layout().index()) {
				list.add(player);
			}
		}
		return list;
	}

	/** 消除房间：登记先注销（槽位立刻可复用）、方块分批清空、房内非玩家实体移除。 */
	private static void wipe(MinecraftServer server, ServerWorld pocket, PocketSpaceStorage.Room room) {
		int slot = room.layout().index();
		GENERATIONS.remove(slot);
		PocketSpaceStorage.unbind(server, room);
		WIPES.put(slot, new Wipe(slot));
		net.minecraft.util.math.Box box = new net.minecraft.util.math.Box(
				PocketSpaceLayout.slotBoundsMinX(slot), PocketSpaceLayout.FLOOR_Y, PocketSpaceLayout.slotBoundsMinZ(slot),
				PocketSpaceLayout.slotBoundsMaxX(slot) + 1, PocketSpaceLayout.slotBoundsMaxY() + 1, PocketSpaceLayout.slotBoundsMaxZ(slot) + 1);
		for (net.minecraft.entity.Entity entity : pocket.getOtherEntities(null, box)) {
			if (!(entity instanceof ServerPlayerEntity)) entity.discard();
		}
	}

	/**
	 * 逐玩家区块过滤：优先按访问记录解析所在房间（传送前就已写入，不受玩家坐标临时值影响），
	 * 允许范围 = 真实房间外包围盒完全覆盖的整区块（再外扩 1 区块裕量）；查无房间时放行——\t * 宁可多发不可漏发，漏发即永久透明空洞。
	 */
	public static boolean shouldSendChunk(ServerPlayerEntity player, net.minecraft.util.math.ChunkPos chunk) {
		if (!isPocket(player.getWorld())) return true;
		PocketSpaceStorage.Room room = null;
		PocketSpaceStorage.Visit visit = PocketSpaceStorage.visit(player);
		if (visit.active()) room = PocketSpaceStorage.findRoom(player.getServer(), visit.roomId());
		if (room == null) {
			int slot = PocketSpaceLayout.indexAt(player.getBlockX(), player.getBlockZ());
			if (slot >= 0) room = PocketSpaceStorage.registry(player.getServer()).bySlot.get(slot);
		}
		if (room == null) return true;
		PocketSpaceLayout layout = room.layout();
		int minChunkX = Math.floorDiv(layout.boundsMinX() - 16, 16);
		int maxChunkX = (layout.boundsMaxX() + 16) >> 4;
		int minChunkZ = Math.floorDiv(layout.boundsMinZ() - 16, 16);
		int maxChunkZ = (layout.boundsMaxZ() + 16) >> 4;
		return chunk.x >= minChunkX && chunk.x <= maxChunkX && chunk.z >= minChunkZ && chunk.z <= maxChunkZ;
	}

	private static void enter(ServerPlayerEntity player, Channel channel, ServerWorld pocket, PocketSpaceStorage.Room room) {
		PocketSpaceStorage.Visit visit = PocketSpaceStorage.visit(player);
		visit.begin(channel.scrollId, channel.dimension, channel.position, channel.yaw, channel.pitch);
		PocketSpaceStorage.save(player.getServer());
		teleportToPortal(player, pocket, room.layout());
		GATES.put(player.getUuid(), new PocketPortalGate());
		message(player, "arrival");
	}

	private static void repairPortal(ServerWorld pocket, PocketSpaceLayout layout) {
		for (int blockX = layout.centerX() - 1; blockX <= layout.centerX(); blockX++) {
			for (int blockZ = layout.minZ() - 3; blockZ <= layout.minZ() - 2; blockZ++) {
				BlockPos pos = new BlockPos(blockX, PocketSpaceLayout.FLOOR_Y + 1, blockZ);
				BlockState current = pocket.getBlockState(pos);
				if (current.isOf(PocketSpaceBlocks.PORTAL)) {
					BlockState expected = PocketSpaceBlocks.portalState(layout, blockX, blockZ);
					if (current != expected) pocket.setBlockState(pos, expected, Block.NOTIFY_LISTENERS);
					if (pocket.getBlockEntity(pos) == null) {
						pocket.addBlockEntity(new PocketSpaceBlocks.PortalBlockEntity(pos, pocket.getBlockState(pos)));
					}
				}
			}
		}
	}

	private static void teleportToPortal(ServerPlayerEntity player, ServerWorld pocket, PocketSpaceLayout layout) {
		repairPortal(pocket, layout);
		player.teleport(pocket, layout.centerX(), PocketSpaceLayout.FLOOR_Y + 1 + PocketSpaceLayout.PORTAL_TOP_HEIGHT,
				layout.minZ() - 2, 0, 0);
		player.setVelocity(Vec3d.ZERO);
		player.fallDistance = 0;
		pocket.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
				SoundCategory.PLAYERS, 0.7f, 1.2f);
	}

	/**
	 * 出口传送台法阵粒子（2026-09-19 用户定稿）：站上传送台倒计时期间，
	 * 以法阵 2×2 区域为界冒紫色上升粒子流——DRAGON_BREATH（龙息紫，服务端发射
	 * 自带向上飘浮初速，观感与施法头顶流一致）+ PORTAL 传送门紫点缀，
	 * 每 5t 一轮（视觉连续且包量克制），服务端撒天然多人同步。
	 */
	private static void spawnExitFx(ServerWorld pocket, PocketSpaceLayout layout) {
		// 法阵中心（2×2 台面的几何中心）
		double cx = layout.centerX();
		double cz = layout.minZ() - 2.0;
		double cy = PocketSpaceLayout.FLOOR_Y + 1 + PocketSpaceLayout.PORTAL_TOP_HEIGHT + 0.05;
		// 龙息紫上升流：法阵范围内随机起爆（±1 格为界），自带缓升
		pocket.spawnParticles(ParticleTypes.DRAGON_BREATH,
				cx, cy, cz, 14, 0.8, 0.08, 0.8, 0.01);
		// 传送门紫：少量高亮点缀（旋升感）
		pocket.spawnParticles(ParticleTypes.PORTAL,
				cx, cy + 0.1, cz, 6, 0.7, 0.15, 0.7, 0.3);
	}

	private static void leave(ServerPlayerEntity player, PocketSpaceStorage.Visit visit) {
		ServerWorld destination = player.getServer().getWorld(visit.dimension());
		if (destination == null || isPocket(destination)) { message(player, "return_unavailable"); return; }
		Vec3d position = visit.position();
		player.teleport(destination, position.x, position.y, position.z, visit.yaw(), visit.pitch());
		player.setVelocity(Vec3d.ZERO);
		player.fallDistance = 0;
		visit.clear();
		PocketSpaceStorage.save(player.getServer());
		destination.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
				SoundCategory.PLAYERS, 0.7f, 1.0f);
	}

	private static void interrupt(ServerPlayerEntity player) {
		if (CHANNELS.remove(player.getUuid()) != null) {
			clearSlow(player);
			message(player, "interrupted");
		}
	}

	private static void clearSlow(ServerPlayerEntity player) {
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) speed.removeModifier(CHANNEL_SLOW_ID);
	}

	public static void setCancelRefund(ServerPlayerEntity player, Runnable refund) {
		Channel channel = CHANNELS.get(player.getUuid());
		if (channel != null) channel.cancelRefund = refund;
	}

	public static boolean cancelByKey(ServerPlayerEntity player) {
		Channel channel = CHANNELS.remove(player.getUuid());
		if (channel == null) return false;
		clearSlow(player);
		if (channel.cancelRefund != null) channel.cancelRefund.run();
		message(player, "cancelled");
		return true;
	}

	public static void message(PlayerEntity player, String key, Object... args) {
		player.sendMessage(Text.translatable("message.ssc_addon.pocket_space." + key, args), true);
	}

	public static boolean canChangeBlock(ServerWorld world, BlockPos pos) {
		if (generating || !isPocket(world)) return true;
		PocketSpaceStorage.Room room = PocketSpaceStorage.roomAt(world.getServer(), pos.getX(), pos.getZ());
		if (room == null) return false;
		PocketSpaceLayout layout = room.layout();
		return layout.isInterior(pos.getX(), pos.getY(), pos.getZ())
				&& !layout.isAlcove(pos.getX(), pos.getY(), pos.getZ());
	}

	private static final class Channel {
		final UUID scrollId;
		final RegistryKey<World> dimension;
		final Vec3d position;
		final float yaw;
		final float pitch;
		int ticks;
		Runnable cancelRefund;

		Channel(ServerPlayerEntity player, UUID scrollId, int ticks) {
			this.scrollId = scrollId;
			this.dimension = player.getWorld().getRegistryKey();
			this.position = player.getPos();
			this.yaw = player.getYaw();
			this.pitch = player.getPitch();
			this.ticks = ticks;
		}
	}

	/** 分批清空一个槽位的全部方块（按 Lv5 最大包围盒），与生成共用 generating 开关绕过边界保护。 */
	private static final class Wipe {
		final int minX, minZ, width, depth, height;
		int cursor;

		Wipe(int slot) {
			this.minX = PocketSpaceLayout.slotBoundsMinX(slot);
			this.minZ = PocketSpaceLayout.slotBoundsMinZ(slot);
			this.width = PocketSpaceLayout.slotBoundsMaxX(slot) - minX + 1;
			this.depth = PocketSpaceLayout.slotBoundsMaxZ(slot) - minZ + 1;
			this.height = PocketSpaceLayout.slotBoundsMaxY() - PocketSpaceLayout.FLOOR_Y + 1;
		}

		boolean done() { return cursor >= width * depth * height; }

		int advance(ServerWorld world, int budget) {
			int used = 0;
			generating = true;
			try {
				BlockState air = net.minecraft.block.Blocks.AIR.getDefaultState();
				while (!done() && used < budget) {
					int blockX = minX + cursor % width;
					int blockZ = minZ + cursor / width % depth;
					int blockY = PocketSpaceLayout.FLOOR_Y + cursor / (width * depth);
					cursor++;
					used++;
					BlockPos pos = new BlockPos(blockX, blockY, blockZ);
					if (world.getBlockState(pos).isAir()) continue;
					net.minecraft.util.Clearable.clear(world.getBlockEntity(pos)); // 容器内容不掉落，直接抹除
					world.setBlockState(pos, air, Block.NOTIFY_LISTENERS | Block.SKIP_DROPS | Block.FORCE_STATE);
				}
			} finally {
				generating = false;
			}
			return used;
		}
	}

	private static final class Generation {
		final PocketSpaceStorage.Room room;
		/** 先清空槽位最大包围盒（覆盖旧世界残留/换级旧块），再生成外壳。 */
		final int cleanMinX, cleanMinZ, cleanW, cleanD, cleanH;
		int cleanCursor;
		int cursor;

		Generation(PocketSpaceStorage.Room room) {
			this.room = room;
			int slot = room.layout().index();
			this.cleanMinX = PocketSpaceLayout.slotBoundsMinX(slot);
			this.cleanMinZ = PocketSpaceLayout.slotBoundsMinZ(slot);
			this.cleanW = PocketSpaceLayout.slotBoundsMaxX(slot) - cleanMinX + 1;
			this.cleanD = PocketSpaceLayout.slotBoundsMaxZ(slot) - cleanMinZ + 1;
			this.cleanH = PocketSpaceLayout.slotBoundsMaxY() - PocketSpaceLayout.FLOOR_Y + 1;
		}

		boolean done() {
			int width = room.layout().size() + 2;
			return cleanCursor >= cleanW * cleanD * cleanH
					&& cursor >= width * width * (width + 4);
		}

		int advance(ServerWorld world, int budget) {
			PocketSpaceLayout layout = room.layout();
			int width = layout.size() + 2;
			int depth = width + 4;
			int used = 0;
			generating = true;
			try {
				BlockState air = net.minecraft.block.Blocks.AIR.getDefaultState();
				while (cleanCursor < cleanW * cleanD * cleanH && used < budget) {
					int blockX = cleanMinX + cleanCursor % cleanW;
					int blockZ = cleanMinZ + cleanCursor / cleanW % cleanD;
					int blockY = PocketSpaceLayout.FLOOR_Y + cleanCursor / (cleanW * cleanD);
					cleanCursor++;
					used++;
					BlockPos pos = new BlockPos(blockX, blockY, blockZ);
					if (world.getBlockState(pos).isAir()) continue;
					net.minecraft.util.Clearable.clear(world.getBlockEntity(pos));
					world.setBlockState(pos, air, Block.NOTIFY_LISTENERS | Block.SKIP_DROPS | Block.FORCE_STATE);
				}
				while (!done() && used < budget) {
					int blockX = layout.minX() - 1 + cursor % width;
					int blockZ = layout.minZ() - 5 + cursor / width % depth;
					int blockY = PocketSpaceLayout.FLOOR_Y + cursor / (width * depth);
					cursor++;
					used++;
					BlockState state = null;
					if (layout.isPortal(blockX, blockY, blockZ)) state = PocketSpaceBlocks.portalState(layout, blockX, blockZ);
					else if (layout.isShell(blockX, blockY, blockZ)) {
						state = blockY == PocketSpaceLayout.FLOOR_Y ? PocketSpaceBlocks.FLOOR.getDefaultState()
								: PocketSpaceBlocks.BEDROCK_WALL.getDefaultState();
					}
					if (state != null) world.setBlockState(new BlockPos(blockX, blockY, blockZ), state, Block.NOTIFY_LISTENERS);
				}
			} finally {
				generating = false;
			}
			return used;
		}
	}
}