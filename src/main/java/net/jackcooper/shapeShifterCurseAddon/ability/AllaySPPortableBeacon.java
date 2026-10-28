package net.jackcooper.shapeShifterCurseAddon.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.jackcooper.shapeShifterCurseAddon.util.PowerUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-owned portable beacon: one payment clock and a whitelisted Haste II aura per owner. */
public final class AllaySPPortableBeacon {
    private static final Identifier ACTIVE = new Identifier("my_addon", "form_allay_sp_beacon_active");
    private static final Identifier MANA = new Identifier("my_addon", "form_allay_sp_mana_resource");
    private static final String SESSION_TAG = "ssca_beacon_session";
    private static final String OWNER_TAG = "ssca_beacon_owner";
    private static final int DRAIN_INTERVAL = 60;
    private static final int EFFECT_INTERVAL = 80;
    private static final double RANGE = 20.0;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
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
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) tick(player);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> clearPlayer(handler.player));
        net.jackcooper.shapeShifterCurseAddon.event.PlayerSessionCleanup.register((handler, server) -> clearPlayer(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) clearPlayer(player);
            SESSIONS.clear();
            DRAIN_TICKS.clear();
        });
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
        player.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.sendMessage(Text.translatable("message.ssc_addon.beacon.activated"), true);
    }

    private static void tick(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null) {
            // Old saved flags and transferred marked stacks never activate a recipient implicitly.
            if (PowerUtils.getResourceValue(player, ACTIVE) != 0) clearPlayer(player);
            else clearMarkers(player);
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
            clearStack(session.stack);
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
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (isActiveStack(stack) && stack.getNbt().getUuid(OWNER_TAG).equals(player.getUuid())
                    && stack.getNbt().getUuid(SESSION_TAG).equals(session.id)) return stack;
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
            player.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            player.sendMessage(Text.translatable("message.ssc_addon.beacon.deactivated"), true);
        }
    }

    private static void clearPlayer(ServerPlayerEntity player) {
        Session session = SESSIONS.remove(player.getUuid());
        if (session != null) clearStack(session.stack);
        PowerUtils.setResourceValueAndSync(player, ACTIVE, 0);
        clearMarkers(player);
        if (player.currentScreenHandler != null) clearStack(player.currentScreenHandler.getCursorStack());
        player.getInventory().markDirty();
    }

    private static void clearMarkers(ServerPlayerEntity player) {
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (isActiveStack(stack)) { clearStack(stack); changed = true; }
        }
        if (changed) player.getInventory().markDirty();
    }

    public static boolean isActiveStack(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(Items.BEACON) && stack.hasNbt()
                && stack.getNbt().containsUuid(SESSION_TAG) && stack.getNbt().containsUuid(OWNER_TAG);
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
