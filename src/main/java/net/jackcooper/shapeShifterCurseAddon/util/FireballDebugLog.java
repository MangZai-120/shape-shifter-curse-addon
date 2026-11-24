package net.jackcooper.shapeShifterCurseAddon.util;

/**
 * 临时诊断日志（排查 Red 火球多次命中无伤问题用，定位后删除）。
 * 输出到游戏日志：[FireballDebug] 前缀，方便 grep。
 */
public final class FireballDebugLog {
	private FireballDebugLog() {}

	public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("ssc_addon/FireballDebug");
}
