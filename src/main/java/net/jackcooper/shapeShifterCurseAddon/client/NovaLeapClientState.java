package net.jackcooper.shapeShifterCurseAddon.client;

/**
 * 朔望「灵跃闪身」第 2 段窗口的客户端镜像。
 *
 * <p>服务端 {@code NovaSkillManager} 经 S2C（PACKET_NOVA_LEAP_WINDOW）在「第 1 段成功 /
 * 第 2 段用掉 / 窗口过期」三个节点推送窗口时长（>0 = 开启倒数锚，0 = 关闭）；
 * 技能 HUD 次技能辅助栏据此自满格倒数，显示「第 2 次灵跃的过期时间」。
 * 判定在服务端，客户端仅渲染。
 */
public final class NovaLeapClientState {
    /** 窗口截止 tick（客户端锚）；Long.MIN_VALUE = 未开启。 */
    private static volatile long windowEndTick = Long.MIN_VALUE;
    /** 窗口总长（收到包时的服务端权威值，作倒数分母）。 */
    private static volatile int windowTicks = 0;

    private NovaLeapClientState() {
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
