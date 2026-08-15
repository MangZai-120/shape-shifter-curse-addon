package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;
import net.jackcooper.shapeShifterCurseAddon.effect.RegAddonEffects;

import java.util.List;

/**
 * 月织蛛被动·月毒侵蚀（B2）：月织蛛玩家周围 8 格内、被「蜘网缠身」或「裹茧」状态困住的生物，
 * 持续被施加中毒 I（每隔一段时间刷新，保证覆盖不断档）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>服务端权威：扫描服务端实体、服务端 addStatusEffect，客户端由原版同步，客机正确。</li>
 *   <li>扫描频率 {@link #SCAN_INTERVAL}（每 60t 扫一次），中毒时长 {@link #POISON_DURATION}（80t）
 *       &gt; 扫描间隔，保证被网困住期间中毒持续不中断。</li>
 *   <li>白名单：默认白名单（白名单内玩家/宠物不受影响）；蜘蛛类、悦灵系因 {@code isBoundImmune}
 *       根本不会被上蛛网状态，故天然免疫，此处无需额外判断。</li>
 *   <li>性能：限制扫描半径 {@link #SCAN_RADIUS} + 每 60t 才扫一次。</li>
 * </ul>
 */
public final class SpiderMoonWeaverMoonPoisonManager {

    /** 扫描半径（格）：对齐蛛丝弹 AURA_RADIUS。 */
    private static final double SCAN_RADIUS = 8.0;
    /** 扫描间隔（tick）：每 3 秒扫一次。 */
    private static final int SCAN_INTERVAL = 60;
    /** 施加中毒 I 的持续时长（tick）：4 秒，大于扫描间隔保证不断档。 */
    private static final int POISON_DURATION = 80;

    private SpiderMoonWeaverMoonPoisonManager() {}

    /**
     * 每服务端 tick 对一个月织蛛玩家执行（由 {@code SscAddonServerEvents} 的玩家循环调用）。
     * 按 {@link #SCAN_INTERVAL} 节流扫描周围受困生物并施加中毒 I。
     */
    public static void tick(ServerPlayerEntity player) {
        if (!FormUtils.isForm(player, FormIdentifiers.SPIDER_MOON_WEAVER)) return;
        if (player.age % SCAN_INTERVAL != 0) return;
        if (!(player.getWorld() instanceof ServerWorld world)) return;

        Box box = new Box(player.getBlockPos()).expand(SCAN_RADIUS);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box, e -> true);
        for (LivingEntity target : targets) {
            // 排除施法者本人
            if (target.getUuid().equals(player.getUuid())) continue;
            // 白名单：默认白名单（白名单内玩家/宠物不受影响）
            if (WhitelistUtils.isProtected(player, target)) continue;
            // 仅对带有「蜘网缠身」或「裹茧」状态的生物生效
            boolean webBound = target.getStatusEffect(RegAddonEffects.SPIDER_WEB_BOUND) != null;
            boolean cocooned = target.getStatusEffect(
                    net.onixary.shapeShifterCurseFabric.status_effects.RegOtherStatusEffects.ENTANGLED_FULL_EFFECT) != null;
            if (!webBound && !cocooned) continue;
            // 施加中毒 I（不显示粒子环境效果，显示粒子以让玩家可见）；带施法者 source 供入梦拦截归因
            target.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.POISON, POISON_DURATION, 0, false, true, true), player);
        }
    }
}
