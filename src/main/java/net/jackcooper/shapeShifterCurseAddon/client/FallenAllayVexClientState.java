package net.jackcooper.shapeShifterCurseAddon.client;

/**
 * 堕落悦灵「召唤恼鬼」召唤物存活窗口的客户端镜像。
 *
 * <p>服务端 {@code FallenAllayVexSkill} 经 S2C（PACKET_FALLEN_ALLAY_VEX_WINDOW）在
 * 「召唤成功（VEX_LIFE_TICKS）/ 全部恼鬼消失（0）」节点推送窗口时长；
 * 技能 HUD 主技能辅助栏据此自满格倒数，显示「召唤物什么时候消失」，
 * 恼鬼提前全部死亡则立即归零。判定在服务端，客户端仅渲染。
 */
public final class FallenAllayVexClientState {
    /** 窗口截止 tick（客户端锚）；Long.MIN_VALUE = 未开启。 */
    private static volatile long windowEndTick = Long.MIN_VALUE;
    /** 窗口总长（收到包时的服务端权威值，作倒数分母）。 */
    private static volatile int windowTicks = 0;

    private FallenAllayVexClientState() {
    }

    /** 窗口开启（>0，anchorTick 为收到时的客户端 tick）或关闭（0）。 */
    public static void update(int ticks, long anchorTick) {
        windowTicks = Math.max(0, ticks);
        windowEndTick = ticks > 0 ? anchorTick + ticks : Long.MIN_VALUE;
    }

    public static void clear() {
        windowEndTick = Long.MIN_VALUE;
        windowTicks = 0;
    }

    /** 窗口剩余占比（自满格倒数 1→0）；未开启返回 -1（辅助栏不显示）。 */
    public static double remainingFraction(long nowTick) {
        long end = windowEndTick;
        if (end == Long.MIN_VALUE || windowTicks <= 0) return -1;
        double remain = Math.max(0, end - nowTick);
        return Math.min(1.0, remain / windowTicks);
    }
}
