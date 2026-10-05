package net.jackcooper.shapeShifterCurseAddon.spell.research;

import net.minecraft.text.Text;
import java.util.*;

/**
 * 阶段B：试运行分级诊断（2026-10-01 获批方案，服务端权威）。
 *
 * <p>TEST 失败时按环（中央核心/小环/大环/超大环）给出分级反馈：空槽数量、结构符文错位数、
 * 语义组合是否成立、该环是否完整成立、各环是否互相矛盾。只报「区域 + 问题类型」，
 * 不泄露任何具体符文或逐格答案；合法解与图纸照抄路径不受影响。</p>
 *
 * <p>结构槽定义：该等级下所有可用配方在该位置期望角色完全一致的位置（由配方表派生，非硬编码）；
 * 其余为语义槽（元素/行为/修饰，随配方变化）。</p>
 */
public final class FormationDiagnosis {
    private FormationDiagnosis() {}

    /** 服务端入口：按当前等级的真实可用配方（含稀有度/最大等级过滤）做诊断。 */
    public static int[] diagnose(int level, int[] slots, RuneLanguage language) {
        List<SlottedSpellRecipes.Recipe> recipes = new ArrayList<>();
        for (var recipe : SlottedSpellRecipes.all()) if (SlottedSpellRecipes.available(recipe, level)) recipes.add(recipe);
        return diagnose(level, slots, language, recipes);
    }

    /**
     * 纯逻辑核心（可脱离 Minecraft 类在 JVM 测试中直接运行）。
     * 扁平编码：[层数, (空槽数, 结构错位数, 语义不符0/1, 该环成立0/1)×层数, 组合不一致0/1]。
     * 返回空数组表示输入非法或无可用配方（调用方回退到原 invalid 提示）。
     */
    public static int[] diagnose(int level, int[] slots, RuneLanguage language, List<SlottedSpellRecipes.Recipe> recipes) {
        if (!SlottedFormation.validDraft(level, slots) || recipes.isEmpty()) return new int[0];
        int[] counts = SlottedFormation.layers(level); int size = SlottedFormation.size(level);
        int[][] roleTable = new int[recipes.size()][];
        for (int r = 0; r < recipes.size(); r++) {
            List<RuneRole> roles = SlottedSpellRecipes.roles(recipes.get(r), level);
            int[] arr = new int[size];
            for (int p = 0; p < size; p++) arr[p] = roles.get(p).ordinal();
            roleTable[r] = arr;
        }
        // 结构槽：所有可用配方在该位置期望角色一致
        boolean[] isFixed = new boolean[size]; int[] fixedRole = new int[size]; Arrays.fill(isFixed, true);
        for (int p = 0; p < size; p++) {
            for (int r = 1; r < recipes.size(); r++) if (roleTable[r][p] != roleTable[0][p]) { isFixed[p] = false; break; }
            fixedRole[p] = isFixed[p] ? roleTable[0][p] : -1;
        }
        List<Integer> flat = new ArrayList<>(); flat.add(counts.length);
        int offset = 0; boolean allComplete = true;
        for (int layer = 0; layer < counts.length; layer++) {
            int count = counts[layer], empty = 0, mismatches = 0; boolean complete = false;
            boolean rotationLayer = level >= 4 && layer == counts.length - 1; // 阶段D：最外环可整体旋转
            for (int p = offset; p < offset + count; p++) {
                if (slots[p] == -1) { empty++; continue; }
                if (isFixed[p] && language.meaning(slots[p]).ordinal() != fixedRole[p]) mismatches++;
            }
            if (empty == 0) {
                for (int r = 0; r < recipes.size() && !complete; r++) {
                    if (rotationLayer) complete = ringEqual(roleTable[r], offset, count, slots, language);
                    else {
                        boolean match = true;
                        for (int p = offset; p < offset + count; p++) if (language.meaning(slots[p]).ordinal() != roleTable[r][p]) { match = false; break; }
                        if (match) complete = true;
                    }
                }
            }
            allComplete &= complete;
            int semantic = (empty == 0 && mismatches == 0 && !complete) ? 1 : 0;
            flat.addAll(List.of(empty, mismatches, semantic, complete ? 1 : 0));
            offset += count;
        }
        flat.add(allComplete ? 1 : 0);
        return flat.stream().mapToInt(Integer::intValue).toArray();
    }

    /** 阶段D：旋转感知的环相等（角色序列）——actual 环是某配方规范环的任意整体旋转则成立。 */
    private static boolean ringEqual(int[] canonicalRoles, int from, int count, int[] slots, RuneLanguage language) {
        for (int shift = 0; shift < count; shift++) {
            boolean same = true;
            for (int i = 0; i < count; i++) {
                if (language.meaning(slots[from + i]).ordinal() != canonicalRoles[from + (i + shift) % count]) { same = false; break; }
            }
            if (same) return true;
        }
        return false;
    }

    /** 把扁平诊断编码拼成一条玩家可读消息（各环问题按层序列出，分隔符「；」）。 */
    public static Text message(int[] flat) {
        if (flat.length < 6) return Text.translatable("research.ssc_addon.slotted.invalid");
        int layers = Math.min(flat[0], (flat.length - 2) / 4);
        boolean inconsistent = flat[flat.length - 1] > 0;
        List<Text> parts = new ArrayList<>();
        for (int layer = 0; layer < layers; layer++) {
            int empty = flat[1 + layer * 4], structural = flat[1 + layer * 4 + 1], semantic = flat[1 + layer * 4 + 2], complete = flat[1 + layer * 4 + 3];
            Text name = Text.translatable("research.ssc_addon.slotted.layer." + layer);
            if (empty > 0) parts.add(Text.translatable("research.ssc_addon.slotted.diag.empty", name, empty));
            else if (structural > 0) parts.add(Text.translatable("research.ssc_addon.slotted.diag.structure", name, structural));
            else if (semantic > 0) parts.add(Text.translatable("research.ssc_addon.slotted.diag.semantic", name));
            else if (complete > 0) parts.add(Text.translatable("research.ssc_addon.slotted.diag.complete", name));
        }
        if (inconsistent) parts.add(Text.translatable("research.ssc_addon.slotted.diag.inconsistent"));
        if (parts.isEmpty()) return Text.translatable("research.ssc_addon.slotted.invalid");
        net.minecraft.text.MutableText result = Text.literal("");
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) result.append(Text.literal("；"));
            result.append(parts.get(index));
        }
        return result;
    }
}
