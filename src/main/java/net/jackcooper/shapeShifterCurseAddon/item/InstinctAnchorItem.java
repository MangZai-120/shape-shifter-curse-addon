package net.jackcooper.shapeShifterCurseAddon.item;

import java.util.List;
import java.util.Locale;
import net.jackcooper.shapeShifterCurseAddon.util.InstinctAnchors;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** 定形锚与解锚符共用入口，扣除和反馈只在服务端执行一次。 */
public final class InstinctAnchorItem extends Item {
    private final boolean fix;

    public InstinctAnchorItem(Settings settings, boolean fix) { super(settings); this.fix = fix; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);
        if (!(user instanceof ServerPlayerEntity player)) return TypedActionResult.fail(stack);
        InstinctAnchors.Result result = InstinctAnchors.apply(player, fix);
        boolean success = result == InstinctAnchors.Result.FIXED || result == InstinctAnchors.Result.RELEASED;
        player.sendMessage(Text.translatable("message.ssc_addon.instinct_anchor." + result.name().toLowerCase(Locale.ROOT))
                .formatted(success ? Formatting.AQUA : Formatting.YELLOW), true);
        if (!success) return TypedActionResult.fail(stack);
        if (!player.getAbilities().creativeMode) stack.decrement(1);
        player.incrementStat(Stats.USED.getOrCreateStat(this));
        player.getItemCooldownManager().set(this, 10);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                fix ? SoundEvents.BLOCK_CHAIN_PLACE : SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, .7f, 1.2f);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable(getTranslationKey() + ".tip").formatted(Formatting.GRAY));
        if (fix) {
            tooltip.add(Text.translatable("item.ssc_addon.instinct_anchor.lifetime_tip").formatted(Formatting.GRAY));
            tooltip.add(Text.translatable("item.ssc_addon.instinct_anchor.condition_tip").formatted(Formatting.DARK_GRAY));
        } else {
            tooltip.add(Text.translatable("item.ssc_addon.instinct_release.native_tip").formatted(Formatting.DARK_GRAY));
        }
        tooltip.add(Text.translatable("item.ssc_addon.instinct_anchor.moon_tip").formatted(Formatting.DARK_PURPLE));
    }
}
