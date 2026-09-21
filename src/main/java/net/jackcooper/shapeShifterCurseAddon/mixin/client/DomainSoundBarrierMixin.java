package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.renderer.DomainRenderer;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 领域声音隔离（jackcooper，2026-09-21 需求）：领域外的任何声音无法穿过黑色球壳
 * 被壳内玩家听到（反之亦然——本地玩家与声音源分属壳内外即静音）。
 *
 * <p>注入点 {@code SoundSystem.play(SoundInstance)} HEAD：该方法开头即按实例取
 * WeightedSoundSet 建 Channel，cancel 掉就不会创建声道，完全静音（已反编译核实）。</p>
 *
 * <p>豁免（无法定位的用途音，拦截会误伤）：</p>
 * <ul>
 *   <li>{@code isRelative()}=true（相对听者的 UI/环境音，无世界坐标）；</li>
 *   <li>MUSIC / WEATHER / AMBIENT 类别（BGM、雷雨等全局氛围，无定位语义）。</li>
 * </ul>
 *
 * <p>判定数据源：{@link DomainRenderer} 已收到的服务端领域同步表（含扩张期半径，
 * 壳成型即隔离）；本地玩家为听者。仅客户端表现层，多人一致（各端各自按同一同步表判定）。</p>
 */
@Mixin(SoundSystem.class)
public abstract class DomainSoundBarrierMixin {
	@Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
	private void ssca$domainMuffleSound(SoundInstance sound, CallbackInfo ci) {
		if (sound.isRelative()) return;
		SoundCategory category = sound.getCategory();
		if (category == SoundCategory.MUSIC || category == SoundCategory.WEATHER
				|| category == SoundCategory.AMBIENT) return;
		if (DomainRenderer.blocksSoundForListener(sound.getX(), sound.getY(), sound.getZ())) ci.cancel();
	}
}
