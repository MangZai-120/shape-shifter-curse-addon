package net.jackcooper.shapeShifterCurseAddon.client.particle;

import java.util.UUID;

/** Includes invisible emitters so their child particles retain the explicit cosmetic owner. */
public interface ParticleOwnership {
    void ssca$setDecorationOwner(UUID owner);
    UUID ssca$getDecorationOwner();

    /** 弹道标记（2026-09-26）：投射物拖尾/爆炸特效豁免准星锥压制，只吃距离衰减。 */
    default boolean ssca$isProjectileDecoration() { return false; }
    default void ssca$markProjectileDecoration() {}
}
