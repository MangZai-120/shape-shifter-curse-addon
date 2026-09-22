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

	private DomainSound(ClientWorld world, UUID owner, SoundEvent sound, Vec3d position, float pitch, long seed) {
		super(sound, SoundCategory.PLAYERS, Random.create(seed));
		this.world = world;
		this.owner = owner;
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
			long seed = buf.readLong();
			client.execute(() -> {
				if (client.world == null || client.player == null
						|| !client.world.getRegistryKey().getValue().equals(dimension)) return;
				SoundEvent sound = Registries.SOUND_EVENT.get(soundId);
				if (sound != null) client.getSoundManager().play(new DomainSound(client.world, owner, sound, position, pitch, seed));
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
		volume = DomainRules.soundVolume(Math.sqrt(client.player.squaredDistanceTo(x, y, z)));
	}

	@Override
	public boolean shouldAlwaysPlay() {
		return true;
	}
}