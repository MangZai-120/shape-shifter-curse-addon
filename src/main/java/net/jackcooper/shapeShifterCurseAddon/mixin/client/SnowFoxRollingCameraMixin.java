package net.jackcooper.shapeShifterCurseAddon.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.jackcooper.shapeShifterCurseAddon.client.SnowFoxEvolutionClient;
import net.jackcooper.shapeShifterCurseAddon.client.renderer.DomainRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value=Camera.class,priority=1100)
abstract class SnowFoxRollingCameraMixin {
    @Shadow protected abstract void setPos(Vec3d pos);
    @Shadow protected abstract void setRotation(float yaw,float pitch);
    @Shadow private boolean thirdPerson;

    @WrapMethod(method="update")
    private void ssca$followShell(BlockView world, Entity entity, boolean third, boolean inverse, float delta, Operation<Void> original) {
        // Other cameras can supply both a position and a free-look rotation, then cancel update.
        original.call(world, entity, third, inverse, delta);
        if (!(entity instanceof ClientPlayerEntity player) || !SnowFoxEvolutionClient.controlsCamera(player)) return;
        Camera camera = (Camera)(Object)this;
        var mc = MinecraftClient.getInstance();
        if (mc.player != player || mc.gameRenderer.getCamera() != camera) return;
        Vec3d eye=player.getCameraPosVec(delta);
        var pose = SnowFoxEvolutionClient.shellCamera(camera, player, eye, delta);
        if (pose == null) return;
        setRotation(pose.yaw(), pose.pitch());
        Vec3d offset = pose.offset();
        double length=offset.length();
        if (length < .001) { setPos(eye); return; }
        double fraction=1;
        for (int i=0;i<8;i++) {
            Vec3d from=eye.add((i%2-.5)*.2,((i/2)%2-.5)*.2,(i/4-.5)*.2);
            var hit=world.raycast(new RaycastContext(from,from.add(offset),RaycastContext.ShapeType.VISUAL,
                    RaycastContext.FluidHandling.NONE,entity));
            if (hit.getType()!=HitResult.Type.MISS) fraction=Math.min(fraction,Math.max(0,from.distanceTo(hit.getPos())-.02)/length);
        }
        Vec3d target = eye.add(offset.multiply(fraction));
        Vec3d insideDomain = DomainRenderer.projectInside(eye, target);
        Vec3d position = insideDomain == null ? target : insideDomain;
        setPos(position);
        if (!third && position.squaredDistanceTo(eye) > .16) thirdPerson = true;
    }
}
