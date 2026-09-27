package net.jackcooper.shapeShifterCurseAddon.balance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * balance 字段级补丁合并（制作流程 §4：对象递归合并、数组整体替换、null 非法）。
 *
 * 输入层按「低优先级 → 高优先级」顺序传入，高优先级仅覆盖明确给出的字段。
 * 合并结果是纯 Json 树（未校验），交由 BalanceSnapshot 做类型/边界/约束校验。
 */
public final class BalanceMerge {

    private BalanceMerge() {}

    /** 单个来源层：来源标识（数据包名/路径，用于报错定位）+ JSON 对象。 */
    public record Layer(String source, Map<String, Object> json) {
        public Layer {
            json = json == null ? Map.of() : json;
        }
    }

    /** 按顺序把多层合并进一棵树：高优先级覆盖低优先级，对象递归、数组/标量整体替换。 */
    public static Map<String, Object> merge(List<Layer> layers) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Layer layer : layers) {
            mergeObject(result, layer.json(), layer.source);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void mergeObject(Map<String, Object> target, Map<String, Object> patch, String source) {
        for (Map.Entry<String, Object> e : patch.entrySet()) {
            if (e.getValue() == null) {
                // null 在 balance 语义中非法：立即中止合并并定位来源，杜绝半份树
                throw new BalanceRejectException("字段 " + e.getKey() + " 为 null（balance 不允许 null）", source, pathOf(e.getKey()));
            }
            if (e.getValue() instanceof Map) {
                Object existing = target.get(e.getKey());
                if (existing instanceof Map) {
                    mergeObject((Map<String, Object>) existing, (Map<String, Object>) e.getValue(), source);
                } else {
                    Map<String, Object> fresh = new LinkedHashMap<>();
                    mergeObject(fresh, (Map<String, Object>) e.getValue(), source);
                    target.put(e.getKey(), fresh);
                }
            } else {
                // 数组与标量：整体替换
                target.put(e.getKey(), e.getValue());
            }
}
    }

    private static String pathOf(String key) { return key; }

    /** 合并阶段拒绝（null 或不可合并结构）：携带来源与字段路径。 */
    public static final class BalanceRejectException extends RuntimeException {
        public final String source;
        public final String fieldPath;

        BalanceRejectException(String message, String source, String fieldPath) {
            super(message);
            this.source = source;
            this.fieldPath = fieldPath;
        }
    }
}
