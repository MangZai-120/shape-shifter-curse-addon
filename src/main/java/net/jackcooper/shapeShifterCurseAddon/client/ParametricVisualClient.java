package net.jackcooper.shapeShifterCurseAddon.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.client.particle.FirstPersonParticles;
import net.jackcooper.shapeShifterCurseAddon.network.visual.ParametricVisuals;
import net.jackcooper.shapeShifterCurseAddon.network.visual.ParametricVisuals.Circle;
import net.jackcooper.shapeShifterCurseAddon.network.visual.ParametricVisuals.Key;
import net.jackcooper.shapeShifterCurseAddon.network.visual.ParametricVisuals.Kind;
import net.jackcooper.shapeShifterCurseAddon.network.visual.ParametricVisuals.View;
import net.jackcooper.shapeShifterCurseAddon.network.visual.SceneRecipes;
 import net.jackcooper.shapeShifterCurseAddon.network.visual.VisualSampleHistory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Cosmetic sampling only. The server retains every gameplay decision and scene lifetime. */
@Environment(EnvType.CLIENT)
public final class ParametricVisualClient {
    private static final int CIRCLE_BUDGET = 4096, MAX_QUEUED_PARTICLES = 262144, MAX_QUEUED_CIRCLES = 4096;
    private static final Map<Key, Runtime> scenes = new LinkedHashMap<>();
    private static final VisualSampleHistory<Key> expiredSamples = new VisualSampleHistory<>(ParametricVisuals.MAX_SCENES);
    private static final ArrayDeque<CircleJob> circles = new ArrayDeque<>();
    private static ClientWorld world;
    private static Pending pending;
    private static PendingDelta pendingDelta;
    private static final Map<Key, TimedMotion> pendingMotions = new LinkedHashMap<>();
    private static boolean initialized, hasSequence;
    private static int sequence, budget = CIRCLE_BUDGET, ticks, receivedAt;
    private static long referenceServer, referenceLocal, lastWorldTick = Long.MIN_VALUE;
    private static long statePackets, deltaPackets, motionPackets, circlePackets, sampledBatches;
    private static long emittedParticles, deferredParticles, droppedParticles, malformedPackets, queuedParticles;
    private static Observer observer;

    @FunctionalInterface
    public interface Observer {
        void observe(View view, int sampleAge, long sampleTick, int batchIndex, ParticleEffect particle,
                     double x, double y, double z, int count, double dx, double dy, double dz,
                     double speed, boolean decoration, boolean projectile);
    }
    public record Metrics(int scenes, int pendingCircles, long statePackets, long motionPackets,
                          long circlePackets, long sampledBatches, long emittedParticles,
                          long deferredParticles, long droppedParticles, long malformedPackets) {}
    private record StatePage(Identifier dimension, long time, int sequence, int page, int pages, List<View> views) {}
    private record DeltaPage(long time, int sequence, int base, int page, int pages, List<View> views, List<Key> removed) {}
    private record Motion(Key key, Vec3d position, Vec3d velocity) {}
    private record TimedMotion(long time, int receivedAt, Motion motion) {}
    private static final class Pending {
        final int sequence, createdAt;
        final long time;
        final List<View>[] pages;
        int count;
        @SuppressWarnings("unchecked")
        Pending(StatePage page) {
            sequence = page.sequence; time = page.time; createdAt = ticks;
            pages = (List<View>[]) new List<?>[page.pages];
        }
    }
    private static final class PendingDelta {
        final int sequence, base, createdAt;
        final long time;
        final DeltaPage[] pages;
        int count;
        PendingDelta(DeltaPage page) {
            sequence=page.sequence; base=page.base; time=page.time; createdAt=ticks;
            pages=new DeltaPage[page.pages];
        }
    }
    private static final class Runtime {
        View view;
        Vec3d position, velocity;
        long motionTime;
        int lastSample = -1;
        final Random recipeRandom = new Random(), jitterRandom = new Random();
        Runtime(View view, long time) {
            update(view, time);
            lastSample = expiredSamples.resume(view.key(), view.anchorUuid(), view.entityId(), view.epoch());
        }
        void update(View next, long time) {
            boolean sameSource=view!=null && view.epoch()==next.epoch()
                    && view.entityId()==next.entityId() && view.anchorUuid().equals(next.anchorUuid());
            if (view != null && view.epoch() != next.epoch()) lastSample = -1;
            view = next;
            // A newer motion packet may arrive between the pages of this metadata update.
            if(!sameSource || time>=motionTime) { position=next.position(); velocity=next.velocity(); motionTime=time; }
        }
    }
    private static final class CircleJob {
        final Circle circle;
        final Random random = new Random();
        int sample, particle;
        boolean initialized;
        CircleJob(Circle circle) { this.circle = circle; }
        long remaining() { return (long)(circle.samples() - sample) * Math.max(1, circle.count()) - particle; }
    }
    private ParametricVisualClient() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientPlayNetworking.registerGlobalReceiver(ParametricVisuals.MIST_FORM_CAPABILITY,
                (client, handler, buf, sender) -> { });
        ClientPlayNetworking.registerGlobalReceiver(ParametricVisuals.STATE, (client, handler, buf, sender) -> {
            try {
                Identifier dimension = buf.readIdentifier(); long time = buf.readLong(); int seq = buf.readInt();
                int page = ParametricVisuals.bounded(buf.readVarInt(), 0, ParametricVisuals.MAX_PAGES - 1);
                int pages = ParametricVisuals.bounded(buf.readVarInt(), 1, ParametricVisuals.MAX_PAGES);
                if (page >= pages) throw new IllegalArgumentException("Invalid visual page");
                int count = ParametricVisuals.bounded(buf.readVarInt(), 0, ParametricVisuals.PAGE_SIZE);
                List<View> incoming = new ArrayList<>(count);
                for (int i = 0; i < count; i++) incoming.add(View.read(buf));
                StatePage decoded = new StatePage(dimension, time, seq, page, pages, incoming);
                client.execute(() -> {
                    if (client.getNetworkHandler() != handler || !acceptWorld(client, dimension)) return;
                    statePackets++;
                    apply(decoded);
                });
            } catch (RuntimeException invalid) { client.execute(() -> malformedPackets++); }
        });
        ClientPlayNetworking.registerGlobalReceiver(ParametricVisuals.MOTION, (client, handler, buf, sender) -> {
            try {
                Identifier dimension = buf.readIdentifier(); long time = buf.readLong();
                int count = ParametricVisuals.bounded(buf.readVarInt(), 0, ParametricVisuals.PAGE_SIZE);
                List<Motion> incoming = new ArrayList<>(count);
                for (int i = 0; i < count; i++) incoming.add(new Motion(Key.read(buf), ParametricVisuals.vector(buf), ParametricVisuals.motion(buf)));
                client.execute(() -> {
                    if (client.getNetworkHandler() != handler || !acceptWorld(client, dimension)) return;
                    motionPackets++;
                    for (Motion motion : incoming) {
                        Runtime runtime = scenes.get(motion.key);
                        if(runtime==null) {
                            TimedMotion previous=pendingMotions.get(motion.key);
                            if((previous==null && pendingMotions.size()<ParametricVisuals.MAX_SCENES)
                                    || previous!=null && time>=previous.time)
                                pendingMotions.put(motion.key,new TimedMotion(time,ticks,motion));
                        } else applyMotion(runtime,motion,time);
                    }
                });
            } catch (RuntimeException invalid) { client.execute(() -> malformedPackets++); }
        });
        ClientPlayNetworking.registerGlobalReceiver(ParametricVisuals.DELTA, (client, handler, buf, sender) -> {
            try {
                Identifier dimension=buf.readIdentifier(); long time=buf.readLong();
                int seq=buf.readInt(), base=buf.readInt();
                int page=ParametricVisuals.bounded(buf.readVarInt(),0,ParametricVisuals.MAX_PAGES-1);
                int pages=ParametricVisuals.bounded(buf.readVarInt(),1,ParametricVisuals.MAX_PAGES);
                if(page>=pages) throw new IllegalArgumentException("Invalid visual delta page");
                int changed=ParametricVisuals.bounded(buf.readVarInt(),0,ParametricVisuals.PAGE_SIZE);
                List<View> incoming=new ArrayList<>(changed);
                for(int i=0;i<changed;i++) incoming.add(View.read(buf));
                int removed=ParametricVisuals.bounded(buf.readVarInt(),0,ParametricVisuals.PAGE_SIZE);
                List<Key> removals=new ArrayList<>(removed);
                for(int i=0;i<removed;i++) removals.add(Key.read(buf));
                DeltaPage decoded=new DeltaPage(time,seq,base,page,pages,incoming,removals);
                client.execute(() -> {
                    if(client.getNetworkHandler()!=handler || !acceptWorld(client,dimension)) return;
                    deltaPackets++; apply(decoded);
                });
            } catch(RuntimeException invalid) {client.execute(() -> malformedPackets++);}
        });
        ClientPlayNetworking.registerGlobalReceiver(ParametricVisuals.CIRCLE, (client, handler, buf, sender) -> {
            try {
                Identifier dimension = buf.readIdentifier(); Circle circle = Circle.read(buf);
                client.execute(() -> {
                    if (client.getNetworkHandler() != handler || !acceptWorld(client, dimension) || client.player == null) return;
                    circlePackets++;
                    CircleJob job = new CircleJob(circle);
                    if (!client.isPaused() && circles.isEmpty()) renderCircle(client, job);
                    long left = job.remaining();
                    if (left == 0) return;
                    if (left > MAX_QUEUED_PARTICLES - queuedParticles || circles.size() >= MAX_QUEUED_CIRCLES) {
                        droppedParticles += left; return;
                    }
                    circles.add(job); queuedParticles += left; deferredParticles += left;
                });
            } catch (RuntimeException invalid) { client.execute(() -> malformedPackets++); }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.START_CLIENT_TICK.register(client -> budget = CIRCLE_BUDGET);
        ClientTickEvents.END_CLIENT_TICK.register(ParametricVisualClient::tick);
    }
    public static void setObserver(Observer value) { observer = value; }
    public static int activeSceneCount() { return scenes.size(); }
    public static long deltaPackets() { return deltaPackets; }
    public static Metrics metrics() {
        return new Metrics(scenes.size(), circles.size(), statePackets, motionPackets, circlePackets,
                sampledBatches, emittedParticles, deferredParticles, droppedParticles, malformedPackets);
    }
    public static void resetMetrics() {
        statePackets = deltaPackets = motionPackets = circlePackets = sampledBatches = emittedParticles = deferredParticles = droppedParticles = malformedPackets = 0;
    }
    private static boolean acceptWorld(MinecraftClient client, Identifier dimension) {
        if (client.world == null || !client.world.getRegistryKey().getValue().equals(dimension)) return false;
        if (world != client.world) { clear(); world = client.world; }
        return true;
    }
    private static void clear() {
        scenes.clear(); circles.clear(); pendingMotions.clear(); queuedParticles = 0; world = null; pending = null; pendingDelta=null;
        expiredSamples.clear();
        hasSequence = false; ticks = receivedAt = 0; lastWorldTick = Long.MIN_VALUE;
        referenceServer = referenceLocal = 0; budget = CIRCLE_BUDGET;
    }
    private static void apply(StatePage page) {
        if (hasSequence && page.sequence != sequence && page.sequence - sequence <= 0) return;
        if (hasSequence && page.sequence == sequence) return;
        if (pending != null && page.sequence != pending.sequence && page.sequence - pending.sequence <= 0) return;
        if (pending == null || pending.sequence != page.sequence) pending = new Pending(page);
        if (pending.pages.length != page.pages || pending.time != page.time) { pending = null; malformedPackets++; return; }
        if (pending.pages[page.page] == null) { pending.pages[page.page] = page.views; pending.count++; }
        if (pending.count != pending.pages.length) return;
        Map<Key, Runtime> next = new LinkedHashMap<>();
        for (List<View> views : pending.pages) for (View view : views) {
            Runtime runtime = scenes.get(view.key());
            if (runtime == null) runtime = new Runtime(view, page.time); else runtime.update(view, page.time);
            applyPendingMotion(runtime);
            next.put(view.key(), runtime);
        }
        scenes.clear(); scenes.putAll(next);
        sequence = page.sequence; hasSequence = true; pending = null; pendingDelta=null;
        receivedAt = ticks; referenceServer = page.time; referenceLocal = world.getTime();
    }
    private static void apply(DeltaPage page) {
        // A delta is meaningful only against the exact complete baseline it was generated from.
        // Missing/incomplete baselines recover through the existing one-second full heartbeat.
        if(!hasSequence || page.base!=sequence || page.sequence-sequence<=0) return;
        if(pendingDelta!=null && page.sequence!=pendingDelta.sequence
                && page.sequence-pendingDelta.sequence<=0) return;
        if(pendingDelta==null || pendingDelta.sequence!=page.sequence) pendingDelta=new PendingDelta(page);
        if(pendingDelta.pages.length!=page.pages || pendingDelta.time!=page.time || pendingDelta.base!=page.base) {
            pendingDelta=null; malformedPackets++; return;
        }
        if(pendingDelta.pages[page.page]==null) {pendingDelta.pages[page.page]=page;pendingDelta.count++;}
        if(pendingDelta.count!=pendingDelta.pages.length) return;
        // No intermediate page changes a scene or its sampling phase.
        for(DeltaPage part:pendingDelta.pages) for(Key key:part.removed) {
            scenes.remove(key); pendingMotions.remove(key);
        }
        for(DeltaPage part:pendingDelta.pages) for(View view:part.views) {
            Runtime runtime=scenes.get(view.key());
            if(runtime==null) {
                if(scenes.size()>=ParametricVisuals.MAX_SCENES) {malformedPackets++;continue;}
                runtime=new Runtime(view,page.time); scenes.put(view.key(),runtime);
            } else runtime.update(view,page.time);
            applyPendingMotion(runtime);
        }
        sequence=page.sequence; pendingDelta=null;
        if(pending!=null && pending.sequence-sequence<=0) pending=null;
        receivedAt=ticks; referenceServer=page.time; referenceLocal=world.getTime();
    }
    private static void applyMotion(Runtime runtime, Motion motion, long time) {
        if(time<runtime.motionTime) return;
        runtime.position=motion.position; runtime.velocity=motion.velocity; runtime.motionTime=time;
    }
    private static void applyPendingMotion(Runtime runtime) {
        TimedMotion motion=pendingMotions.remove(runtime.view.key());
        if(motion!=null) applyMotion(runtime,motion.motion,motion.time);
    }
    private static void tick(MinecraftClient client) {
        if (client.world != world) { clear(); return; }
        if (world == null || client.player == null || client.isPaused()) return;
        long local = world.getTime();
        if (local == lastWorldTick) return;
        lastWorldTick = local; ticks++;
        if (pending != null && ticks - pending.createdAt > 40) pending = null;
        if(pendingDelta!=null && ticks-pendingDelta.createdAt>40) pendingDelta=null;
        pendingMotions.values().removeIf(motion -> ticks-motion.receivedAt>40);
        if (ticks - receivedAt > 60) {
            // Stop drawing an expired lease, but do not replay its already sampled ages after a
            // stalled server resumes with a clock behind the client's former projection.
            for (Runtime runtime : scenes.values()) {
                View view = runtime.view;
                expiredSamples.retain(view.key(), view.anchorUuid(), view.entityId(), view.epoch(), runtime.lastSample);
            }
            scenes.clear();hasSequence=false;pendingDelta=null;
        }
        long now = referenceServer + Math.max(0, local - referenceLocal);
        for (Runtime runtime : scenes.values()) {
            View view = runtime.view;
            long ageLong = now - view.epoch();
            if (ageLong < 0 || ageLong > Integer.MAX_VALUE) continue;
            int age = (int)ageLong;
            if (!SceneRecipes.active(view, age)) continue;
            int sample = SceneRecipes.latestSampleAge(view, age);
            if (sample < 0 || sample <= runtime.lastSample) continue;
            runtime.lastSample = sample;
            long sampleTick = view.epoch() + sample, seed = ParametricVisuals.seed(view, sampleTick);
            runtime.recipeRandom.setSeed(seed);
            Vec3d origin = origin(runtime, now);
            int[] batchIndex = { 0 };
            SceneRecipes.emit(view, sample, sampleTick, runtime.recipeRandom,
                    (particle,x,y,z,count,dx,dy,dz,speed,decoration,projectile) -> {
                        int index = batchIndex[0]++; sampledBatches++;
                        Observer listening = observer;
                        if (listening != null) listening.observe(view,sample,sampleTick,index,particle,x,y,z,count,dx,dy,dz,speed,decoration,projectile);
                        UUID owner = decoration ? view.decorationOwner() : null;
                        double px = origin.x+x, py = origin.y+y, pz = origin.z+z;
                        if (!visible(client,view.key().kind(),owner,px,py,pz)) return;
                        // Recipe and jitter never share a random stream. Local culling cannot move another point.
                        runtime.jitterRandom.setSeed(ParametricVisuals.mix(seed ^ ((long)index * 0xd1342543de82ef95L)));
                        Runnable emission = () -> cloud(particle,view.key().kind().force,px,py,pz,count,dx,dy,dz,speed,runtime.jitterRandom);
                        if (projectile) FirstPersonParticles.emitProjectile(owner,emission); else FirstPersonParticles.emit(owner,emission);
                    });
        }
        while (budget > 0 && !circles.isEmpty()) {
            CircleJob job = circles.peek(); long before = job.remaining();
            renderCircle(client,job); queuedParticles -= before - job.remaining();
            if (job.remaining() == 0) circles.remove(); else break;
        }
    }
    private static Vec3d origin(Runtime runtime, long now) {
        if (!runtime.view.follows()) return runtime.view.position();
        Entity source = world.getEntityById(runtime.view.entityId());
        if (source != null && source.getUuid().equals(runtime.view.anchorUuid())) return source.getPos();
        return runtime.position.add(runtime.velocity.multiply(Math.max(0, Math.min(5, now-runtime.motionTime))));
    }
    private static boolean visible(MinecraftClient client, Kind kind, UUID owner, double x,double y,double z) {
        if (kind == Kind.CORRUPT_MIST && client.player.getUuid().equals(owner)) return true;
        if (ParametricVisuals.exactRange(kind))
            return client.player.squaredDistanceTo(x,y,z) <= kind.range*kind.range;
        return client.player.getBlockPos().isWithinDistance(new Vec3d(x,y,z),kind.range);
    }
    private static void cloud(ParticleEffect particle, boolean force, double x,double y,double z,int count,
                              double dx,double dy,double dz,double speed,Random random) {
        dx = (float)dx; dy = (float)dy; dz = (float)dz; speed = (float)speed;
        if (count == 0) { world.addParticle(particle,force,x,y,z,dx*speed,dy*speed,dz*speed); emittedParticles++; }
        else for (int i=0;i<count;i++) {
            world.addParticle(particle,force,x+random.nextGaussian()*dx,y+random.nextGaussian()*dy,z+random.nextGaussian()*dz,
                    random.nextGaussian()*speed,random.nextGaussian()*speed,random.nextGaussian()*speed); emittedParticles++;
        }
    }
    private static void renderCircle(MinecraftClient client, CircleJob job) {
        Circle c = job.circle;
        while (budget > 0 && job.sample < c.samples()) {
            double angle = Math.PI*2*job.sample/c.samples();
            double x = c.center().x+Math.cos(angle)*c.radius(), y=c.center().y, z=c.center().z+Math.sin(angle)*c.radius();
            if (!client.player.getBlockPos().isWithinDistance(new Vec3d(x,y,z), c.force()?512:32)) {
                // Range rejection is also bounded work: a huge invisible ring cannot monopolize this tick.
                budget--; job.sample++; job.particle=0; job.initialized=false; continue;
            }
            if (!job.initialized) {
                job.random.setSeed(ParametricVisuals.mix(c.seed() ^ ((long)job.sample*0xd1342543de82ef95L)));
                job.initialized = true;
            }
            int amount = Math.min(budget,Math.max(1,c.count())-job.particle);
            Runnable emission = () -> cloud(c.particle(),c.force(),x,y,z,c.count()==0?0:amount,
                    c.spread().x,c.spread().y,c.spread().z,c.speed(),job.random);
            if (c.projectile()) FirstPersonParticles.emitProjectile(c.owner(),emission); else FirstPersonParticles.emit(c.owner(),emission);
            budget-=amount; job.particle+=amount;
            if (job.particle>=Math.max(1,c.count())) { job.sample++; job.particle=0; job.initialized=false; }
        }
    }
}
