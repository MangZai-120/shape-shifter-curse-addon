package net.ssca.neomagic;

import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;

/** 只保留正式法术的 HUD 同步与崩解度调试指令，不接收旧实验研究协议。 */
public final class NeoNetwork {
    public static final Identifier HUD=NeoMagic.id("hud");
    public static final Identifier HUD_V2=NeoMagic.id("hud_v2");
    public static final Identifier HUD_RESYNC_V2=NeoMagic.id("hud_resync_v2");
    private record HudSnapshot(ServerPlayerEntity player,Object world,Object connection,NbtCompound state,int sentTick,int fullTick,long revision,boolean compact) {}
    private static final Map<UUID,HudSnapshot> HUD_SENT=new HashMap<>();
    private static final Map<UUID,Integer> HUD_RESYNC_REQUESTED=new HashMap<>();
    private static final Map<Profile,String> PROFILE_JSON=new LinkedHashMap<>(32,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Profile,String> entry){return size()>128;}
    };
    public static void close(ServerPlayerEntity p){HUD_SENT.remove(p.getUuid());HUD_RESYNC_REQUESTED.remove(p.getUuid());}
    public static void clear(){HUD_SENT.clear();HUD_RESYNC_REQUESTED.clear();PROFILE_JSON.clear();}
    public static void hud(ServerPlayerEntity p) {
        boolean compact=ServerPlayNetworking.canSend(p,HUD_V2);
        if(!compact&&!ServerPlayNetworking.canSend(p,HUD))return;
        NbtCompound state=hudState(p);int now=p.getServer().getTicks();
        HudSnapshot previous=HUD_SENT.get(p.getUuid());
        boolean sameSession=previous!=null&&previous.player==p&&previous.world==p.getWorld()&&previous.connection==p.networkHandler&&previous.compact==compact;
        boolean full=!sameSession||now-previous.fullTick<0||now-previous.fullTick>=100;
        if(!full&&previous.state.equals(state))return;
        long revision=previous==null?1:previous.revision+1;
        var packet=PacketByteBufs.create();
        if(compact)NeoHudPacket.write(packet,NeoHudPacket.between(full?new NbtCompound():previous.state,state,revision,full?0:previous.revision,full));
        else packet.writeNbt(state); // Older peers keep the original complete-NBT protocol.
        ServerPlayNetworking.send(p,compact?HUD_V2:HUD,packet);
        HUD_SENT.put(p.getUuid(),new HudSnapshot(p,p.getWorld(),p.networkHandler,state,now,full?now:previous.fullTick,revision,compact));
    }
    public static NbtCompound hudState(ServerPlayerEntity p) {
        var d=NeoRuntime.data(p);var a=NeoRuntime.ACTIVE.get(p.getUuid());
        var legacy=NeoLegacyCasting.active(p);
        ItemStack book=net.jackcooper.shapeShifterCurseAddon.spell.SpellCastManager.getEquippedBook(p);
        boolean holding=book!=null||p.getMainHandStack().isOf(net.jackcooper.shapeShifterCurseAddon.SscAddon.MOON_DUST_SPELLBOOK)||p.getOffHandStack().isOf(net.jackcooper.shapeShifterCurseAddon.SscAddon.MOON_DUST_SPELLBOOK)||p.getMainHandStack().getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.MagicScrollItem||p.getOffHandStack().getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.MagicScrollItem;
        var n=new NbtCompound();n.putBoolean("Visible",holding||d.burden>0||a!=null||NeoEffects.hasStored(p.getUuid()));
        n.putInt("PlayerId",p.getId());n.putString("Dimension",p.getWorld().getRegistryKey().getValue().toString());
        var profile=NeoLegacyCasting.profile(p,book);
        n.putInt("MasteryExpTen",d.spellbookMasteryExpTen);
        n.putBoolean("BookEquipped",book!=null&&!book.isEmpty());
        n.putInt("CastingLevel",net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.tier(d));
        n.putDouble("DangerMax",profile.safety()*2);
        n.putDouble("C",d.burden);n.putDouble("S",profile.safety());n.putDouble("BodyS",NeoRuntime.bodyProfile(p).safety());n.putInt("Recovery",d.recovery);n.putInt("RecoveryDelay",NeoRules.recoveryDelay);
        n.putDouble("FrostSpellMultiplier",net.jackcooper.shapeShifterCurseAddon.spell.FrostSpellLink.multiplier(p,true));
        n.putLong("FrostReadyUntil",net.jackcooper.shapeShifterCurseAddon.spell.FrostSpellLink.readyUntil(p));
        n.putLong("NovaQuickCastUntil",net.jackcooper.shapeShifterCurseAddon.spell.OcelotSpellLink.quickUntil(p));
        Blueprint b;
        Quote q;
        int prep;
        String legacyName="";
        boolean hasSpell=a!=null||legacy!=null;
        // 每次只计算最终采用的预览；活动施法直接复用起手快照，不先计算随后会被覆盖的草稿。
        if(legacy!=null) {
            b=legacy.session.blueprint;q=legacy.session.quote;prep=legacy.duration;legacyName=legacy.record.spell;
        } else if(a!=null) {
            b=a.session.blueprint;q=a.session.quote;prep=a.record.preparation;
        } else {
            ItemStack scroll=p.getMainHandStack().getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.MagicScrollItem?p.getMainHandStack():p.getOffHandStack().getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.MagicScrollItem?p.getOffHandStack():ItemStack.EMPTY;
            boolean solo=!scroll.isEmpty();
            if(!solo&&book!=null)scroll=net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData.getScroll(book,net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData.getSelectedSlot(book));
            var spell=net.jackcooper.shapeShifterCurseAddon.spell.ScrollData.getSpell(scroll);
            if(spell!=null) {
                hasSpell=true;
                int level=solo?net.jackcooper.shapeShifterCurseAddon.spell.ScrollData.getLevel(scroll):net.jackcooper.shapeShifterCurseAddon.spell.ScrollData.getCastLevel(scroll);
                var modifiers=net.jackcooper.shapeShifterCurseAddon.spell.research.RuneScheme.modifiers(p,scroll,level);
                int original=spell.getCastingProfile(p,level,solo).ticks();
                prep=net.jackcooper.shapeShifterCurseAddon.spell.FormSpellStyle.timing(p,original,modifiers.time(original),spell.getLockInTick()).duration();
                b=NeoLegacyCasting.descriptor(spell,level,prep);
                int normal=solo?modifiers.mana(Math.round(spell.getManaCost()*spell.getConfig().manaCostMultiplier(level))):net.jackcooper.shapeShifterCurseAddon.spell.SpellNumbers.finalManaCost(spell,book,p,level,scroll);
                q=NeoLegacyCasting.quote(p,spell,level,prep,normal);legacyName=spell.getId().toString();
            } else {
                b=d.draft;
                q=NeoLegacyCasting.blueprintQuote(p,b,book);
                prep=net.jackcooper.shapeShifterCurseAddon.spell.FormSpellStyle.timing(p,b.prep(),b.prep(),-1).duration();
            }
        }
        Risk r=offsetRisk(b,d.burden,q.actual(),profile);
        var session=legacy!=null?legacy.session:a==null?null:a.session;
        double forecast=session==null?failureChance(b,d.burden+q.actual(),q.actual(),profile):session.failureProbability;
        int age=legacy!=null?legacy.age:a==null?-1:a.age;
        boolean protectedByLock=session!=null&&prep>0&&session.failureBoundary<prep&&age>=session.failureBoundary;
        double failure=d.burden<=profile.safety()||protectedByLock?0:forecast;
        n.putDouble("Q",q.actual());n.putDouble("Failure",failure);n.putDouble("ForecastFailure",forecast);n.putDouble("Offset",r.offset());
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
        n.putInt("CollapseStage",p.isAlive()&&!p.isSpectator()?net.jackcooper.shapeShifterCurseAddon.spell.CollapsePenalties.stage(d.burden,profile.safety()):0);
        n.putInt("Blocked",0);n.putString("Form",d.form);n.putBoolean("Reference",NeoRuntime.bodyProfile(p).reference());
        n.putString("Profile",PROFILE_JSON.computeIfAbsent(profile,NeoState.JSON::toJson));
        return n;
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(HUD_RESYNC_V2,(server,p,handler,buf,sender)->server.execute(()->{
            if(p.networkHandler!=handler||p.isRemoved())return;
            int now=server.getTicks();Integer last=HUD_RESYNC_REQUESTED.get(p.getUuid());
            if(last!=null&&now-last>=0&&now-last<20)return;
            HUD_RESYNC_REQUESTED.put(p.getUuid(),now);
            // Retain the revision even when forcing a full snapshot, so queued older deltas stay stale.
            HudSnapshot old=HUD_SENT.get(p.getUuid());
            if(old!=null)HUD_SENT.put(p.getUuid(),new HudSnapshot(old.player,old.world,old.connection,old.state,old.sentTick,now-100,old.revision,old.compact));
            hud(p);
        }));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((p,joined)->{
            HudSnapshot old=HUD_SENT.get(p.getUuid());
            if(old!=null)HUD_SENT.put(p.getUuid(),new HudSnapshot(old.player,old.world,old.connection,old.state,old.sentTick,p.getServer().getTicks()-100,old.revision,old.compact));
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(CommandManager.literal("neomagic")
                .requires(source->source.hasPermissionLevel(2))
                .then(CommandManager.literal("reset_burden").executes(context->{NeoRuntime.relieve(context.getSource().getPlayerOrThrow(),Double.MAX_VALUE);return 1;}))));
    }
}
