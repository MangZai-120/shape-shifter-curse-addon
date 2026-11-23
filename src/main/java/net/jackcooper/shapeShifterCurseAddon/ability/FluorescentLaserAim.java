package net.jackcooper.shapeShifterCurseAddon.ability;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public final class FluorescentLaserAim {
    private static final double LOOK_LIMIT = 60.0;
    private static final double COS_LIMIT = Math.cos(Math.toRadians(LOOK_LIMIT));

    private static final int RAMP_TICKS = 20;

    private FluorescentLaserAim() {}

    /** World-space rotation axis times radians per server tick. */
    public record Turn(Vec3d direction, Vec3d angularVelocity) {}

    public static Turn advance(Vec3d from, Vec3d target, Vec3d velocity, double degreesPerSecond) {
        if (from.lengthSquared() == 0) return new Turn(target, Vec3d.ZERO);
        double limit = Math.toRadians(degreesPerSecond) / 20;
        double acceleration = limit / RAMP_TICKS;
        Vec3d cross = from.crossProduct(target);
        double sine = cross.length();
        double distance = Math.atan2(sine, from.dotProduct(target));
        // Leave enough angular distance to remove one acceleration step each subsequent tick.
        double brakingTicks = Math.max(1, Math.ceil((Math.sqrt(1 + 8 * distance / acceleration) - 1) / 2));
        double brakingSpeed = distance / brakingTicks + (brakingTicks - 1) * acceleration / 2;
        double desiredSpeed = Math.min(limit, brakingSpeed);
        Vec3d desired = Vec3d.ZERO;
        if (desiredSpeed > 0) {
            Vec3d axis;
            if (sine > 1.0e-12) axis = cross.multiply(1 / sine);
            else if (velocity.lengthSquared() > 1.0e-12) axis = velocity.multiply(1 / velocity.length());
            else axis = oppositeTurnAxis(from);
            desired = axis.multiply(desiredSpeed);
        }

        Vec3d change = desired.subtract(velocity);
        double changeLength = change.length();
        if (changeLength <= acceleration && desiredSpeed == distance) return new Turn(target, Vec3d.ZERO);
        Vec3d next = changeLength <= acceleration ? desired : velocity.add(change.multiply(acceleration / changeLength));
        double speed = next.length();
        if (speed > limit) {
            next = next.multiply(limit / speed);
            speed = limit;
        }
        if (speed < 1.0e-12) return new Turn(from, Vec3d.ZERO);
        Vec3d direction = from.multiply(Math.cos(speed)).add(next.crossProduct(from).multiply(Math.sin(speed) / speed));
        return new Turn(direction.normalize(), next);
    }

    private static Vec3d oppositeTurnAxis(Vec3d from) {
        Vec3d tangent = Math.abs(from.y) < 0.999999 ? new Vec3d(-from.z, 0, from.x) : from.crossProduct(new Vec3d(1, 0, 0));
        return from.crossProduct(tangent).normalize();
    }

    /** Advance along the shorter great-circle arc between unit directions. */
    public static Vec3d turnTowards(Vec3d from, Vec3d target, double stepRadians) {
        double dot = from.dotProduct(target);
        if (from.lengthSquared() == 0 || dot >= Math.cos(stepRadians)) return target;
        Vec3d tangent = target.subtract(from.multiply(dot));
        double length = tangent.length();
        if (length < 1.0e-10) {
            // Opposite directions have no unique shortest arc.
            tangent = oppositeTurnAxis(from).crossProduct(from);
            length = tangent.length();
        }
        return from.multiply(Math.cos(stepRadians)).add(tangent.multiply(Math.sin(stepRadians) / length)).normalize();
    }

    /** Returns pitch (x) and yaw (y) in degrees. Both mouse deltas are in degrees. */
    public static Vec2f limitLook(Vec3d beam, float yaw, float pitch, double deltaYaw, double deltaPitch) {
        double horizontal = beam.horizontalLength();
        double axisPitch = Math.toDegrees(Math.atan2(-beam.y, horizontal));
        double nextPitch = MathHelper.clamp(pitch + deltaPitch, Math.max(-90, axisPitch - LOOK_LIMIT), Math.min(90, axisPitch + LOOK_LIMIT));
        double pitchRad = Math.toRadians(nextPitch);
        double denominator = horizontal * Math.cos(pitchRad);
        double nextYaw = yaw + deltaYaw;
        if (denominator > 1.0e-10) {
            // At this pitch, solve dot(look, beam) >= cos(60°) for the permitted yaw interval.
            double threshold = (COS_LIMIT + beam.y * Math.sin(pitchRad)) / denominator;
            if (threshold > -1) {
                double halfWidth = Math.toDegrees(Math.acos(MathHelper.clamp(threshold, -1, 1)));
                double axisYaw = Math.toDegrees(Math.atan2(-beam.x, beam.z));
                double offset = MathHelper.wrapDegrees(yaw - axisYaw);
                // Clamp before wrapping the mouse delta, so a large flick stops on its own side.
                nextYaw = yaw - offset + MathHelper.clamp(offset + deltaYaw, -halfWidth, halfWidth);
            }
        }
        return new Vec2f((float)nextPitch, (float)nextYaw);
    }
}
