package net.jackcooper.shapeShifterCurseAddon.balance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * balance 文件解析与整备（制作流程 §5 的 BalanceLoader 前半部分，阶段 2）。
 *
 * 职责边界（阶段 2）：Json 文本 → 解析后的 Map 树（Long/Double/Boolean/String/List/Map）。
 * 不触碰 Minecraft ResourceManager——阶段 3 再写薄适配器把有序资源栈映射为本类输入。
 * 这样合并/校验/快照全部可在 JavaExec 测试中脱离 MC 运行时验证。
 */
public final class BalanceJson {

    private BalanceJson() {}

    /** 单个 balance 文件：来源描述（包名/路径，报错定位用）+ 已解析树。 */
    public static final class File {
        public final String source;
        public final Map<String, Object> tree;

        public File(String source, Map<String, Object> tree) {
            this.source = source;
            this.tree = tree;
        }
    }

    /** 解析单个文件文本；语法错误 / 根不是对象 → 抛 BalanceMerge.BalanceRejectException。 */
    public static File parse(String source, String json) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            throw new BalanceMerge.BalanceRejectException("JSON 语法错误：" + e.getMessage(), source, "<root>");
        }
        if (!root.isJsonObject()) {
            throw new BalanceMerge.BalanceRejectException("根必须是 JSON 对象", source, "<root>");
        }
        return new File(source, toTree(root.getAsJsonObject()));
    }

    /** Gson 元素 → 纯 Java 树（Long/Double/Boolean/String/List<Object>/Map<String,Object>）。 */
    public static Map<String, Object> toTree(JsonObject obj) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            out.put(e.getKey(), convert(e.getValue()));
        }
        return out;
    }

    private static Object convert(JsonElement el) {
        if (el.isJsonNull()) {
            return null;   // 由 BalanceMerge 在合并阶段拒绝
        }
        if (el.isJsonObject()) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                m.put(e.getKey(), convert(e.getValue()));
            }
            return m;
        }
        if (el.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement e : el.getAsJsonArray()) {
                list.add(convert(e));
            }
            return list;
        }
        var p = el.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isString()) return p.getAsString();
        // 数字：无小数点/指数的写成 Long（供 INT 精确判定），否则 Double
        String raw = p.getAsString();
        if (raw.matches("-?\\d+")) {
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException overflow) {
                return Double.parseDouble(raw);
            }
        }
        return p.getAsDouble();
    }
}
