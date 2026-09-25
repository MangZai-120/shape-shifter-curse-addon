package net.jackcooper.shapeShifterCurseAddon.client.particle;

/** Pure render policy: no particle position, lifetime or simulation state is changed. */
public final class ParticleAvoidance {
    public enum Strength {
        // 2026-09-26 用户定稿：轻度4 / 中度10 / 重度16 格；边缘处保留 50%，准星 30° 锥内再降 25%
        OFF(0), LIGHT(4), STANDARD(10), STRONG(16);

        private final double radius;

        Strength(double radius) { this.radius = radius; }

        /** Camera-to-centre distance at which the original appearance is fully restored. */
        public double radius() { return radius; }

        // AutoConfig 11 uses Text.translatable(enum.toString()), without the field's prefix.
        @Override public String toString() {
            return "text.autoconfig.ssc_addon_client.option.firstPersonParticleAvoidance." + name();
        }
    }

    private ParticleAvoidance() {}

    /** 边缘（半径外沿）保留的透明度：范围内从 0 升到该值，边界外短恢复带平滑回到 1。 */
    private static final float EDGE_VISIBILITY = 0.5f;
    /** 恢复带宽度（格）：从半径处到 radius+RECOVERY 内从 50% 平滑回到 100%。 */
    private static final double RECOVERY = 2.0;
    /** 准星锥半角（度）：视线方向该角度内的粒子透明度再乘 CROSSHAIR_MULT，防挡准星。 */
    private static final float CROSSHAIR_CONE_DEGREES = 30.0f;
    private static final float CROSSHAIR_MULT = 0.75f;
    /**
     * 近距抽样区（2026-09-26 用户定稿）：特别近（< NEAR_ZONE 格）的粒子不整体隐藏，
     * 而是按 hash 抽 20% 显示、透明度压到 25%（= 边缘 50% 再低一半，「留一点影子」），
     * 其余 80% 完全隐藏。抽样按粒子对象 hash 决定，同一粒子存续期内不闪烁。
     */
    public static final double NEAR_ZONE = 1.5;
    public static final float NEAR_KEEP_RATIO = 0.2f;
    public static final float NEAR_VISIBILITY = 0.25f;

    public static float visibility(Strength strength, boolean ownDecoration, boolean firstPerson,
                                   double distance, float size) {
        if (!ownDecoration || !firstPerson || strength == null || strength == Strength.OFF) return 1;
        double radius = strength.radius();
        if (!Double.isFinite(distance)) return 1;
        if (distance >= radius + RECOVERY) return 1;
        if (distance < NEAR_ZONE) return NEAR_VISIBILITY;
        if (distance < radius) {
            // 主衰减段：近距区边界（影子级 25%）→ 边缘 50%，smoothstep 两端零斜率、无断崖
            double t = (distance - NEAR_ZONE) / (radius - NEAR_ZONE);
            float ramp = (float) (t * t * (3 - 2 * t));
            return NEAR_VISIBILITY + ramp * (EDGE_VISIBILITY - NEAR_VISIBILITY);
        }
        // 恢复带：半径处 50% → 半径+2 格处 100%，与主段在半径处连续且斜率为零
        double t = (distance - radius) / RECOVERY;
        float ramp = (float) (t * t * (3 - 2 * t));
        return EDGE_VISIBILITY + ramp * (1.0f - EDGE_VISIBILITY);
    }

    /**
     * 近距抽样判定（2026-09-26 用户要求）：特别近的粒子抽 20% 显示（影子级 25% 透明度），
     * 其余 80% 隐藏。按粒子对象 identity hash 取样——同一粒子整个寿命内判定一致，
     * 不随帧闪烁；不依赖随机数，多帧稳定。
     *
     * @return true = 该粒子属于被保留显示的 20%
     */
    public static boolean keepNearSample(Object particle) {
        // identityHashCode 均匀分布，取模近似 20% 保留率
        return (System.identityHashCode(particle) & 0xFF) < NEAR_KEEP_RATIO * 0xFF;
    }

    /**
     * 准星锥额外衰减（2026-09-26 用户要求）：粒子方向与视线夹角 < 30° 时，
     * 透明度再乘 0.75（叠在距离曲线之上）。仅对打标的自身装饰粒子生效。
     *
     * @param directionX/Y/Z 粒子中心相对镜头的单位方向向量
     * @param range          粒子与镜头的距离（格），距离过近时锥判定放宽（避免贴脸粒子全在锥外）
     */
    public static float crosshairConeFactor(net.minecraft.client.render.Camera camera, double distance,
                                            double directionX, double directionY, double directionZ) {
        if (camera == null) return 1;
        // 1.20.1 Camera 无 getDirection()，用 yaw/pitch 复算视线单位向量（与 Entity.getRotationVector 同式）
        double yawRad = Math.toRadians(camera.getYaw());
        double pitchRad = Math.toRadians(camera.getPitch());
        double lx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double ly = -Math.sin(pitchRad);
        double lz = Math.cos(yawRad) * Math.cos(pitchRad);
        double dot = lx * directionX + ly * directionY + lz * directionZ;
        double cosThreshold = Math.cos(Math.toRadians(CROSSHAIR_CONE_DEGREES));
        // 距离小于 1 格时，锥判定按比例放宽到 60°：贴脸粒子视线夹角普遍偏大，但同样挡准星
        if (distance < 1.0) cosThreshold = Math.cos(Math.toRadians(CROSSHAIR_CONE_DEGREES * 2));
        return dot >= cosThreshold ? CROSSHAIR_MULT : 1.0f;
    }
}
