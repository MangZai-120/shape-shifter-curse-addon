package net.jackcooper.shapeShifterCurseAddon.balance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * balance 有效配置快照（制作流程 §5 的 BalanceSnapshot 组件，阶段 2）。
 *
 * 输入：schema + 已合并（或空）的 Json 树 → 输出：不可变的、已校验的类型化字段表 + 确定性 hash。
 * 校验失败抛 {@link BalanceRejectException}，调用方保留上一份有效快照（文档 §5 第 6 步）。
 */
public final class BalanceSnapshot {

    /** 校验/加载拒绝：携带来源包与字段路径，绝不产出半份配置。 */
    public static final class BalanceRejectException extends RuntimeException {
        public final String source;
        public final String fieldPath;

        public BalanceRejectException(String message, String source, String fieldPath) {
            super(message);
            this.source = source;
            this.fieldPath = fieldPath;
        }
    }

    private final int schemaVersion;
    private final Map<String, Map<String, Object>> values;   // scopeId -> paramId -> typed value
    private final String hash;
    private java.util.Set<String> explicitPowerOverrides = java.util.Set.of();
    public boolean overridesPower(String scope, String param) { return explicitPowerOverrides.contains(scope + "." + param); }

    private BalanceSnapshot(int schemaVersion, Map<String, Map<String, Object>> values, String hash) {
        this.schemaVersion = schemaVersion;
        this.values = deepImmutable(values);
        this.hash = hash;
    }

    /** 无任何数据包时的全默认快照（文档 §5：首次启动/无外部包 → 内置默认定义）。 */
    public static BalanceSnapshot defaults(BalanceSchema schema) {
        Map<String, Map<String, Object>> all = new LinkedHashMap<>();
        for (BalanceSchema.Scope scope : schema.scopes().values()) {
            Map<String, Object> scopeValues = new LinkedHashMap<>();
            for (Map.Entry<String, BalanceSchema.Param> p : scope.params().entrySet()) {
                scopeValues.put(p.getKey(), p.getValue().defaultTyped());
            }
            all.put(scope.id(), scopeValues);
        }
        return new BalanceSnapshot(schema.schemaVersion(), all, computeHash(schema, all));
    }

    /**
     * 从合并后的树构造快照：先逐 scope 校验（类型/有限性/边界/枚举/列表长度），再跑跨字段约束。
     * 任一失败 → 抛 BalanceRejectException（整批拒绝，不产出半份配置）。
     *
     * @param tree   合并后的有效内容树（可传 null/空 = 全默认）
     * @param source 报错定位用的来源包描述
     */
    public static BalanceSnapshot fromTree(BalanceSchema schema, Map<String, Object> tree, String source) {
        if (tree == null) tree = Map.of();

        // schema_version 门控：缺失或与当前版本不符 → 整批拒绝（防止旧格式被误读）
        Object versionRaw = tree.get("schema_version");
        if (!(versionRaw instanceof Number n) || n.doubleValue() != BalanceSchema.CURRENT_SCHEMA_VERSION) {
            throw new BalanceRejectException(
                    "schema_version 缺失或不符（期望 " + BalanceSchema.CURRENT_SCHEMA_VERSION + "）",
                    source, "schema_version");
        }

        Map<String, Map<String, Object>> all = new LinkedHashMap<>();
        // 1) 每个 scope：缺字段继承默认；出现的字段逐一校验（scope id 按点号嵌套进树，对应 balance/forms/<id>.json 目录结构）
        for (BalanceSchema.Scope scope : schema.scopes().values()) {
            Map<String, Object> scopeValues = new LinkedHashMap<>();
            Object scopeRaw = resolveNested(tree, scope.id());
            Map<String, Object> scopeTree = scopeRaw == null ? null : asMap(scopeRaw, source, scope.id());
            for (Map.Entry<String, BalanceSchema.Param> p : scope.params().entrySet()) {
                String paramId = p.getKey();
                BalanceSchema.Param param = p.getValue();
                Object raw = scopeTree == null ? null : scopeTree.get(paramId);
                if (scopeTree != null && scopeTree.containsKey(paramId) && raw == null)
                    throw new BalanceRejectException("Null parameter", source, scope.id() + "." + paramId);
                scopeValues.put(paramId, raw == null ? param.defaultTyped() : validate(param, raw, source, scope.id() + "." + paramId));
            }
            // scope 下未知字段 = 疑似拼写错误 → 拒绝（文档 §5 第 2 步「未知ID」）
            if (scopeTree != null) {
                for (String key : scopeTree.keySet()) {
                    if (!scope.params().containsKey(key)) {
                        throw new BalanceRejectException("未知字段 " + key + "（不在 " + scope.id() + " 的 schema 登记内）", source, scope.id() + "." + key);
                    }
                }
            }
            all.put(scope.id(), scopeValues);
        }
        // 2) 树里出现 schema 未登记的域 → 拒绝（顶级目录 + 二级 id 都校验，防拼错文件名）
        validateTreeKeys(schema, tree, "", source);

        // 3) 跨字段约束（文档 §5 第 3 步）
        for (BalanceSchema.Scope scope : schema.scopes().values()) {
            for (BalanceSchema.Constraint c : scope.constraints()) {
                String err = c.check(all.get(scope.id()));
                if (err != null) {
                    throw new BalanceRejectException("跨字段约束失败：" + err, source, scope.id());
                }
            }
        }
        java.util.Set<String> explicit = new java.util.TreeSet<>();
        Object snow = resolveNested(tree, "forms.snow_fox_sp");
        if (snow instanceof Map<?, ?> fields) {
            for (String key : java.util.List.of("melee_speed_bonus", "ranged_speed_penalty"))
                if (fields.containsKey(key)) explicit.add("forms.snow_fox_sp." + key);
        }
        String hash = computeHash(schema, all);
        if (!explicit.isEmpty()) hash = sha256Hex(hash + explicit);
        var snapshot = new BalanceSnapshot(schema.schemaVersion(), all, hash);
        snapshot.explicitPowerOverrides = java.util.Set.copyOf(explicit);
        return snapshot;
    }

    private static void validateTreeKeys(BalanceSchema schema, Map<String, Object> tree, String prefix, String source) {
        if (schema.scopes().containsKey(prefix)) return;
        for (var entry : tree.entrySet()) {
            if (prefix.isEmpty() && entry.getKey().equals("schema_version")) continue;
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (!schema.scopes().containsKey(path) && schema.scopes().keySet().stream().noneMatch(id -> id.startsWith(path + ".")))
                throw new BalanceRejectException("Unknown balance scope: " + path, source, path);
            validateTreeKeys(schema, asMap(entry.getValue(), source, path), path, source);
        }
    }

    /** 按 scope id（如 forms.snow_fox_sp）逐段下钻嵌套树；任一段缺失返回 null（= 全默认）。 */
    private static Object resolveNested(Map<String, Object> tree, String scopeId) {
        Object current = tree;
        for (String part : scopeId.split("\\.")) {
            if (!(current instanceof Map<?, ?> m)) return null;
            current = m.get(part);
            if (current == null) return null;
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o, String source, String path) {
        if (o instanceof Map) return (Map<String, Object>) o;
        throw new BalanceRejectException(path + " 必须是对象", source, path);
    }

    private static Object validate(BalanceSchema.Param param, Object raw, String source, String path) {
        switch (param.type()) {
            case INT -> {
                if (!(raw instanceof Long l)) {
                    throw new BalanceRejectException(path + " 期望整数，实际 " + describe(raw), source, path);
                }
                if (l < (long) param.min() || l > (long) param.max()) {
                    throw new BalanceRejectException(path + " 值 " + l + " 超出 [" + (long) param.min() + "," + (long) param.max() + "]", source, path);
                }
                return l;
            }
            case DOUBLE -> {
                // INT 字面量（Gson/手写包常见）在 DOUBLE 域宽容提升：8 → 8.0
                Double d;
                if (raw instanceof Long l) {
                    d = l.doubleValue();
                } else {
                    d = asFiniteDouble(raw, source, path);
                }
                if (d < param.min() || d > param.max()) {
                    throw new BalanceRejectException(path + " 值 " + d + " 超出 [" + param.min() + "," + param.max() + "]", source, path);
                }
                return d;
            }
            case BOOLEAN -> {
                if (!(raw instanceof Boolean)) {
                    throw new BalanceRejectException(path + " 期望布尔，实际 " + describe(raw), source, path);
                }
                return raw;
            }
            case STRING -> {
                if (!(raw instanceof String s)) {
                    throw new BalanceRejectException(path + " 期望字符串，实际 " + describe(raw), source, path);
                }
                if (param.allowed() != null && !param.allowed().contains(s)) {
                    throw new BalanceRejectException(path + " 值 " + s + " 不在枚举白名单内", source, path);
                }
                return s;
            }
            case DOUBLE_LIST -> {
                if (!(raw instanceof List<?> list)) {
                    throw new BalanceRejectException(path + " 期望数值数组，实际 " + describe(raw), source, path);
                }
                if (list.size() < param.minItems() || list.size() > param.maxItems()) {
                    throw new BalanceRejectException(path + " 数组长度 " + list.size() + " 超出 [" + param.minItems() + "," + param.maxItems() + "]", source, path);
                }
                List<Double> out = new ArrayList<>(list.size());
                for (int i = 0; i < list.size(); i++) {
                    Object e = list.get(i);
                    if (e == null) {
                        throw new BalanceRejectException(path + "[" + i + "] 为 null", source, path);
                    }
                    Double d = asFiniteDouble(e, source, path + "[" + i + "]");
                    if (d < param.min() || d > param.max()) {
                        throw new BalanceRejectException(path + "[" + i + "] 值 " + d + " 超出 [" + param.min() + "," + param.max() + "]", source, path);
                    }
                    out.add(d);
                }
                return Collections.unmodifiableList(out);
            }
        }
        throw new IllegalStateException("未知类型 " + param.type());
    }

    private static Double asFiniteDouble(Object raw, String source, String path) {
        if (raw instanceof Double d) {
            if (!Double.isFinite(d)) {
                throw new BalanceRejectException(path + " 非有限数（NaN/Infinity）", source, path);
            }
            return d;
        }
        if (raw instanceof Float f) {
            if (!Double.isFinite(f)) {
                throw new BalanceRejectException(path + " 非有限数", source, path);
            }
            return f.doubleValue();
        }
        if (raw instanceof Long l) {
            if (l < -4503599627370496L || l > 4503599627370496L) {   // 2^52，超过会丢精度
                throw new BalanceRejectException(path + " 整数 " + l + " 超出安全双精度范围", source, path);
            }
            return l.doubleValue();
        }
        if (raw instanceof Integer i) {
            return i.doubleValue();
        }
        throw new BalanceRejectException(path + " 期望数值，实际 " + describe(raw), source, path);
    }

    private static String describe(Object raw) {
        if (raw == null) return "null";
        return raw.getClass().getSimpleName() + "(" + raw + ")";
    }

    private static Map<String, Map<String, Object>> deepImmutable(Map<String, Map<String, Object>> values) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : values.entrySet()) {
            out.put(e.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(e.getValue())));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * 确定性内容 hash（制作流程 §5 第 4 步）：按 schema 登记顺序遍历，数值用 doubleToLongBits 规范化，
     * 不含时间戳或遍历随机顺序 → 分层方式不同但有效值相同 ⇒ hash 相同。
     */
    private static String computeHash(BalanceSchema schema, Map<String, Map<String, Object>> values) {
        StringBuilder sb = new StringBuilder();
        for (BalanceSchema.Scope scope : schema.scopes().values()) {
            sb.append(scope.id()).append('{');
            Map<String, Object> scopeValues = values.get(scope.id());
            for (Map.Entry<String, BalanceSchema.Param> p : scope.params().entrySet()) {
                sb.append(p.getKey()).append('=');
                appendValue(sb, scopeValues.get(p.getKey()));
                sb.append(';');
            }
            sb.append('}');
        }
        return sha256Hex(sb.toString());
    }

    private static void appendValue(StringBuilder sb, Object v) {
        if (v instanceof Long l) {
            sb.append('i').append(l);
        } else if (v instanceof Double d) {
            sb.append('d').append(Long.toHexString(Double.doubleToLongBits(d)));
        } else if (v instanceof Boolean b) {
            sb.append('b').append(b);
        } else if (v instanceof String s) {
            sb.append('s').append(s);
        } else if (v instanceof List<?> list) {
            sb.append('[');
            for (Object e : list) {
                appendValue(sb, e);
                sb.append(',');
            }
            sb.append(']');
        } else {
            throw new IllegalStateException("hash 遇到未登记类型：" + v);
        }
    }

    private static String sha256Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public int schemaVersion() { return schemaVersion; }

    /** 内容 hash：一致性检测用（文档 §6.2），不代替传输安全。 */
    public String hash() { return hash; }

    /** 全部有效值（不可变视图）：scopeId -> (paramId -> typed value)。 */
    public Map<String, Map<String, Object>> values() { return values; }

    public Map<String, Object> scope(String scopeId) {
        Map<String, Object> v = values.get(scopeId);
        if (v == null) throw new IllegalArgumentException("未知配置域：" + scopeId);
        return v;
    }

    public long getInt(String scopeId, String paramId) {
        return (Long) require(scopeId, paramId, Long.class);
    }

    public double getDouble(String scopeId, String paramId) {
        return (Double) require(scopeId, paramId, Double.class);
    }

    public boolean getBool(String scopeId, String paramId) {
        return (Boolean) require(scopeId, paramId, Boolean.class);
    }

    public String getString(String scopeId, String paramId) {
        return (String) require(scopeId, paramId, String.class);
    }

    @SuppressWarnings("unchecked")
    public List<Double> getDoubleList(String scopeId, String paramId) {
        return (List<Double>) require(scopeId, paramId, List.class);
    }

    private Object require(String scopeId, String paramId, Class<?> expected) {
        Map<String, Object> scopeValues = values.get(scopeId);
        if (scopeValues == null) {
            throw new IllegalArgumentException("未知配置域：" + scopeId);
        }
        Object v = scopeValues.get(paramId);
        if (v == null) {
            throw new IllegalArgumentException("未知参数：" + scopeId + "." + paramId);
        }
        if (!expected.isInstance(v)) {
            throw new IllegalStateException(scopeId + "." + paramId + " 类型不符：期望 " + expected.getSimpleName() + " 实际 " + v.getClass().getSimpleName());
        }
        return v;
    }
}
