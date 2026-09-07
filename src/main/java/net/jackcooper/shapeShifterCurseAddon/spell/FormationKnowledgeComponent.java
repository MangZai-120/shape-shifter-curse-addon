package net.jackcooper.shapeShifterCurseAddon.spell;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * 玩家法术知识数据组件（jackcooper）：记录「已记录的法阵」与「已学习的法阵」。
 *
 * <p>两级数据（都跟随玩家、多人各自独立）：</p>
 * <ul>
 *   <li><b>已记录（recorded）</b>：右键使用法阵物品获得，格式 {@code element:level}；
 *       同系各等级独立记录（高等级不覆盖低等级，学习时自选等级）；</li>
 *   <li><b>已学习（learned）</b>：研究台消耗月尘学习后获得，只存最高等级（低等级自动包含）；
 *       学习后才能在研究台抄写对应等级的法阵。</li>
 * </ul>
 */
public class FormationKnowledgeComponent implements AutoSyncedComponent {
	/** 已记录的法阵（element:level 集合，如 ice:3、fire:2）。 */
	private final Set<String> recorded = new HashSet<>();
	/** 已学习的法阵系别 → 最高等级（0 = 未学习）。 */
	private final int[] learnedLevels = new int[FormationElement.values().length];

	public static FormationKnowledgeComponent get(PlayerEntity player) {
		return RegFormationKnowledgeComponent.FORMATION_KNOWLEDGE.get(player);
	}

	// ---- 已记录 ----

	/** 是否已记录指定系别 + 等级的法阵。 */
	public boolean hasRecorded(FormationElement element, int level) {
		return recorded.contains(key(element, level));
	}

	/** 记录指定法阵（幂等）。 */
	public void record(FormationElement element, int level) {
		recorded.add(key(element, level));
	}

	/** 指定系别已记录的最高等级（0 = 从未记录）。 */
	public int getMaxRecordedLevel(FormationElement element) {
		int max = 0;
		for (int lv = 1; lv <= FormationData.MAX_FORMATION_LEVEL; lv++) {
			if (hasRecorded(element, lv)) {
				max = lv;
			}
		}
		return max;
	}

	// ---- 已学习 ----

	/** 指定系别已学习到的最高等级（0 = 未学习）。 */
	public int getLearnedLevel(FormationElement element) {
		return learnedLevels[element.ordinal()];
	}

	/** 是否已学习指定系别至少到指定等级。 */
	public boolean hasLearned(FormationElement element, int level) {
		return getLearnedLevel(element) >= level;
	}

	/** 学习指定系别到指定等级（只升不降）。 */
	public void learn(FormationElement element, int level) {
		if (level > learnedLevels[element.ordinal()]) {
			learnedLevels[element.ordinal()] = level;
		}
	}

	// ---- 持久化 / 同步 ----

	private static String key(FormationElement element, int level) {
		return element.id + ":" + level;
	}

	@Override
	public void readFromNbt(NbtCompound nbt) {
		recorded.clear();
		NbtList list = nbt.getList("recorded", NbtElement.STRING_TYPE);
		for (int i = 0; i < list.size(); i++) {
			recorded.add(list.getString(i));
		}
		for (FormationElement element : FormationElement.values()) {
			learnedLevels[element.ordinal()] = Math.max(0,
					Math.min(FormationData.MAX_FORMATION_LEVEL, nbt.getInt("learned_" + element.id)));
		}
	}

	@Override
	public void writeToNbt(NbtCompound nbt) {
		NbtList list = new NbtList();
		for (String key : recorded) {
			list.add(NbtString.of(key));
		}
		nbt.put("recorded", list);
		for (FormationElement element : FormationElement.values()) {
			nbt.putInt("learned_" + element.id, learnedLevels[element.ordinal()]);
		}
	}

	/** 服务端变更后同步给客户端（研究台 GUI 需要实时读）。 */
	public static void sync(ServerPlayerEntity player) {
		RegFormationKnowledgeComponent.FORMATION_KNOWLEDGE.sync(player);
	}
}
