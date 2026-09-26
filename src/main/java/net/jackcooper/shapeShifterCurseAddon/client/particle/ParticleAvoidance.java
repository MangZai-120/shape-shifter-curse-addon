package net.jackcooper.shapeShifterCurseAddon.client.particle;

/** Pure render policy: no particle position, lifetime or simulation state is changed. */
public final class ParticleAvoidance {
    public enum Strength {
        // Distance settings retained from the current implementation.
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
    public static final double RECOVERY = 2.0;
    /** 0..15 degrees: distant decoration retains 10% visibility; 15..45 smoothly restores normal fading. */
    private static final double CROSSHAIR_INNER_COS = Math.cos(Math.toRadians(15));
    private static final double CROSSHAIR_OUTER_COS = Math.cos(Math.toRadians(45));
    private static final float CROSSHAIR_MULT = 0.10f;
    /**
     * 2026-09-26 用户定稿（覆盖早前 25/15 方案）：锥内最低透明度依据消除强度分化，
     * 且轻/中档按粒子抽样豁免——轻度抽 40% 粒子完全跳过锥压制、其余压到 40%；
     * 中度抽 20% 豁免、其余压到 20%；重度不豁免、全部压到 10%。
     */
    private static float coneMult(Strength strength) {
        if (strength == Strength.LIGHT) return 0.40f;
        if (strength == Strength.STANDARD) return 0.20f;
        return CROSSHAIR_MULT;
    }

    /** 锥压制抽样豁免率：轻度 40%（中度已改计数式剔除、重度不豁免）。 */
    private static float coneExemptRate(Strength strength) {
        if (strength == Strength.LIGHT) return 0.40f;
        return 0f;
    }

    /**
     * 锥压制抽样豁免判定：按粒子 identity hash 稳定取样（同粒子终身一致不闪烁），
     * 命中的粒子完全跳过锥压制（只吃距离曲线）。重度档不豁免。
     */
    public static boolean coneExempt(Object particle, Strength strength) {
        float rate = coneExemptRate(strength);
        if (rate <= 0 || particle == null) return false;
        return (System.identityHashCode(particle) & 0xFF) < rate * 0xFF;
    }
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

    /** Pure angular policy. Dot is the cosine of the angle from the camera's forward direction. */
    public static float crosshairConeFactor(double dot) {
        return crosshairConeFactor(dot, CROSSHAIR_MULT);
    }

    /** 档位感知版：锥内目标值随消除强度分化（轻 25% / 中 15% / 重 10%）。 */
    static float crosshairConeFactor(double dot, float coneMult) {
        if (!Double.isFinite(dot) || dot <= CROSSHAIR_OUTER_COS) return 1;
        if (dot >= CROSSHAIR_INNER_COS) return coneMult;
        double t = (dot - CROSSHAIR_OUTER_COS) / (CROSSHAIR_INNER_COS - CROSSHAIR_OUTER_COS);
        double weight = t * t * (3 - 2 * t);
        return (float) (1 - (1 - coneMult) * weight);
    }

    /** Angular fading remains active at long range; it never completely removes distant decoration. */
    public static float withCrosshair(float visibility, double distance, Strength strength, double dot) {
        if (strength == null || strength == Strength.OFF || !Double.isFinite(distance)) return visibility;
        if (distance >= strength.radius() + RECOVERY) return visibility;
        return withCrosshairMult(visibility, distance, dot, coneMult(strength));
    }

    /** 参数化目标值的锥透明度曲线：15° 内压到 mult、15°~45° 平滑恢复到距离曲线值。 */
    static float withCrosshairMult(float visibility, double distance, double dot, float mult) {
        float factor = crosshairConeFactor(dot, mult);
        float weight = (1 - factor) / (1 - mult);
        return visibility + weight * (Math.min(visibility, mult) - visibility);
    }

    /**
     * 弹道粒子分派（2026-09-26 火球反馈）：火球等沿视线飞行的投射物特效全程落在准星 15° 锥内，
     * 若再叠锥压制整条弹道被压到最低值。弹道粒子只吃距离曲线、跳过锥压制。
     */
    public static float applyAngular(float visibility, double distance, Strength strength, double dot,
                                     boolean projectile) {
        return applyAngular(visibility, distance, strength, dot, projectile, null);
    }

    /** 中度 15° 锥内保留显示的粒子比例（2026-09-26 最终定稿：重度曲线打底+中心数量剔除）。 */
    private static final float STANDARD_CONE_KEEP = 0.20f;

    /**
     * 中度中心剔除曲线（2026-09-26 最终定稿）：数量剔除只作用于中心 15° 内（20% 显示），
     * 15°~45° 与重度完全一致（无剔除、纯重度透明度曲线平滑恢复），45° 外正常。
     */
    static float standardKeepFraction(double dot) {
        if (!Double.isFinite(dot) || dot < CROSSHAIR_INNER_COS) return 1f;
        return STANDARD_CONE_KEEP;
    }

    /** 中度显示者透明度补偿：基于重度曲线结果 ×1.5（clamp 1）。 */
    static float standardOpacityFactor(double dot) { return 1.5f; }

    /** 计数式抽样：按粒子 identity hash 与阈值比较，阈值单调变化时粒子只单次翻转不闪烁。 */
    static boolean keepAngularSample(Object particle, float keepFraction) {
        if (particle == null || keepFraction >= 1f) return true;
        if (keepFraction <= 0f) return false;
        return (System.identityHashCode(particle) & 0xFF) < keepFraction * 0xFF;
    }

    /**
     * 带抽样键的完整分派（2026-09-26 最终定稿）：
     * 中度＝重度透明度曲线打底（15°~45° 与重度完全一致）+ 中心 15° 内数量剔除：
     * 抽 20% 粒子显示（透明度×1.5 补偿、clamp 1），其余 80% 不显示。
     * 轻度＝抽40%豁免+其余压到 40%；重度＝全员压到 10%。
     */
    public static float applyAngular(float visibility, double distance, Strength strength, double dot,
                                     boolean projectile, Object sampleKey) {
        if (projectile || strength == null || strength == Strength.OFF || !Double.isFinite(distance)) return visibility;
        if (distance >= strength.radius() + RECOVERY) return visibility;
        if (strength == Strength.STANDARD) {
            // 周围区域与重度同款透明度曲线（目标 10%）
            float faded = withCrosshairMult(visibility, distance, dot, CROSSHAIR_MULT);
            float keep = standardKeepFraction(dot);
            if (keep >= 1f) return faded;
            return keepAngularSample(sampleKey, keep)
                    ? Math.min(1f, faded * standardOpacityFactor(dot)) : 0;
        }
        if (sampleKey != null && coneExempt(sampleKey, strength)) return visibility;
        return withCrosshair(visibility, distance, strength, dot);
    }

}
