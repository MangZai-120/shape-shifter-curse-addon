package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import net.jackcooper.shapeShifterCurseAddon.client.SnowFoxEvolutionClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=Camera.class,priority=1100)
abstract class SnowFoxRollingCameraMixin {
    @Shadow protected abstract void setPos(Vec3d pos);
    @Shadow protected abstract void setRotation(float yaw,float pitch);

    @Inject(method="update",at=@At("TAIL"))
    private void ssca$followShell(BlockView world,Entity entity,boolean third,boolean inverse,float delta,CallbackInfo ci) {
        if (!third || !(entity instanceof ClientPlayerEntity player) || !SnowFoxEvolutionClient.cameraActive()) return;
        Vec3d eye=player.getCameraPosVec(delta);
        Vec3d offset=SnowFoxEvolutionClient.cameraOffset(player,delta,((Camera)(Object)this).getPos().subtract(eye));
        float front=SnowFoxEvolutionClient.frontWeight(player,delta);
        if (front>0) setRotation(player.getYaw(delta)+180*front,player.getPitch(delta)*(1-2*front));
        double length=offset.length();
        if (length < .001) { setPos(eye); return; }
        double fraction=1;
        for (int i=0;i<8;i++) {
            Vec3d from=eye.add((i%2-.5)*.2,((i/2)%2-.5)*.2,(i/4-.5)*.2);
            var hit=world.raycast(new RaycastContext(from,from.add(offset),RaycastContext.ShapeType.VISUAL,
                    RaycastContext.FluidHandling.NONE,entity));
            if (hit.getType()!=HitResult.Type.MISS) fraction=Math.min(fraction,Math.max(0,from.distanceTo(hit.getPos())-.02)/length);
        }
        setPos(eye.add(offset.multiply(fraction)));
    }
}
