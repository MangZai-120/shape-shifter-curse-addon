package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Accepted locks use a frozen effective range; this guard never owns released effects. */
public final class CastRangeGuard {
    public static final double MULTIPLIER=1.5;
    private CastRangeGuard() {}
    public record Lock(Vec3d point,LivingEntity entity,double range) {
        public boolean exceeded(ServerPlayerEntity caster) {
            if(entity!=null&&(entity.isRemoved()||!entity.isAlive()||entity.getWorld()!=caster.getWorld()))return false;
            return exceeds(caster.getPos(),entity==null?point:entity.getPos(),range);
        }
    }
    public static Lock point(Vec3d point,double range){return point!=null&&validRange(range)?new Lock(point,null,range):null;}
    public static Lock entity(LivingEntity entity,double range){return entity!=null&&validRange(range)?new Lock(entity.getPos(),entity,range):null;}
    private static boolean validRange(double range){return Double.isFinite(range)&&range>0;}
    public static boolean exceeds(Vec3d caster,Vec3d target,double range) {
        if(caster==null||target==null||!validRange(range))return false;
        double limit=range*MULTIPLIER;
        return caster.squaredDistanceTo(target)>limit*limit;
    }
}
