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
    private static float recoveryStep;
    private static long anchor;
    private static net.minecraft.client.world.ClientWorld world;

    private ClawClientState() {
    }

    public static void update(int newPhase, float progress, float step, long serverTime) {
        phase = newPhase;
        crosshairProgress = progress;
        recoveryStep = step;
        anchor = serverTime;
        world = net.minecraft.client.MinecraftClient.getInstance().world;
    }

    public static void reset() {
        phase = 0;
        crosshairProgress = 1.0f;
        recoveryStep = 0;
        world = null;
    }

    /** 是否处于爪击/过热（此时准星攻击冷却条由风灵接管显示）。 */
    public static boolean isActive() {
        if (net.minecraft.client.MinecraftClient.getInstance().world != world) reset();
        return phase != 0;
    }

    /** 准星条进度：爪击期=当前这一击的间隔充能；过热期=回复进度。 */
    public static float getCrosshairProgress() {
        if (!isActive() || world == null) return 1;
        return Math.min(1, Math.max(0, crosshairProgress + recoveryStep * Math.max(0, world.getTime() - anchor)));
    }
}
