package net.jackcooper.shapeShifterCurseAddon.spell;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 玩家法术知识数据组件（jackcooper）：记录「已记录的法阵」与「已学习的法阵」。
 *
 * <p>两级数据（都跟随玩家、多人各自独立）：</p>
 * <ul>
 *   <li><b>已记录（recorded）</b>：右键使用法阵物品获得，格式 {@code element[/variant]:level}；
 *       同系各等级独立记录（高等级不覆盖低等级，学习时自选等级）；通用系三变体
 *       （regen/mana/exp）各自独立记录；</li>
 *   <li><b>已学习（learned）</b>：研究台消耗月尘学习后获得，只存最高等级（低等级自动包含）；
 *       学习后才能在研究台抄写对应等级的法阵。存储键 {@code element[/variant]}。</li>
 * </ul>
 *
 * <p><b>旧档迁移（2026-09-15 变体拆分）</b>：旧记录键 {@code universal:N} 归为回能变体
 * {@code universal/regen:N}；旧学习键 {@code learned_universal} 归为
 * {@code learned_universal/regen}——旧通用法阵物品缺省变体即回能，读写一致。</p>
 */
public class FormationKnowledgeComponent implements AutoSyncedComponent {
/** 已记录的法阵（element[/variant]:level 集合，如 ice:3、universal/regen:2）。 */
private final Set<String> recorded = new HashSet<>();
/** 已学习的法阵 → 最高等级（0 = 未学习）。键：element id 或 universal/<variant>。 */
private final Map<String, Integer> learned = new HashMap<>();

public static FormationKnowledgeComponent get(PlayerEntity player) {
return RegFormationKnowledgeComponent.FORMATION_KNOWLEDGE.get(player);
}

// ---- 存储键 ----

/** 学习等级存储键：非通用系 = element id；通用系 = universal/<variant>（非法变体归 regen）。 */
private static String storageKey(FormationElement element, String variant) {
if (element == FormationElement.UNIVERSAL) {
String v = FormationData.normalizeVariant(variant);
return element.id + "/" + (v != null ? v : FormationData.VARIANT_REGEN);
}
return element.id;
}

/** 记录条目键：element[/variant]:level。 */
private static String key(FormationElement element, String variant, int level) {
return storageKey(element, variant) + ":" + level;
}

// ---- 已记录 ----

/** 是否已记录指定系别（+变体）+ 等级的法阵。variant 仅通用系有效，传 null 归 regen。 */
public boolean hasRecorded(FormationElement element, String variant, int level) {
return recorded.contains(key(element, variant, level));
}

/** 记录指定法阵（幂等）。variant 仅通用系有效，传 null 归 regen。 */
public void record(FormationElement element, String variant, int level) {
recorded.add(key(element, variant, level));
}

// ---- 已学习 ----

/** 指定系别（+变体）已学习到的最高等级（0 = 未学习）。variant 仅通用系有效。 */
public int getLearnedLevel(FormationElement element, String variant) {
return learned.getOrDefault(storageKey(element, variant), 0);
}

/** 是否已学习指定系别（+变体）至少到指定等级。 */
public boolean hasLearned(FormationElement element, String variant, int level) {
return getLearnedLevel(element, variant) >= level;
}

/** 学习指定系别（+变体）到指定等级（只升不降）。 */
public void learn(FormationElement element, String variant, int level) {
String k = storageKey(element, variant);
if (level > learned.getOrDefault(k, 0)) {
learned.put(k, level);
}
}

// ---- 持久化 / 同步 ----

@Override
public void readFromNbt(NbtCompound nbt) {
recorded.clear();
NbtList list = nbt.getList("recorded", NbtElement.STRING_TYPE);
for (int i = 0; i < list.size(); i++) {
String entry = list.getString(i);
// 旧档迁移：universal:N → universal/regen:N（旧通用法阵一律视为回能变体）
if (entry.startsWith("universal:")) {
entry = "universal/" + FormationData.VARIANT_REGEN + ":" + entry.substring("universal:".length());
}
recorded.add(entry);
}
learned.clear();
for (FormationElement element : FormationElement.values()) {
if (element == FormationElement.UNIVERSAL) {
// 通用系三变体独立读取
for (String variant : new String[]{FormationData.VARIANT_REGEN, FormationData.VARIANT_MANA, FormationData.VARIANT_EXP}) {
String nbtKey = "learned_" + storageKey(element, variant);
if (nbt.contains(nbtKey)) {
int lv = clampLevel(nbt.getInt(nbtKey));
if (lv > 0) {
learned.put(storageKey(element, variant), lv);
}
}
}
// 旧键迁移：learned_universal → learned_universal/regen（仅当新键不存在时）
String regenKey = "learned_" + storageKey(element, FormationData.VARIANT_REGEN);
if (!nbt.contains(regenKey) && nbt.contains("learned_universal")) {
int legacy = clampLevel(nbt.getInt("learned_universal"));
if (legacy > 0) {
learned.put(storageKey(element, FormationData.VARIANT_REGEN), legacy);
}
}
} else {
int lv = clampLevel(nbt.getInt("learned_" + element.id));
if (lv > 0) {
learned.put(element.id, lv);
}
}
}
}

@Override
public void writeToNbt(NbtCompound nbt) {
NbtList list = new NbtList();
for (String entry : recorded) {
list.add(NbtString.of(entry));
}
nbt.put("recorded", list);
for (Map.Entry<String, Integer> e : learned.entrySet()) {
nbt.putInt("learned_" + e.getKey(), e.getValue());
}
}

/** 服务端变更后同步给客户端（研究台 GUI 需要实时读）。 */
public static void sync(ServerPlayerEntity player) {
RegFormationKnowledgeComponent.FORMATION_KNOWLEDGE.sync(player);
}

private static int clampLevel(int lv) {
return Math.max(0, Math.min(FormationData.MAX_FORMATION_LEVEL, lv));
}
}