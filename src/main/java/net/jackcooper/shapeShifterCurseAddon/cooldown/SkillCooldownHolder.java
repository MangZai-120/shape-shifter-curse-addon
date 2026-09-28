package net.jackcooper.shapeShifterCurseAddon.cooldown;

/** 带统一冷却字段的技能 power；技能 ID = 该 power 的注册 ID。 */
public interface SkillCooldownHolder {
	SkillCooldownSpec cooldownSpec();
}
