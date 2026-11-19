package net.jackcooper.shapeShifterCurseAddon.entity;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.core.animation.EasingType;
import software.bernie.geckolib.core.keyframe.AnimationPoint;
import software.bernie.geckolib.core.keyframe.BoneAnimation;
import software.bernie.geckolib.core.keyframe.Keyframe;
import software.bernie.geckolib.loading.json.typeadapter.BakedAnimationsAdapter;
import software.bernie.geckolib.loading.object.BakedAnimations;

/** Rebuilds section bounds using the authored geometry and the same clips sampled by the renderer. */
final class OrcafisCollisionRig {
    enum Section { TORSO, HEAD, LOWER_NECK, UPPER_NECK, TAIL_BASE, TAIL_MIDDLE, TAIL_TIP, FLUKE }

    private record Bone(int parent, String name, Vector3f pivot, Vector3f rotation, Section section, List<Vector3f> corners) {}
    private static final String ASSETS = "/assets/my_addon/";
    private static final String CLIP_PREFIX = "animation.orcafis.";
    private static final List<Bone> BONES = bones();
    private static final Map<String,Map<String,BoneAnimation>> CLIPS = clips();

    private OrcafisCollisionRig() {}

    record Pose(Box[] boxes, Vec3d mouth) {
        Box torso() { return boxes[Section.TORSO.ordinal()]; }
    }

    static Pose sample(OrcafisEntity boss) {
        return sample(boss.motion(), boss.getPos(), boss.bodyYaw,
                boss.animationTick(boss.motionTick(0), false), boss.animationTick(boss.motionTick(0), true),
                boss.jetElevation(), boss.undertowOffset());
    }

    static Pose local(OrcafisEntity.Motion motion, int tick) {
        return sample(motion,Vec3d.ZERO,0,OrcafisEntity.animationTick(motion,tick,false,motion.ticks),
                OrcafisEntity.animationTick(motion,tick,true,motion.ticks),0,0);
    }

    private static Pose sample(OrcafisEntity.Motion motion, Vec3d origin, float yaw,
                               double bodyTick, double turnTick, float neckPitch, float waterOffset) {
        var turn = switch (motion) {
            case WATER_LUNGE, DASH -> OrcafisEntity.Motion.DASH;
            case RETURN -> OrcafisEntity.Motion.RETURN;
            default -> null;
        };
        Map<String,BoneAnimation> body = CLIPS.get(turn == null ? motion.clip : "swim");
        Map<String,BoneAnimation> overlay = turn == null ? Map.of() : CLIPS.get(turn.clip);
        Matrix4f worldYaw = new Matrix4f().rotateY((180 - yaw)*MathHelper.RADIANS_PER_DEGREE);
        Matrix4f[] matrices = new Matrix4f[BONES.size()];
        Bounds[] bounds = new Bounds[Section.values().length];
        Vector3f corner = new Vector3f();
        Vec3d mouth = origin;
        for (int index = 0; index < BONES.size(); index++) {
            Bone bone = BONES.get(index);
            Transform pose = new Transform(bone.rotation);
            pose.apply(body.get(bone.name),bodyTick);
            pose.apply(overlay.get(bone.name),turnTick);
            if (bone.name.equals("shore_neck_aim")) pose.rotation.x += neckPitch;
            if (bone.name.equals("shore_water_height")) pose.position.y += waterOffset;
            Matrix4f transform = matrices[index] = new Matrix4f(bone.parent < 0 ? worldYaw : matrices[bone.parent]);
            transform.translate(pose.position).translate(bone.pivot)
                    .rotateZYX(pose.rotation.z,pose.rotation.y,pose.rotation.x)
                    .scale(pose.scale).translate(-bone.pivot.x,-bone.pivot.y,-bone.pivot.z);
            if (bone.name.equals("Head") || bone.name.equals("shore_jaw")) {
                Vec3d lip = origin.add(new Vec3d(transform.transformPosition(new Vector3f(0,100.79703f/16,3.28202f/16))));
                mouth = bone.name.equals("Head") ? lip : mouth.lerp(lip,.5);
            }
            if (bone.section == null || bone.corners.isEmpty()) continue;
            int section = bone.section.ordinal();
            if (bounds[section] == null) bounds[section] = new Bounds();
            for (Vector3f local : bone.corners) bounds[section].include(transform.transformPosition(local,corner));
        }
        Box[] boxes = new Box[bounds.length];
        for (int index = 0; index < boxes.length; index++) {
            Bounds bound = bounds[index];
            if (bound != null) boxes[index] = bound.box().offset(origin);
        }
        return new Pose(boxes,mouth);
    }

    private static final class Transform {
        final Vector3f restRotation, rotation, position = new Vector3f(), scale = new Vector3f(1);
        Transform(Vector3f restRotation) {
            this.restRotation = restRotation;
            rotation = new Vector3f(restRotation);
        }

        void apply(BoneAnimation animation, double tick) {
            if (animation == null) return;
            var rot = animation.rotationKeyFrames();
            var pos = animation.positionKeyFrames();
            var size = animation.scaleKeyFrames();
            if (!rot.xKeyframes().isEmpty()) rotation.set(restRotation.x + at(rot.xKeyframes(),tick),
                    restRotation.y + at(rot.yKeyframes(),tick), restRotation.z + at(rot.zKeyframes(),tick));
            if (!pos.xKeyframes().isEmpty()) position.set(-at(pos.xKeyframes(),tick)/16,
                    at(pos.yKeyframes(),tick)/16, at(pos.zKeyframes(),tick)/16);
            if (!size.xKeyframes().isEmpty()) scale.set(at(size.xKeyframes(),tick),
                    at(size.yKeyframes(),tick), at(size.zKeyframes(),tick));
        }

        private static float at(List<? extends Keyframe<?>> frames, double tick) {
            for (Keyframe<?> frame : frames) {
                if (tick < frame.length()) {
                    return (float)EasingType.lerpWithOverride(new AnimationPoint(frame,tick,frame.length(),
                            frame.startValue().get(),frame.endValue().get()),null);
                }
                tick -= frame.length();
            }
            return (float)frames.get(frames.size()-1).endValue().get();
        }
    }

    private static List<Bone> bones() {
        JsonArray nodes = read("geo/orcafis.geo.json").getAsJsonArray("minecraft:geometry")
                .get(0).getAsJsonObject().getAsJsonArray("bones");
        Map<String,Integer> indexes = new HashMap<>();
        List<Bone> result = new ArrayList<>();
        for (var node : nodes) {
            JsonObject json = node.getAsJsonObject();
            String name = json.get("name").getAsString();
            int parent = json.has("parent") ? indexes.get(json.get("parent").getAsString()) : -1;
            Section section = section(name);
            List<Vector3f> corners = section == null ? List.of() : corners(json.getAsJsonArray("cubes"));
            indexes.put(name,result.size());
            result.add(new Bone(parent,name,point(json,"pivot"),rotation(json),section,corners));
        }
        return List.copyOf(result);
    }

    private static Section section(String bone) {
        return switch (bone) {
            case "body6", "upbody_1", "upbody_2", "upbody_3" -> Section.TORSO;
            case "Head", "shore_jaw" -> Section.HEAD;
            case "neck_behind", "neck2_behind", "neck3_behind" -> Section.LOWER_NECK;
            case "neck4_behind", "neck5_behind", "Head6_behind" -> Section.UPPER_NECK;
            case "tail", "tail2" -> Section.TAIL_BASE;
            case "tail3_l_or_r", "tail6" -> Section.TAIL_MIDDLE;
            case "tail9", "tail8" -> Section.TAIL_TIP;
            case "tail_left", "tail_right" -> Section.FLUKE;
            default -> null;
        };
    }

    private static List<Vector3f> corners(JsonArray cubes) {
        if (cubes == null) return List.of();
        List<Vector3f> corners = new ArrayList<>();
        for (var element : cubes) {
            JsonObject cube = element.getAsJsonObject();
            Vector3f origin = point(cube,"origin");
            Vector3f size = point(cube,"size");
            Vector3f pivot = point(cube,"pivot");
            Vector3f angle = rotation(cube);
            Matrix4f transform = new Matrix4f().translate(pivot).rotateZYX(angle.z,angle.y,angle.x)
                    .translate(-pivot.x,-pivot.y,-pivot.z);
            for (int corner = 0; corner < 8; corner++) {
                corners.add(transform.transformPosition(new Vector3f(
                        origin.x + ((corner&1) == 0 ? 0 : size.x),
                        origin.y + ((corner&2) == 0 ? 0 : size.y),
                        origin.z + ((corner&4) == 0 ? 0 : size.z))));
            }
        }
        return List.copyOf(corners);
    }

    private static Vector3f point(JsonObject object, String key) {
        return vector(object,key).mul(-1f/16,1f/16,1f/16);
    }

    private static Vector3f rotation(JsonObject object) {
        float radians = MathHelper.RADIANS_PER_DEGREE;
        return vector(object,"rotation").mul(-radians,-radians,radians);
    }

    private static Vector3f vector(JsonObject object, String key) {
        JsonArray vector = object.getAsJsonArray(key);
        return vector == null ? new Vector3f() : new Vector3f(vector.get(0).getAsFloat(),vector.get(1).getAsFloat(),vector.get(2).getAsFloat());
    }

    private static Map<String,Map<String,BoneAnimation>> clips() {
        var gson = new GsonBuilder().registerTypeAdapter(BakedAnimations.class,new BakedAnimationsAdapter()).create();
        BakedAnimations baked = gson.fromJson(read("animations/orcafis.animation.json").get("animations"),BakedAnimations.class);
        Map<String,Map<String,BoneAnimation>> clips = new HashMap<>();
        baked.animations().forEach((name,animation) -> {
            Map<String,BoneAnimation> tracks = new HashMap<>();
            for (BoneAnimation track : animation.boneAnimations()) tracks.put(track.boneName(),track);
            if (name.startsWith(CLIP_PREFIX)) clips.put(name.substring(CLIP_PREFIX.length()),tracks);
        });
        return Map.copyOf(clips);
    }

    private static JsonObject read(String relativePath) {
        try (var stream = OrcafisCollisionRig.class.getResourceAsStream(ASSETS + relativePath)) {
            if (stream == null) throw new IllegalStateException("Missing Orcafis asset " + relativePath);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private static final class Bounds {
        final Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        final Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        void include(Vector3f corner) { min.min(corner); max.max(corner); }
        Box box() { return new Box(min.x,min.y,min.z,max.x,max.y,max.z); }
    }
}
