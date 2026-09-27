package net.jackcooper.shapeShifterCurseAddon.balance;

import java.util.List;
import java.util.Map;

/**
 * balance 框架自检（阶段 2 · 制作流程 §10.1「配置解析与兼容」）。
 *
 * 覆盖：默认快照、字段级合并、覆盖方向、非法输入（字符串冒充数字/非整数 CD/NaN/Infinity/
 * 负伤害/零间隔/超大数量/null/语法错误）、未知字段与未知域拒绝、跨字段约束、
 * hash 确定性（分层无关）、schema_version 门控。
 * 纯逻辑：不启动 MC，直接构造 schema 与 Json 文本。
 */
public final class BalanceFrameworkTest {

    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        schemaDefaultsMatchCode();
        partialPatchInheritsDefaults();
        overrideDirectionAndRemoval();
        illegalInputsRejected();
        unknownFieldAndScopeRejected();
        crossFieldConstraint();
        hashDeterminismAcrossLayering();
        schemaVersionGating();

        System.out.println("Balance framework: " + checks + " checks, " + failures + " failures"
                + (failures == 0 ? " — all passed." : " — FAILURES PRESENT."));
        if (failures > 0) {
            System.exit(1);
        }
    }

    // ---- 测试用 schema：模拟雪狐姿态移速 + 音波技能两个域 ----

    private static BalanceSchema testSchema() {
        var builder = BalanceSchema.create();
        builder.scope("forms.snow_fox_sp")
                .doubleParam("melee_speed_bonus", 0.1, -2.0, 2.0)
                .doubleParam("ranged_speed_penalty", -0.1, -2.0, 2.0)
                .constraint("近战加成必须大于等于远程惩罚", v ->
                        (Double) v.get("melee_speed_bonus") < (Double) v.get("ranged_speed_penalty"));
        builder.scope("abilities.bat_sonic_wave")
                .intParam("damage", 6, 0, 1000)
                .intParam("cooldown_ticks", 160, 1, 24000)
                .doubleParam("range", 8.0, 0.0, 64.0)
                .doubleParam("half_width", 1.75, 0.0, 16.0);
        return builder.build();
    }

    private static void schemaDefaultsMatchCode() {
        var s = BalanceSnapshot.defaults(testSchema());
        check(s.getDouble("forms.snow_fox_sp", "melee_speed_bonus") == 0.1, "默认近战加成 = 0.1");
        check(s.getDouble("forms.snow_fox_sp", "ranged_speed_penalty") == -0.1, "默认远程惩罚 = -0.1");
        check(s.getInt("abilities.bat_sonic_wave", "damage") == 6, "音波默认伤害 = 6");
        check(s.getInt("abilities.bat_sonic_wave", "cooldown_ticks") == 160, "音波默认 CD = 160");
        check(s.hash() != null && s.hash().length() == 64, "默认快照 hash 为 64 位十六进制");
        // 默认快照 = 仅含 schema_version 的空树 fromTree
        var fromEmpty = BalanceSnapshot.fromTree(testSchema(), Map.of("schema_version", 1L), "empty");
        check(fromEmpty.hash().equals(s.hash()), "空树快照与默认快照 hash 一致");
    }

    private static void partialPatchInheritsDefaults() {
        // 单层部分补丁：只写 damage → 其余继承默认
        var merged = BalanceMerge.merge(List.of(
                layer("packB", "{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":8}}}")));
        var snap = BalanceSnapshot.fromTree(testSchema(), merged, "merged");
        check(snap.getInt("abilities.bat_sonic_wave", "damage") == 8, "部分补丁只改 damage=8");
        check(snap.getInt("abilities.bat_sonic_wave", "cooldown_ticks") == 160, "其余字段继承默认 160");
    }

    private static void overrideDirectionAndRemoval() {
        // 低优先级 packA damage=7；高优先级 packB 只覆盖 cooldown → damage 仍 7（高层仅覆盖明确给出的字段）
        var merged = BalanceMerge.merge(List.of(
                layer("packA", "{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":7}}}"),
                layer("packB", "{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"cooldown_ticks\":200}}}")));
        var snap = BalanceSnapshot.fromTree(testSchema(), merged, "merged");
        check(snap.getInt("abilities.bat_sonic_wave", "damage") == 7, "低层字段未被高层无关字段影响");
        check(snap.getInt("abilities.bat_sonic_wave", "cooldown_ticks") == 200, "高层明确给出的字段生效");
    }

    private static void illegalInputsRejected() {
        expectReject("字符串冒充数字", () -> BalanceSnapshot.fromTree(testSchema(),
                tree("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":\"8\"}}}"), "t"));
        expectReject("非整数 CD", () -> BalanceSnapshot.fromTree(testSchema(),
                tree("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"cooldown_ticks\":160.5}}}"), "t"));
        expectReject("NaN", () -> BalanceSnapshot.fromTree(testSchema(),
                tree("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"range\":NaN}}}"), "t"));
        expectReject("Infinity", () -> BalanceSnapshot.fromTree(testSchema(),
                tree("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"range\":Infinity}}}"), "t"));
        expectReject("负伤害", () -> BalanceSnapshot.fromTree(testSchema(),
                tree("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":-5}}}"), "t"));
        expectReject("零间隔", () -> BalanceSnapshot.fromTree(testSchema(),
                tree("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"cooldown_ticks\":0}}}"), "t"));
        expectReject("超大数量", () -> BalanceSnapshot.fromTree(testSchema(),
                tree("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":99999999999999999999}}}"), "t"));
        expectReject("null 字段", () -> {
            var merged = BalanceMerge.merge(List.of(layer("p", "{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":null}}}")));
            BalanceSnapshot.fromTree(testSchema(), merged, "t");
        });
        expectReject("JSON 语法错误", () -> BalanceJson.parse("p", "{\"schema_version\":1,"));
    }

    private static void unknownFieldAndScopeRejected() {
        expectReject("未知字段", () -> BalanceSnapshot.fromTree(testSchema(),
                tree("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damag\":8}}}"), "t"));
        expectReject("未知配置域", () -> BalanceSnapshot.fromTree(testSchema(),
                tree("{\"schema_version\":1,\"forms\":{\"bat_sonic_wave\":{\"damage\":8}}}"), "t"));
        expectReject("顶层非对象", () -> BalanceSnapshot.fromTree(testSchema(),
                tree("{\"schema_version\":1,\"abilities\":5}"), "t"));
    }

    private static void crossFieldConstraint() {
        expectReject("跨字段约束（近战加成小于远程惩罚）", () -> BalanceSnapshot.fromTree(testSchema(),
                tree("{\"schema_version\":1,\"forms\":{\"snow_fox_sp\":{\"melee_speed_bonus\":-0.5,\"ranged_speed_penalty\":0.2}}}"), "t"));
        // 合法方向通过
        var ok = BalanceSnapshot.fromTree(testSchema(),
                tree("{\"schema_version\":1,\"forms\":{\"snow_fox_sp\":{\"melee_speed_bonus\":0.2,\"ranged_speed_penalty\":-0.5}}}"), "t");
        check(ok.getDouble("forms.snow_fox_sp", "melee_speed_bonus") == 0.2, "合法跨字段组合通过");
    }

    private static void hashDeterminismAcrossLayering() {
        // 方式 1：单层直接给全部值
        var single = BalanceSnapshot.fromTree(testSchema(),
                tree("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":9,\"range\":10.0}}}"), "t");
        // 方式 2：两层叠加得到相同有效值
        var layered = BalanceSnapshot.fromTree(testSchema(), BalanceMerge.merge(List.of(
                layer("low", "{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":7,\"range\":10.0}}}"),
                layer("high", "{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":9}}}"))), "t");
        check(single.hash().equals(layered.hash()), "hash 与分层方式无关（确定性）");
        // 值不同 → hash 必须不同
        var other = BalanceSnapshot.fromTree(testSchema(),
                tree("{\"schema_version\":1,\"abilities\":{\"bat_sonic_wave\":{\"damage\":10,\"range\":10.0}}}"), "t");
        check(!single.hash().equals(other.hash()), "值不同则 hash 不同");
    }

    private static void schemaVersionGating() {
        expectReject("schema_version 缺失", () -> BalanceSnapshot.fromTree(testSchema(),
                tree("{\"abilities\":{\"bat_sonic_wave\":{\"damage\":8}}}"), "t"));
        expectReject("schema_version=2（未来版本）", () -> BalanceSnapshot.fromTree(testSchema(),
                tree("{\"schema_version\":2,\"abilities\":{\"bat_sonic_wave\":{\"damage\":8}}}"), "t"));
        expectReject("schema_version 是字符串", () -> BalanceSnapshot.fromTree(testSchema(),
                tree("{\"schema_version\":\"1\",\"abilities\":{\"bat_sonic_wave\":{\"damage\":8}}}"), "t"));
    }

    // ---- 工具 ----

    private static Map<String, Object> tree(String json) {
        return BalanceJson.parse("t", json).tree;
    }

    private static BalanceMerge.Layer layer(String source, String json) {
        return new BalanceMerge.Layer(source, BalanceJson.parse(source, json).tree);
    }

    private static void expectReject(String name, Runnable r) {
        checks++;
        try {
            r.run();
            failures++;
            System.out.println("  [FAIL] " + name + "：未被拒绝");
        } catch (BalanceSnapshot.BalanceRejectException | BalanceMerge.BalanceRejectException expected) {
            // 拒绝符合预期
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (condition) return;
        failures++;
        System.out.println("  [FAIL] " + message);
    }
}
