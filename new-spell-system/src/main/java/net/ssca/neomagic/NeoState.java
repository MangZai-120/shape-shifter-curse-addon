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
        public int recovery;
        public String form = "", hypothesis = "", conclusion = "";
        public List<String> citations = new ArrayList<>();
        public Set<String> known = new HashSet<>();
        public Map<String, double[]> progress = new HashMap<>();
        public Map<String, Integer> cooldowns = new HashMap<>();
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
            while(records.size()>32)for(int i=records.size()-1;i>=0;i--)if(!citations.contains(records.get(i).id)){records.remove(i);break;}
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
            if(data == null || !Double.isFinite(data.burden) || data.burden < 0 || data.recovery < 0
                    || !errors(data.draft).isEmpty() || !errors(data.baseline).isEmpty())
                throw new IllegalStateException("Invalid neomagic player save: "+id);
            s.players.put(UUID.fromString(id),data);
        }
        return s;
    }
    public PlayerData player(UUID uuid) { return players.computeIfAbsent(uuid,k -> new PlayerData()); }
    @Override public NbtCompound writeNbt(NbtCompound n) {
        n.putInt("Version",VERSION); NbtCompound entries = new NbtCompound();
        players.forEach((id,data) -> entries.putByteArray(id.toString(),JSON.toJson(data).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        n.put("Players",entries); return n;
    }
}
