package net.onixary.shapeShifterCurseFabric.ssc_addon.client.palette;

import me.shedaniel.autoconfig.AutoConfig;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.config.PlayerCustomConfig;
import net.onixary.shapeShifterCurseFabric.ssc_addon.palette.PaletteCodec;

/**
 * 把分享码同步回 SSC 主包客户端配置 PlayerCustomConfig。
 *
 * 为什么需要：apply 配色后玩家可能去 ModMenu 看 SSC 客户端配置——
 * 如果不同步，那边显示的还是旧颜色（ColorPicker 不会"反向感知"PlayerSkinComponent 的变化）。
 *
 * 关键点：
 *   1. PaletteCodec 编出来的是 RGBA；PlayerCustomConfig 用的是 ARGB → 需要转换
 *   2. 写完后必须 save()，否则游戏重启 cloth 会用磁盘旧值覆盖内存里的新值
 *   3. enable_form_color = true，避免出现"应用了但 SSC 配置开关是关的"的诡异状态
 */
public final class PaletteConfigSync {
    private PaletteConfigSync() {}

    public static void apply(PaletteCodec.PaletteData data) {
        PlayerCustomConfig cfg = ShapeShifterCurseFabric.playerCustomConfig;
        if (cfg == null) return;
        cfg.enable_form_color = true;
        cfg.primaryColor = rgbaToArgb(data.primaryRGBA());
        cfg.accentColor1Color = rgbaToArgb(data.accent1RGBA());
        cfg.accentColor2Color = rgbaToArgb(data.accent2RGBA());
        cfg.eyeColorA = rgbaToArgb(data.eyeARGBA());
        cfg.eyeColorB = rgbaToArgb(data.eyeBRGBA());
        cfg.primaryGreyReverse = data.primaryGreyReverse();
        cfg.accent1GreyReverse = data.accent1GreyReverse();
        cfg.accent2GreyReverse = data.accent2GreyReverse();
        try {
            AutoConfig.getConfigHolder(PlayerCustomConfig.class).save();
        } catch (RuntimeException ignored) {
            // 保存失败不影响内存里的应用效果，下次启动会丢失但当前会话已生效
        }
    }

    /** RGBA(R高字节) → ARGB(A高字节)：A 抽到最高位，其余左移补 0。 */
    private static int rgbaToArgb(int rgba) {
        return ((rgba & 0xFF) << 24) | ((rgba >>> 8) & 0x00FFFFFF);
    }
}
