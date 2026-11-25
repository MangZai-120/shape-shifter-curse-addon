package net.jackcooper.shapeShifterCurseAddon.client.sound;

import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.jackcooper.shapeShifterCurseAddon.ability.snowfox.SnowFoxMotion;
import net.jackcooper.shapeShifterCurseAddon.ability.snowfox.SnowFoxSounds;
import net.jackcooper.shapeShifterCurseAddon.ability.snowfox.SnowFoxState;
import net.jackcooper.shapeShifterCurseAddon.sound.ExtendedRangeSound;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;

/** Sweep one amethyst resonance from the active resource pack throughout the ten-tick windup. */
public final class SnowFoxGatherSound extends MovingSoundInstance implements FabricSoundInstance, ExtendedRangeSound {
    private final PlayerEntity owner;
    private final float elapsed;

    public SnowFoxGatherSound(PlayerEntity owner, float age) {
        super(SnowFoxSounds.GATHER, SoundCategory.PLAYERS, Random.create());
        this.owner = owner;
        elapsed = MathHelper.clamp((age - 1) / 10f, 0f, .99f);
        volume = .82f;
        x = owner.getX(); y = owner.getY() + .45; z = owner.getZ();
    }

    @Override public boolean ssca$hasExtendedRange() { return true; }

    @Override public void tick() {
        if (owner.isRemoved() || owner.getWorld() != MinecraftClient.getInstance().world
                || SnowFoxMotion.STATE.get(owner).phase() != SnowFoxState.Phase.CHARGE) setDone();
    }

    @Override public CompletableFuture<AudioStream> getAudioStream(SoundLoader loader, Identifier id, boolean repeat) {
        return loader.loadStreamed(id, false).thenApply(source -> {
            try (source) {
                return new Glide(source, elapsed);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
    }

    private static final class Glide implements AudioStream {
        private final AudioFormat format;
        private final ByteBuffer pcm;

        Glide(AudioStream source, float elapsed) throws IOException {
            format = source.getFormat();
            int channels = format.getChannels(), frameBytes = format.getFrameSize();
            int frames = Math.round(format.getSampleRate() * .5f);
            // The Minecraft Ogg decoder outputs signed 16-bit PCM. Integrate an exponential
            // playback rate per sample so the crystal slides by a full octave without retriggering.
            double octave = Math.log(2), low = .76;
            int needed = (int)Math.ceil(low * frames / octave) + 2;
            var input = source.getBuffer(needed * frameBytes);
            int first = Math.round(elapsed * frames);
            pcm = BufferUtils.createByteBuffer((frames - first) * frameBytes);
            if (input != null) {
                var samples = input.order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN).asShortBuffer();
                pcm.order(input.order());
                double step = low * Math.exp(octave * first / frames);
                double growth = Math.exp(octave / frames);
                double position = low * frames / octave * Math.expm1(octave * first / frames);
                for (int i = first; i < frames; i++) {
                    int a = (int)position;
                    if ((a + 2) * channels > samples.limit()) break;
                    double mix = position - a;
                    double fade = Math.min(1, Math.min((i-first) / (frames*.012), (frames-1-i) / (frames*.04)));
                    for (int ch = 0; ch < channels; ch++) {
                        short start = samples.get(a * channels + ch), end = samples.get((a + 1) * channels + ch);
                        pcm.putShort((short)Math.round((start + (end-start) * mix) * fade));
                    }
                    position += step;
                    step *= growth;
                }
            }
            pcm.flip();
        }

        @Override public AudioFormat getFormat() { return format; }

        @Override public ByteBuffer getBuffer(int bytes) {
            if (!pcm.hasRemaining()) return null;
            int length = Math.min(bytes, pcm.remaining());
            ByteBuffer part = pcm.slice(pcm.position(), length).order(pcm.order());
            pcm.position(pcm.position() + length);
            return part;
        }

        @Override public void close() { }
    }
}
