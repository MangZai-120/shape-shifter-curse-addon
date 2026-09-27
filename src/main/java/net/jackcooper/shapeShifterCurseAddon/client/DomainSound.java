package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.spell.DomainManager;
import net.jackcooper.shapeShifterCurseAddon.spell.DomainRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class DomainSound extends MovingSoundInstance {
	private final ClientWorld world;
	private final UUID owner;
	/** 整体音量倍率（服务端随包下发）：乘在距离曲线上，用于单独压低/抬高某个领域音效。 */
	private final float volumeScale;
	/** 起手快照半径（SOUND 包 v2 尾部携带，2026-09-27）：距离曲线几何与服务端结算同源。 */
	private final double innerRadius;
	private final double outerRadius;

	private DomainSound(ClientWorld world, UUID owner, SoundEvent sound, Vec3d position, float pitch,
				float volumeScale, long seed, double innerRadius, double outerRadius) {
		super(sound, SoundCategory.PLAYERS, Random.create(seed));
		this.world = world;
		this.owner = owner;
		this.volumeScale = volumeScale;
		this.innerRadius = innerRadius;
		this.outerRadius = outerRadius;
		this.x = position.x;
		this.y = position.y;
		this.z = position.z;
		this.pitch = pitch;
		this.attenuationType = AttenuationType.NONE;
		tick();
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(DomainManager.SOUND, (client, handler, buf, sender) -> {
			var dimension = buf.readIdentifier();
			UUID owner = buf.readUuid();
			var soundId = buf.readIdentifier();
			Vec3d position = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
			float pitch = buf.readFloat();
			float volumeScale = buf.readFloat();
			long seed = buf.readLong();
			// v2 尾部（2026-09-27）：快照半径；无剩余字节（理论不出现）回退本地 balance 镜像。
			double inner = buf.isReadable() ? buf.readDouble() : DomainRules.innerRadius();
			double outer = buf.isReadable() ? buf.readDouble() : DomainRules.outerRadius();
			client.execute(() -> {
				if (client.world == null || client.player == null
						|| !client.world.getRegistryKey().getValue().equals(dimension)) return;
				SoundEvent sound = Registries.SOUND_EVENT.get(soundId);
				if (sound != null) client.getSoundManager().play(
						new DomainSound(client.world, owner, sound, position, pitch, volumeScale, seed, inner, outer));
			});
		});
	}

	@Override
	public void tick() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != world || client.player == null) {
			volume = 0;
			setDone();
			return;
		}
		var caster = world.getPlayerByUuid(owner);
		if (caster != null && !caster.isRemoved()) {
			x = caster.getX();
			y = caster.getY();
			z = caster.getZ();
		}
		volume = DomainRules.soundVolume(Math.sqrt(client.player.squaredDistanceTo(x, y, z)), innerRadius, outerRadius)
				* volumeScale;
	}

	@Override
	public boolean shouldAlwaysPlay() {
		return true;
	}
}