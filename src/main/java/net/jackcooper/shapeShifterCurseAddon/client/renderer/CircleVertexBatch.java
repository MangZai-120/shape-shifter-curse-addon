package net.jackcooper.shapeShifterCurseAddon.client.renderer;

import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;

/** 保留原顶点顺序与浮点运算，通过 Sodium 公开 API 一次提交完整线层；其它消费者沿用原路径。 */
final class CircleVertexBatch {
    private CircleVertexBatch() {}
    private static final int MAX_BYTES=1024*1024;
    private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
    private static final class Scratch {
        ByteBuffer buffer;
        final Vector4f position=new Vector4f();
        boolean busy;
        long reserve(int bytes) {
            if(buffer==null||buffer.capacity()<bytes) {
                int capacity=1024;while(capacity<bytes)capacity*=2;
                // 有上限的直接缓冲区交由 JVM 清理，不留下显式分配的原生内存。
                buffer=org.lwjgl.BufferUtils.createByteBuffer(capacity);
            }
            return MemoryUtil.memAddress(buffer);
        }
    }
    private static final class Sodium {
        static final MethodHandle TRY, PUSH;
        static final Object FORMAT;
        static {
            MethodHandle find=null,push=null;Object format=null;
            if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium"))try {
                Class<?> writer=Class.forName("net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter");
                Class<?> description=Class.forName("net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription");
                var lookup=MethodHandles.publicLookup();
                find=lookup.unreflect(writer.getMethod("tryOf",VertexConsumer.class))
                        .asType(MethodType.methodType(Object.class,VertexConsumer.class));
                push=lookup.unreflect(writer.getMethod("push",MemoryStack.class,long.class,int.class,description))
                        .asType(MethodType.methodType(void.class,Object.class,MemoryStack.class,long.class,int.class,Object.class));
                format=Class.forName("net.caffeinemc.mods.sodium.api.vertex.format.common.ColorVertex").getField("FORMAT").get(null);
            }catch(ReflectiveOperationException|LinkageError unavailable) {
                find=null;push=null;format=null;
            }
            TRY=find;PUSH=push;FORMAT=format;
        }
    }
    static boolean draw(VertexConsumer consumer,Matrix4f matrix,MagicCircleGeometry.Mesh mesh,
                        boolean glow,int glowColor,float alpha) {
        if(Sodium.TRY==null||mesh.size()==0||mesh.size()>MAX_BYTES/64)return false;
        Scratch scratch=SCRATCH.get();if(scratch.busy)return false;
        try {
            Object writer=(Object)Sodium.TRY.invokeExact(consumer);if(writer==null)return false;
            scratch.busy=true;
            long base=scratch.reserve(mesh.size()*64),pointer=base;
            for(int i=0;i<mesh.size();i++) {
                var quad=mesh.quad(i,glow);
                float opacity=alpha*quad.alpha();if(glow)opacity*=MagicCircleGeometry.GLOW_ALPHA;
                int color=glow?glowColor:quad.color();
                // 与 VertexConsumer.color(float,...) 完全相同的乘法、截断和字节布局。
                int red=(int)((((color>>16)&255)/255F)*255F);
                int green=(int)((((color>>8)&255)/255F)*255F);
                int blue=(int)(((color&255)/255F)*255F);
                int packed=(red&255)|((green&255)<<8)|((blue&255)<<16)|(((int)(opacity*255F)&255)<<24);
                put(scratch,matrix,pointer,quad.x1(),quad.z1(),packed);pointer+=16;
                put(scratch,matrix,pointer,quad.x2(),quad.z2(),packed);pointer+=16;
                put(scratch,matrix,pointer,quad.x3(),quad.z3(),packed);pointer+=16;
                put(scratch,matrix,pointer,quad.x4(),quad.z4(),packed);pointer+=16;
            }
            try(MemoryStack stack=MemoryStack.stackPush()) {
                Sodium.PUSH.invokeExact(writer,stack,base,mesh.size()*4,Sodium.FORMAT);
            }
            return true;
        }catch(RuntimeException|Error error) {throw error;}
        catch(Throwable error) {throw new IllegalStateException("Cannot submit circle vertices",error);}
        finally {scratch.busy=false;}
    }
    private static void put(Scratch scratch,Matrix4f matrix,long pointer,float x,float z,int color) {
        // 原 API 使用 matrix.transform(Vector4f)，复用向量而不改变变换的计算顺序。
        Vector4f p=matrix.transform(scratch.position.set(x,0,z,1));
        MemoryUtil.memPutFloat(pointer,p.x());MemoryUtil.memPutFloat(pointer+4,p.y());
        MemoryUtil.memPutFloat(pointer+8,p.z());MemoryUtil.memPutInt(pointer+12,color);
    }
}
