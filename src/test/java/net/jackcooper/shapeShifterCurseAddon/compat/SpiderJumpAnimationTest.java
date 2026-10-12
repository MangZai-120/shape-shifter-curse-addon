package net.jackcooper.shapeShifterCurseAddon.compat;

import com.mojang.authlib.GameProfile;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.gson.AnimationSerializing;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.jackcooper.shapeShifterCurseAddon.forms.Form_SpiderMoonWeaver;
import net.jackcooper.shapeShifterCurseAddon.forms.Form_SpiderSalticidae;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.player_animation.AnimationHolder;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimRegistries;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimSystem;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimFSM.InAirFSM;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimFSM.OnGroundFSM;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_Spider3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Actual SSC assets, KosmX playback and Fabric-transformed form controllers; no game window. */
public final class SpiderJumpAnimationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.class.path", Files.readString(Path.of(
                System.getProperty("spiderAnimation.testClasspathFile"))));
        var knot = new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);
        var loader = knot.init(new String[]{"--version", "spider-animation-test", "--accessToken", "0"});
        Thread.currentThread().setContextClassLoader(loader);
        Class.forName("net.minecraft.SharedConstants", true, loader).getMethod("createGameVersion").invoke(null);
        Class.forName("net.minecraft.Bootstrap", true, loader).getMethod("initialize").invoke(null);
        Class.forName(SpiderJumpAnimationTest.class.getName() + "$Probe", true, loader)
                .getMethod("run").invoke(null);
    }

    public static final class Probe {
        private static int checks;
        private static int nextEntityId = 1;
        private static sun.misc.Unsafe unsafe;

        @SuppressWarnings("unchecked")
        public static void run() throws Exception {
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            var registryField = PlayerAnimationRegistry.class.getDeclaredField("animations");
            registryField.setAccessible(true);
            var registry = (Map<Identifier, KeyframeAnimation>) registryField.get(null);
            for (String name : new String[]{"jump", "fall", "walk", "run", "idle", "sneak_walk", "sneak_idle"}) {
                String id = "spider_3_" + name;
                try (var input = Probe.class.getClassLoader().getResourceAsStream(
                        "assets/shape-shifter-curse/player_animation/" + id + ".json")) {
                    check(input != null, "real SSC animation asset present: " + id);
                    registry.put(new Identifier("shape-shifter-curse", id),
                            AnimationSerializing.deserializeAnimation(input).get(0));
                }
            }
            var originalJump = registry.get(new Identifier("shape-shifter-curse", "spider_3_jump"));
            var before = new KeyframeAnimationPlayer(originalJump);
            for (int tick = 0; tick <= originalJump.stopTick; tick++) before.tick();
            check(!before.isActive(), "reproduce the original single-shot jump running out");
            check(!originalJump.isInfinite, "SSC resource remains a single-shot animation");

            for (Form_Spider3 form : new Form_Spider3[]{
                    new Form_SpiderSalticidae(new Identifier("my_addon", "spider_salticidae")),
                    new Form_SpiderMoonWeaver(new Identifier("my_addon", "spider_moon_weaver"))}) {
                testForm(form, originalJump);
            }
            check(!originalJump.isInfinite, "no mutation of the shared SSC jump asset");
            System.out.println("Spider jump animation PASS: " + checks
                    + " checks; actual SSC/KosmX single-shot reproduction, second-jump restart while ascending,"
                    + " final-pose hold, late fall/long ascent, player isolation, landing to walk/idle,"
                    + " inherited non-jump controllers and physical fall distance preserved. Live rendering NOT tested.");
        }

        private static void testForm(Form_Spider3 form, KeyframeAnimation originalJump) throws Exception {
            var player = player();
            var data = data(form, player);
            var controller = form.getAnimStateController(player, data, AnimRegistries.ANIM_STATE_JUMP);
            check(controller instanceof SpiderJumpAnimController, "only addon spider jump controller replaced");
            controller.registerAnim(player, data);
            AnimationHolder first = controller.getAnimation(player, data);
            check(first != null && first.getAnimation() != null, "first jump has an animation");
            check(first.getAnimation() != originalJump, "holding uses an independent animation copy");
            var animation = new KeyframeAnimationPlayer(first.getAnimation());
            for (int tick = 0; tick < 6; tick++) {
                player.age++;
                check(controller.getAnimation(player, data) == first, "continuous jump does not restart every tick");
                animation.tick();
            }
            player.fallDistance = 5.0f;
            data.fallDistanceTemp = 70;
            data.fallDistance = 6;
            SpiderJumpAnimController.restartJump(player);
            player.age++;
            AnimationHolder second = controller.getAnimation(player, data);
            check(second.getAnimation() != first.getAnimation(), "same JUMP state gets a new animation identity");
            check(data.fallDistanceTemp == player.getY() && data.fallDistance == 0,
                    "reset the animation's independent falling counter");
            check(player.fallDistance == 5.0f, "never change physical fall distance for animation");
            animation = new KeyframeAnimationPlayer(second.getAnimation());
            var expectedEnd = new KeyframeAnimationPlayer(originalJump, originalJump.endTick);
            expectedEnd.setupAnim(0);
            Vec3f expectedLeg = expectedEnd.get3DTransform("bipedLeftHindLeg", TransformType.ROTATION, 0,
                    new Vec3f(0, 0, 0));
            check(Math.abs(expectedLeg.getX()) > .1f, "SSC final jump pose has non-default hind legs");
            for (int tick = 0; tick < 200; tick++) {
                player.age++;
                check(controller.getAnimation(player, data) == second, "long ascent keeps the selected jump holder");
                animation.tick();
                animation.setupAnim(0);
                check(animation.isActive(), "no empty animation after the original stop tick");
                if (tick > originalJump.endTick) {
                    Vec3f held = animation.get3DTransform("bipedLeftHindLeg", TransformType.ROTATION, 0,
                            new Vec3f(0, 0, 0));
                    check(Math.abs(held.getX() - expectedLeg.getX()) < .0001f
                                    && Math.abs(held.getZ() - expectedLeg.getZ()) < .0001f,
                            "hold the last pose instead of replaying the whole jump");
                }
            }
            var other = player();
            check(other.getId() != player.getId(), "fixture players have distinct Minecraft entity identities");
            other.age = player.age;
            var otherData = data(form, other);
            AnimationHolder otherHolder = controller.getAnimation(other, otherData);
            check(otherHolder.getAnimation() != second.getAnimation(), "players do not share playback identity");
            SpiderJumpAnimController.restartJump(other);
            player.age++;
            check(controller.getAnimation(player, data) == second, "another player cannot restart this jump");

            data.fakeVelocity = new Vec3d(.1, -.1, 0);
            data.fallDistance = .7;
            var state = new InAirFSM().getStateID(player, data);
            check(state.equals(AnimRegistries.ANIM_STATE_FALL), "natural fall takes over at SSC's threshold");
            var fall = form.getAnimStateController(player, data, state);
            check(fall == Form_Spider3.FALL_CONTROLLER, "preserve original falling controller");
            if (!fall.isRegistered(player, data)) fall.registerAnim(player, data);
            check(fall.getAnimation(player, data).getAnimation().isInfinite, "falling stays animated");

            data.IsOnGround = true;
            data.IsWalking = true;
            state = new OnGroundFSM().getStateID(player, data);
            check(state.equals(AnimRegistries.ANIM_STATE_WALK), "landing while moving selects walking");
            var walk = form.getAnimStateController(player, data, state);
            check(walk == Form_Spider3.WALK_CONTROLLER, "preserve original walking controller");
            if (!walk.isRegistered(player, data)) walk.registerAnim(player, data);
            var walking = new KeyframeAnimationPlayer(walk.getAnimation(player, data).getAnimation());
            for (int tick = 0; tick < 80; tick++) walking.tick();
            check(walking.isActive(), "landing walk remains animated beyond one cycle");
            data.IsWalking = false;
            check(new OnGroundFSM().getStateID(player, data).equals(AnimRegistries.ANIM_STATE_IDLE),
                    "landing stationary restores idle");
            for (Identifier inherited : new Identifier[]{AnimRegistries.ANIM_STATE_IDLE, AnimRegistries.ANIM_STATE_WALK,
                    AnimRegistries.ANIM_STATE_SPRINT, AnimRegistries.ANIM_STATE_FALL, AnimRegistries.ANIM_STATE_CLIMB,
                    AnimRegistries.ANIM_STATE_SWIM, AnimRegistries.ANIM_STATE_RIDE, AnimRegistries.ANIM_STATE_SLEEP}) {
                check(form.getAnimStateController(player, data, inherited)
                                == new Form_Spider3(new Identifier("shape-shifter-curse", "spider_3"))
                                .getAnimStateController(player, data, inherited), "other states retain SSC controller: " + inherited);
            }
            player.age += 3;
            check(controller.getAnimation(player, data).getAnimation() != second.getAnimation(),
                    "a new airborne sequence restarts instead of reusing the held pose");
        }

        private static TestPlayer player() throws Exception {
            var player = (TestPlayer) unsafe.allocateInstance(TestPlayer.class);
            var id = Entity.class.getDeclaredField("id");
            id.setAccessible(true);
            id.setInt(player, nextEntityId++);
            var position = Entity.class.getDeclaredField("pos");
            position.setAccessible(true);
            position.set(player, new Vec3d(0, 64, 0));
            return player;
        }

        private static AnimSystem.AnimSystemData data(Form_Spider3 form, TestPlayer player) throws Exception {
            var data = (AnimSystem.AnimSystemData) unsafe.allocateInstance(AnimSystem.AnimSystemData.class);
            data.playerForm = form;
            data.customData = new NbtCompound();
            data.LastPosition = player.getPos();
            data.fakeVelocity = new Vec3d(.1, .3, 0);
            return data;
        }

        private static void check(boolean success, String message) {
            checks++;
            if (!success) throw new AssertionError(message);
        }
    }

    public static final class TestPlayer extends PlayerEntity {
        private TestPlayer(World world, BlockPos pos, float yaw, GameProfile profile) {
            super(world, pos, yaw, profile);
        }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public boolean isSleeping() { return false; }
        @Override public boolean isClimbing() { return false; }
        @Override public boolean isTouchingWater() { return false; }
        @Override public boolean isFallFlying() { return false; }
        @Override public boolean isCrawling() { return false; }
        @Override public boolean isSprinting() { return false; }
        @Override public boolean isSneaking() { return false; }
        @Override public boolean isOnGround() { return false; }
        @Override public net.minecraft.entity.player.PlayerAbilities getAbilities() {
            return new net.minecraft.entity.player.PlayerAbilities();
        }
    }
}
