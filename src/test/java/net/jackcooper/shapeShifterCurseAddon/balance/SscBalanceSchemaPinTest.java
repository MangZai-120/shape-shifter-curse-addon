package net.jackcooper.shapeShifterCurseAddon.balance;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * 生产 schema 默认值 pin 测试（阶段 1）：守卫「schema 默认值 = 代码现值」。
 *
 * 自动遍历 schema 的来源注解（srcClass/srcField）反射对照代码常量；
 * 无常量来源的字面量参数在 LITERAL_PINS 显式维护。
 * 改任一侧的数值都必须同步另一侧与台账，否则此处失败。
 */
public final class SscBalanceSchemaPinTest {

    private static int checks;
    private static int failures;
    private static int annotated;
    private static int literals;

    /** 字面量参数（代码中无常量，函数内字面量）：scope / 参数 / 期望代码现值。 */
    private static final Object[][] LITERAL_PINS = {
            {"spells.flame_nova", "knockback", 0.8},
            {"spells.flame_nova", "rarity_radius_multiplier", 1.25},
    };

    public static void main(String[] args) {
        var schema = SscBalanceSchema.create();
        var s = BalanceSnapshot.defaults(schema);

        // 1) 来源注解参数：自动反射对照
        for (var scope : schema.scopes().values()) {
            for (var entry : scope.params().entrySet()) {
                var p = entry.getValue();
                if (p.srcClass() == null) continue;
                annotated++;
                double codeValue = constOf(p.srcClass(), p.srcField());
                double schemaValue = readAsDouble(s, scope.id(), entry.getKey());
                checks++;
                if (schemaValue != codeValue) {
                    failures++;
                    System.out.println("  [FAIL] " + scope.id() + "." + entry.getKey()
                            + "：schema=" + schemaValue + " ≠ 代码 " + p.srcClass() + "." + p.srcField() + "=" + codeValue);
                }
            }
        }

        // 2) 字面量参数：显式对照
        for (Object[] pin : LITERAL_PINS) {
            literals++;
            String scopeId = (String) pin[0];
            String param = (String) pin[1];
            double expected = (Double) pin[2];
            double schemaValue = readAsDouble(s, scopeId, param);
            checks++;
            if (schemaValue != expected) {
                failures++;
                System.out.println("  [FAIL] " + scopeId + "." + param + "：schema=" + schemaValue + " ≠ 代码字面量=" + expected);
            }
        }

        System.out.println("Balance schema pin: " + checks + " checks (" + annotated + " annotated + " + literals
                + " literal), " + failures + " failures" + (failures == 0 ? " — all passed." : " — FAILURES PRESENT."));
        if (failures > 0) System.exit(1);
    }

    /** INT 域取 long、DOUBLE 域取 double，统一成 double 比较。 */
    private static double readAsDouble(BalanceSnapshot s, String scope, String param) {
        try {
            return s.getDouble(scope, param);
        } catch (IllegalStateException intParam) {
            return s.getInt(scope, param);
        }
    }

    /** 常量值读取缓存：类名 → (字段名 → 池化常量值)。 */
    private static final Map<String, Map<String, Object>> CONST_POOL_CACHE = new HashMap<>();

    /**
     * 读代码常量现值。优先 ASM 字节码常量池（不触发目标类 <clinit>）。
     * float 常量按「最短十进制往返」归一（0.8333333f → 0.8333333）：schema 默认值以
     * 程序员书写字面量登记（double 字面量或 float 字面量→拓宽），往返归一后两侧相等；
     * 已实践验证 37 处 float 常量（0.06f/1.15f/2.0f 等）全部通过。
     */
    private static double constOf(String srcClass, String srcField) {
        Object v = readConstant(srcClass, srcField);
        if (v instanceof Float fl) {
            return Double.parseDouble(Float.toString(fl));
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalStateException(srcClass + "." + srcField + " 无法读取数值常量，实际=" + v);
    }

    private static Object readConstant(String srcClass, String srcField) {
        Map<String, Object> pool = CONST_POOL_CACHE.computeIfAbsent(srcClass, SscBalanceSchemaPinTest::scanConstants);
        if (pool.containsKey(srcField)) {
            return pool.get(srcField);
        }
        // 常量池未命中 → 反射兜底（目标类静态初始化可失败的场景会在下面抛出）
        return reflectConstant(srcClass, srcField);
    }

    /** ASM 扫描类字节码的 static final 数值字段的 ConstantValue 属性（不加载类）。 */
    private static Map<String, Object> scanConstants(String srcClass) {
        String fqn = "net.jackcooper.shapeShifterCurseAddon." + srcClass;
        String resource = "/" + fqn.replace('.', '/') + ".class";
        Map<String, Object> out = new HashMap<>();
        try (InputStream in = SscBalanceSchemaPinTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("找不到类资源：" + resource);
            }
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                    if (value != null) {
                        out.put(name, value);
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE);
        } catch (Exception e) {
            throw new IllegalStateException("ASM 扫描失败：" + fqn, e);
        }
        return out;
    }

    private static Object reflectConstant(String srcClass, String srcField) {
        String fqn = "net.jackcooper.shapeShifterCurseAddon." + srcClass;
        try {
            Class<?> owner = Class.forName(fqn);
            Field f = owner.getDeclaredField(srcField);
            f.setAccessible(true);
            return f.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("无法读取 " + fqn + "." + srcField
                    + "（常量池未池化且反射初始化失败；请改用编译期常量或手写 pin）", e);
        }
    }
}
