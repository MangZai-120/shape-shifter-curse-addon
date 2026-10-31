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
    private record HudSnapshot(ServerPlayerEntity player,Object world,NbtCompound state,int sentTick) {}
    private static final Map<UUID,HudSnapshot> HUD_SENT=new HashMap<>();
    public static void close(ServerPlayerEntity p){HUD_SENT.remove(p.getUuid());}
    public static void clear(){HUD_SENT.clear();}
    public static void hud(ServerPlayerEntity p) {
        if(!ServerPlayNetworking.canSend(p,HUD))return;
        NbtCompound state=hudState(p);int now=p.getServer().getTicks();
        HudSnapshot previous=HUD_SENT.get(p.getUuid());
        // 活动状态仍按原来的频率同步；仅省略完全相同的快照，每 5 秒保底重发。
        // 实体或世界变化强制重发，避免重连、重生或切维度后客户端保留空状态。
        if(previous!=null&&previous.player==p&&previous.world==p.getWorld()
                &&now-previous.sentTick>=0&&now-previous.sentTick<100&&previous.state.equals(state))return;
        var packet=PacketByteBufs.create();packet.writeNbt(state);ServerPlayNetworking.send(p,HUD,packet);
        HUD_SENT.put(p.getUuid(),new HudSnapshot(p,p.getWorld(),state,now));
    }
    public static NbtCompound hudState(ServerPlayerEntity p) {
        var d=NeoRuntime.data(p);var a=NeoRuntime.ACTIVE.get(p.getUuid());
        var legacy=NeoLegacyCasting.active(p);
        ItemStack book=net.jackcooper.shapeShifterCurseAddon.spell.SpellCastManager.getEquippedBook(p);
        boolean holding=book!=null||p.getMainHandStack().isOf(net.jackcooper.shapeShifterCurseAddon.SscAddon.MOON_DUST_SPELLBOOK)||p.getOffHandStack().isOf(net.jackcooper.shapeShifterCurseAddon.SscAddon.MOON_DUST_SPELLBOOK)||p.getMainHandStack().getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.MagicScrollItem||p.getOffHandStack().getItem() instanceof net.jackcooper.shapeShifterCurseAddon.item.MagicScrollItem;
        var n=new NbtCompound();n.putBoolean("Visible",holding||d.burden>0||a!=null||NeoEffects.hasStored(p.getUuid()));
        var profile=NeoLegacyCasting.profile(p,book);
        n.putInt("MasteryExpTen",d.spellbookMasteryExpTen);
        n.putBoolean("BookEquipped",book!=null&&!book.isEmpty());
        n.putInt("CastingLevel",net.jackcooper.shapeShifterCurseAddon.spell.PlayerSpellMastery.tier(d));
        n.putDouble("DangerMax",profile.safety()*2);
        n.putDouble("C",d.burden);n.putDouble("S",profile.safety());n.putDouble("BodyS",NeoRules.profile(d.form).safety());n.putInt("Recovery",d.recovery);n.putInt("RecoveryDelay",NeoRules.recoveryDelay);
        n.putDouble("FrostSpellMultiplier",net.jackcooper.shapeShifterCurseAddon.spell.FrostSpellLink.multiplier(p,true));
        n.putLong("FrostReadyUntil",net.jackcooper.shapeShifterCurseAddon.spell.FrostSpellLink.readyUntil(p));
        n.putLong("NovaQuickCastUntil",net.jackcooper.shapeShifterCurseAddon.spell.OcelotSpellLink.quickUntil(p));
        Blueprint b=a==null?null:a.session.blueprint;
        boolean loaded=false;
        boolean hasSpell=loaded||a!=null||legacy!=null;
        if(b==null)b=d.draft;
        Quote q=a==null?NeoLegacyCasting.blueprintQuote(p,b,book):a.session.quote;
        int prep=a==null?net.jackcooper.shapeShifterCurseAddon.spell.FormSpellStyle.timing(p,b.prep(),b.prep(),-1).duration():a.record.preparation;String legacyName="";
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
                int original=spell.getCastingProfile(p,level,solo).ticks();
                prep=net.jackcooper.shapeShifterCurseAddon.spell.FormSpellStyle.timing(p,original,modifiers.time(original),spell.getLockInTick()).duration();
                b=NeoLegacyCasting.descriptor(spell,level,prep);
                int normal=solo?modifiers.mana(Math.round(spell.getManaCost()*spell.getConfig().manaCostMultiplier(level))):net.jackcooper.shapeShifterCurseAddon.spell.SpellNumbers.finalManaCost(spell,book,p,level,scroll);
                q=NeoLegacyCasting.quote(p,spell,level,prep,normal);legacyName=spell.getId().toString();
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
        int status=0;for(var effect:List.of(NeoMagic.DISORDER,NeoMagic.DISINTEGRATION,NeoMagic.BREAKDOWN))if(p.hasStatusEffect(effect))status=Math.max(status,p.getStatusEffect(effect).getDuration());
        n.putInt("Blocked",status);n.putString("Form",d.form);n.putBoolean("Reference",NeoRules.profile(d.form).reference());
        n.putString("Profile",NeoState.JSON.toJson(profile));
        return n;
    }
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(CommandManager.literal("neomagic")
                .requires(source->source.hasPermissionLevel(2))
                .then(CommandManager.literal("reset_burden").executes(context->{NeoRuntime.relieve(context.getSource().getPlayerOrThrow(),Double.MAX_VALUE);return 1;}))));
    }
}
