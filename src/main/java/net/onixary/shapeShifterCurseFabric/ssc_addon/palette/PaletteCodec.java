package net.onixary.shapeShifterCurseFabric.ssc_addon.palette;

/**
 * 形态配色「分享码」编解码工具。
 *
 * 设计目的：把 SSC 主包 PlayerSkinComponent 的 8 个配色字段
 * （5 个 RGBA 颜色 + 3 个 greyReverse 布尔）打包成一串可复制粘贴的 ASCII 文本，
 * 让玩家通过指令一键导入/分享配色，绕开 ClothConfig 无法添加按钮的限制。
 *
 * 格式：SSCA-RRGGBBAA-RRGGBBAA-RRGGBBAA-RRGGBBAA-RRGGBBAA-Y
 *   - 5 段 8 位 hex = primary / accent1 / accent2 / eyeA / eyeB（均为 RGBA）
 *   - 末尾 Y 为 1 位 hex（0–7），3 个位分别对应 primary/accent1/accent2 的 greyReverse
 *
 * 总长固定 56 字符，便于一行复制；前缀 SSCA- 防止误粘贴其它字符串。
 */
public final class PaletteCodec {
    private PaletteCodec() {}

    public static final String PREFIX = "SSCA-";
    public static final int EXPECTED_LENGTH = 56; // SSCA- + 5*(8+1) + 1

    /**
     * 编码 8 个字段为字符串。颜色参数为 RGBA 格式（高字节为 R）。
     */
    public static String encode(int primaryRGBA, int accent1RGBA, int accent2RGBA, int eyeARGBA, int eyeBRGBA,
                                boolean primaryGreyReverse, boolean accent1GreyReverse, boolean accent2GreyReverse) {
        int flags = (primaryGreyReverse ? 1 : 0) | (accent1GreyReverse ? 2 : 0) | (accent2GreyReverse ? 4 : 0);
        return PREFIX
                + hex8(primaryRGBA) + "-"
                + hex8(accent1RGBA) + "-"
                + hex8(accent2RGBA) + "-"
                + hex8(eyeARGBA) + "-"
                + hex8(eyeBRGBA) + "-"
                + Integer.toHexString(flags).toUpperCase();
    }

    /**
     * 解码字符串到数据对象。失败抛 IllegalArgumentException（含原因描述，可直接展示给玩家）。
     */
    public static PaletteData decode(String raw) {
        if (raw == null) throw new IllegalArgumentException("配色代码为空");
        String code = raw.trim().toUpperCase();
        if (!code.startsWith(PREFIX)) throw new IllegalArgumentException("配色代码格式错误：缺少 SSCA- 前缀");
        String[] parts = code.substring(PREFIX.length()).split("-");
        if (parts.length != 6) throw new IllegalArgumentException("配色代码段数错误：应为 5 颜色 + 1 标志位");
        for (int i = 0; i < 5; i++) {
            if (parts[i].length() != 8) throw new IllegalArgumentException("第 " + (i + 1) + " 段颜色长度应为 8 位 hex");
        }
        if (parts[5].length() != 1) throw new IllegalArgumentException("标志位应为 1 位 hex");
        try {
            int primary = parseHex8(parts[0]);
            int accent1 = parseHex8(parts[1]);
            int accent2 = parseHex8(parts[2]);
            int eyeA = parseHex8(parts[3]);
            int eyeB = parseHex8(parts[4]);
            int flags = Integer.parseInt(parts[5], 16);
            if (flags < 0 || flags > 7) throw new IllegalArgumentException("标志位超出范围（应为 0-7）");
            return new PaletteData(primary, accent1, accent2, eyeA, eyeB,
                    (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("hex 解析失败：" + e.getMessage());
        }
    }

    private static String hex8(int v) {
        return String.format("%08X", v);
    }

    // 用 parseUnsignedInt 避免 0xFFxxxxxx 越界
    private static int parseHex8(String s) {
        return Integer.parseUnsignedInt(s, 16);
    }

    /** 解码结果：5 个 RGBA 颜色 + 3 个 greyReverse 布尔。 */
    public record PaletteData(int primaryRGBA, int accent1RGBA, int accent2RGBA, int eyeARGBA, int eyeBRGBA,
                              boolean primaryGreyReverse, boolean accent1GreyReverse, boolean accent2GreyReverse) {}
}
