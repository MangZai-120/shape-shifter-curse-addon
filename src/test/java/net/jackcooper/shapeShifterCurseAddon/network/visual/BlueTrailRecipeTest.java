package net.jackcooper.shapeShifterCurseAddon.network.visual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.jackcooper.shapeShifterCurseAddon.network.visual.ParametricVisuals.Kind;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.registry.Registries;

/** Compare the replacement recipes against fixed, pre-optimization power JSON and trail values. */
public final class BlueTrailRecipeTest {
    private static final String ROOT = "network-visual/original-blue-ring/";
    private static final UUID OWNER = new UUID(0x687564L, 0x72696e67L);
    private static int checks;
    private record Batch(String particle, double x, double y, double z, int count,
                         double dx, double dy, double dz, double speed,
                         boolean force, UUID owner, boolean projectile) {}
    private record Layer(JsonObject original, JsonObject replacement, int id) {}

    public static void main(String[] args) throws Exception {
        SharedConstants.createGameVersion(); Bootstrap.initialize();
        fixtureProvenance();
        List<Layer> layers = originalLayersAndUnchangedGameplay();
        blueRings(layers);
        trails();
        Path report = Path.of("build/reports/network-sync/blue-trails.txt");
        Files.createDirectories(report.getParent());
        Files.writeString(report, "Blue ring and projectile recipe checks: " + checks + "\n"
                + "Baseline: fixed original blue-ring JSON from cd8aa83a6bbf08e003b6c6befa28179d3ed85d50.\n"
                + "Each SOUL layer: 65 batches / 74 particles every four ticks.\n"
                + "Amulet-toggle SOUL + FLAME layer: 130 batches / 148 particles.\n"
                + "All three overlapping powers retain 260 batches / 296 particles per wave.\n"
                + "Three SSC WebBullet Tiers plus fire/lunar bolt batch values remain unchanged.\n"
                + "Fixed-source geometry/data checks only; native lifecycle and frame-time acceptance are separate.\n");
        System.out.println("BlueTrailRecipeTest: " + checks + " checks");
    }
    private static InputStream open(String resource) {
        InputStream stream = BlueTrailRecipeTest.class.getClassLoader().getResourceAsStream(resource);
        if (stream == null) throw new AssertionError("Missing fixture: " + resource);
        return stream;
    }
    private static JsonObject json(String resource) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(open(resource), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private static void fixtureProvenance() throws Exception {
        JsonObject manifest = json(ROOT + "provenance.json");
        check(manifest.get("source_commit").getAsString().equals("cd8aa83a6bbf08e003b6c6befa28179d3ed85d50"),
                "Fixtures are the accepted original source, not replacement-derived values");
        for (var entry : manifest.getAsJsonObject("files").entrySet()) {
            byte[] bytes;
            try (InputStream stream = open(ROOT + entry.getKey())) { bytes = stream.readAllBytes(); }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            check(hash.equals(entry.getValue().getAsJsonObject().get("sha256").getAsString()),
                    "Original JSON fixture checksum: " + entry.getKey());
        }
    }
    private static List<Layer> originalLayersAndUnchangedGameplay() throws Exception {
        JsonObject originalBase = json(ROOT + "base.json"), originalAmulet = json(ROOT + "amulet.json");
        JsonObject newBase = json("data/my_addon/powers/form_familiar_fox_sp_blue_fire_ring.json");
        JsonObject newAmulet = json("data/my_addon/powers/form_familiar_fox_sp_blue_fire_ring_amulet.json");
        List<Layer> layers = List.of(
                new Layer(originalBase.getAsJsonObject("particle_loop"), newBase.getAsJsonObject("particle_loop"), 0),
                new Layer(originalBase.getAsJsonObject("particle_loop_amulet"), newBase.getAsJsonObject("particle_loop_amulet"), 1),
                new Layer(originalAmulet.getAsJsonObject("particle_loop"), newAmulet.getAsJsonObject("particle_loop"), 2));
        JsonObject oldGameplay = originalBase.deepCopy(), newGameplay = newBase.deepCopy();
        oldGameplay.remove("particle_loop"); oldGameplay.remove("particle_loop_amulet");
        newGameplay.remove("particle_loop"); newGameplay.remove("particle_loop_amulet");
        check(oldGameplay.equals(newGameplay), "Base blue-ring damage, resources, actions and other conditions are unchanged");
        oldGameplay = originalAmulet.deepCopy(); newGameplay = newAmulet.deepCopy();
        oldGameplay.remove("particle_loop"); newGameplay.remove("particle_loop");
        check(oldGameplay.equals(newGameplay), "Amulet blue-ring gameplay and every nonvisual field are unchanged");
        Set<Integer> ids = new HashSet<>();
        for (Layer layer : layers) {
            check(layer.original.get("type").getAsString().equals("apoli:action_over_time")
                    && layer.original.get("interval").getAsInt() == 4, "Original four-tick power cadence");
            check(layer.replacement.get("type").getAsString().equals("ssc_addon:blue_fire_ring_visual"), "Replacement uses visual-only power");
            check(layer.original.get("condition").equals(layer.replacement.get("condition")), "Each original equipment/toggle condition is preserved");
            check(layer.replacement.get("layer").getAsInt() == layer.id && ids.add(layer.id), "Overlapping power leases remain distinct");
        }
        return layers;
    }
    private static void blueRings(List<Layer> layers) {
        float[][] sizes = {{.6f, 1.62f}, {1.25f, .9f}, {.35f, 2.1f}};
        int overlapBatches = 0, overlapParticles = 0;
        for (Layer layer : layers) {
            for (float[] size : sizes) {
                List<Batch> expected = new ArrayList<>();
                originalAction(layer.original.getAsJsonObject("entity_action"), false, size[0], size[1], expected);
                int expectedLayers = layer.id == 2 ? 2 : 1;
                check(expected.size() == 65 * expectedLayers, "Original source has center plus 64 points per element");
                check(expected.stream().mapToInt(Batch::count).sum() == 74 * expectedLayers, "Original source has ten center and 64 peripheral particles");
                List<Batch> actual = recipe(Kind.BLUE_FIRE_RING,4,size[0],size[1],
                        layer.replacement.get("radius").getAsDouble(),layer.replacement.get("flame_layer").getAsBoolean()?1:0);
                compare(actual,expected,"Blue ring layer " + layer.id);
                double radius = layer.replacement.get("radius").getAsDouble();
                for (int band = 0; band < expectedLayers; band++) {
                    Batch center = actual.get(band*65);
                    check(center.count == 10 && center.x == 0 && center.y == .5 && center.z == 0,
                            "Center cloud retains position and density");
                    check(center.dx == 3 && center.dy == .1 && center.dz == 3,
                            "Center cloud spread remains absolute, independent of actor size");
                    for (int point = 0; point < 64; point++) {
                        Batch a=actual.get(band*65+1+point), b=actual.get(band*65+1+(point+1)%64);
                        near(Math.hypot(a.x,a.z),radius,"Ring radius");
                        near(Math.hypot(a.x-b.x,a.z-b.z),2*radius*Math.sin(Math.PI/64),"Even original angular spacing");
                        check(a.y == .5 && a.count == 1 && a.force && OWNER.equals(a.owner) && !a.projectile,
                                "Ring remains forced self-decoration without projectile exemption");
                    }
                }
                for (int age : new int[]{-1,1,2,3,5,6,7,9,999})
                    check(recipe(Kind.BLUE_FIRE_RING,age,size[0],size[1],radius,expectedLayers-1).isEmpty(),
                            "No particle emission between original four-tick phases");
                int lifetimeParticles=0;
                // ActionOverTime records phase on its first tick; these are its five subsequent executions.
                for(int age=1;age<=20;age++) lifetimeParticles+=recipe(Kind.BLUE_FIRE_RING,age,size[0],size[1],radius,expectedLayers-1)
                        .stream().mapToInt(Batch::count).sum();
                check(lifetimeParticles==5*74*expectedLayers,"Five original blue-ring waves retain all particles");
                if(size==sizes[0]) {overlapBatches+=actual.size();overlapParticles+=actual.stream().mapToInt(Batch::count).sum();}
            }
        }
        check(overlapBatches==260 && overlapParticles==296,"All three original overlapping layers remain additive");
    }
    private static void originalAction(JsonObject action, boolean owned, float width,float eye,List<Batch> output) {
        String type=action.get("type").getAsString();
        switch(type) {
            case "apoli:and" -> { for(JsonElement element:action.getAsJsonArray("actions")) originalAction(element.getAsJsonObject(),owned,width,eye,output); }
            case "my_addon:self_decoration" -> originalAction(action.getAsJsonObject("entity_action"),true,width,eye,output);
            case "apoli:execute_command" -> {
                String[] command=action.get("command").getAsString().split(" +");
                check(command.length==12 && command[0].equals("particle") && command[11].equals("@a"),"Original center command retains original audience");
                output.add(new Batch(command[1],relative(command[2]),relative(command[3]),relative(command[4]),Integer.parseInt(command[9]),
                        Double.parseDouble(command[5]),Double.parseDouble(command[6]),Double.parseDouble(command[7]),Double.parseDouble(command[8]),
                        command[10].equals("force"),owned?OWNER:null,false));
            }
            case "shape-shifter-curse:spawn_particles_in_circle" -> {
                int samples=Math.max(1,action.get("sample_count").getAsInt());
                JsonObject spread=action.getAsJsonObject("spread"); double radius=action.get("radius").getAsDouble();
                for(int i=0;i<samples;i++) {
                    double angle=2*Math.PI*i/samples;
                    output.add(new Batch(action.get("particle").getAsString(),Math.cos(angle)*radius,.5,Math.sin(angle)*radius,
                            Math.max(0,action.get("count").getAsInt()),spread.get("x").getAsDouble()*width,spread.get("y").getAsDouble()*eye,
                            spread.get("z").getAsDouble()*width,action.get("speed").getAsFloat(),action.get("force").getAsBoolean(),owned?OWNER:null,false));
                }
            }
            default -> throw new AssertionError("Unexpected original visual action: "+type);
        }
    }
    private static double relative(String value) { return value.equals("~")?0:Double.parseDouble(value.substring(1)); }
    private static List<Batch> recipe(Kind kind,int age,float width,float eye,double... params) {
        List<Batch> result=new ArrayList<>();
        BlueTrailRecipes.emit(kind,age,1234+age,width,eye,params,new Random(17),
                (particle,x,y,z,count,dx,dy,dz,speed,decoration,projectile)->result.add(new Batch(
                        Registries.PARTICLE_TYPE.getId(particle.getType()).toString(),x,y,z,count,dx,dy,dz,speed,kind.force,decoration?OWNER:null,projectile)));
        return result;
    }
    private static void trails() throws Exception {
        JsonObject fixture=json(ROOT+"trails.json");
        check(fixture.get("ssc_jar_sha256").getAsString().equals("8fe4edd4613677326db28f5c24bf918ee5c4725d35765df065e3bfd461fe2cf7"),
                "SSC trail fixture identifies the actual tested upstream JAR");
        for(JsonElement element:fixture.getAsJsonArray("cases")) {
            JsonObject c=element.getAsJsonObject(); Kind kind=Kind.valueOf(c.get("kind").getAsString());
            double[] params=c.has("tier")?new double[]{c.get("tier").getAsInt()}:new double[0];
            double spread=c.get("spread").getAsDouble();
            Batch original=new Batch(c.get("particle").getAsString(),0,0,0,c.get("count").getAsInt(),spread,spread,spread,
                    c.get("speed").getAsDouble(),c.get("force").getAsBoolean(),null,true);
            for(int age:new int[]{0,1,2,19,127,10001}) compare(recipe(kind,age,.6f,1.62f,params),List.of(original),kind+" trail");
        }
        for(int tier:new int[]{-1,0,4,999}) check(recipe(Kind.SSC_WEB_TRAIL,2,.6f,1.62f,tier).isEmpty(),"Unsupported SSC Tier remains invisible");
    }
    private static void compare(List<Batch> actual,List<Batch> expected,String context) {
        check(actual.size()==expected.size(),context+" batch count");
        for(int i=0;i<actual.size();i++) {
            Batch a=actual.get(i),e=expected.get(i);
            check(a.particle.equals(e.particle) && a.count==e.count,context+" particle type and density");
            near(a.x,e.x,context+" x"); near(a.y,e.y,context+" y"); near(a.z,e.z,context+" z");
            check((float)a.dx==(float)e.dx && (float)a.dy==(float)e.dy && (float)a.dz==(float)e.dz
                    && (float)a.speed==(float)e.speed,context+" original on-wire float spread and speed");
            check(a.force==e.force && java.util.Objects.equals(a.owner,e.owner) && a.projectile==e.projectile,
                    context+" range, ownership and projectile avoidance semantics");
        }
    }
    private static void near(double actual,double expected,String context) {check(Math.abs(actual-expected)<1e-10,context+": "+actual+" / "+expected);}
    private static void check(boolean passed,String context) {checks++;if(!passed)throw new AssertionError(context);}
}
