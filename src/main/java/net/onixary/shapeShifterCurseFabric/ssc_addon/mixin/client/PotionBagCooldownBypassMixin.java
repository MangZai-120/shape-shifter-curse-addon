package net.onixary.shapeShifterCurseFabric.ssc_addon.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.onixary.shapeShifterCurseFabric.ssc_addon.item.PotionBagItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 绕过服务端冷却拦截，让药水袋在 ItemCooldownManager 冷却期间仍可潜行右键开包。
 * <p>
 * 根因：右键交互的冷却拦截在<b>服务端</b> {@code ServerPlayerInteractionManager.interactItem}
 * 中——它在调用 {@code stack.use()} <b>之前</b>检查
 * {@code player.getItemCooldownManager().isCoolingDown(stack.getItem())} 并直接返回 PASS，
 * 导致 {@code use()} 不被调用、开包逻辑无法触发（客户端 interactItem / doItemUse 均无此检查，
 * 之前误把 mixin 打到客户端类导致 "Scanned 0 target(s)" 注入失败而崩溃）。
 * <p>
 * 修复：当物品为 {@link PotionBagItem} 且玩家潜行时，让该冷却检查返回 false（放行），
 * 服务端 {@code use()} 先判断潜行 → 开包。白色遮罩仍由 ItemCooldownManager 正常渲染。
 * <p>
 * 注意：必须注册到 mixins.json 的 <b>common</b>（"mixins"）列表，使其在专用服务器与单人
 * 集成服务器上都生效，保证主客机一致（本类虽在 client 子包，仅为目录组织，与运行环境无关）。
 */
@Mixin(ServerPlayerInteractionManager.class)
public class PotionBagCooldownBypassMixin {

    /**
     * 拦截 {@code interactItem} 内的 {@code isCoolingDown(Item)} 调用（用 @WrapOperation 兼容其它 mod 的同位注入）。
     * 接收者 (manager, item) 之后用 {@code Operation} 承接原调用，{@code @Local} 取目标方法的服务端玩家参数读取潜行状态。
     */
    @WrapOperation(
            method = "interactItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/ItemCooldownManager;isCoolingDown(Lnet/minecraft/item/Item;)Z")
    )
    private boolean ssc_addon$bypassCooldownForSneaking(ItemCooldownManager manager, Item item,
            Operation<Boolean> original, @Local(argsOnly = true) ServerPlayerEntity player) {
        if (item instanceof PotionBagItem && player.isSneaking()) {
            // 潜行开包：绕过冷却检查，让服务端 use() 正常触发
            return false;
        }
        // 非潜行或非药水袋：保持原版冷却行为
        return original.call(manager, item);
    }
}