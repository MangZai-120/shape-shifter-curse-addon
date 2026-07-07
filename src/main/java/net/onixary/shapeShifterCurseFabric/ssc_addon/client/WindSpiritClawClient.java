package net.onixary.shapeShifterCurseFabric.ssc_addon.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.ssc_addon.network.SscAddonNetworking;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;

/**
 * 风灵「疾风连爪」客户端左键检测器。
 *
 * 每客户端 tick 检测左键（原版攻击键）是否按住：仅在风灵形态、无 GUI 打开时生效；
 * 按住状态变化（边沿）才发 C2S（PACKET_CLAW_HOLD），避免每 tick 刷包。
 */
public final class WindSpiritClawClient {
    private static boolean lastHold = false;
    private static boolean lastSecPressed = false;

    private WindSpiritClawClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                lastHold = false;
                ClawClientState.reset();
                return;
            }
            ClientPlayerEntity player = client.player;
            IForm form = RegPlayerFormComponent.PLAYER_FORM.get(player).nowForm;
            boolean isOcelot = form != null && form.getFormID() != null
                    && FormIdentifiers.OCELOT_SP.equals(form.getFormID());

            boolean hold;
            if (!isOcelot || client.currentScreen != null) {
                hold = false; // 非风灵 或 打开 GUI → 不触发
            } else {
                hold = client.options.attackKey.isPressed();
            }

            if (hold != lastHold) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeBoolean(hold);
                ClientPlayNetworking.send(SscAddonNetworking.PACKET_CLAW_HOLD, buf);
                lastHold = hold;
            }

            // 副技能：sp_secondary 边沿 → 发增伤 buff 包
            boolean secPressed = isOcelot && client.currentScreen == null
                    && SscAddonKeybindings.getSecondaryKey().isPressed();
            if (secPressed && !lastSecPressed) {
                ClientPlayNetworking.send(SscAddonNetworking.PACKET_CLAW_BUFF, PacketByteBufs.empty());
            }
            lastSecPressed = secPressed;

            if (!isOcelot) {
                ClawClientState.reset();
            }
        });
    }
}
