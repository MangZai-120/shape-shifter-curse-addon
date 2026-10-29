package net.ssca.neomagic;

import com.google.gson.Gson;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** Saved only in overworld/data/ssca_neomagic.dat; never reads old spell components. */
public final class NeoState extends PersistentState {
    public static final Gson JSON = new Gson();
    public final Map<UUID, PlayerData> players = new HashMap<>();
    public record Measurement(String target,String type,String position,double incoming,double healthBefore,double healthAfter,double absorptionBefore,double absorptionAfter) {}
    public static final class Experiment {
        public String id = UUID.randomUUID().toString(), version = NeoRules.version(), result = "preparing";
        public String spell = "";
        public String failureReason="";
        public int preparation, omittedChecks, moistureSpent;
        public boolean snowReadyUsed,novaQuickCast;
        public int frostThornsSpent,frostLifetimeTicksSpent;
        public double failureBasis, failureProbability;
        public int failureDraws, failureBoundary;
        public Blueprint blueprint;
        public Profile profile;
        public String form, dimension, target = "none";
        public double initial, normal, actual, paid, arrived, damage, absorbed, healing, yaw, pitch;
        public int hits, summons, pending;
        public boolean reversed;
        public List<Checkpoint> checks = new ArrayList<>();
        public List<Measurement> measurements = new ArrayList<>();
    }
    public static final class PlayerData {
        public double burden;
        /** 玩家精通经验（十分之一点）；独立于每个法术的减耗熟练度。 */
        public int spellbookMasteryExpTen;
        public int recovery;
        public String form = "", hypothesis = "", conclusion = "";
        public List<String> citations = new ArrayList<>();
        public Set<String> known = new HashSet<>();
        public Map<String, double[]> progress = new HashMap<>();
        public Map<String, Integer> cooldowns = new HashMap<>();
        public transient Map<String,Integer> cooldownStarted = new HashMap<>();
        public transient int lastPaymentTick = Integer.MIN_VALUE;
        public List<Experiment> records = new ArrayList<>();
        public Blueprint draft = Blueprint.starter(), baseline = Blueprint.starter();
        public PlayerData() {
            known.add("core:THERMAL"); known.add("carrier:PROJECTILE");
        }
        public double mastery(Blueprint b) {
            double[] values = progress.get(b.identity());
            return values == null ? 0 : clamp(values[b.band()]/NeoRules.masteryNeeded(b),0,1);
        }
        public void successful(Blueprint b) {
            double[] values = progress.computeIfAbsent(b.identity(), key -> new double[4]);
            // Success at a harder working range also trains the easier ranges of this exact spell.
            for (int band=0; band<=b.band(); band++) values[band] = Math.min(100000,values[band]+1);
        }
        public boolean knows(Blueprint b) {
            return known.contains("core:"+b.core()) && known.contains("carrier:"+b.carrier())
                    && b.controls().stream().allMatch(c -> known.contains("control:"+c))
                    && (!b.controls().contains(Control.CONVERT) || known.contains("core:"+b.conversion()));
        }
        public void add(Experiment e) {
            records.add(0,e);
            while(records.size()>32) {
                int remove=-1;for(int i=records.size()-1;i>=0;i--)if(!citations.contains(records.get(i).id)){remove=i;break;}
                if(remove<0)throw new IllegalStateException("Too many pinned research records");records.remove(remove);
            }
        }
    }
    public static NeoState get(MinecraftServer server) {
        var manager = server.getOverworld().getPersistentStateManager();
        NeoState found = manager.get(NeoState::read,"ssca_neomagic");
        if (found != null) return found;
        var path = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("data/ssca_neomagic.dat");
        if (java.nio.file.Files.exists(path)) throw new IllegalStateException("Unreadable neomagic data: "+path);
        found = new NeoState(); manager.set("ssca_neomagic", found); found.markDirty(); return found;
    }
    private static NeoState read(NbtCompound n) {
        if(n.getInt("Version") != VERSION) throw new IllegalStateException("Unknown neomagic save version");
        NeoState s = new NeoState();
        NbtCompound entries = n.getCompound("Players");
        for(String id : entries.getKeys()) {
            String json=entries.contains(id,NbtElement.BYTE_ARRAY_TYPE)?new String(entries.getByteArray(id),java.nio.charset.StandardCharsets.UTF_8):entries.getString(id);
            PlayerData data = JSON.fromJson(json,PlayerData.class);
            validate(data);
            s.players.put(UUID.fromString(id),data);
        }
        return s;
    }
    public static void validate(PlayerData d) {
        if(d==null||!Double.isFinite(d.burden)||d.burden<0||d.recovery<0||d.spellbookMasteryExpTen<0||d.draft==null||d.baseline==null
                ||!errors(d.draft).isEmpty()||!errors(d.baseline).isEmpty()||d.form==null||d.known==null
                ||d.hypothesis==null||d.conclusion==null||d.hypothesis.length()>1000||d.conclusion.length()>1000
                ||d.citations==null||d.citations.size()>4||d.records==null||d.records.size()>32||d.progress==null||d.cooldowns==null)
            throw new IllegalStateException("Invalid neomagic player state");
        if(d.known.stream().anyMatch(java.util.Objects::isNull))throw new IllegalStateException("Invalid research knowledge");
        for(var entry:d.progress.entrySet())if(entry.getKey()==null||entry.getKey().length()>256||entry.getValue()==null||entry.getValue().length!=4
                ||Arrays.stream(entry.getValue()).anyMatch(v->!Double.isFinite(v)||v<0||v>100000))throw new IllegalStateException("Invalid adaptation ledger");
        if(d.cooldowns.entrySet().stream().anyMatch(e->e.getKey()==null||e.getValue()==null||e.getValue()<0))throw new IllegalStateException("Invalid cooldown ledger");
        for(var record:d.records)if(record==null||record.blueprint==null||!errors(record.blueprint).isEmpty()||record.id==null||record.result==null
                ||record.id.length()<8||record.form==null||record.version==null||record.target==null||record.spell==null||record.spell.length()>256||record.failureReason==null||record.failureReason.length()>64||record.preparation<0||record.omittedChecks<0||record.moistureSpent<0||record.frostThornsSpent<0||record.frostLifetimeTicksSpent<0
                ||!Double.isFinite(record.failureBasis+record.failureProbability)||record.failureBasis<0||record.failureProbability<0||record.failureProbability>1||record.failureDraws<0||record.failureDraws>1||record.failureBoundary<0
                ||!Double.isFinite(record.initial+record.actual+record.normal+record.paid+record.arrived+record.damage+record.healing+record.absorbed+record.yaw+record.pitch)
                ||record.checks==null||record.checks.size()>512||record.checks.stream().anyMatch(c->c==null||c.risk()==null)
                ||record.measurements==null||record.measurements.size()>32)
            throw new IllegalStateException("Invalid research record");
    }
    public PlayerData player(UUID uuid) { return players.computeIfAbsent(uuid,k -> new PlayerData()); }
    @Override public NbtCompound writeNbt(NbtCompound n) {
        n.putInt("Version",VERSION); NbtCompound entries = new NbtCompound();
        players.forEach((id,data) -> entries.putByteArray(id.toString(),JSON.toJson(data).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        n.put("Players",entries); return n;
    }
}
