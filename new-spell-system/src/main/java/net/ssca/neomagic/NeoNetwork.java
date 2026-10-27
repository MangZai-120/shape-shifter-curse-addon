package net.ssca.neomagic;

import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

public final class NeoNetwork {
    public static final Identifier ACTION=NeoMagic.id("action"),STATE=NeoMagic.id("state"),HUD=NeoMagic.id("hud");
    private record Context(UUID token,BlockPos table,String dimension) {}
    private static final Map<UUID,Context> OPEN=new HashMap<>();
    private static final Map<UUID,Integer> LAST=new HashMap<>();
    public static void close(ServerPlayerEntity p) {OPEN.remove(p.getUuid());LAST.remove(p.getUuid());}
    public static void clear() {OPEN.clear();LAST.clear();}
    public static boolean nearTable(ServerPlayerEntity p) {
        for(BlockPos pos:BlockPos.iterateOutwards(p.getBlockPos(),4,3,4))if(p.getWorld().getBlockState(pos).isOf(NeoMagic.TABLE))return true;
        return false;
    }
    private static boolean tableContext(ServerPlayerEntity p,Context c) {
        return c.table!=null&&c.dimension.equals(p.getWorld().getRegistryKey().getValue().toString())
                &&p.squaredDistanceTo(c.table.toCenterPos())<=64&&p.getWorld().getBlockState(c.table).isOf(NeoMagic.TABLE);
    }
    public static void open(ServerPlayerEntity p,BlockPos table) {
        OPEN.put(p.getUuid(),new Context(UUID.randomUUID(),table==null?null:table.toImmutable(),p.getWorld().getRegistryKey().getValue().toString()));
        NeoRuntime.refreshForm(p);send(p,true);
    }
    public static boolean discover(ServerPlayerEntity p,String piece) {
        var d=NeoRuntime.data(p);
        if(!d.known.add(piece))return false;
        p.sendMessage(NeoMagic.text("discovered",pieceText(piece)),false);NeoRuntime.dirty(p);send(p,false);return true;
    }
    public static Text pieceText(String piece) {
        return NeoMagic.text(piece.toLowerCase(Locale.ROOT).replace(':','.'));
    }
    public static Blueprint decode(String json) {
        if(json.length()>4096)throw new IllegalArgumentException("Blueprint too large");
        Blueprint b=NeoState.JSON.fromJson(json,Blueprint.class);
        if(b==null||!errors(b).isEmpty())throw new IllegalArgumentException("Invalid blueprint");
        return b;
    }
    public static Blueprint bound(ItemStack wand) {
        if(!wand.isOf(NeoMagic.WAND)||!wand.hasNbt()||!wand.getNbt().contains("NeoBlueprint"))return null;
        try{return decode(wand.getNbt().getString("NeoBlueprint"));}catch(RuntimeException invalid){return null;}
    }
    public static void importBlueprint(ServerPlayerEntity p,ItemStack stack) {
        if(!stack.hasNbt()) {NeoRuntime.message(p,"empty_blueprint");return;}
        try {
            Blueprint b=decode(stack.getNbt().getString("NeoBlueprint"));
            if(!NeoRuntime.data(p).knows(b)) {NeoRuntime.message(p,"unknown");return;}
            NeoRuntime.data(p).draft=b;NeoRuntime.dirty(p);open(p,null);
        } catch(RuntimeException bad){NeoRuntime.message(p,"invalid");}
    }
    public static void send(ServerPlayerEntity p,boolean open) {
        Context c=OPEN.get(p.getUuid());if(c==null||!ServerPlayNetworking.canSend(p,STATE))return;
        NbtCompound n=new NbtCompound();n.putBoolean("Open",open);n.putUuid("Token",c.token);n.putBoolean("Table",tableContext(p,c));
        n.putByteArray("DataJson",NeoState.JSON.toJson(NeoRuntime.data(p)).getBytes(java.nio.charset.StandardCharsets.UTF_8));n.putString("Rules",NeoRules.version());
        n.putString("Profile",NeoState.JSON.toJson(NeoRules.profile(NeoRuntime.data(p).form)));
        var costs=new EnumMap<Core,Coefficients>(Core.class);for(Core core:Core.values())costs.put(core,NeoRules.cost(core));
        n.putString("Costs",NeoState.JSON.toJson(costs));
        n.putByteArray("Tunings",NeoState.JSON.toJson(NeoRules.tunings()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var packet=PacketByteBufs.create();packet.writeNbt(n);ServerPlayNetworking.send(p,STATE,packet);
    }
    public static void hud(ServerPlayerEntity p) {
        if(!ServerPlayNetworking.canSend(p,HUD))return;
        var d=NeoRuntime.data(p);var a=NeoRuntime.ACTIVE.get(p.getUuid());
        boolean holding=p.getMainHandStack().isOf(NeoMagic.WAND)||p.getOffHandStack().isOf(NeoMagic.WAND);
        var n=new NbtCompound();n.putBoolean("Visible",holding||d.burden>0||a!=null||NeoEffects.hasStored(p.getUuid()));
        n.putDouble("C",d.burden);n.putDouble("S",NeoRules.profile(d.form).safety());n.putInt("Recovery",d.recovery);
        Blueprint b=a==null?bound(p.getMainHandStack()):a.session.blueprint;
        if(b==null)b=d.draft;
        Quote q=a==null?NeoRules.quote(b,d.mastery(b)):a.session.quote;
        Risk r=risk(b,d.burden,q.actual(),NeoRules.profile(d.form));
        n.putDouble("Q",q.actual());n.putDouble("Failure",r.failure());n.putDouble("Offset",r.offset());
        n.putInt("CD",d.cooldowns.getOrDefault(b.identity(),0));n.putBoolean("Stored",NeoEffects.hasStored(p.getUuid()));
        n.putInt("Age",a==null?-1:a.age);n.putInt("Prep",b.prep());n.putDouble("Paid",a==null?0:a.session.paid);
        n.putDouble("Yaw",a==null?0:a.session.yaw);n.putDouble("Pitch",a==null?0:a.session.pitch);
        n.putDouble("Cumulative",a==null?0:1-a.session.survival);
        int status=0;for(var effect:List.of(NeoMagic.DISORDER,NeoMagic.DISINTEGRATION,NeoMagic.BREAKDOWN))if(p.hasStatusEffect(effect))status=Math.max(status,p.getStatusEffect(effect).getDuration());
        n.putInt("Blocked",status);n.putString("Form",d.form);n.putBoolean("Reference",NeoRules.profile(d.form).reference());
        n.putString("Profile",NeoState.JSON.toJson(NeoRules.profile(d.form)));
        var packet=PacketByteBufs.create();packet.writeNbt(n);ServerPlayNetworking.send(p,HUD,packet);
    }
    private static boolean materials(ServerPlayerEntity p,boolean consume) {
        if(p.isCreative())return true;
        int paper=0,amethyst=0;
        for(int i=0;i<p.getInventory().size();i++) {ItemStack s=p.getInventory().getStack(i);if(s.isOf(Items.PAPER))paper+=s.getCount();if(s.isOf(Items.AMETHYST_SHARD))amethyst+=s.getCount();}
        if(paper<1||amethyst<1)return false;
        if(consume)for(Item item:List.of(Items.PAPER,Items.AMETHYST_SHARD))for(int i=0;i<p.getInventory().size();i++)if(p.getInventory().getStack(i).isOf(item)){p.getInventory().getStack(i).decrement(1);break;}
        return true;
    }
    private static void handle(ServerPlayerEntity p,NbtCompound request) {
        if(!p.isAlive()||p.isSpectator()){close(p);return;}
        Context c=OPEN.get(p.getUuid());if(c==null||!request.containsUuid("Token")||!c.token.equals(request.getUuid("Token")))return;
        int now=p.getServer().getTicks();if(now-LAST.getOrDefault(p.getUuid(),-100)<2)return;LAST.put(p.getUuid(),now);
        String action=request.getString("Action");var d=NeoRuntime.data(p);
        if(action.equals("close")){close(p);return;}
        if(action.equals("notes")) {
            String h=request.getString("Hypothesis"),con=request.getString("Conclusion");
            if(h.length()>1000||con.length()>1000)return;
            d.hypothesis=h;d.conclusion=con;
            String ids=request.getString("Citations");if(ids.length()>256)return;
            var valid=d.records.stream().map(e->e.id).toList();
            d.citations=Arrays.stream(ids.split(",")).filter(valid::contains).distinct().limit(4).toList();
        } else {
            if(!tableContext(p,c)){NeoRuntime.message(p,"need_table");send(p,false);return;}
            Blueprint b=decode(request.getString("Blueprint"));
            if(!d.knows(b)){NeoRuntime.message(p,"unknown");return;}
            switch(action) {
                case "draft" -> d.draft=b;
                case "baseline" -> {d.draft=b;d.baseline=b;}
                case "craft" -> {
                    if(!materials(p,false)){NeoRuntime.message(p,"materials");break;}
                    ItemStack page=new ItemStack(NeoMagic.BLUEPRINT);
                    page.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(b));
                    page.getOrCreateNbt().putString("ResearchNote",d.conclusion);page.getOrCreateNbt().putString("RuleVersion",NeoRules.version());
                    page.getOrCreateNbt().putByteArray("Evidence",NeoState.JSON.toJson(d.records.stream().filter(e->d.citations.contains(e.id)).toList()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    materials(p,true);p.getInventory().offerOrDrop(page);d.draft=b;NeoRuntime.message(p,"crafted");
                }
                case "equip" -> {
                    boolean page=false;ItemStack wand=p.getMainHandStack().isOf(NeoMagic.WAND)?p.getMainHandStack():p.getOffHandStack().isOf(NeoMagic.WAND)?p.getOffHandStack():ItemStack.EMPTY;
                    for(int i=0;i<p.getInventory().size();i++) {
                        ItemStack stack=p.getInventory().getStack(i);
                        if(wand.isEmpty()&&stack.isOf(NeoMagic.WAND))wand=stack;
                        if(stack.isOf(NeoMagic.BLUEPRINT)&&stack.hasNbt())try{if(decode(stack.getNbt().getString("NeoBlueprint")).equals(b))page=true;}catch(RuntimeException ignored){}
                    }
                    if(!page||wand.isEmpty()){NeoRuntime.message(p,"need_blueprint");break;}
                    wand.getOrCreateNbt().putString("NeoBlueprint",NeoState.JSON.toJson(b));d.draft=b;NeoRuntime.message(p,"equipped");
                }
                default -> {return;}
            }
        }
        NeoRuntime.dirty(p);send(p,false);
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ACTION,(server,p,handler,buf,sender)->{
            if(buf.readableBytes()>16384)return;
            final NbtCompound request;
            try {request=buf.readNbt();if(request==null||buf.isReadable())return;}catch(RuntimeException bad){return;}
            server.execute(()->{try{handle(p,request);}catch(RuntimeException malformed){NeoRuntime.message(p,"invalid");}});
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(CommandManager.literal("neomagic")
                .requires(source->source.hasPermissionLevel(2))
                .then(CommandManager.literal("kit").executes(context->{
                    var p=context.getSource().getPlayerOrThrow();
                    for(Item i:List.of(NeoMagic.WAND,NeoMagic.NOTEBOOK,NeoMagic.TABLE.asItem(),NeoMagic.TARGET.asItem(),Items.PAPER,Items.AMETHYST_SHARD))p.getInventory().offerOrDrop(new ItemStack(i,1));
                    NeoMagic.SAMPLES.values().forEach(i->p.getInventory().offerOrDrop(new ItemStack(i)));return 1;
                }))
                .then(CommandManager.literal("reset_burden").executes(context->{NeoRuntime.relieve(context.getSource().getPlayerOrThrow(),Double.MAX_VALUE);return 1;}))
                .then(CommandManager.literal("research_all").executes(context->{var p=context.getSource().getPlayerOrThrow();NeoMagic.SAMPLES.keySet().forEach(key->discover(p,key));return 1;}))));
    }
}
