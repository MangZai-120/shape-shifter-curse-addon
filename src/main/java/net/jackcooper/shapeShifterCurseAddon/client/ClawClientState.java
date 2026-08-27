package net.jackcooper.shapeShifterCurseAddon.client;

/**
 * 风灵「疾风连爪」客户端状态镜像。
 *
 * 服务端 {@code WindSpiritClawManager} 经 S2C（PACKET_CLAW_STATE）同步爪击阶段与准星条进度到本类；
 * {@code ClawCrosshairMixin} 渲染准星攻击冷却条时读取本类，显示「爪击间隔充能 / 过热回复进度」。
 */
public final class ClawClientState {
    // 0 = 空闲，1 = 连续爪击，2 = 过热回复
    private static volatile int phase = 0;
    private static volatile float crosshairProgress = 1.0f;

    private ClawClientState() {
    }

    public static void update(int newPhase, float progress) {
        phase = newPhase;
        crosshairProgress = progress;
    }

    public static void reset() {
        phase = 0;
        crosshairProgress = 1.0f;
    }

    /** 是否处于爪击/过热（此时准星攻击冷却条由风灵接管显示）。 */
    public static boolean isActive() {
        return phase != 0;
    }

    /** 准星条进度：爪击期=当前这一击的间隔充能；过热期=回复进度。 */
    public static float getCrosshairProgress() {
        return crosshairProgress;
    }
}
