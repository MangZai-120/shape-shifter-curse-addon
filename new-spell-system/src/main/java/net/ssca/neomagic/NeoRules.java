package net.ssca.neomagic;

import com.google.gson.*;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** A reload is atomic; active sessions finish with their locked quote and version. */
public final class NeoRules implements SimpleSynchronousResourceReloadListener {
    private static Map<String, Profile> forms = Map.of();
    private static Map<Core,Coefficients> costs = Map.of();
    private static Map<String,Tuning> spells=Map.of();
    private static String version = "v2-default";
    public static int recoveryDelay = 120;
    public static double recoveryRate = .25;
    private static final String ENGINE=net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("ssc_addon").map(mod->mod.getMetadata().getVersion().getFriendlyString()).orElse("test");
    public static String version() { return ENGINE+":"+version; }
    public static Profile profile(String id) { return forms.getOrDefault(id,new Profile(100,1,1,1,1,true)); }
    public static Coefficients cost(Core c) { return costs.getOrDefault(c,coefficients(c)); }
    public static Map<String,Tuning> tunings(){return spells;}
    public static Tuning tuning(Blueprint b){return spells.getOrDefault(b.identity()+"#"+b.band(),spells.getOrDefault(b.identity(),Tuning.defaults()));}
    public static Quote quote(Blueprint b,double mastery){return NeoModel.quote(b,mastery,cost(b.core()),tuning(b));}
    public static int cooldown(Blueprint b){return tuning(b).cooldown()>0?tuning(b).cooldown():NeoModel.cooldown(b);}
    public static double masteryNeeded(Blueprint b){return tuning(b).mastery()>0?tuning(b).mastery():NeoModel.masteryNeeded(b);}
    @Override public Identifier getFabricId() { return NeoMagic.id("rules"); }
    @Override public void reload(ResourceManager resources) {
        try (Reader reader = new InputStreamReader(resources.getResource(NeoMagic.id("rules/defaults.json")).orElseThrow().getInputStream(),StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String,Profile> next = new HashMap<>();
            root.getAsJsonObject("forms").entrySet().forEach(e -> {
                JsonObject p = e.getValue().getAsJsonObject();
                double s=p.get("safety").getAsDouble(), f=p.get("failure").getAsDouble(), j=p.get("offset").getAsDouble();
                double m=p.has("merge")?p.get("merge").getAsDouble():1, split=p.has("split")?p.get("split").getAsDouble():1;
                if(!Double.isFinite(s+f+j+m+split) || s<=0 || f<=0 || j<=0 || m<=0 || split<=0) throw new IllegalArgumentException("Invalid form: "+e.getKey());
                next.put(e.getKey(),new Profile(s,f,j,m,split,false));
            });
            Map<Core,Coefficients> nextCosts=new EnumMap<>(Core.class);
            for(Core c:Core.values()) {
                JsonObject p=root.getAsJsonObject("costs").getAsJsonObject(c.name());
                double a=p.get("a").getAsDouble(),b=p.get("b").getAsDouble();
                if(!Double.isFinite(a+b)||a<=0||b<=0)throw new IllegalArgumentException("Invalid cost: "+c);
                nextCosts.put(c,new Coefficients(a,b));
            }
            int delay=root.get("recovery_delay").getAsInt(); double rate=root.get("recovery_per_tick").getAsDouble();
            if(delay<0||!Double.isFinite(rate)||rate<=0)throw new IllegalArgumentException("Invalid recovery");
            Map<String,Tuning> nextSpells=new HashMap<>();
            if(root.has("spells"))root.getAsJsonObject("spells").entrySet().forEach(entry->{
                var o=entry.getValue().getAsJsonObject();
                var tune=new Tuning(o.has("cost")?o.get("cost").getAsDouble():1,o.has("floor")?o.get("floor").getAsDouble():0,
                        o.has("cooldown")?o.get("cooldown").getAsInt():0,o.has("mastery")?o.get("mastery").getAsDouble():0);
                if(!Double.isFinite(tune.cost()+tune.floor()+tune.mastery())||tune.cost()<=0||tune.cooldown()<0||tune.mastery()<0
                        ||(tune.floor()!=0&&(tune.floor()<.35||tune.floor()>.5)))throw new IllegalArgumentException("Invalid spell tuning: "+entry.getKey());
                nextSpells.put(entry.getKey(),tune);
            });
            String nextVersion=root.get("version").getAsString()+":"+Integer.toHexString(root.toString().hashCode());
            forms=Map.copyOf(next); costs=Map.copyOf(nextCosts);spells=Map.copyOf(nextSpells); recoveryDelay=delay; recoveryRate=rate;version=nextVersion;
            NeoMagic.LOG.info("Neomagic loaded {} independent form profiles, rules {}",forms.size(),version);
        } catch(Exception ex) { throw new IllegalStateException("Cannot load neomagic rules; retaining previous rules",ex); }
    }
}
