package net.onixary.shapeShifterCurseFabric.ssc_addon.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
//import net.onixary.shapeShifterCurseFabric.ssc_addon.ability.Ability_AllayHeal;


public class SscAddonNetworking {
	public static final Identifier PACKET_KEY_PRESS = new Identifier("my_addon", "key_press");

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(PACKET_KEY_PRESS, (server, player, handler, buf, responseSender) -> {
            buf.readInt();
            server.execute(() -> handleKeyPress(player));
		});
	}

	private static void handleKeyPress(ServerPlayerEntity player) {
		// Find current form
        FormAbilityManager.getForm(player);


        // Allay Heal (using keyId 1 for now, mapped from client)
        /*if (keyId == 1 && (formId.getPath().equals("form_allay_sp") || formId.getPath().equals("allay_sp"))) {
             Ability_AllayHeal.onHold(player);
        }*/

		// Add other key handlers here if needed (e.g. Fox Fire)
	}
}
