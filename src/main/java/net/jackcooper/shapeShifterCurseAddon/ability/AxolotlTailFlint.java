package net.jackcooper.shapeShifterCurseAddon.ability;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.event.GameEvent;
import net.onixary.shapeShifterCurseFabric.event.SSCEvent;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;

public final class AxolotlTailFlint implements AutoSyncedComponent {
    public static final ComponentKey<AxolotlTailFlint> STATE = ComponentRegistry.getOrCreate(
            new Identifier("ssc_addon", "axolotl_tail_flint"), AxolotlTailFlint.class);
    public static final int PANIC_TICKS = 30;
    private boolean mounted;
    private boolean selfIgnited;
    private long panicUntil;

    // The equipment API, screen slots and hand-swap packet all write this same one-slot list.
    // Inventory hydration / respawn copies happen before the new player joins the player manager.
    public static DefaultedList<ItemStack> observeOffhand(DefaultedList<ItemStack> slots, PlayerEntity owner) {
        if (!(owner instanceof ServerPlayerEntity player)) return slots;
        return new DefaultedList<ItemStack>(slots, ItemStack.EMPTY) {
            @Override
            public ItemStack set(int index, ItemStack stack) {
                ItemStack previous = super.set(index, stack);
                if (stack != previous && (stack.isOf(Items.FLINT_AND_STEEL) || previous.isOf(Items.FLINT_AND_STEEL))
                        && player.getServer().getPlayerManager().getPlayer(player.getUuid()) == player) {
                    AxolotlTailFlint state = STATE.get(player);
                    boolean onTail = stack.isOf(Items.FLINT_AND_STEEL) && isAxolotl(FormUtils.getCurrentForm(player))
                            && player.getRandom().nextInt(20) == 0;
                    if (onTail != state.mounted) {
                        state.mounted = onTail;
                        STATE.sync(player);
                    }
                }
                return previous;
            }
        };
    }

    private static boolean isAxolotl(IForm form) {
        if (form == null) return false;
        if (form == RegPlayerForms.AXOLOTL_0 || form == RegPlayerForms.AXOLOTL_1
                || form == RegPlayerForms.AXOLOTL_2 || form == RegPlayerForms.AXOLOTL_3) return true;
        Identifier id = form.getFormID();
        return FormIdentifiers.UPGRADE_AXOLOTL.equals(id) || FormIdentifiers.AXOLOTL_SP.equals(id)
                || FormIdentifiers.AXOLOTL_FLUORESCENT.equals(id) || FormIdentifiers.AXOLOTL_ALING.equals(id);
    }

    public static void register() {
        SSCEvent.FORM_CHANGE_END.register((player, before, after) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
            AxolotlTailFlint state = STATE.get(player);
            if ((state.mounted || state.panicUntil > player.getWorld().getTime()) && !isAxolotl(after)) {
                state.mounted = false;
                state.panicUntil = 0;
                STATE.sync(serverPlayer);
            }
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> use(player, hand));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> use(player, hand));
        UseItemCallback.EVENT.register((player, world, hand) ->
                new TypedActionResult<>(use(player, hand), player.getStackInHand(hand)));
    }

    public static boolean onTail(PlayerEntity player) {
        return STATE.get(player).mounted && player.getOffHandStack().isOf(Items.FLINT_AND_STEEL);
    }

    public float panicRemaining(PlayerEntity player, float delta) {
        if (panicUntil == 0) return 0;
        return Math.max(0, Math.min(PANIC_TICKS, panicUntil - player.getWorld().getTime() - delta));
    }

    public boolean suppressesExtinguish() {
        return selfIgnited;
    }

    public void endSelfIgnition(ServerPlayerEntity player) {
        if (selfIgnited) {
            selfIgnited = false;
            STATE.sync(player);
        }
    }

    private static ActionResult use(PlayerEntity player, Hand hand) {
        if (hand != Hand.OFF_HAND || !onTail(player) || player.isSpectator()) return ActionResult.PASS;
        if (player instanceof ServerPlayerEntity serverPlayer) {
            AxolotlTailFlint state = STATE.get(player);
            long until = player.getWorld().getTime() + PANIC_TICKS;
            if (state.panicUntil != until) {
                state.panicUntil = until;
                player.setOnFireFor(3);
                state.selfIgnited = player.getFireTicks() > 0;
                player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_FLINTANDSTEEL_USE,
                        player.getSoundCategory(), 1.0F, 1.0F);
                player.emitGameEvent(GameEvent.ITEM_INTERACT_FINISH);
                player.incrementStat(Stats.USED.getOrCreateStat(Items.FLINT_AND_STEEL));
                player.getOffHandStack().damage(1, player, p -> p.sendToolBreakStatus(hand));
                if (player.getOffHandStack().isEmpty()) state.mounted = false;
                STATE.sync(serverPlayer);
            }
        }
        return ActionResult.success(player.getWorld().isClient);
    }

    @Override
    public void readFromNbt(NbtCompound nbt) {
        mounted = nbt.getBoolean("Mounted");
        selfIgnited = nbt.getBoolean("SelfIgnited");
        panicUntil = nbt.getLong("PanicUntil");
    }

    @Override
    public void writeToNbt(NbtCompound nbt) {
        if (mounted) nbt.putBoolean("Mounted", true);
        if (selfIgnited) nbt.putBoolean("SelfIgnited", true);
        if (panicUntil != 0) nbt.putLong("PanicUntil", panicUntil);
    }
}
