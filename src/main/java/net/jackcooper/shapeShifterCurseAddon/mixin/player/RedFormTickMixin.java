package net.jackcooper.shapeShifterCurseAddon.mixin.player;

import net.minecraft.network.packet.c2s.play.ClientSettingsC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家客户端语言捕获微 mixin（jackcooper）。
 *
 * <p>原 {@code tick} 注入（SP 使魔变红 / 悦灵物品发放 / 超时回退）已迁到
 * {@link net.jackcooper.shapeShifterCurseAddon.ability.RedFormTickManager}（世界级 tick 事件），
 * 本文件仅保留 {@code setClientSettings} 语言捕获——Fabric 1.20.1 无对应事件
 * （JOIN 时 ClientSettingsC2SPacket 可能未到、也不追踪游戏中改语言），必须保留此注入。</p>
 */
@Mixin(ServerPlayerEntity.class)
public class RedFormTickMixin {

	// 捕获玩家客户端语言设置，存入SscAddon.PLAYER_LANGUAGES
	@Inject(method = "setClientSettings", at = @At("HEAD"))
	private void onSetClientSettings(ClientSettingsC2SPacket packet, CallbackInfo ci) {
		ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
		SscAddon.PLAYER_LANGUAGES.put(player.getUuid(), packet.language());
	}
}
