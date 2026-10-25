package net.jackcooper.shapeShifterCurseAddon.compat;

import net.minecraft.util.Identifier;

/**
 * 原版 SSC perk 的登记 ID（与 {@code RegPerks} 常量等值）。
 *
 * <p>上游会把 perk 常量整段注释掉（例如 7f533bd6 注释了 {@code P_OcelotLongPounce}），
 * 直接引用 {@code RegPerks.X} 会让附属随上游改动编译失败；这里用 ID 字面量比较，
 * 新旧原版都能编译，perk 重新启用时行为也一致。</p>
 */
public final class SscPerkIds {
    public static final Identifier OCELOT_LONG_POUNCE = new Identifier("shape-shifter-curse", "ocelot_long_pounce");

    private SscPerkIds() { }
}
