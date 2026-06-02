/*
 * Copyright (c) 2026 宋明禹(Song Mingyu)
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the MIT License.
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;

/**
 * 寄生果蝠主要技能满层（stack==3）目标的 outline 高光辅助类。
 * 实现策略：
 *   1. 服务端创建两个 Scoreboard team：
 *      - my_addon_pfb_friend → 绿色边框（友军/自己）
 *      - my_addon_pfb_enemy → 红色边框（敌方）
 *   2. 通过给目标加短时长（不可见粒子）GLOWING status effect 让客户端持续描边；
 *      团队颜色决定描边颜色。
 *   3. 取消高光时仅移除 team（不主动 remove GLOWING，留给 status effect 自然过期），
 *      避免误清其它来源的 GLOWING。
 *
 * 限制：原版机制下 outline 是全员可见的，无法做"仅施法者可见"。本方案优先实现"颜色标记"。
 */
public final class GlowMarker {

    public static final String TEAM_FRIEND = "my_addon_pfb_friend";
    public static final String TEAM_ENEMY = "my_addon_pfb_enemy";
    /** GLOWING 状态效果刷新时长（tick），略大于刷新间隔以保持连续。 */
    private static final int GLOWING_DURATION = 30;

    private GlowMarker() {
    }

    /** 给目标打上"满层友军"绿色 outline；幂等，可重复调用。 */
    public static void markFriend(LivingEntity target) {
        applyMark(target, TEAM_FRIEND, Formatting.GREEN);
    }

    /** 给目标打上"满层敌方"红色 outline；幂等，可重复调用。 */
    public static void markEnemy(LivingEntity target) {
        applyMark(target, TEAM_ENEMY, Formatting.RED);
    }

    /** 取消目标的本机制 outline（仅在目标当前属于本机制 team 时移除）。 */
    public static void unmark(LivingEntity target) {
        if (!(target.getWorld() instanceof ServerWorld world)) return;
        MinecraftServer server = world.getServer();
        if (server == null) return;
        Scoreboard scoreboard = server.getScoreboard();
        AbstractTeam current = target.getScoreboardTeam();
        if (current instanceof Team team
                && (TEAM_FRIEND.equals(team.getName()) || TEAM_ENEMY.equals(team.getName()))) {
            scoreboard.removePlayerFromTeam(target.getEntityName(), team);
        }
    }

    private static void applyMark(LivingEntity target, String teamName, Formatting color) {
        if (!(target.getWorld() instanceof ServerWorld world)) return;
        MinecraftServer server = world.getServer();
        if (server == null) return;
        Scoreboard scoreboard = server.getScoreboard();
        Team team = ensureTeam(scoreboard, teamName, color);

        AbstractTeam current = target.getScoreboardTeam();
        if (!(current instanceof Team currentTeam) || !teamName.equals(currentTeam.getName())) {
            // 若之前在另一个本机制 team，先移除避免重复
            if (current instanceof Team prev
                    && (TEAM_FRIEND.equals(prev.getName()) || TEAM_ENEMY.equals(prev.getName()))) {
                scoreboard.removePlayerFromTeam(target.getEntityName(), prev);
            }
            // 仅当目标完全无 team 或之前在本机制 team 时才接管，避免破坏外部 team 配置
            if (target.getScoreboardTeam() == null) {
                scoreboard.addPlayerToTeam(target.getEntityName(), team);
            }
        }
        // 持续刷新 GLOWING 状态以保证客户端描边
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING,
                GLOWING_DURATION, 0, false, false, false));
    }

    private static Team ensureTeam(Scoreboard scoreboard, String name, Formatting color) {
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.addTeam(name);
            team.setColor(color);
            team.setShowFriendlyInvisibles(false);
            team.setFriendlyFireAllowed(true);
        }
        return team;
    }
}
