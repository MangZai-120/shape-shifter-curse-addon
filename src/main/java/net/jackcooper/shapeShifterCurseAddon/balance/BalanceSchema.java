package net.jackcooper.shapeShifterCurseAddon.balance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * SSCA balance 数据包的字段登记表（制作流程 §5 的 BalanceSchema 组件，阶段 2）。
 *
 * 登记：稳定参数 ID、类型、缺省值、上下限、跨字段约束。
 * 实例不可变；缺省值在构造时自检（带病缺省直接抛出，防止注册阶段埋雷）。
 * 纯逻辑类：不依赖 Minecraft / Fabric，可在 JavaExec 测试中直接构造。
 */
public final class BalanceSchema {

    /** 当前 balance 文件格式版本；文件内 schema_version 与之不符即整批拒绝。 */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public enum Type { INT, DOUBLE, BOOLEAN, STRING, DOUBLE_LIST }

    /** 单个参数定义：类型 + 缺省 + 边界（数值与列表元素共用；BOOLEAN/STRING 忽略数值边界）。 */
    public static final class Param {
        private final Type type;
        private final Object defaultValue;   // Long / Double / Boolean / String / List<Double>
        private final double min;
        private final double max;
        private final Set<String> allowed;   // STRING 枚举白名单；null = 不限
        private final int minItems;          // DOUBLE_LIST 尺寸边界
        private final int maxItems;
        // 代码常量来源注解（pin 自动对照用）：srcClass = 相对 net.jackcooper.shapeShifterCurseAddon. 的类名；null = 无常量（字面量由 pin 表显式维护）
        private final String srcClass;
        private final String srcField;

        private Param(Type type, Object defaultValue, double min, double max,
                      Set<String> allowed, int minItems, int maxItems, String id,
                      String srcClass, String srcField) {
            this.type = type;
            this.defaultValue = defaultValue;
            this.min = min;
            this.max = max;
            this.allowed = allowed;
            this.minItems = minItems;
            this.maxItems = maxItems;
            this.srcClass = srcClass;
            this.srcField = srcField;
            validateDefault(id);
        }

        private void validateDefault(String id) {
            switch (type) {
                case INT -> {
                    long v = (Long) defaultValue;
                    if (v < min || v > max) {
                        throw new IllegalArgumentException("参数 " + id + " 缺省值 " + v + " 超出 [" + (long) min + "," + (long) max + "]");
                    }
                }
                case DOUBLE -> {
                    double v = (Double) defaultValue;
                    if (!Double.isFinite(v) || v < min || v > max) {
                        throw new IllegalArgumentException("参数 " + id + " 缺省值 " + v + " 非法或超出 [" + min + "," + max + "]");
                    }
                }
                case BOOLEAN -> { /* 布尔无边界 */ }
                case STRING -> {
                    if (allowed != null && !allowed.contains(defaultValue)) {
                        throw new IllegalArgumentException("参数 " + id + " 缺省值 " + defaultValue + " 不在枚举白名单内");
                    }
                }
                case DOUBLE_LIST -> {
                    List<?> list = (List<?>) defaultValue;
                    if (list.size() < minItems || list.size() > maxItems) {
                        throw new IllegalArgumentException("参数 " + id + " 缺省列表长度 " + list.size() + " 超出 [" + minItems + "," + maxItems + "]");
                    }
                    for (Object o : list) {
                        double v = (Double) o;
                        if (!Double.isFinite(v) || v < min || v > max) {
                            throw new IllegalArgumentException("参数 " + id + " 缺省列表元素 " + v + " 非法或超出 [" + min + "," + max + "]");
                        }
                    }
                }
            }
        }

        public Type type() { return type; }
        public Object defaultTyped() { return defaultValue; }
        public double min() { return min; }
        public double max() { return max; }
        public Set<String> allowed() { return allowed; }
        public int minItems() { return minItems; }
        public int maxItems() { return maxItems; }
        public String srcClass() { return srcClass; }
        public String srcField() { return srcField; }
    }

    /** 跨字段约束：返回 null = 通过；否则返回错误说明（制作流程 §5 第 3 步「校验关系」）。 */
    public interface Constraint {
        String check(Map<String, Object> scopeValues);
    }

    /** 一个 balance 域（如 forms/<形态ID>、abilities/<能力ID>、systems/<系统ID>）。 */
    public static final class Scope {
        private final String id;
        private final Map<String, Param> params;
        private final List<Constraint> constraints;

        private Scope(String id, Map<String, Param> params, List<Constraint> constraints) {
            this.id = id;
            this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
            this.constraints = List.copyOf(constraints);
        }

        public String id() { return id; }
        public Map<String, Param> params() { return params; }
        public List<Constraint> constraints() { return constraints; }
    }

    private final Map<String, Scope> scopes;

    private BalanceSchema(Map<String, ScopeBuilder> builders) {
        Map<String, Scope> result = new LinkedHashMap<>();
        for (Map.Entry<String, ScopeBuilder> e : builders.entrySet()) {
            result.put(e.getKey(), new Scope(e.getKey(), e.getValue().params, e.getValue().constraints));
        }
        this.scopes = Collections.unmodifiableMap(result);
    }

    public Map<String, Scope> scopes() { return scopes; }
    public int schemaVersion() { return CURRENT_SCHEMA_VERSION; }

    public static SchemaBuilder create() { return new SchemaBuilder(); }

    public static final class SchemaBuilder {
        private final Map<String, ScopeBuilder> scopes = new LinkedHashMap<>();

        public ScopeBuilder scope(String id) {
            ScopeBuilder b = new ScopeBuilder(id);
            scopes.put(id, b);
            return b;
        }

        public BalanceSchema build() { return new BalanceSchema(scopes); }
    }

    public static final class ScopeBuilder {
        private final Map<String, Param> params = new LinkedHashMap<>();
        private final List<Constraint> constraints = new ArrayList<>();

        /** id 由 SchemaBuilder 的 map key 承载（BalanceSchema 构造时用 e.getKey()），此处无需保存。 */
        private ScopeBuilder(String id) { }

        public ScopeBuilder intParam(String id, long def, long min, long max) {
            return intParam(id, def, min, max, null, null);
        }

        /** 带代码常量来源注解的登记：pin 测试自动反射对照该常量，改任一侧不同步即构建红。 */
        public ScopeBuilder intParam(String id, long def, long min, long max, String srcClass, String srcField) {
            params.put(id, new Param(Type.INT, def, min, max, null, 0, 0, id, srcClass, srcField));
            return this;
        }

        public ScopeBuilder doubleParam(String id, double def, double min, double max) {
            return doubleParam(id, def, min, max, null, null);
        }

        /** 带代码常量来源注解的登记（同 intParam）。 */
        public ScopeBuilder doubleParam(String id, double def, double min, double max, String srcClass, String srcField) {
            params.put(id, new Param(Type.DOUBLE, def, min, max, null, 0, 0, id, srcClass, srcField));
            return this;
        }

        public ScopeBuilder boolParam(String id, boolean def) {
            params.put(id, new Param(Type.BOOLEAN, def, 0, 0, null, 0, 0, id, null, null));
            return this;
        }

        public ScopeBuilder stringParam(String id, String def, String... allowed) {
            Set<String> whitelist = allowed == null || allowed.length == 0 ? null : Set.of(allowed);
            params.put(id, new Param(Type.STRING, def, 0, 0, whitelist, 0, 0, id, null, null));
            return this;
        }

        public ScopeBuilder doubleListParam(String id, List<Double> def, int minItems, int maxItems) {
            params.put(id, new Param(Type.DOUBLE_LIST, List.copyOf(def), 0, 0, null, minItems, maxItems, id, null, null));
            return this;
        }

        /** violated 为 true 时以 message 报错（如「外半径必须 ≥ 内半径」）。 */
        public ScopeBuilder constraint(String message, Predicate<Map<String, Object>> violated) {
            constraints.add(values -> Boolean.TRUE.equals(violated.test(values)) ? message : null);
            return this;
        }
    }
}
