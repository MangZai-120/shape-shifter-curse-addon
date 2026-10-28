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
    public static final Identifier ACTION=NeoMagic.id("action"),STATE=NeoMagic.id("state_v2"),HUD=NeoMagic.id("hud");
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
        n.putByteArray("DataJson",encodeData(NeoRuntime.data(p)));n.putString("Rules",NeoRules.version());
        n.putString("Profile",NeoState.JSON.toJson(NeoRuntime.profile(p)));
        var costs=new EnumMap<Core,Coefficients>(Core.class);for(Core core:Core.values())costs.put(core,NeoRules.cost(core));
        n.putString("Costs",NeoState.JSON.toJson(costs));
        var bookEffects=new EnumMap<Core,double[]>(Core.class);
        for(Core core:Core.values())bookEffects.put(core,new double[]{NeoLegacyCasting.blueprintCostMultiplier(p,core),NeoLegacyCasting.blueprintPower(p,core)});
        n.putString("BookEffects",NeoState.JSON.toJson(bookEffects));
        n.putByteArray("Tunings",NeoPayload.pack(NeoState.JSON.toJson(NeoRules.tunings()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var packet=PacketByteBufs.create();packet.writeNbt(n);
        if(packet.readableBytes()>1_000_000){NeoMagic.LOG.error("Research snapshot too large for {}: {} bytes",p.getUuid(),packet.readableBytes());packet.release();return;}
        ServerPlayNetworking.send(p,STATE,packet);
    }
    static byte[] encodeData(NeoState.PlayerData data) {return NeoPayload.pack(NeoState.JSON.toJson(data).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    public static void hud(ServerPlayerEntity p) {
        if(!ServerPlayNetworking.canSend(p,HUD))return;
        var packet=PacketByteBufs.create();packet.writeNbt(hudState(p));ServerPlayNetworking.send(p,HUD,packet);
    }
    public static NbtCompound hudState(ServerPlayerEntity p) {
        var d=NeoRuntime.data(p);var a=NeoRuntime.ACTIVE.get(p.getUuid());
        var legacy=NeoLegacyCasting.active(p);
        ItemStack book=net.jackcooper.shapeShifterCurseAddon.spell.SpellCastManager.getEquippedBook(p);
        boolean holding=book!=null||p.getMainHandStack().isOf(net.jackcooper.shapeShifterCurseAddon.SscAddon.MOON_DUST_SPELLBOOK)||p.getOffHandStack().isOf(net.jackcooper.shapeShifterCurseAddon.SscAddon.MOON_DUST_SPELLBOOK)||p.getMainHandStack().getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.MagicScrollItem||p.getOffHandStack().getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.MagicScrollItem||p.getMainHandStack().isOf(NeoMagic.WAND)||p.getOffHandStack().isOf(NeoMagic.WAND);
        var n=new NbtCompound();n.putBoolean("Visible",holding||d.burden>0||a!=null||NeoEffects.hasStored(p.getUuid()));
        var profile=NeoRuntime.profile(p);
        n.putInt("MasteryExpTen",d.spellbookMasteryExpTen);
        n.putInt("CastingLevel",net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.tier(d));
        n.putDouble("DangerMax",profile.safety()*2);
        n.putDouble("C",d.burden);n.putDouble("S",profile.safety());n.putDouble("BodyS",NeoRules.profile(d.form).safety());n.putInt("Recovery",d.recovery);n.putInt("RecoveryDelay",NeoRules.recoveryDelay);
        Blueprint b=a==null?bound(p.getMainHandStack()):a.session.blueprint;
        if(b==null)b=bound(p.getOffHandStack());
        boolean loaded=b!=null;
        boolean hasSpell=loaded||a!=null||legacy!=null;
        if(b==null)b=d.draft;
        Quote q=a==null?NeoLegacyCasting.blueprintQuote(p,b):a.session.quote;
        int prep=b.prep();String legacyName="";
        if(legacy!=null){b=legacy.session.blueprint;q=legacy.session.quote;prep=legacy.duration;legacyName=legacy.record.spell;}
        else if(a==null&&!loaded) {
            ItemStack scroll=p.getMainHandStack().getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.MagicScrollItem?p.getMainHandStack():p.getOffHandStack().getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.MagicScrollItem?p.getOffHandStack():ItemStack.EMPTY;
            boolean solo=!scroll.isEmpty();
            if(!solo&&book!=null)scroll=net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData.getScroll(book,net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData.getSelectedSlot(book));
            var spell=net.jackcooper.shapeShifterCurseAddon.spell.ScrollData.getSpell(scroll);
            if(spell!=null) {
                hasSpell=true;
                int level=solo?net.jackcooper.shapeShifterCurseAddon.spell.ScrollData.getLevel(scroll):net.jackcooper.shapeShifterCurseAddon.spell.ScrollData.getCastLevel(scroll);
                var modifiers=net.jackcooper.shapeShifterCurseAddon.spell.research.RuneScheme.modifiers(p,scroll,level);
                prep=modifiers.time(spell.getCastingProfile(p,level,solo).ticks());
                b=NeoLegacyCasting.descriptor(spell,level,prep);
                int normal=solo?modifiers.mana(Math.round(spell.getManaCost()*spell.getConfig().manaCostMultiplier(level))):net.jackcooper.shapeShifterCurseAddon.spell.SpellNumbers.finalManaCost(spell,book,p,level,scroll);
                q=NeoLegacyCasting.quote(p,spell,level,prep,normal);legacyName=spell.getId().toString();
            }
        }
        Risk r=offsetRisk(b,d.burden,q.actual(),profile);
        var session=legacy!=null?legacy.session:a==null?null:a.session;
        double failure=session==null?failureChance(b,d.burden+q.actual(),q.actual(),profile):session.failureProbability;
        n.putDouble("Q",q.actual());n.putDouble("Failure",failure);n.putDouble("Offset",r.offset());
        n.putBoolean("HasSpell",hasSpell);
        n.putInt("LockBoundary",session==null?prep:session.failureBoundary);
        if(!d.records.isEmpty()){var last=d.records.get(0);n.putString("ResultId",last.id);n.putString("Result",last.result);}
        n.putDouble("FailureBasis",session==null?d.burden+q.actual():session.failureBasis);
        n.putInt("CD",d.cooldowns.getOrDefault(b.identity(),0));n.putBoolean("Stored",NeoEffects.hasStored(p.getUuid()));
        if(!legacyName.isEmpty()) {
            var spell=net.jackcooper.shapeShifterCurseAddon.spell.SpellRegistry.get(new Identifier(legacyName));
            if(spell!=null)n.putInt("CD",(int)Math.max(0,net.jackcooper.shapeShifterCurseAddon.spell.SharedSpellCooldowns.getCooldownEndOf(p,spell)-p.getWorld().getTime()));
        }
        n.putInt("Age",legacy!=null?legacy.age:a==null?-1:a.age);n.putInt("Prep",prep);n.putDouble("Paid",session==null?0:session.paid);
        n.putDouble("Yaw",session==null?0:session.yaw);n.putDouble("Pitch",session==null?0:session.pitch);
        n.putDouble("Cumulative",session==null?0:1-session.survival);n.putString("Spell",legacyName);
        int status=0;for(var effect:List.of(NeoMagic.DISORDER,NeoMagic.DISINTEGRATION,NeoMagic.BREAKDOWN))if(p.hasStatusEffect(effect))status=Math.max(status,p.getStatusEffect(effect).getDuration());
        n.putInt("Blocked",status);n.putString("Form",d.form);n.putBoolean("Reference",NeoRules.profile(d.form).reference());
        n.putString("Profile",NeoState.JSON.toJson(profile));
        return n;
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
        String action=request.getString("Action");
        if(action.equals("close")){close(p);return;}
        int now=p.getServer().getTicks();if(now-LAST.getOrDefault(p.getUuid(),-100)<2)return;LAST.put(p.getUuid(),now);
        var d=NeoRuntime.data(p);
        if(action.equals("notes")) {
            String h=request.getString("Hypothesis"),con=request.getString("Conclusion");
            if(h.length()>1000||con.length()>1000)return;
            String ids=request.getString("Citations");if(ids.length()>256)return;
            var valid=d.records.stream().map(e->e.id).toList();
            var citations=Arrays.stream(ids.split(",")).filter(valid::contains).distinct().limit(4).toList();
            d.hypothesis=h;d.conclusion=con;d.citations=citations;
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
            final NbtCompound request=readRequest(buf);if(request==null)return;
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
    static NbtCompound readRequest(net.minecraft.network.PacketByteBuf buf) {
        if(buf.readableBytes()>16384)return null;
        // This protocol is a flat compound of strings plus a UUID. Reject other NBT shapes
        // before Minecraft's generic reader can construct expensive crash/hardware reports.
        byte[] bytes=new byte[buf.readableBytes()];buf.readBytes(bytes);
        try(var input=new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes))) {
            if(input.readUnsignedByte()!=10||!requestString(input,0).isEmpty())return null;
            var request=new NbtCompound();var seen=new HashSet<String>();
            while(true) {
                int type=input.readUnsignedByte();if(type==0)break;
                String key=requestString(input,32);if(!seen.add(key))return null;
                if(key.equals("Token")) {
                    if(type!=11||input.readInt()!=4)return null;
                    int[] uuid=new int[4];for(int i=0;i<4;i++)uuid[i]=input.readInt();request.putIntArray(key,uuid);
                } else {
                    int limit=switch(key){case "Action"->16;case "Blueprint"->4096;case "Hypothesis","Conclusion"->1000;case "Citations"->256;default->-1;};
                    if(type!=8||limit<0)return null;
                    String value=requestString(input,limit*3);if(value.length()>limit)return null;request.putString(key,value);
                }
            }
            return input.available()!=0||!request.containsUuid("Token")||!request.contains("Action",8)?null:request;
        }catch(java.io.IOException|RuntimeException malformed){return null;}
    }
    private static String requestString(java.io.DataInputStream input,int maxBytes)throws java.io.IOException {
        input.mark(2);int length=input.readUnsignedShort();input.reset();
        if(length>maxBytes||length>input.available()-2)throw new java.io.IOException("Invalid request string length");
        return input.readUTF();
    }
}
