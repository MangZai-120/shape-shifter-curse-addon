package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import java.util.UUID;
import java.util.WeakHashMap;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneCastContext;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneModifiers;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class WrathMarkSpell extends Spell {
    private final WeakHashMap<ServerPlayerEntity,UUID> locks = new WeakHashMap<>();

    public WrathMarkSpell() { super(new Identifier("ssc_addon", "wrath_mark"), SpellRarity.WHITE); }
    @Override public Identifier getIconTexture() { return new Identifier("ssc_addon","textures/item/scroll_mask_base.png"); }
    @Override public boolean requiresTargetBeforeChannel() { return true; }
    @Override public SpellCastingRules.Mode getCastingMode() { return SpellCastingRules.Mode.RELEASE; }
    @Override public Text getCastFailureMessage(ServerPlayerEntity caster) { return Text.translatable("message.ssc_addon.spellbook.no_entity_target"); }

    public static final Identifier AIM_CHECK = new Identifier("ssc_addon","wrath_aim_check");
    private static final double BASE_RANGE = 24;

    public static void registerAimCheck() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(AIM_CHECK,(server,player,handler,buf,sender)->{
            int id=buf.readVarInt(), sequence=buf.readVarInt();
            server.execute(()->{
                var entity=player.getWorld().getEntityById(id);
                boolean allowed=entity instanceof LivingEntity living && eligible(player,living)
                        && player.squaredDistanceTo(living)<=Math.pow(BASE_RANGE*1.6,2);
                var reply=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                reply.writeVarInt(sequence); reply.writeBoolean(allowed);
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,AIM_CHECK,reply);
            });
        });
    }

    private static double range() { return RuneCastContext.current().scale(BASE_RANGE, RuneModifiers.Stat.DISTANCE); }
    public static LivingEntity aim(PlayerEntity caster) {
        double reach=range();
        LivingEntity hit=LunarPhaseSpell.raycastEntity(caster,reach,0.75);
        return hit!=null && caster.squaredDistanceTo(hit)<=reach*reach ? hit : null;
    }
    @Override public double getCastingLockRange(ServerPlayerEntity caster,int level) { return range(); }
    private static boolean eligible(ServerPlayerEntity caster,LivingEntity entity) {
        return entity != null && !(entity instanceof net.minecraft.entity.decoration.ArmorStandEntity) && entity.isAlive() && !entity.isSpectator() && !entity.hasStatusEffect(net.jackcooper.shapeShifterCurseAddon.SscAddon.PURIFIED)
                && !WhitelistUtils.isProtected(caster,entity) && !DomainManager.blocksTargeting(caster,entity);
    }
    @Override public Vec3d captureCastTarget(ServerPlayerEntity caster,int level) {
        LivingEntity entity = aim(caster);
        if (!eligible(caster,entity)) { locks.remove(caster); return null; }
        locks.put(caster,entity.getUuid());
        return entity.getPos();
    }
    @Override public LivingEntity getCastingLockEntity(ServerPlayerEntity caster) {
        UUID uuid = locks.get(caster);
        return uuid != null && caster.getServerWorld().getEntity(uuid) instanceof LivingEntity target ? target : null;
    }
    @Override public boolean canContinueCasting(ServerPlayerEntity caster,ItemStack scroll) {
        return eligible(caster,getCastingLockEntity(caster));
    }
    @Override public void onChannelEnded(ServerPlayerEntity caster,boolean interrupted) {
        if (interrupted) locks.remove(caster);
    }
    private static int duration(int tier) {
        int seconds = switch (tier) {
            case 1, 2 -> 10;
            case 3 -> 8;
            case 4 -> 7;
            default -> 5;
        };
        return RuneCastContext.current().duration(20 * seconds, RuneModifiers.Stat.NEGATIVE);
    }
    @Override public SpellBacklash.Effects backlashEffects(ServerPlayerEntity caster,float power,boolean solo,int level) {
        return SpellBacklash.Effects.debuffs(0,new StatusEffectInstance(StatusEffects.BLINDNESS,duration(level)));
    }
    @Override public void cast(ServerPlayerEntity caster,float power,boolean solo) { cast(caster,power,solo,1); }
    @Override public void cast(ServerPlayerEntity caster,float power,boolean solo,int level) {
        LivingEntity target = getCastingLockEntity(caster);
        locks.remove(caster);
        if (eligible(caster,target)) {
            int tier=Math.max(1,Math.min(level,getMaxLevel()));
            boolean alreadyDebuffed=FormCastingStyle.hasHarmfulEffect(target);
            if (WrathMark.apply(caster,target,duration(tier),power)) {
                net.jackcooper.shapeShifterCurseAddon.anomaly.SpellAnomalyHits.hit(caster,target);
                FormCastingStyle.onSpellHit(caster,target,FormationElement.CURSE,solo ? null : ssc_addon$getRefundCastId(),alreadyDebuffed);
            }
        }
    }
}
