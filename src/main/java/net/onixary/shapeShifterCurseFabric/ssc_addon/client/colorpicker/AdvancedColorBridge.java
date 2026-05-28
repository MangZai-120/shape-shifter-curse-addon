package net.onixary.shapeShifterCurseFabric.ssc_addon.client.colorpicker;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.onixary.shapeShifterCurseFabric.ShapeShifterCurseFabric;
import net.onixary.shapeShifterCurseFabric.config.PlayerCustomConfig;
import net.onixary.shapeShifterCurseFabric.networking.ModPacketsS2C;
import net.onixary.shapeShifterCurseFabric.player_form.skin.PlayerSkinComponent;
import net.onixary.shapeShifterCurseFabric.player_form.skin.RegPlayerSkinComponent;
import net.onixary.shapeShifterCurseFabric.util.FormTextureUtils;

/**
 * 高级调色桥接层：与原版 SSC cloth-config 调色字段解耦。
 *
 * 职责：
 *  - 提供本地"工作态"快照（5 颜色 + 3 GreyReverse + enable 开关），所有 UI 改动只更新此快照。
 *  - 预览：把工作态临时塞进客户端 PlayerSkinComponent.formColor，不发包，仅本地可见。
 *  - 保存：把工作态写回 ShapeShifterCurseFabric.playerCustomConfig，调 AutoConfig.save 持久化，
 *          再走原版 ModPacketsS2C.sendUpdateCustomSetting(true) 同步至服务端及其他玩家。
 *  - 取消：恢复 Screen 打开前的 PlayerSkinComponent 状态（颜色 + enable）。
 *
 * 与 PalettePresetStore / PaletteConfigSync 完全独立——本类不依赖、也不会触碰预设系统。
 */
public final class AdvancedColorBridge {
    private AdvancedColorBridge() {}

    /** 颜色字段索引常量，便于 Screen 内统一寻址。 */
    public static final int IDX_PRIMARY  = 0;
    public static final int IDX_ACCENT1  = 1;
    public static final int IDX_ACCENT2  = 2;
    public static final int IDX_EYE_A    = 3;
    public static final int IDX_EYE_B    = 4;
    public static final int COLOR_COUNT  = 5;

    public static final int GR_IDX_PRIMARY = 0;
    public static final int GR_IDX_ACCENT1 = 1;
    public static final int GR_IDX_ACCENT2 = 2;
    public static final int GR_COUNT       = 3;

    /** Screen 当前工作态（ARGB 格式，与 cloth-config 字段保持一致）。 */
    public static final class WorkingState {
        public final int[] colorsARGB = new int[COLOR_COUNT];
        public final boolean[] greyReverse = new boolean[GR_COUNT];
        public boolean enabled;

        public WorkingState copy() {
            WorkingState s = new WorkingState();
            System.arraycopy(this.colorsARGB, 0, s.colorsARGB, 0, COLOR_COUNT);
            System.arraycopy(this.greyReverse, 0, s.greyReverse, 0, GR_COUNT);
            s.enabled = this.enabled;
            return s;
        }
    }

    /** 从当前 cloth-config 字段读出工作态。 */
    public static WorkingState snapshotFromConfig() {
        PlayerCustomConfig cfg = ShapeShifterCurseFabric.playerCustomConfig;
        WorkingState s = new WorkingState();
        s.enabled = cfg.enable_form_color;
        s.colorsARGB[IDX_PRIMARY] = cfg.primaryColor;
        s.colorsARGB[IDX_ACCENT1] = cfg.accentColor1Color;
        s.colorsARGB[IDX_ACCENT2] = cfg.accentColor2Color;
        s.colorsARGB[IDX_EYE_A]   = cfg.eyeColorA;
        s.colorsARGB[IDX_EYE_B]   = cfg.eyeColorB;
        s.greyReverse[GR_IDX_PRIMARY] = cfg.primaryGreyReverse;
        s.greyReverse[GR_IDX_ACCENT1] = cfg.accent1GreyReverse;
        s.greyReverse[GR_IDX_ACCENT2] = cfg.accent2GreyReverse;
        return s;
    }

    /** 备份打开 Screen 时的客户端 PlayerSkinComponent 状态（颜色 + enable），用于取消时回滚预览。 */
    public static final class PreviewBackup {
        FormTextureUtils.ColorSetting colorABGR;
        boolean enabled;
    }

    /** 备份本地预览状态。 */
    public static PreviewBackup backupPreview() {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return null;
        PlayerSkinComponent skin = RegPlayerSkinComponent.SKIN_SETTINGS.get(p);
        PreviewBackup b = new PreviewBackup();
        b.colorABGR = skin.getFormColor();
        b.enabled = skin.isEnableFormColor();
        return b;
    }

    /** 把工作态推到客户端 PlayerSkinComponent 实现本地预览（不发包）。 */
    public static void applyPreview(WorkingState s) {
        if (s == null) return;
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return;
        PlayerSkinComponent skin = RegPlayerSkinComponent.SKIN_SETTINGS.get(p);
        skin.setEnableFormColor(s.enabled);
        skin.setFormColor(new FormTextureUtils.ColorSetting(
                FormTextureUtils.ARGB2ABGR(s.colorsARGB[IDX_PRIMARY]),
                FormTextureUtils.ARGB2ABGR(s.colorsARGB[IDX_ACCENT1]),
                FormTextureUtils.ARGB2ABGR(s.colorsARGB[IDX_ACCENT2]),
                FormTextureUtils.ARGB2ABGR(s.colorsARGB[IDX_EYE_A]),
                FormTextureUtils.ARGB2ABGR(s.colorsARGB[IDX_EYE_B]),
                s.greyReverse[GR_IDX_PRIMARY],
                s.greyReverse[GR_IDX_ACCENT1],
                s.greyReverse[GR_IDX_ACCENT2]));
    }

    /** 取消：把备份的客户端组件状态还原回去（仅本地，不发包）。 */
    public static void restoreFromBackup(PreviewBackup b) {
        if (b == null) return;
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return;
        PlayerSkinComponent skin = RegPlayerSkinComponent.SKIN_SETTINGS.get(p);
        skin.setFormColor(b.colorABGR);
        skin.setEnableFormColor(b.enabled);
    }

    /**
     * 提交保存：写回 cloth-config + 持久化 + 发同步包。
     * 注意：必须强制同步（auto_sync_config 可能为 false 时也要发）。
     */
    public static void commitSave(WorkingState s) {
        if (s == null) return;
        PlayerCustomConfig cfg = ShapeShifterCurseFabric.playerCustomConfig;
        cfg.enable_form_color = s.enabled;
        cfg.primaryColor      = s.colorsARGB[IDX_PRIMARY];
        cfg.accentColor1Color = s.colorsARGB[IDX_ACCENT1];
        cfg.accentColor2Color = s.colorsARGB[IDX_ACCENT2];
        cfg.eyeColorA         = s.colorsARGB[IDX_EYE_A];
        cfg.eyeColorB         = s.colorsARGB[IDX_EYE_B];
        cfg.primaryGreyReverse = s.greyReverse[GR_IDX_PRIMARY];
        cfg.accent1GreyReverse = s.greyReverse[GR_IDX_ACCENT1];
        cfg.accent2GreyReverse = s.greyReverse[GR_IDX_ACCENT2];
        try {
            AutoConfig.getConfigHolder(PlayerCustomConfig.class).save();
        } catch (Throwable t) {
            ShapeShifterCurseFabric.LOGGER.error("[SSC_ADDON] AdvancedColorBridge: failed to persist PlayerCustomConfig", t);
        }
        // ForceUpdate=true，绕过 auto_sync_config 开关
        ModPacketsS2C.sendUpdateCustomSetting(true);
        // 修复 SSC 主包 sendUpdateCustomSetting 漏发颜色包的 bug（buf 构造完未调 ClientPlayNetworking.send）。
        // 直接走 ClientPlayNetworking.send + update_custom_color 通道补发颜色，
        // 不依赖 SSC 主包是否暴露 sendUpdateCustomColor 方法（1.9.0 上还没暴露）。
        try {
            net.minecraft.network.PacketByteBuf cbuf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            cbuf.writeInt(FormTextureUtils.ARGB2ABGR(s.colorsARGB[IDX_PRIMARY]));
            cbuf.writeInt(FormTextureUtils.ARGB2ABGR(s.colorsARGB[IDX_ACCENT1]));
            cbuf.writeInt(FormTextureUtils.ARGB2ABGR(s.colorsARGB[IDX_ACCENT2]));
            cbuf.writeInt(FormTextureUtils.ARGB2ABGR(s.colorsARGB[IDX_EYE_A]));
            cbuf.writeInt(FormTextureUtils.ARGB2ABGR(s.colorsARGB[IDX_EYE_B]));
            cbuf.writeBoolean(s.greyReverse[GR_IDX_PRIMARY]);
            cbuf.writeBoolean(s.greyReverse[GR_IDX_ACCENT1]);
            cbuf.writeBoolean(s.greyReverse[GR_IDX_ACCENT2]);
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new net.minecraft.util.Identifier(ShapeShifterCurseFabric.MOD_ID, "update_custom_color"), cbuf);
        } catch (Throwable t) {
            ShapeShifterCurseFabric.LOGGER.error("[SSC_ADDON] AdvancedColorBridge: failed to send color packet", t);
        }
    }
}
