package net.jackcooper.shapeShifterCurseAddon.client;

/**
 * 风灵「风之冲刺」客户端阶段镜像（由 S2C {@code PACKET_DASH_STATE} 同步）。
 *
 * <p>客户端据此判断是否处于悬浮阶段（渲染绿色落点预览），并驱动主技能辅助栏
 * 显示「还能在天上飞多久」（RISE 满格 → HOVER 自满格倒数 hover_ticks）。
 * 所有判定在服务端。
 */
public final class DashClientState {
    /** 与服务端 WindDashManager 阶段常量一致。 */
    public static final int PHASE_NONE = 0;
    public static final int PHASE_RISE = 1;
    public static final int PHASE_HOVER = 2;
    public static final int PHASE_DASH = 3;
    public static final int PHASE_FALL = 4;

    public static volatile int phase = PHASE_NONE;
    public static volatile double targetY = 0.0;
    /** 进入 HOVER 的客户端 tick 锚（驱动辅助栏悬浮倒数；非 HOVER 阶段无意义）。 */
    private static volatile long hoverStartTick = Long.MIN_VALUE;

    private DashClientState() {
    }

    public static void update(int p, double y) {
        net.minecraft.client.world.ClientWorld world =
                net.minecraft.client.MinecraftClient.getInstance().world;
        // 刚进入 HOVER：记录客户端 tick 锚（辅助栏自此倒数 hover_ticks）
        if (p == PHASE_HOVER && phase != PHASE_HOVER) {
            hoverStartTick = world == null ? Long.MIN_VALUE : world.getTime();
        }
        phase = p;
        targetY = y;
    }

    public static void reset() {
        phase = PHASE_NONE;
        targetY = 0.0;
        hoverStartTick = Long.MIN_VALUE;
    }

    /**
     * 主技能辅助栏进度（飞行剩余）：RISE → 1.0 满格；HOVER → 悬浮剩余占比（自满格倒数）；
     * 其余阶段（DASH/FALL/NONE）→ -1 不显示。
     */
    public static double hoverRemainingFraction(int hoverTicks) {
        if (phase == PHASE_RISE) return 1.0;
        if (phase != PHASE_HOVER || hoverStartTick == Long.MIN_VALUE) return -1;
        net.minecraft.client.world.ClientWorld world =
                net.minecraft.client.MinecraftClient.getInstance().world;
        if (world == null) return -1;
        double remain = Math.max(0, hoverStartTick + hoverTicks - world.getTime());
        return Math.min(1.0, remain / Math.max(1, hoverTicks));
    }
}
