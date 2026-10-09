package net.jackcooper.shapeShifterCurseAddon.sound;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Scope isolation and actual cached 1.20.1 method targets; no client/audio device needed. */
public final class SoundRangeTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(!SoundRangeRules.inActionScope(), "scope starts clear");
        SoundRangeRules.withActionScope(() -> {
            check(SoundRangeRules.isAddonPlayback(), "data/command playback inherits form scope");
            SoundRangeRules.withActionScope(() -> check(SoundRangeRules.inActionScope(), "nested target action keeps scope"));
            check(SoundRangeRules.inActionScope(), "nested return keeps outer scope");
        });
        check(!SoundRangeRules.inActionScope(), "return clears scope");
        try {
            SoundRangeRules.withActionScope(() -> { throw new IllegalStateException("test"); });
        } catch (IllegalStateException expected) { }
        check(!SoundRangeRules.inActionScope(), "exception clears scope for later vanilla sounds");
        SoundRangeRules.withActionScope(() -> {
            boolean[] leaked = new boolean[1];
            Thread other = new Thread(() -> leaked[0] = SoundRangeRules.inActionScope());
            other.start();
            try { other.join(); } catch (InterruptedException e) { throw new AssertionError(e); }
            check(!leaked[0], "scope never leaks to another thread");
        });
        String root = "net.jackcooper.shapeShifterCurseAddon.";
        for (String name : new String[]{"spell.DeathFinaleManager", "spell.spells.FireBoltSpell",
                "ability.AllaySPJukebox", "power.ParasiticFruitSeedPower", "entity.TidalOrbEntity",
                "item.WaterSpearEntity", "block.WebMembraneBlock", "client.sound.SpellChargeSoundInstance"})
            check(SoundRangeRules.isAddonSource(root + name), "spell/form source: " + name);
        for (String name : new String[]{"net.minecraft.server.world.ServerWorld", "net.minecraft.block.Block",
                root + "mixin.SoundRangePacketMixin", root + "sound.AddonSoundRange", root + "screen.SpellResearchTableScreenHandler"})
            check(!SoundRangeRules.isAddonSource(name), "unrelated source stays unchanged: " + name);
        check(!SoundRangeRules.isAddonPlayback(), "ordinary caller stays unscaled");
        method("net/minecraft/server/world/ServerWorld", "playSound",
                "(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;FFJ)V");
        method("net/minecraft/server/world/ServerWorld", "playSoundFromEntity",
                "(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;FFJ)V");
        method("net/minecraft/network/packet/s2c/play/PlaySoundS2CPacket", "<init>",
                "(Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;DDDFFJ)V");
        method("net/minecraft/network/packet/s2c/play/PlaySoundFromEntityS2CPacket", "<init>",
                "(Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;Lnet/minecraft/entity/Entity;FFJ)V");
        method("net/minecraft/client/sound/PositionedSoundInstance", "<init>",
                "(Lnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FFLnet/minecraft/util/math/random/Random;ZILnet/minecraft/client/sound/SoundInstance$AttenuationType;DDD)V");
        invocation("net/minecraft/client/sound/SoundSystem", "play", "net/minecraft/client/sound/Sound", "getAttenuation");
        invocation("net/minecraft/server/command/PlaySoundCommand", "execute", "net/minecraft/sound/SoundEvent", "of");
        invocation("io/github/apace100/apoli/power/factory/action/ActionFactory$Instance", "accept", "java/util/function/BiConsumer", "accept");
        System.out.println("Sound range: " + checks + " scope/bytecode checks passed; real packet and Mixin checks run in soundRangeIntegrationTest.");
    }

    private static ClassNode read(String name) throws Exception {
        try (var input = SoundRangeTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing class " + name);
            var result = new ClassNode();
            new ClassReader(input).accept(result, 0);
            return result;
        }
    }

    private static void method(String owner, String name, String descriptor) throws Exception {
        check(read(owner).methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor)), "target " + owner + "." + name);
    }

    private static void invocation(String owner, String method, String callee, String name) throws Exception {
        int found = 0;
        for (var m : read(owner).methods) if (m.name.equals(method))
            for (var insn : m.instructions) if (insn instanceof MethodInsnNode call && call.owner.equals(callee) && call.name.equals(name)) found++;
        check(found == 1, "unique injection target " + owner + "." + method);
    }

    private static void check(boolean passed, String reason) {
        checks++;
        if (!passed) throw new AssertionError(reason);
    }
}
