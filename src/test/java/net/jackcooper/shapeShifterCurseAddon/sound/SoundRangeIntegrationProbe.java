package net.jackcooper.shapeShifterCurseAddon.sound;

import io.github.apace100.apoli.power.factory.action.ActionFactory;
import io.github.apace100.calio.data.SerializableData;
import io.netty.buffer.Unpooled;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.PlaySoundCommand;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.lang.reflect.Method;

/** Real transformed constructors, vanilla wire codec and the applied attenuation/command hooks. */
public final class SoundRangeIntegrationProbe {
    private static int checks;

    public static void run() throws Exception {
        // Loading these targets applies their actual Mixin transformations (no world/window needed).
        Class.forName(ServerWorld.class.getName());
        Class.forName("net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket");
        Class.forName("net.minecraft.client.sound.EntityTrackingSoundInstance");
        Method attenuation = hook(SoundSystem.class, "ssca$extendAttenuation");
        Object system = allocate(SoundSystem.class);
        SoundEvent original = SoundEvents.ITEM_CROSSBOW_LOADING_START;
        for (float volume : new float[]{0.25f, 0.9f, 1, 1.1f, 1.3f, 2, 3, 4.5f}) {
            var normal = new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(original), SoundCategory.PLAYERS,
                    10, 64, 20, volume, 0.73f, 1234567L);
            check(normal.getSound().value() == original, "ordinary packet remains a registry reference");
            final PlaySoundS2CPacket[] captured = new PlaySoundS2CPacket[1];
            SoundRangeRules.withActionScope(() -> captured[0] = new PlaySoundS2CPacket(
                    Registries.SOUND_EVENT.getEntry(original), SoundCategory.PLAYERS, 10, 64, 20, volume, 0.73f, 1234567L));
            var packet = captured[0];
            check(packet.getSound().value().getDistanceToTravel(volume) == original.getDistanceToTravel(volume) * 2,
                    "server broadcast radius doubles for " + volume);
            check(AddonSoundRange.extend(packet.getSound().value(), volume) == packet.getSound().value(), "range never doubles twice");
            var buf = new PacketByteBuf(Unpooled.buffer());
            try {
                packet.write(buf);
                var restored = new PlaySoundS2CPacket(buf);
                check(buf.readableBytes() == 0, "vanilla packet format consumes exactly its bytes");
                check(restored.getVolume() == volume && restored.getPitch() == 0.73f && restored.getSeed() == 1234567L,
                        "volume, pitch and sample seed stay unchanged");
                check(AddonSoundRange.originalId(restored.getSound().value().getId()).equals(original.getId()), "wire marker resolves to original resource");
                check(AddonSoundRange.isExtended(restored.getSound().value(), volume), "distance marker survives packet round trip");
                var ranged = new PositionedSoundInstance(restored.getSound().value(), SoundCategory.PLAYERS,
                        restored.getVolume(), restored.getPitch(), Random.create(1234567), 10, 64, 20);
                var plain = new PositionedSoundInstance(original, SoundCategory.PLAYERS, volume, 0.73f,
                        Random.create(1234567), 10, 64, 20);
                check(((ExtendedRangeSound) ranged).ssca$hasExtendedRange(), "received positional sound retains range marker");
                check(ranged.getId().equals(original.getId()), "client resource and stop-sound ID stay unchanged");
                check(!((ExtendedRangeSound) plain).ssca$hasExtendedRange(), "vanilla positional sound stays unmarked");
                check((int) attenuation.invoke(system, 16, ranged) == 32, "client material attenuation doubles");
                check((int) attenuation.invoke(system, 16, plain) == 16, "vanilla client attenuation stays unchanged");
            } finally { buf.release(); }
        }
        var otherMod = new PositionedSoundInstance(SoundEvent.of(original.getId(), 32), SoundCategory.PLAYERS,
                1, 1, Random.create(), 10, 64, 20);
        check(!((ExtendedRangeSound) otherMod).ssca$hasExtendedRange(), "unrelated fixed-range sound must not acquire SSCA scaling");
        check((int) attenuation.invoke(system, 16, otherMod) == 16, "unrelated fixed-range client attenuation stays unchanged");
        Method command = hook(PlaySoundCommand.class, "ssca$extendSkillCommand");
        SoundEvent[] commandSound = new SoundEvent[1];
        SoundRangeRules.withActionScope(() -> {
            try { commandSound[0] = (SoundEvent) command.invoke(null, SoundEvent.of(original.getId()), 1.5f); }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        check(commandSound[0].getDistanceToTravel(1.5f) == 48, "data playsound broadcasts to doubled radius without louder volume");
        var factory = new ActionFactory<Object>(new Identifier("ssc_addon", "sound_range_test"), new SerializableData(),
                (data, target) -> check(SoundRangeRules.inActionScope(), "nested Apoli effect retains inherited scope"));
        var json = new com.google.gson.JsonObject();
        json.addProperty("inverted", false);
        SoundRangeRules.withActionScope(() -> factory.read(json).accept(new Object()));
        check(!SoundRangeRules.inActionScope(), "action scope clears after actual transformed factory");
        System.out.println("Sound range integration: " + checks + " real Mixin/packet checks passed. Live client hearing NOT tested.");
    }

    private static Method hook(Class<?> owner, String fragment) {
        for (Method method : owner.getDeclaredMethods()) if (method.getName().contains(fragment)) {
            method.setAccessible(true);
            return method;
        }
        throw new AssertionError("Mixin hook missing: " + owner.getName() + " " + fragment);
    }

    private static Object allocate(Class<?> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
    }

    private static void check(boolean passed, String reason) {
        checks++;
        if (!passed) throw new AssertionError(reason);
    }
}
