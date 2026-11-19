package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.jackcooper.shapeShifterCurseAddon.entity.OrcafisEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

final class OrcafisWaterFlow {
    private static final Identifier FLOW = new Identifier("my_addon","textures/entity/orcafis/water_flow.png");
    private static final Identifier INTAKE = new Identifier("my_addon","textures/entity/orcafis/intake_foam.png");
    private static final double TAU = Math.PI*2;

    static void render(OrcafisEntity boss, Vec3d mouth, float t, float partial, MatrixStack m, VertexConsumerProvider buffers, int light) {
        if (boss.motion() == OrcafisEntity.Motion.UNDERTOW) {
            if (!boss.drawingWater() || t >= OrcafisEntity.UNDERTOW_DRAW_TICKS) return;
            Vec3d origin = boss.getLerpedPos(partial);
            Vec3d forward = boss.jetDirection().normalize();
            if (!wet(boss.getWorld(),origin.add(mouth))) return;
            intake(boss,origin,mouth,new Frame(forward),t,m,buffers.getBuffer(RenderLayer.getEntityTranslucent(INTAKE)),light);
        } else if (t >= 14 && t < 54 && boss.jetLength() > .02f) {
            // The source follows the rendered lips. The tip shares the server's terrain-clipped endpoint.
            Vec3d end = boss.jetOrigin().add(boss.jetDirection().multiply(boss.jetLength()));
            Vec3d axis = end.subtract(mouth);
            if (axis.lengthSquared() > .001)
                jet(mouth,new Frame(axis.normalize()),axis.length(),t,m,buffers.getBuffer(RenderLayer.getEntityTranslucent(FLOW)),light);
        }
    }

    private record Frame(Vec3d forward,Vec3d side,Vec3d up) {
        Frame(Vec3d f) { this(f,new Vec3d(f.z,0,-f.x).lengthSquared() < 1e-6
                ? new Vec3d(1,0,0) : new Vec3d(f.z,0,-f.x).normalize()); }
        private Frame(Vec3d f,Vec3d s) { this(f,s,f.crossProduct(s)); }
        Vec3d radial(double a) { return side.multiply(Math.cos(a)).add(up.multiply(Math.sin(a))); }
        Vec3d point(Vec3d start,double axial,double radius,double angle) {
            return start.add(forward.multiply(axial)).add(radial(angle).multiply(radius));
        }
    }

    private static void intake(OrcafisEntity boss, Vec3d origin, Vec3d mouth, Frame frame, float t,
                               MatrixStack m, VertexConsumer buf, int light) {
        double strength = Math.min(1,(t+1)/7)*Math.min(1,(OrcafisEntity.UNDERTOW_DRAW_TICKS-t)/2);
        // A packet of foam follows each curved current from the edge of the cone to the mouth.
        double clock = t*.040+t*t*.00025;
        for (int lane=0; lane<9; lane++) {
            double phase = 1-MathHelper.fractionalPart(clock+lane*.382);
            double baseAngle = TAU*lane/9;
            double spread = .26+.29*(.5+.5*Math.sin(lane*2.4));
            double far = Math.min(1,phase+.34);
            Vec3d farEnd = frame.point(mouth,far*6.9,far*6.9*spread,baseAngle+far*3.25+t*.026);
            if (blocked(boss,origin.add(mouth),origin.add(farEnd))) continue;
            for (int j=0;j<8;j++) {
                double a=MathHelper.clamp(phase-.22+j*.07,0,1), b=MathHelper.clamp(phase-.15+j*.07,0,1);
                if (a==b) continue;
                double angleA=baseAngle+a*3.25+t*.026, angleB=baseAngle+b*3.25+t*.026;
                Vec3d p=frame.point(mouth,a*6.9,a*6.9*spread,angleA), q=frame.point(mouth,b*6.9,b*6.9*spread,angleB);
                double w=(.065+(lane%2==0 ? .72 : .31)*a)*strength;
                Vec3d left=frame.radial(angleA+Math.PI/2).multiply(w*Math.sin(Math.PI*j/8));
                Vec3d right=frame.radial(angleB+Math.PI/2).multiply(w*Math.sin(Math.PI*(j+1)/8));
                wetStrip(boss.getWorld(),origin,m,buf,p,q,left,right,(float)(a*2+t*.13),(float)(b*2+t*.13),light,245);
            }
        }
        // Broken crests contract around the flow. Their open ends keep the silhouette readable.
        for (int ring=0;ring<3;ring++) {
            double s=1-MathHelper.fractionalPart(clock*.83+ring/3.0);
            double reach=.12+s*6.6, radius=(.10+reach*.52)*strength;
            double base=ring*2.21+t*.071;
            for (int j=0;j<14;j++) {
                double a=base+j*.29,b=a+.29;
                Vec3d p=frame.point(mouth,reach+.13*Math.sin(a),radius,a);
                Vec3d q=frame.point(mouth,reach+.13*Math.sin(b),radius,b);
                if (blocked(boss,origin.add(mouth),origin.add(p))) continue;
                double width=(.12+.23*s)*strength*Math.sin((j+.5)*Math.PI/14);
                Vec3d edgeA=frame.forward.add(frame.radial(a).multiply(.65)).multiply(width);
                Vec3d edgeB=frame.forward.add(frame.radial(b).multiply(.65)).multiply(width);
                wetStrip(boss.getWorld(),origin,m,buf,p,q,edgeA,edgeB,j*.12f,j*.12f+.12f,light,211);
            }
        }
    }

    private static boolean blocked(OrcafisEntity boss,Vec3d start,Vec3d end) {
        return boss.getWorld().raycast(new RaycastContext(start,end,RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,boss)).getType()!=HitResult.Type.MISS;
    }

    private static boolean wet(World world,Vec3d point) {
        BlockPos pos=BlockPos.ofFloored(point);
        var water=world.getFluidState(pos);
        return water.isIn(FluidTags.WATER) && point.y<pos.getY()+water.getHeight(world,pos);
    }

    private static void wetStrip(World world,Vec3d origin,MatrixStack m,VertexConsumer buf,Vec3d p,Vec3d q,
                                 Vec3d n,Vec3d k,float u,float v,int light,int alpha) {
        Vec3d a=p.subtract(n),b=p.add(n),c=q.add(k),d=q.subtract(k);
        if (!wet(world,origin.add(a)) || !wet(world,origin.add(b)) || !wet(world,origin.add(c))
                || !wet(world,origin.add(d)) || !wet(world,origin.add(p.lerp(q,.5)))) return;
        quad(m,buf,a,b,c,d,u,v,light,alpha);
    }

    private static void jet(Vec3d mouth,Frame frame,double length,float t,MatrixStack m,VertexConsumer buf,int light) {
        int sections=Math.max(2,MathHelper.ceil(length/.55));
        // Each cross section grows and breaks up toward the tip, with crests travelling downstream.
        Vec3d[] prev=new Vec3d[9],next=new Vec3d[9];
        for (int j=0;j<=sections;j++) {
            double s=(double)j/sections, x=length*s;
            for (int k=0;k<8;k++) {
                double a=k*TAU/8;
                double r=(.125+.17*s)*(.95+.13*Math.sin(x*3.1-t*.78+a*2)+.08*Math.sin(a*3-x*1.8+t*.39));
                next[k]=frame.point(mouth,x,r,a+.18*Math.sin(x*1.4-t*.27));
            }
            next[8]=next[0];
            if (j>0) {
                float u=(float)((x-length/sections)*.57-t*.16),v=(float)(x*.57-t*.16);
                for (int k=0;k<8;k++) quad(m,buf,prev[k],prev[k+1],next[k+1],next[k],u,v,light,222);
            }
            Vec3d[] swap=prev;prev=next;next=swap;
        }
    }

    private static void quad(MatrixStack m,VertexConsumer buf,Vec3d a,Vec3d b,Vec3d c,Vec3d d,
                             float start,float end,int light,int alpha) {
        vertex(m,buf,a,start,0,light,alpha); vertex(m,buf,b,start,1,light,alpha);
        vertex(m,buf,c,end,1,light,alpha); vertex(m,buf,d,end,0,light,alpha);
    }
    private static void vertex(MatrixStack m,VertexConsumer buf,Vec3d p,float u,float v,int light,int alpha) {
        OrcafisRenderer.vertex(buf,m,(float)p.x,(float)p.y,(float)p.z,u,v,light,alpha);
    }
}
