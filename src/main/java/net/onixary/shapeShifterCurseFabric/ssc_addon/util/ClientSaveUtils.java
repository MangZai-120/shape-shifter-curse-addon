/*
 * Copyright (c) 2026 宋明禹(Song Mingyu)
 * This file is part of the "shape shifter curse addon" project.
 * Licensed under the MIT License.
 */
package net.onixary.shapeShifterCurseFabric.ssc_addon.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.integrated.IntegratedServer;

/**
 * 客户端「当前存档/服务器」标识工具。
 * 用于「按存档看一遍」类的客户端记忆（如颜色编辑器首次教程）。
 */
public final class ClientSaveUtils {
    private ClientSaveUtils() {
    }

    /**
     * 取当前存档/服务器的稳定标识：
     * - 单机：integrated 服务器存档目录名（world:&lt;name&gt;）
     * - 多人：服务器地址（server:&lt;address&gt;）
     * - 取不到：unknown
     */
    public static String getCurrentSaveId() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return "unknown";
        IntegratedServer integrated = mc.getServer();
        if (integrated != null) {
            return "world:" + integrated.getSaveProperties().getLevelName();
        }
        ServerInfo info = mc.getCurrentServerEntry();
        if (info != null && info.address != null) {
            return "server:" + info.address;
        }
        return "unknown";
    }
}
