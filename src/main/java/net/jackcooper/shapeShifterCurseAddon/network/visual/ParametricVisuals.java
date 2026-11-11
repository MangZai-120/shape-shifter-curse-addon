package net.jackcooper.shapeShifterCurseAddon.network.visual;

import java.util.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.event.PlayerSessionCleanup;
import net.jackcooper.shapeShifterCurseAddon.network.DecorationParticleScope;
import net.jackcooper.shapeShifterCurseAddon.network.DecorationParticles;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Server-authorized cosmetic scenes. No damage, resources or collision are delegated to clients. */
public final class ParametricVisuals {
    public static final Identifier STATE = new Identifier("ssc_addon", "visual_scenes_v1");
    public static final Identifier DELTA = new Identifier("ssc_addon", "visual_delta_v1");
    public static final Identifier MOTION = new Identifier("ssc_addon", "visual_motion_v1");
    public static final Identifier CIRCLE = new Identifier("ssc_addon", "visual_circle_v1");
    public static final Identifier ABILITY_BURST = new Identifier("ssc_addon", "ability_visuals_v1");
    /** Receiver registration is the capability; no periodic capability packet is sent. */
    public static final Identifier MIST_FORM_CAPABILITY = new Identifier("ssc_addon", "mist_form_visuals_v1");
    public static final int PAGE_SIZE = 128, MAX_PAGES = 128, MAX_PARAMETERS = 16;
    public static final int MAX_SCENES = PAGE_SIZE * MAX_PAGES;
    public enum Kind {
        CORRUPT_MIST(32, false), METEOR_WARNING(32, false), DOMAIN_EDGE(512, true),
        DOMAIN_AMBIENT(512, true), BLUE_FIRE_RING(512, true), SSC_WEB_TRAIL(32, false),
        FIRE_BOLT_TRAIL(512, true), LUNAR_BOLT_TRAIL(32, false),
        MIST_FORM_AMBIENT(512, true), MIST_FORM_CHARGE(512, true),
        FOX_EXPLOSION(32, false), FOX_RING(32, false), PARASITIC_SEED_TRAIL(32, false), SPORE_CLOUD(32, false);
        public final double range;
        public final boolean force;
        Kind(double range, boolean force) { this.range = range; this.force = force; }
    }
    public record Key(UUID id, Kind kind) {
        public void write(PacketByteBuf b) { b.writeUuid(id); b.writeEnumConstant(kind); }
        public static Key read(PacketByteBuf b) { return new Key(b.readUuid(), b.readEnumConstant(Kind.class)); }
    }
    public record View(Key key, UUID anchorUuid, int entityId, UUID decorationOwner, Vec3d position,
                       Vec3d velocity, long epoch, int duration, boolean follows, float width,
                       float eyeHeight, double extent, double[] parameters) {
        public View { parameters = parameters.clone(); }
        @Override public double[] parameters() { return parameters.clone(); }
        public double parameter(int index) { return parameters[index]; }
        public double parameter(int index, double fallback) { return index < parameters.length ? parameters[index] : fallback; }
        public void write(PacketByteBuf b) {
            key.write(b); b.writeUuid(anchorUuid); b.writeVarInt(entityId);
            b.writeBoolean(decorationOwner != null); if (decorationOwner != null) b.writeUuid(decorationOwner);
            vector(b, position); motion(b, velocity); b.writeLong(epoch); b.writeVarInt(duration);
            b.writeBoolean(follows); b.writeFloat(width); b.writeFloat(eyeHeight); b.writeDouble(extent);
            b.writeVarInt(parameters.length); for (double v : parameters) b.writeDouble(v);
        }
        public static View read(PacketByteBuf b) {
            Key key = Key.read(b); UUID anchor = b.readUuid(); int id = b.readVarInt();
            UUID owner = b.readBoolean() ? b.readUuid() : null;
            Vec3d pos = vector(b), velocity = motion(b); long epoch = b.readLong();
            int duration = bounded(b.readVarInt(), 0, 1_000_000);
            if (key.kind == Kind.FOX_RING && duration == 0 || key.kind == Kind.FOX_EXPLOSION && duration != 1)
                throw new IllegalArgumentException("Invalid ability visual duration");
            boolean follows = b.readBoolean(); float width = b.readFloat(), eye = b.readFloat();
            double extent = finite(b.readDouble());
            if (!Float.isFinite(width) || !Float.isFinite(eye) || width < 0 || eye < 0 || extent < 0 || extent > 65536)
                throw new IllegalArgumentException("Invalid visual dimensions");
            int n = bounded(b.readVarInt(), 0, MAX_PARAMETERS); double[] p = new double[n];
            for (int i = 0; i < n; i++) p[i] = finite(b.readDouble());
            validateParameters(key.kind, p);
            return new View(key, anchor, id, owner, pos, velocity, epoch, duration, follows, width, eye, extent, p);
        }
    }
    public record Circle(long seed, UUID owner, ParticleEffect particle, boolean force, boolean projectile,
                         Vec3d center, int count, double speed, Vec3d spread, double radius, int samples) {
        public void write(PacketByteBuf b) {
            b.writeLong(seed); b.writeBoolean(owner != null); if (owner != null) b.writeUuid(owner);
            b.writeVarInt(Registries.PARTICLE_TYPE.getRawId(particle.getType())); particle.write(b);
            b.writeBoolean(force); b.writeBoolean(projectile); vector(b, center);
            b.writeVarInt(count); b.writeFloat((float) speed); motion(b, spread); b.writeDouble(radius); b.writeVarInt(samples);
        }
        public static Circle read(PacketByteBuf b) {
            long seed = b.readLong(); UUID owner = b.readBoolean() ? b.readUuid() : null;
            int raw = bounded(b.readVarInt(), 0, Registries.PARTICLE_TYPE.size() - 1);
            ParticleEffect particle = readParticle(Registries.PARTICLE_TYPE.get(raw), b);
            boolean force = b.readBoolean(), projectile = b.readBoolean(); Vec3d center = vector(b);
            int count = bounded(b.readVarInt(), 0, 65536); double speed = finite(b.readFloat());
            Vec3d spread = motion(b); double radius = finite(b.readDouble());
            if (Math.abs(radius) > 65536) throw new IllegalArgumentException("Invalid visual circle radius");
            int samples = bounded(b.readVarInt(), 1, 65536);
            return new Circle(seed, owner, particle, force, projectile, center, count, speed, spread, radius, samples);
        }
    }
    private static final class Entry {
        Entity anchor; ServerWorld world; View view; int touched;
        final Random random = new Random();
    }
    private record Motion(Vec3d position, Vec3d velocity) {}
    private record Audience(ServerPlayerEntity player, ServerWorld world, Object connection,
                            int lastFullTick, int sequence, List<View> views, Map<Key, Motion> motion) {}
    private static final Map<Key, Entry> ACTIVE = new LinkedHashMap<>();
    private static final Map<UUID, Audience> AUDIENCES = new HashMap<>();
    private static boolean initialized;
    private static int sequence;
    private static long circleSequence, statePackets, deltaPackets, motionPackets, circlePackets, legacyPackets, burstPackets;
    public record Metrics(int scenes, int audiences, long statePackets, long deltaPackets,
                          long motionPackets, long circlePackets, long legacyPackets, long burstPackets) {}
    private ParametricVisuals() {}

    public static void init() {
        if (initialized) return; initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(ParametricVisuals::flush);
        PlayerSessionCleanup.register((handler, server) -> {
            Audience audience = AUDIENCES.get(handler.player.getUuid());
            if (audience != null && audience.connection == handler) AUDIENCES.remove(handler.player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { ACTIVE.clear(); AUDIENCES.clear(); sequence = 0; });
    }
    public static Metrics metrics() { return new Metrics(ACTIVE.size(), AUDIENCES.size(), statePackets, deltaPackets, motionPackets, circlePackets, legacyPackets, burstPackets); }
    public static void resetMetrics() { statePackets = deltaPackets = motionPackets = circlePackets = legacyPackets = burstPackets = 0; }
    public static void touch(Entity anchor, UUID eventId, Kind kind, UUID decorationOwner, Vec3d origin,
                             boolean follows, long epoch, int duration, double extent, double... parameters) {
        if (!(anchor.getWorld() instanceof ServerWorld world)) return;
        if (parameters.length > MAX_PARAMETERS || duration < 0) throw new IllegalArgumentException("Invalid visual scene");
        Key key = new Key(eventId, kind); Entry e = ACTIVE.computeIfAbsent(key, ignored -> new Entry());
        int tick = world.getServer().getTicks();
        Vec3d velocity = Vec3d.ZERO;
        if (follows) {
            if (e.view != null && e.world == world && tick > e.touched && tick - e.touched <= 5) {
                Vec3d delta = origin.subtract(e.view.position);
                if (delta.lengthSquared() <= 64) velocity = delta.multiply(1.0 / (tick - e.touched));
            } else velocity = anchor.getVelocity();
        }
        e.anchor = anchor; e.world = world; e.touched = tick;
        e.view = new View(key, anchor.getUuid(), anchor.getId(), decorationOwner, origin, velocity, epoch,
                duration, follows, anchor.getWidth(), anchor.getEyeHeight(anchor.getPose()), extent, parameters);
    }
    public static void stop(UUID eventId, Kind kind) { ACTIVE.remove(new Key(eventId, kind)); }

    /** Fixed effects can outlive their source entity, and still expire when no longer touched. */
    public static void touchWorld(ServerWorld world, UUID eventId, Kind kind, UUID owner, Vec3d origin,
                                  long epoch, int duration, double extent, double... parameters) {
        if (DecorationParticleScope.isProtected()) owner = null;
        Key key=new Key(eventId,kind);
        Entry e=ACTIVE.computeIfAbsent(key,ignored->new Entry());
        e.anchor=null;e.world=world;e.touched=world.getServer().getTicks();
        e.view=new View(key,eventId,-1,owner,origin,Vec3d.ZERO,epoch,duration,false,0,0,extent,parameters);
    }

    /** One-shot cosmetic event; old clients retain the original particle fallback. */
    public static void burst(ServerWorld world, UUID owner, Kind kind, Vec3d origin, double radius) {
        if(kind!=Kind.FOX_EXPLOSION)throw new IllegalArgumentException("Not a burst kind");
        if (DecorationParticleScope.isProtected()) owner = null;
        UUID id=UUID.randomUUID();
        View v=new View(new Key(id,kind),id,-1,owner,origin,Vec3d.ZERO,world.getTime(),1,false,0,0,radius,new double[]{radius});
        Entry e=new Entry();e.world=world;e.view=v;
        for(ServerPlayerEntity viewer:world.getPlayers()) {
            double range=kind.range+radius+4;
            if(viewer.squaredDistanceTo(origin)>range*range)continue;
            if(ServerPlayNetworking.canSend(viewer,ABILITY_BURST)) {
                var b=PacketByteBufs.create();b.writeIdentifier(world.getRegistryKey().getValue());v.write(b);
                ServerPlayNetworking.send(viewer,ABILITY_BURST,b);
                burstPackets++;
            } else legacy(e,viewer);
        }
    }

    private static void flush(MinecraftServer server) {
        int tick = server.getTicks();
        ACTIVE.values().removeIf(e -> e.touched < tick || e.anchor != null && (e.anchor.isRemoved() || e.anchor.getWorld() != e.world));
        if (ACTIVE.isEmpty() && AUDIENCES.isEmpty()) return;
        Map<UUID, Collection<ServerPlayerEntity>> tracking = new HashMap<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            boolean modern = ServerPlayNetworking.canSend(player, STATE) && ServerPlayNetworking.canSend(player, CIRCLE);
            boolean mistForm = modern && ServerPlayNetworking.canSend(player, MIST_FORM_CAPABILITY);
            boolean abilityVisuals = modern && ServerPlayNetworking.canSend(player, ABILITY_BURST);
            List<View> visible = new ArrayList<>();
            for (Entry e : ACTIVE.values()) {
                View v = e.view; double range = v.key.kind.range + v.extent + 4;
                if (e.world != player.getWorld() || (!player.getUuid().equals(v.decorationOwner)
                        && player.squaredDistanceTo(v.position) > range * range)) continue;
                // The protocol is bounded. Extreme cosmetic overload must not abort a server tick.
                if (modern && supportsKind(v.key.kind, mistForm, abilityVisuals)) { if (visible.size() < MAX_SCENES) visible.add(v); }
                else legacy(e, player);
            }
            if (!modern) { AUDIENCES.remove(player.getUuid()); continue; }
            Audience previous = AUDIENCES.get(player.getUuid());
            boolean sameSession = previous != null && previous.player == player && previous.world == player.getWorld()
                    && previous.connection == player.networkHandler;
            if (visible.isEmpty() && (previous == null || previous.views.isEmpty())) continue;
            boolean full = !sameSession || tick - previous.lastFullTick < 0 || tick - previous.lastFullTick >= 20;
            boolean changed = !sameSession || !sameState(previous.views, visible);
            if (full || changed && !ServerPlayNetworking.canSend(player, DELTA)) {
                int sentSequence = sendSnapshot(player, visible);
                if (visible.isEmpty()) AUDIENCES.remove(player.getUuid());
                else {
                    Map<Key, Motion> motion = new HashMap<>();
                    for (View v : visible) motion.put(v.key, new Motion(v.position, v.velocity));
                    AUDIENCES.put(player.getUuid(), new Audience(player, player.getServerWorld(), player.networkHandler,
                            tick, sentSequence, List.copyOf(visible), motion));
                }
                continue;
            }
            if (changed) {
                Delta delta = diff(previous.views, visible);
                int sentSequence = delta.isEmpty() ? previous.sequence : sendDelta(player, delta, previous.sequence);
                // Retain existing motion history. Churn in one projectile must not allocate a new
                // history for every persistent scene or postpone the periodic full repair.
                for (Key removed : delta.removed) previous.motion.remove(removed);
                for (View v : delta.changed) previous.motion.put(v.key, new Motion(v.position, v.velocity));
                if (visible.isEmpty()) { AUDIENCES.remove(player.getUuid()); continue; }
                previous = new Audience(player, player.getServerWorld(), player.networkHandler,
                        previous.lastFullTick, sentSequence, List.copyOf(visible), previous.motion);
                AUDIENCES.put(player.getUuid(), previous);
            }
            // Untracked persistent origins still need movement while unrelated projectiles churn.
            if (tick % 5 == 0 && ServerPlayNetworking.canSend(player, MOTION)) {
                List<View> moving = new ArrayList<>();
                for (View v : visible) {
                    Entry e = ACTIVE.get(v.key);
                    if (v.follows && e.anchor != player && !tracking.computeIfAbsent(v.anchorUuid,
                            ignored -> PlayerLookup.tracking(e.anchor)).contains(player)) {
                        Motion sent = previous.motion.get(v.key);
                        if (sent == null || !sent.position.equals(v.position) || !sent.velocity.equals(v.velocity)) {
                            moving.add(v);
                            previous.motion.put(v.key, new Motion(v.position, v.velocity));
                        }
                    }
                }
                if (!moving.isEmpty()) sendMotion(player, moving);
            }
        }
    }
    private static int sendSnapshot(ServerPlayerEntity player, List<View> views) {
        int size = Math.min(views.size(), MAX_SCENES);
        int pages = Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE), seq = ++sequence;
        for (int page = 0; page < pages; page++) {
            var b = PacketByteBufs.create(); b.writeIdentifier(player.getWorld().getRegistryKey().getValue());
            b.writeLong(player.getWorld().getTime()); b.writeInt(seq); b.writeVarInt(page); b.writeVarInt(pages);
            int from = page * PAGE_SIZE, to = Math.min(from + PAGE_SIZE, size);
            b.writeVarInt(to - from); for (int i = from; i < to; i++) views.get(i).write(b);
            ServerPlayNetworking.send(player, STATE, b); statePackets++;
        }
        return seq;
    }
    private static int sendDelta(ServerPlayerEntity player, Delta delta, int base) {
        int pages = Math.max(1, Math.max((delta.changed.size() + PAGE_SIZE - 1) / PAGE_SIZE,
                (delta.removed.size() + PAGE_SIZE - 1) / PAGE_SIZE)), seq = ++sequence;
        for (int page = 0; page < pages; page++) {
            var b = PacketByteBufs.create(); b.writeIdentifier(player.getWorld().getRegistryKey().getValue());
            b.writeLong(player.getWorld().getTime()); b.writeInt(seq); b.writeInt(base);
            b.writeVarInt(page); b.writeVarInt(pages);
            int from = Math.min(page * PAGE_SIZE, delta.changed.size());
            int to = Math.min(from + PAGE_SIZE, delta.changed.size());
            b.writeVarInt(to - from); for (int i = from; i < to; i++) delta.changed.get(i).write(b);
            from = Math.min(page * PAGE_SIZE, delta.removed.size());
            to = Math.min(from + PAGE_SIZE, delta.removed.size());
            b.writeVarInt(to - from); for (int i = from; i < to; i++) delta.removed.get(i).write(b);
            ServerPlayNetworking.send(player, DELTA, b); deltaPackets++;
        }
        return seq;
    }
    private static void sendMotion(ServerPlayerEntity player, List<View> views) {
        for (int from = 0; from < views.size(); from += PAGE_SIZE) {
            var b = PacketByteBufs.create(); b.writeIdentifier(player.getWorld().getRegistryKey().getValue());
            b.writeLong(player.getWorld().getTime()); int to = Math.min(from + PAGE_SIZE, views.size());
            b.writeVarInt(to - from);
            for (int i = from; i < to; i++) { View v = views.get(i); v.key.write(b); vector(b, v.position); motion(b, v.velocity); }
            ServerPlayNetworking.send(player, MOTION, b); motionPackets++;
        }
    }
    public static boolean sameState(List<View> a, List<View> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!sameView(a.get(i), b.get(i))) return false;
        }
        return true;
    }
    public static boolean sameView(View x, View y) {
        return x.key.equals(y.key) && x.anchorUuid.equals(y.anchorUuid) && x.entityId == y.entityId
                && Objects.equals(x.decorationOwner, y.decorationOwner) && x.epoch == y.epoch
                && x.duration == y.duration && x.follows == y.follows && x.width == y.width
                && x.eyeHeight == y.eyeHeight && x.extent == y.extent && Arrays.equals(x.parameters, y.parameters)
                && (x.follows || x.position.equals(y.position));
    }
    public record Delta(List<View> changed, List<Key> removed) {
        public Delta { changed = List.copyOf(changed); removed = List.copyOf(removed); }
        public boolean isEmpty() { return changed.isEmpty() && removed.isEmpty(); }
    }
    /** Identity-based metadata diff; motion uses its own bounded, low-frequency path. */
    public static Delta diff(List<View> before, List<View> after) {
        Map<Key, View> remaining = new LinkedHashMap<>();
        for (View v : before) remaining.put(v.key, v);
        List<View> changed = new ArrayList<>();
        for (View v : after) {
            View old = remaining.remove(v.key);
            if (old == null || !sameView(old, v)) changed.add(v);
        }
        return new Delta(changed, new ArrayList<>(remaining.keySet()));
    }
    private static void legacy(Entry e, ServerPlayerEntity viewer) {
        View v = e.view; long now = e.world.getTime(); int age = (int) Math.max(0, now - v.epoch);
        if (!SceneRecipes.active(v, age)) return;
        e.random.setSeed(seed(v, now));
        SceneRecipes.emit(v, age, now, e.random, (effect,x,y,z,count,dx,dy,dz,speed,decoration,projectile) -> {
            Vec3d p = v.position.add(x,y,z);
            if (!visible(viewer, p, v.key.kind, decoration ? v.decorationOwner : null)) return;
            var packet = new ParticleS2CPacket(effect, v.key.kind.force, p.x,p.y,p.z,
                    (float)dx,(float)dy,(float)dz,(float)speed,count);
            DecorationParticles.sendVisualFallback(e.world, viewer, packet, decoration ? v.decorationOwner : null, projectile);
            legacyPackets++;
        });
    }
    public static boolean visible(ServerPlayerEntity player, Vec3d pos, Kind kind, UUID owner) {
        if (kind == Kind.CORRUPT_MIST && player.getUuid().equals(owner)) return true;
        return exactRange(kind)
                ? player.squaredDistanceTo(pos) <= kind.range * kind.range
                : player.getBlockPos().isWithinDistance(pos, kind.range);
    }
    public static boolean supportsKind(Kind kind, boolean mistFormCapability) {
        return supportsKind(kind,mistFormCapability,false);
    }
    public static boolean supportsKind(Kind kind, boolean mistFormCapability, boolean abilityCapability) {
        return switch(kind) {
            case FOX_EXPLOSION, FOX_RING, PARASITIC_SEED_TRAIL, SPORE_CLOUD -> abilityCapability;
            case MIST_FORM_AMBIENT, MIST_FORM_CHARGE -> mistFormCapability;
            default -> true;
        };
    }
    public static boolean exactRange(Kind kind) {
        return kind == Kind.FIRE_BOLT_TRAIL || kind == Kind.DOMAIN_EDGE || kind == Kind.DOMAIN_AMBIENT
                || kind == Kind.MIST_FORM_AMBIENT || kind == Kind.MIST_FORM_CHARGE;
    }
    public static long seed(View v, long sampleTick) {
        return mix(v.key.id.getMostSignificantBits() ^ Long.rotateLeft(v.key.id.getLeastSignificantBits(), 17)
                ^ v.epoch ^ (sampleTick * 0x9e3779b97f4a7c15L) ^ v.key.kind.ordinal());
    }
    public static long mix(long value) {
        value = (value ^ value >>> 30) * 0xbf58476d1ce4e5b9L;
        value = (value ^ value >>> 27) * 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }
    public static void circle(Entity actor, ParticleEffect particle, boolean force, int count, double speed,
                              Vec3d spread, Vec3d offset, double radius, int samples,
                              Predicate<ServerPlayerEntity> audience, UUID decorationOwner) {
        if (!(actor.getWorld() instanceof ServerWorld world)) return;
        boolean projectile = DecorationParticleScope.isProjectileScope();
        if (DecorationParticleScope.isProtected()) decorationOwner = null;
        Circle circle = new Circle(mix(actor.getUuid().getLeastSignificantBits() ^ world.getTime() ^ ++circleSequence),
                decorationOwner, particle, force, projectile, actor.getPos().add(offset), count, speed, spread, radius, samples);
        double range = (force ? 512 : 32) + Math.abs(radius) + 4;
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            if (viewer.squaredDistanceTo(circle.center) > range * range || audience != null && !audience.test(viewer)) continue;
            if (ServerPlayNetworking.canSend(viewer, CIRCLE)) {
                var b = PacketByteBufs.create(); b.writeIdentifier(world.getRegistryKey().getValue()); circle.write(b);
                ServerPlayNetworking.send(viewer, CIRCLE, b); circlePackets++;
            } else {
                for (int i = 0; i < samples; i++) {
                    double angle = Math.PI * 2 * i / samples;
                    Vec3d p = circle.center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                    if (!viewer.getBlockPos().isWithinDistance(p, force ? 512 : 32)) continue;
                    var packet = new ParticleS2CPacket(particle, force, p.x,p.y,p.z,(float)spread.x,(float)spread.y,
                            (float)spread.z,(float)speed,count);
                    DecorationParticles.sendVisualFallback(world, viewer, packet, decorationOwner, projectile); legacyPackets++;
                }
            }
        }
    }
    private static <T extends ParticleEffect> T readParticle(ParticleType<T> type, PacketByteBuf b) {
        return type.getParametersFactory().read(type, b);
    }
    public static void vector(PacketByteBuf b, Vec3d p) { b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z); }
    public static Vec3d vector(PacketByteBuf b) { return new Vec3d(finite(b.readDouble()),finite(b.readDouble()),finite(b.readDouble())); }
    public static void motion(PacketByteBuf b, Vec3d p) { b.writeFloat((float)p.x); b.writeFloat((float)p.y); b.writeFloat((float)p.z); }
    public static Vec3d motion(PacketByteBuf b) { return new Vec3d(finite(b.readFloat()),finite(b.readFloat()),finite(b.readFloat())); }
    public static int bounded(int n, int min, int max) { if(n < min || n > max)throw new IllegalArgumentException("Invalid scene count");return n; }
    public static double finite(double n) { if(!Double.isFinite(n))throw new IllegalArgumentException("Invalid visual number");return n; }
    private static void validateParameters(Kind kind, double[] p) {
        int minimum = switch (kind) {
            case CORRUPT_MIST, METEOR_WARNING, BLUE_FIRE_RING -> 2;
            case DOMAIN_EDGE -> 3;
            case DOMAIN_AMBIENT -> 4;
            case MIST_FORM_AMBIENT -> 2;
            case MIST_FORM_CHARGE -> 4;
            case SSC_WEB_TRAIL -> 1;
            case FOX_EXPLOSION, FOX_RING, SPORE_CLOUD -> 1;
            default -> 0;
        };
        if (p.length < minimum) throw new IllegalArgumentException("Incomplete visual recipe: " + kind);
        if ((kind == Kind.FOX_EXPLOSION || kind == Kind.FOX_RING || kind == Kind.SPORE_CLOUD)
                && (p[0] < 0 || p[0] > 65536)) throw new IllegalArgumentException("Invalid ability visual radius");
        // Decoding a finite radius must not authorize an unbounded loop on the client tick.
        if (kind == Kind.CORRUPT_MIST && Math.max(10, p[0] * p[0] * 4) > 65536
                || kind == Kind.METEOR_WARNING && Math.max(12, p[0] * 10) > 65536)
            throw new IllegalArgumentException("Visual recipe exceeds sample capacity: " + kind);
        if (kind == Kind.MIST_FORM_AMBIENT || kind == Kind.MIST_FORM_CHARGE) {
            int heightIndex = kind == Kind.MIST_FORM_AMBIENT ? 0 : 1;
            double height = p[heightIndex], flags = p[heightIndex + 1];
            if (height < 0 || height > 65532 || flags != Math.rint(flags) || flags < 0 || flags > 3)
                throw new IllegalArgumentException("Invalid mist form dimensions or scope");
            if (kind == Kind.MIST_FORM_CHARGE && (p[0] < 1 || p[0] > 1_000_000
                    || p[0] != Math.rint(p[0]) || p[3] < 0 || p[3] > p[0] || p[3] != Math.rint(p[3])))
                throw new IllegalArgumentException("Invalid mist form charge timeline");
        }
    }
}
