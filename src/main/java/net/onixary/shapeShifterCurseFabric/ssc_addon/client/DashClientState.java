package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

/**
 * 风灵「风之冲刺」客户端阶段镜像（由 S2C {@code PACKET_DASH_STATE} 同步）。
 *
 * <p>客户端只据此判断是否处于悬浮阶段（渲染绿色落点预览）。所有判定在服务端。
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

    private DashClientState() {
    }

    public static void update(int p, double y) {
        phase = p;
        targetY = y;
    }

    public static void reset() {
        phase = PHASE_NONE;
        targetY = 0.0;
    }
}
