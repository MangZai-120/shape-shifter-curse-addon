package net.jackcooper.shapeShifterCurseAddon.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionRegistry;
import net.jackcooper.shapeShifterCurseAddon.network.SscAddonNetworking;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;

import java.util.List;

/**
 * 灵能宝珠：仅进化形态（数据驱动 —— 某进化路线的起点形态）可用。
 *
 * <p>右键长按后打开「选择形态」界面（复用 {@code SscaFormSelectScreen} 转职模式），玩家选定一个进化形态确认后
 * 触发正常变身动画流程转职到该形态。当前所处形态在界面里灰显不可选。</p>
 *
 * <p>转职需 ≥ {@code JOB_CHANGE_COST}(=3) 个进化加点点数，倒退 3 个里程碑档；具体校验 / 扣点 / 变身 / 消耗道具
 * 全在服务端 {@code EvolutionManager.startJobChange}（防作弊）。本道具只负责「长按打开界面」这一步。</p>
 */
public class PsionicOrbItem extends Item {

    public PsionicOrbItem(Settings settings) {
        super(settings);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 32;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        user.setCurrentHand(hand);
        return TypedActionResult.consume(user.getStackInHand(hand));
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            PlayerFormBase currentForm = FormUtils.getCurrentForm(player);
            Identifier formId = currentForm != null ? currentForm.FormID : null;
            // 仅进化形态（某进化路线的起点形态）可用；数据驱动，未来新增进化形态自动接入
            if (formId != null && EvolutionRegistry.INSTANCE.getRouteByStartForm(formId) != null) {
                // 服务端发 S2C 包让客户端打开转职选择界面（此处不消耗道具，玩家确认转职成功时才消耗）
                SscAddonNetworking.sendOpenJobChange(player);
            } else {
                player.sendMessage(Text.translatable("message.ssc_addon.job_change.fail.not_evolution").formatted(Formatting.RED), true);
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0F, 1.0F);
            }
        }
        return stack;
    }

    @Override
    public void appendTooltip(ItemStack stack, @org.jetbrains.annotations.Nullable World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        String key = "item.ssc_addon.psionic_orb.tooltip";
        if (I18n.hasTranslation(key)) {
            for (String line : I18n.translate(key).split("\n")) {
                tooltip.add(Text.literal(line).formatted(Formatting.GRAY));
            }
        } else {
            tooltip.add(Text.translatable(key).formatted(Formatting.GRAY));
        }
    }
}
