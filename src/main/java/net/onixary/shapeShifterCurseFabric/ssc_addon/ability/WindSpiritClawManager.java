package net.onixary.shapeShifterCurseFabric.ssc_addon.ability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.PowerUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.TrinketUtils;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.WhitelistUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 风灵（月髓环豹猫） - 「疾风连爪」左键连击技能（服务端状态机）。
 *
 * 按死左键 → 连续爪击，越打越快（间隔 1.0s → 3 秒后 0.34s）；每次爪击对前方 2.5 格范围造成横扫伤害
 * （基础 8，随时长衰减，且叠加 MC 原版攻击冷却减伤），前冲一小段、扣饱食度。准星条即「耐力」，连击开始时满、
 * 随每次爪击下降；松开或打空后从当前进度慢慢回满（回满约 8 秒，期间左键=弱普攻 0%→90%）。
 * 移速随时长线性衰减（-15%/秒，封顶 -45%）。
 *
 * 客户端准星攻击冷却条由 {@code ClawCrosshairMixin} 读取经 S2C（PACKET_CLAW_STATE）同步的
 * {@code ClawClientState} 绘制。本类只做服务端判定，保证多人一致。
 */
public final class WindSpiritClawManager {

    private static final float BASE_DAMAGE = 8.0f;
    private static final double RADIUS = 2.5;
    private static final double REACH = 2.0;
    private static final int MAX_INTERVAL = 20;   // 1.0s
    private static final int MIN_INTERVAL = 7;    // ≈0.34s
    private static final int RAMP_TICKS = 60;     // 3 秒达最快
    private static final int MAX_CLAW_TICKS = 200; // 连击最长 10 秒兜底
    private static final int RECOVER_TICKS = 160;  // 从 0 回满 8 秒
    private static final int RECOVER_TICKS_NECKLACE = 110; // 戴风灵专属项链：耐力回满约 5.5 秒
    private static final float DMG_DECAY_PER_SEC = 0.12f;
    private static final float DMG_DECAY_MAX = 0.36f;
    private static final float SPD_DECAY_PER_SEC = 0.15f;
    private static final float SPD_DECAY_MAX = 0.45f;
    private static final float EXHAUSTION_PER_HIT = 0.64f; // ≈0.16 饱食度点
    private static final float FORWARD_LUNGE = 0.28f;
    private static final float PROGRESS_PER_HIT = 1.0f / 25.0f; // 最快频率下约 10 秒打空

    private static final UUID SPEED_MOD_UUID = UUID.fromString("6a1d3c9e-7b2f-4c8a-9e1d-0f5a2c7b4d33");
    private static final String SPEED_MOD_NAME = "wind_spirit_claw_slow";

    public static final int PHASE_IDLE = 0;
    public static final int PHASE_CLAW = 1;
    public static final int PHASE_OVERHEAT = 2;

    private static final Map<UUID, ClawState> STATES = new ConcurrentHashMap<>();

    // 副技能：+50% 徒手/形态伤害 buff
    private static final int BUFF_DURATION = 10;      // 0.5 秒
    private static final int SECONDARY_CD_TICKS = 40; // 2 秒
    private static final float BUFF_MULT = 1.5f;
    private static final Map<UUID, Integer> BUFF_TICKS = new ConcurrentHashMap<>();

    public static final class ClawState {
        boolean holding = false;
        int phase = PHASE_IDLE;
        int holdTicks = 0;
        int sinceLastAttack = 0;
        float progress = 1.0f;
        float recovery = 0.0f;
    }

    private WindSpiritClawManager() {
    }

    /** 客户端上报左键按住状态。 */
    public static void setHolding(ServerPlayerEntity player, boolean holding) {
        if (!FormUtils.isOcelotSP(player)) {
            clear(player);
            return;
        }
        ClawState s = STATES.computeIfAbsent(player.getUuid(), k -> new ClawState());
        s.holding = holding;
        if (holding && s.phase == PHASE_IDLE) {
            s.phase = PHASE_CLAW;
            s.holdTicks = 0;
            s.sinceLastAttack = MIN_INTERVAL; // 按下稍缓冲，避免按下即命中
        }
    }

    /** 每服务端 tick 对每个在线玩家调用。 */
    public static void tick(ServerPlayerEntity player) {
        // 副技能 buff 计时（独立于连击状态）
        Integer buff = BUFF_TICKS.get(player.getUuid());
        if (buff != null) {
            if (buff <= 1 || !FormUtils.isOcelotSP(player)) BUFF_TICKS.remove(player.getUuid());
            else BUFF_TICKS.put(player.getUuid(), buff - 1);
        }

        ClawState s = STATES.get(player.getUuid());
        if (s == null) return;

        if (player.isDead() || !FormUtils.isOcelotSP(player)) {
            clear(player);
            return;
        }

        switch (s.phase) {
            case PHASE_CLAW -> tickClaw(player, s);
            case PHASE_OVERHEAT -> tickOverheat(player, s);
            default -> {
            }
        }

        // 同步爪击阶段 + 准星条进度给客户端
        if (STATES.containsKey(player.getUuid())) {
            SscAddonNetworking.syncClawState(player, s.phase, crosshairProgress(s));
        }
    }

    private static void tickClaw(ServerPlayerEntity player, ClawState s) {
        s.holdTicks++;
        applySpeedSlow(player, slowFactor(s.holdTicks));

        if (!s.holding) {
            enterOverheat(s);
            removeSpeedSlow(player);
            return;
        }

        s.sinceLastAttack++;
        int interval = currentInterval(s.holdTicks);
        if (s.sinceLastAttack >= interval) {
            performClawAttack(player, s);
            s.sinceLastAttack = 0;
            s.progress -= PROGRESS_PER_HIT;
            player.resetLastAttackedTicks(); // 让原版减伤/准星充能反映爪击节奏
        }

        if (s.progress <= 0.0f || s.holdTicks >= MAX_CLAW_TICKS) {
            s.progress = 0.0f;
            enterOverheat(s);
            removeSpeedSlow(player);
        }
    }

    private static void tickOverheat(ServerPlayerEntity player, ClawState s) {
        removeSpeedSlow(player);

        // 停手后立即从当前进度慢慢回满（无延迟）；期间左键 = 弱普攻（getRecoveryMultiplier 缩放 0→90%）。
        // 戴风灵专属项链「御风爪铃」时耐力回复更快（8s → 5.5s）。
        int recoverTicks = TrinketUtils.isWearing(player, net.onixary.shapeShifterCurseFabric.ssc_addon.SscAddon.WIND_SPIRIT_STAMINA_NECKLACE)
                ? RECOVER_TICKS_NECKLACE : RECOVER_TICKS;
        s.recovery += 1.0f / recoverTicks;
        if (s.recovery >= 1.0f) {
            // 回满：仍按住 → 无缝重启连击；否则结束、准星条消失
            if (s.holding) {
                s.phase = PHASE_CLAW;
                s.holdTicks = 0;
                s.sinceLastAttack = MIN_INTERVAL;
                s.progress = 1.0f;
                s.recovery = 0.0f;
            } else {
                clear(player);
            }
        }
    }

    private static void enterOverheat(ClawState s) {
        s.phase = PHASE_OVERHEAT;
        s.recovery = MathHelper.clamp(s.progress, 0.0f, 1.0f); // 从当前剩余进度开始回满（打得越久剩越少、回越久）
    }

    private static void performClawAttack(ServerPlayerEntity player, ClawState s) {
        ServerWorld sw = (ServerWorld) player.getWorld();

        Vec3d look = player.getRotationVec(1.0f);
        double randSide = (player.getRandom().nextDouble() - 0.5) * 1.2;
        double randUp = (player.getRandom().nextDouble() - 0.5) * 0.6;
        Vec3d side = new Vec3d(-look.z, 0, look.x).normalize();
        Vec3d center = player.getEyePos()
                .add(look.multiply(REACH))
                .add(side.multiply(randSide))
                .add(0, randUp, 0);

        // MC 原版攻击冷却减伤系数（真实充能进度）× 爪击时长衰减
        float g = player.getAttackCooldownProgress(0.5f);
        float vanillaFactor = 0.2f + g * g * 0.8f;
        float clawFactor = 1.0f - Math.min(DMG_DECAY_MAX, DMG_DECAY_PER_SEC * (s.holdTicks / 20.0f));
        float dmg = BASE_DAMAGE * vanillaFactor * clawFactor;

        Box box = new Box(center.subtract(RADIUS, RADIUS, RADIUS), center.add(RADIUS, RADIUS, RADIUS));
        for (Entity e : sw.getOtherEntities(player, box)) {
            if (!(e instanceof LivingEntity living)) continue;
            if (WhitelistUtils.isProtected(player, living)) continue; // 默认白名单
            living.damage(player.getDamageSources().playerAttack(player), dmg);
            Vec3d push = living.getPos().subtract(player.getPos());
            if (push.lengthSquared() < 1.0e-4) push = look;
            push = push.normalize();
            living.takeKnockback(0.35, -push.x, -push.z);
        }

        sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 1, 0, 0, 0, 0);

        Vec3d flat = new Vec3d(look.x, 0, look.z).normalize().multiply(FORWARD_LUNGE);
        player.addVelocity(flat.x, 0.0, flat.z);
        player.velocityModified = true;

        player.addExhaustion(EXHAUSTION_PER_HIT);

        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.7f, 1.4f);
    }

    private static float slowFactor(int holdTicks) {
        return Math.min(SPD_DECAY_MAX, SPD_DECAY_PER_SEC * (holdTicks / 20.0f));
    }

    private static void applySpeedSlow(ServerPlayerEntity player, float factor) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr == null) return;
        double target = -factor; // MULTIPLY_TOTAL：-0.15 = 移速×0.85
        EntityAttributeModifier existing = attr.getModifier(SPEED_MOD_UUID);
        if (existing != null) {
            if (Math.abs(existing.getValue() - target) < 1.0e-4) return;
            attr.removeModifier(SPEED_MOD_UUID);
        }
        attr.addTemporaryModifier(new EntityAttributeModifier(
                SPEED_MOD_UUID, SPEED_MOD_NAME, target,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void removeSpeedSlow(ServerPlayerEntity player) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr != null && attr.getModifier(SPEED_MOD_UUID) != null) {
            attr.removeModifier(SPEED_MOD_UUID);
        }
    }

    /** 按住时长 → 当前攻击间隔（tick）：20 → 3 秒后 7。 */
    private static int currentInterval(int holdTicks) {
        float t = Math.min(holdTicks, RAMP_TICKS) / (float) RAMP_TICKS;
        return Math.round(MAX_INTERVAL - (MAX_INTERVAL - MIN_INTERVAL) * t);
    }

    /** 准星条=耐力：爪击期=剩余进度(随每次爪击下降)；过热期=从当前进度回满(0→1)；空闲=1。 */
    public static float crosshairProgress(ClawState s) {
        return switch (s.phase) {
            case PHASE_CLAW -> MathHelper.clamp(s.progress, 0.0f, 1.0f);
            case PHASE_OVERHEAT -> MathHelper.clamp(s.recovery, 0.0f, 1.0f);
            default -> 1.0f;
        };
    }

    /** 徒手/非武器近战伤害倍率 = 过热回复缩放(0-0.9) × 副技能 buff(1.5)。给 ClawDamageBoostMixin 用。 */
    public static float getNormalMeleeMultiplier(ServerPlayerEntity player) {
        float recovery = 1.0f;
        ClawState s = STATES.get(player.getUuid());
        if (s != null && s.phase == PHASE_OVERHEAT) {
            recovery = s.recovery * 0.9f; // 回复进度 0→1 映射伤害 0→90%
        }
        float buff = BUFF_TICKS.containsKey(player.getUuid()) ? BUFF_MULT : 1.0f;
        return recovery * buff;
    }

    /** 是否手持武器（与主包 is_weapon 一致：主手攻击力加成 &gt; 1）。拿武器时不吃疾风连爪/副技能加伤（火把/食物/方块等非武器仍吃）。 */
    public static boolean isHoldingWeapon(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) return false;
        double totalAdd = 0.0;
        for (EntityAttributeModifier m : stack
                .getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
            if (m.getOperation() == EntityAttributeModifier.Operation.ADDITION) {
                totalAdd += m.getValue();
            }
        }
        return totalAdd > 1.0;
    }

    /** 副技能（sp_secondary）：0.5 秒内徒手/形态伤害 +50%，cd 2 秒。 */
    public static void activateSecondaryBuff(ServerPlayerEntity player) {
        if (!FormUtils.isOcelotSP(player)) return;
        if (PowerUtils.getResourceValue(player, FormIdentifiers.SP_SECONDARY_CD) > 0) return; // CD 中
        BUFF_TICKS.put(player.getUuid(), BUFF_DURATION);
        PowerUtils.setResourceValueAndSync(player, FormIdentifiers.SP_SECONDARY_CD, SECONDARY_CD_TICKS);
        ServerWorld sw = (ServerWorld) player.getWorld();
        sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 0.8f, 1.6f);
        sw.spawnParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.5, 0.4, 0.2);
    }

    /** 彻底清理：移除状态 + 移速修饰符，并通知客户端准星条消失。 */
    public static void clear(ServerPlayerEntity player) {
        removeSpeedSlow(player);
        if (STATES.remove(player.getUuid()) != null) {
            SscAddonNetworking.syncClawState(player, PHASE_IDLE, 1.0f);
        }
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        removeSpeedSlow(player);
        STATES.remove(player.getUuid());
        BUFF_TICKS.remove(player.getUuid());
    }
}
