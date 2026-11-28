package net.jackcooper.shapeShifterCurseAddon.ability;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.VariableIntPower;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.jackcooper.shapeShifterCurseAddon.power.BeaconActivePower;
import net.jackcooper.shapeShifterCurseAddon.sound.AddonSoundRange;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-owned portable beacon: one payment clock and a whitelisted Haste II aura per owner. */
public final class AllaySPPortableBeacon {
    private static final Identifier ACTIVE = new Identifier("ssc_addon", "form_allay_sp_beacon_active");
    private static final Identifier MANA = new Identifier("ssc_addon", "form_allay_sp_mana_resource");
    private static final String SESSION_TAG = "ssca_beacon_session";
    private static final String OWNER_TAG = "ssca_beacon_owner";
    private static final int DRAIN_INTERVAL = 60;
    private static final int EFFECT_INTERVAL = 80;
    private static final double RANGE = 20.0;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static Set<UUID> flagsToCheck = new HashSet<>();
    private static final Set<UUID> RETAINED_FLAG_OWNERS = new HashSet<>();
    private static boolean pollOverriddenFlag;
    // Cumulative active ticks belong to the owner, so toggling or swapping beacons cannot reset payment.
    private static final Map<UUID, Integer> DRAIN_TICKS = new HashMap<>();

    private static final class Session {
        final UUID id = UUID.randomUUID();
        ItemStack stack;
        int ticks;

        Session(ItemStack stack) { this.stack = stack; }
    }

    private AllaySPPortableBeacon() { }

    public static void init() {
        UseItemCallback.EVENT.register(AllaySPPortableBeacon::onUseItem);
        ServerLifecycleEvents.SERVER_STARTED.register(AllaySPPortableBeacon::checkFlagDefinition);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
            if (success) checkFlagDefinition(server);
        });
        ServerTickEvents.END_SERVER_TICK.register(AllaySPPortableBeacon::tickPlayers);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> clearPlayer(handler.player));
        net.jackcooper.shapeShifterCurseAddon.event.PlayerSessionCleanup.register((handler, server) -> {
            RETAINED_FLAG_OWNERS.remove(handler.player.getUuid());
            clearPlayer(handler.player);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) clearPlayer(player);
            SESSIONS.clear();
            flagsToCheck.clear();
            RETAINED_FLAG_OWNERS.clear();
            DRAIN_TICKS.clear();
        });
    }

    private static void checkFlagDefinition(MinecraftServer server) {
        // Data packs may replace this power with a plain Apoli resource that cannot notify us.
        RETAINED_FLAG_OWNERS.clear();
        pollOverriddenFlag = false;
        if (!PowerTypeRegistry.contains(ACTIVE)) return;
        var definition = PowerTypeRegistry.get(ACTIVE).getFactory().getFactory().getSerializerId();
        pollOverriddenFlag = !BeaconActivePower.FACTORY_ID.equals(definition);
        if (!pollOverriddenFlag) {
            // Apoli can retain the previous power instance on an online player after a reload.
            for (var player : server.getPlayerManager().getPlayerList()) RETAINED_FLAG_OWNERS.add(player.getUuid());
        }
    }

    private static boolean hasRetainedFlag(ServerPlayerEntity player) {
        if (!RETAINED_FLAG_OWNERS.contains(player.getUuid())) return false;
        var flag = PowerHolderComponent.KEY.get(player).getPower(PowerTypeRegistry.get(ACTIVE));
        if (flag instanceof VariableIntPower && !(flag instanceof BeaconActivePower)) return true;
        RETAINED_FLAG_OWNERS.remove(player.getUuid());
        return false;
    }

    public static void flagRaised(ServerPlayerEntity player) {
        if (!SESSIONS.containsKey(player.getUuid())) flagsToCheck.add(player.getUuid());
    }

    private static void tickPlayers(MinecraftServer server) {
        if (SESSIONS.isEmpty() && flagsToCheck.isEmpty() && RETAINED_FLAG_OWNERS.isEmpty() && !pollOverriddenFlag) return;
        Set<UUID> raised = flagsToCheck;
        // Writes during someone's tick must still be checked if their owner already took a turn.
        if (!raised.isEmpty()) flagsToCheck = new HashSet<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID owner = player.getUuid();
            if (pollOverriddenFlag || SESSIONS.containsKey(owner) || raised.contains(owner) || hasRetainedFlag(player)) tick(player);
        }
    }

    private static TypedActionResult<ItemStack> onUseItem(PlayerEntity player, World world, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (stack.isOf(Items.BEACON) && !player.isSneaking()) {
            if (player instanceof ServerPlayerEntity serverPlayer && PowerUtils.isSpAllay(serverPlayer)) {
                toggleBeacon(serverPlayer, stack);
                return TypedActionResult.success(stack);
            }
        }
        return TypedActionResult.pass(stack);
    }

    public static void toggleBeacon(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (!stack.isOf(Items.BEACON)) stack = player.getOffHandStack();
        toggleBeacon(player, stack);
    }

    private static void toggleBeacon(ServerPlayerEntity player, ItemStack stack) {
        if (PowerUtils.getResourceValue(player, ACTIVE) == 1 || SESSIONS.containsKey(player.getUuid())) {
            deactivateBeacon(player);
            return;
        }
        if (!PowerUtils.isSpAllay(player) || !stack.isOf(Items.BEACON)) return;
        if (PowerUtils.getResourceValue(player, MANA) <= 0) {
            player.sendMessage(Text.translatable("message.ssc_addon.beacon.no_energy"), true);
            return;
        }
        clearMarkers(player);
        Session session = new Session(stack);
        stack.getOrCreateNbt().putUuid(SESSION_TAG, session.id);
        stack.getOrCreateNbt().putUuid(OWNER_TAG, player.getUuid());
        SESSIONS.put(player.getUuid(), session);
        PowerUtils.setResourceValueAndSync(player, ACTIVE, 1);
        player.getInventory().markDirty();
        applyAura(player);
        AddonSoundRange.play(player, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.sendMessage(Text.translatable("message.ssc_addon.beacon.activated"), true);
    }

    private static void tick(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null) {
            // An old resource flag can survive without a live session.
            if (PowerUtils.getResourceValue(player, ACTIVE) != 0) clearPlayer(player);
            return;
        }
        ItemStack carried = findCarried(player, session);
        if (!player.isAlive() || !PowerUtils.isSpAllay(player) || carried.isEmpty()
                || PowerUtils.getResourceValue(player, ACTIVE) != 1
                || PowerUtils.getResourceValue(player, MANA) <= 0) {
            deactivateBeacon(player);
            return;
        }
        if (session.stack != carried) {
            clearSessionStack(player.getUuid(), session);
            session.stack = carried;
        }
        session.ticks++;
        int drainTicks = DRAIN_TICKS.getOrDefault(player.getUuid(), 0) + 1;
        if (drainTicks >= DRAIN_INTERVAL) {
            DRAIN_TICKS.remove(player.getUuid());
            PowerUtils.changeResourceValueAndSync(player, MANA, -1);
            if (PowerUtils.getResourceValue(player, MANA) <= 0) {
                deactivateBeacon(player);
                return;
            }
        } else DRAIN_TICKS.put(player.getUuid(), drainTicks);
        if (session.ticks % EFFECT_INTERVAL == 0) applyAura(player);
    }

    private static ItemStack findCarried(ServerPlayerEntity player, Session session) {
        PlayerInventory inventory = player.getInventory();
        ItemStack stack = findCarried(inventory.main, player.getUuid(), session);
        if (!stack.isEmpty()) return stack;
        stack = findCarried(inventory.armor, player.getUuid(), session);
        return !stack.isEmpty() ? stack : findCarried(inventory.offHand, player.getUuid(), session);
    }

    private static ItemStack findCarried(java.util.List<ItemStack> stacks, UUID owner, Session session) {
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            if (belongsToSession(stack, owner, session)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void applyAura(ServerPlayerEntity player) {
        player.addStatusEffect(haste());
        for (LivingEntity target : player.getServerWorld().getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(RANGE), target -> target != player && target.isAlive()
                        && !target.isSpectator() && target.squaredDistanceTo(player) <= RANGE * RANGE
                        && WhitelistUtils.isBuffTarget(player, target))) {
            target.addStatusEffect(haste());
        }
    }

    private static StatusEffectInstance haste() {
        return new StatusEffectInstance(StatusEffects.HASTE, 100, 1, false, true, true);
    }

    public static void deactivateBeacon(ServerPlayerEntity player) {
        boolean wasActive = SESSIONS.containsKey(player.getUuid()) || PowerUtils.getResourceValue(player, ACTIVE) == 1;
        clearPlayer(player);
        if (wasActive) {
            AddonSoundRange.play(player, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            player.sendMessage(Text.translatable("message.ssc_addon.beacon.deactivated"), true);
        }
    }

    private static void clearPlayer(ServerPlayerEntity player) {
        Session session = SESSIONS.remove(player.getUuid());
        if (session != null) clearSessionStack(player.getUuid(), session);
        PowerUtils.setResourceValueAndSync(player, ACTIVE, 0);
        clearMarkers(player);
        if (player.currentScreenHandler != null) clearStack(player.currentScreenHandler.getCursorStack());
        player.getInventory().markDirty();
    }

    private static void clearMarkers(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        // Keep all 41 slots and their main -> armor -> offhand ordering, without
        // allocating the combinedInventory iterator for every getStack call.
        boolean changed = clearMarkers(inventory.main);
        changed |= clearMarkers(inventory.armor);
        changed |= clearMarkers(inventory.offHand);
        if (changed) inventory.markDirty();
    }

    private static boolean clearMarkers(java.util.List<ItemStack> stacks) {
        boolean changed = false;
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            if (isActiveStack(stack)) { clearStack(stack); changed = true; }
        }
        return changed;
    }

    public static boolean isActiveStack(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(Items.BEACON) && stack.hasNbt()
                && stack.getNbt().containsUuid(SESSION_TAG) && stack.getNbt().containsUuid(OWNER_TAG);
    }

    private static boolean belongsToSession(ItemStack stack, UUID owner, Session session) {
        return isActiveStack(stack) && stack.getNbt().getUuid(OWNER_TAG).equals(owner)
                && stack.getNbt().getUuid(SESSION_TAG).equals(session.id);
    }

    private static void clearSessionStack(UUID owner, Session session) {
        // The same stack may already have been transferred and activated by its new owner.
        if (belongsToSession(session.stack, owner, session)) clearStack(session.stack);
    }

    /** Called by the beacon's ordinary inventory tick, including armor and offhand slots. */
    public static void clearStaleCarriedMarker(ServerPlayerEntity holder, ItemStack stack) {
        if (!isActiveStack(stack)) return;
        Session session = SESSIONS.get(holder.getUuid());
        if (session != null && belongsToSession(stack, holder.getUuid(), session)) return;
        clearStack(stack);
        holder.getInventory().markDirty();
    }

    /** Clear only our visual marker; real enchantments and unrelated NBT remain intact. */
    public static void clearStack(ItemStack stack) {
        if (stack.isOf(Items.BEACON) && stack.hasNbt()) {
            stack.getNbt().remove(SESSION_TAG);
            stack.getNbt().remove(OWNER_TAG);
            if (stack.getNbt().isEmpty()) stack.setNbt(null);
        }
    }
}
