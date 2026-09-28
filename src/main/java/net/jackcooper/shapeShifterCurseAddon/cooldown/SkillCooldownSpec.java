package net.jackcooper.shapeShifterCurseAddon.cooldown;

import com.google.gson.JsonElement;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataType;
import net.minecraft.network.PacketByteBuf;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 所有技能 power 共用的冷却字段（数据包作者只需认这一套）：
 * cooldown（成功 CD）/ fail_cooldown（失败 CD，0=失败不进 CD）/ cooldown_start（on_cast|on_release|on_end）/
 * extra_cooldowns（该技能特有的附加 CD，如 {"cursed_moon": 280}）。单位均为 tick。
 */
public record SkillCooldownSpec(int cooldown, int failCooldown, String cooldownStart, Map<String, Integer> extras) {

	private static final Set<String> STARTS = Set.of(
			SkillCastManager.START_ON_CAST, SkillCastManager.START_ON_RELEASE, SkillCastManager.START_ON_END);

    public static final SerializableDataType<Integer> TICKS = new SerializableDataType<>(Integer.class,
            PacketByteBuf::writeInt, PacketByteBuf::readInt, SkillCooldownSpec::strictTicks);

	/** 本地 STRING 类型：与 calio SerializableDataTypes.STRING 等价（String.class + getAsString），
	 * 但不触碰 SerializableDataTypes 类初始化（其静态块引用 MC 注册表，裸 JVM 测试未 bootstrap 会崩）。 */
	public static final SerializableDataType<String> PLAIN_STRING = new SerializableDataType<>(String.class,
			PacketByteBuf::writeString, buf -> buf.readString(32767), JsonElement::getAsString);

    static int strictTicks(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new com.google.gson.JsonSyntaxException("Cooldown must be an integer number");
        try {
            int ticks = value.getAsBigDecimal().intValueExact();
            if (ticks < 0) throw new ArithmeticException("negative");
            return ticks;
        } catch (ArithmeticException ex) {
            throw new com.google.gson.JsonSyntaxException("Cooldown must be an integer in 0..2147483647", ex);
        }
    }

    /** Called before Apoli resets its registry, so an invalid reload cannot erase valid powers. */
    public static void validatePower(JsonElement json) {
        if (!json.isJsonObject()) return;
        var object = json.getAsJsonObject();
        if (object.has("type")) {
            String type = object.get("type").getAsString();
            if (Set.of("my_addon:fail_aware_active_self", "ssc_addon:familiar_skill",
                    "my_addon:mist_form", "my_addon:sonic_wave", "my_addon:parasitic_fruit_seed",
                    "my_addon:parasitic_spore_bomb", "my_addon:true_invisibility").contains(type)) {
                for (String field : new String[]{"cooldown", "fail_cooldown"})
                    if (object.has(field)) strictTicks(object.get(field));
                if (object.has("cooldown_start")) {
                    var start = object.get("cooldown_start");
                    if (!start.isJsonPrimitive() || !start.getAsJsonPrimitive().isString()
                            || !STARTS.contains(start.getAsString()))
                        throw new com.google.gson.JsonSyntaxException("Invalid cooldown_start: " + start);
                }
                if (object.has("extra_cooldowns")) parseExtras(object.get("extra_cooldowns"));
            }
        }
        for (var entry : object.entrySet()) if (entry.getValue().isJsonObject()) validatePower(entry.getValue());
    }

	@SuppressWarnings("unchecked")
	public static final SerializableDataType<Map<String, Integer>> EXTRA_COOLDOWNS = new SerializableDataType<>(
			(Class<Map<String, Integer>>) (Class<?>) Map.class,
			SkillCooldownSpec::writeExtras,
			SkillCooldownSpec::readExtras,
			SkillCooldownSpec::parseExtras);

	public static SerializableData addFields(SerializableData data, int defaultCooldown, String defaultStart) {
		return data.add("cooldown", TICKS, defaultCooldown)
				.add("fail_cooldown", TICKS, 0)
				.add("cooldown_start", PLAIN_STRING, defaultStart)
				.add("extra_cooldowns", EXTRA_COOLDOWNS, Map.of());
	}

	/** 读取并校验；非法值记日志后回退，不让一个写错的字段拖垮数据包加载。 */
	public static SkillCooldownSpec read(SerializableData.Instance data) {
		int cooldown = nonNegative("cooldown", data.getInt("cooldown"));
		int fail = nonNegative("fail_cooldown", data.getInt("fail_cooldown"));
		String start = data.getString("cooldown_start");
        if (!STARTS.contains(start)) throw new com.google.gson.JsonSyntaxException("Invalid cooldown_start: " + start);

		Map<String, Integer> extras = new HashMap<>();
		Map<String, Integer> raw = data.get("extra_cooldowns");
		if (raw != null) raw.forEach((key, value) -> extras.put(key, nonNegative("extra_cooldowns." + key, value)));
		return new SkillCooldownSpec(cooldown, fail, start, Map.copyOf(extras));
	}

	/** 附加 CD；数据包删掉该键时回退到正常 cooldown。 */
	public int extra(String key) {
		return extras.getOrDefault(key, cooldown);
	}

	public SkillCastManager.ResolvedConfig resolve() {
		return new SkillCastManager.ResolvedConfig(cooldown, failCooldown, cooldownStart);
	}

	private static int nonNegative(String field, int value) {
		if (value >= 0) return value;
        throw new com.google.gson.JsonSyntaxException(field + " must be non-negative: " + value);
	}

	private static void writeExtras(PacketByteBuf buf, Map<String, Integer> map) {
		buf.writeVarInt(map.size());
		map.forEach((key, value) -> {
			buf.writeString(key);
			buf.writeInt(value);
		});
	}

	private static Map<String, Integer> readExtras(PacketByteBuf buf) {
		int size = buf.readVarInt();
		Map<String, Integer> map = new HashMap<>();
		for (int i = 0; i < size; i++) map.put(buf.readString(), buf.readInt());
		return map;
	}

	private static Map<String, Integer> parseExtras(JsonElement json) {
		Map<String, Integer> map = new HashMap<>();
		for (var entry : json.getAsJsonObject().entrySet()) map.put(entry.getKey(), strictTicks(entry.getValue()));
		return map;
	}
}
