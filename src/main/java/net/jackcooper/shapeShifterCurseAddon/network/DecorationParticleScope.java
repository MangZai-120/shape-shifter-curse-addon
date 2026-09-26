package net.jackcooper.shapeShifterCurseAddon.network;

import net.minecraft.entity.Entity;

/** Explicit cosmetic action scope; server and client threads cannot leak ownership into each other. */
public final class DecorationParticleScope {
    private static final ThreadLocal<Entity> OWNER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PROTECTED = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PROJECTILE = new ThreadLocal<>();

    private DecorationParticleScope() {}

    public static Entity owner() { return OWNER.get(); }

    public static boolean isProtected() { return Boolean.TRUE.equals(PROTECTED.get()); }

    /** 弹道作用域（2026-09-26）：投射物拖尾/爆炸/火环特效豁免准星锥压制，只吃距离衰减。 */
    public static boolean isProjectileScope() { return Boolean.TRUE.equals(PROJECTILE.get()); }

    /** Projectile cues must remain visible even if spawned inside another decoration action. */
    public static void protectedVisual(Runnable action) {
        Boolean previous = PROTECTED.get();
        PROTECTED.set(true);
        try {
            action.run();
        } finally {
            if (previous == null) PROTECTED.remove(); else PROTECTED.set(previous);
        }
    }

    public static void run(Entity owner, Runnable action) {
        Entity previous = OWNER.get();
        OWNER.set(owner);
        try {
            action.run();
        } finally {
            if (previous == null) OWNER.remove(); else OWNER.set(previous);
        }
    }

    /** 弹道作用域：服务端发送 + 客户端 tag 均带弹道标记，粒子豁免锥压制。 */
    public static void runProjectile(Entity owner, Runnable action) {
        Entity previous = OWNER.get();
        Boolean previousProjectile = PROJECTILE.get();
        OWNER.set(owner);
        PROJECTILE.set(true);
        try {
            action.run();
        } finally {
            if (previous == null) OWNER.remove(); else OWNER.set(previous);
            if (previousProjectile == null) PROJECTILE.remove(); else PROJECTILE.set(previousProjectile);
        }
    }
}
