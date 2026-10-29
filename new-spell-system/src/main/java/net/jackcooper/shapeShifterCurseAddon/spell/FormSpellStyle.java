package net.jackcooper.shapeShifterCurseAddon.spell;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.jackcooper.shapeShifterCurseAddon.compat.MoistureWaterRecovery;
import net.jackcooper.shapeShifterCurseAddon.resource.BarKeys;
import net.jackcooper.shapeShifterCurseAddon.resource.ResourceBars;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.FormUtils;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** 本次施法的形态协同快照；原生mana与身体崩解度分别支付。 */
public final class FormSpellStyle {
    private static final BalanceReader BAL = new BalanceReader("systems.form_casting");
    private static final ThreadLocal<FormSpellStyle> EFFECT = new ThreadLocal<>();
    public AllaySpellLink.Cast allay;
    public static AllaySpellLink.Cast currentAllay(){var style=EFFECT.get();return style==null?null:style.allay;}
    public void bindAllay(double actual,net.ssca.neomagic.NeoState.Experiment record,boolean directCurse){allay=AllaySpellLink.create(player,actual,record,directCurse);}
    /** Client wiring supplies the server-authoritative active-cast snapshot without loading client classes here. */
    public static java.util.function.ToDoubleFunction<PlayerEntity> clientRangeSnapshot = player -> Double.NaN;
    private final ServerPlayerEntity player;
    public final Timing timing;
    public final boolean accelerated;
    public final boolean novaQuickCast;
    public final boolean moonWeaver;
    public final SpiderSpellLink.RollGroup weave;
    public final double collapseMultiplier, range;
    public final double projectileSpeed,projectileRange;
    public final LivingEntity target;
    public final MoistureHealing healing;
    public final FrostSpellLink.Payment frost;
    private final int mana;
    private final int moisture;
    private boolean paid;

    /** originalPre之前按比例推进演出，之后严格每真实tick推进一tick。 */
    public record Timing(int originalDuration, int originalPre, int preparation, int duration, int lockTick) {
        public static Timing of(int original, int adjusted, int lock, double multiplier) {
            original = Math.max(0, original);
            int before = lock < 0 ? original : Math.min(original, Math.max(0, lock));
            int tail = original - before;
            int pre = (int)Math.ceil(Math.max(0, adjusted - tail) * multiplier);
            return new Timing(original, before, pre, pre + tail, lock < 0 ? -1 : pre);
        }
        public int logical(int elapsed) { return (int)logical(elapsed, originalPre, preparation); }
        public static double logical(double elapsed, int originalPre, int preparation) {
            double t = Math.max(0, elapsed);
            return preparation > 0 && t < preparation ? t * originalPre / preparation
                    : originalPre + Math.max(0, t - preparation);
        }
    }

    private FormSpellStyle(ServerPlayerEntity player, Timing timing, boolean accelerated,
                           double collapseMultiplier, LivingEntity target, double range, int mana,boolean projectile,boolean ice) {
        this.player=player;this.timing=timing;this.accelerated=accelerated;
        this.novaQuickCast=OcelotSpellLink.quickCast(player);
        boolean enhanced=projectile&&canEnhanceProjectiles(player);
        this.projectileSpeed=enhanced?BAL.d("fluorescent_projectile_speed",1.5):1;
        this.projectileRange=enhanced?BAL.d("fluorescent_projectile_range",1.5):1;
        this.moisture=enhanced?BAL.i("fluorescent_projectile_moisture",10):0;
        this.frost=FrostSpellLink.plan(player,ice);
        this.collapseMultiplier=collapseMultiplier*(enhanced?BAL.d("fluorescent_projectile_collapse_multiplier",1.05):1)
                *(frost==null?1:frost.multiplier);
        this.moonWeaver=projectile&&SpiderSpellLink.canWeave(player);
        this.weave=moonWeaver?new SpiderSpellLink.RollGroup():null;
        this.target=target;this.range=range*this.projectileRange;this.mana=mana+(moonWeaver?SpiderSpellLink.webCost():0);
        this.healing=MoistureHealing.create(player);
    }
    public static boolean canAccelerate(PlayerEntity player) {
        return player!=null&&FormUtils.isAnyForm(player,FormIdentifiers.FAMILIAR_FOX_SP,FormIdentifiers.FAMILIAR_FOX_RED)
                && ResourceBars.get(player,BarKeys.VANILLA_MANA)>=BAL.i("acceleration_mana",10);
    }
    public static double collapseMultiplier(PlayerEntity player) {
        return canAccelerate(player)?BAL.d("acceleration_collapse_multiplier",1.05):1;
    }
    public static double collapseMultiplier(PlayerEntity player,boolean projectile) {
        return collapseMultiplier(player)*(projectile?projectileCollapseMultiplier(player):1);
    }
    public static double collapseMultiplier(PlayerEntity player,boolean projectile,boolean ice) {
        return collapseMultiplier(player,projectile)*FrostSpellLink.multiplier(player,ice);
    }
    public static double sheepProjectileRange() {return BAL.d("sheep_projectile_range",40);}
    private static boolean fluorescent(PlayerEntity player) {
        return player!=null&&FormUtils.isAnyForm(player,FormIdentifiers.AXOLOTL_FLUORESCENT,FormIdentifiers.AXOLOTL_ALING);
    }
    private static boolean canEnhanceProjectiles(PlayerEntity player) {
        return fluorescent(player)&&player.getAir()>=BAL.i("fluorescent_projectile_moisture",10);
    }
    public static double projectileCollapseMultiplier(PlayerEntity player) {
        return canEnhanceProjectiles(player)?BAL.d("fluorescent_projectile_collapse_multiplier",1.05):1;
    }
    public static double projectileSpeedMultiplier(PlayerEntity player) {
        return canEnhanceProjectiles(player)?BAL.d("fluorescent_projectile_speed",1.5):1;
    }
    public static double projectileRangeMultiplier(PlayerEntity player) {
        var effect=EFFECT.get();
        if(effect!=null&&effect.player==player)return effect.projectileRange;
        double snapshot=player instanceof ServerPlayerEntity server?SpellChannelManager.projectileRangeSnapshot(server)
                :player==null?Double.NaN:clientRangeSnapshot.applyAsDouble(player);
        if(Double.isFinite(snapshot))return snapshot;
        return canEnhanceProjectiles(player)?BAL.d("fluorescent_projectile_range",1.5):1;
    }
    public static Timing timing(ServerPlayerEntity player,int original,int adjusted,int lock) {
        return Timing.of(original,adjusted,lock,OcelotSpellLink.quickCast(player)?0:canAccelerate(player)?BAL.d("preparation_multiplier",.75):1);
    }
    public static FormSpellStyle plan(ServerPlayerEntity player, Spell spell, int level, int original, int adjusted) {
        return plan(player,spell.getConfig().projectile,spell.getProjectileRange(level),original,adjusted,spell.getLockInTick(),spell.getElement()==FormationElement.ICE);
    }
    public static FormSpellStyle planTriggered(ServerPlayerEntity player,Spell spell,int level,int original,int adjusted) {
        return new FormSpellStyle(player,Timing.of(original,adjusted,spell.getLockInTick(),0),false,1,null,
                spell.getProjectileRange(level),0,spell.getConfig().projectile,spell.getElement()==FormationElement.ICE);
    }
    public static FormSpellStyle plan(ServerPlayerEntity player,boolean projectile,double range,int original,int adjusted,int lock) {
        return plan(player,projectile,range,original,adjusted,lock,false);
    }
    public static FormSpellStyle plan(ServerPlayerEntity player,boolean projectile,double range,int original,int adjusted,int lock,boolean ice) {
        boolean quick=canAccelerate(player);
        LivingEntity target=projectile&&FormUtils.isForm(player,FormIdentifiers.FAMILIAR_FOX_MANCIANIMA)
                ?findTarget(player,range):null;
        return new FormSpellStyle(player,timing(player,original,adjusted,lock),
                quick,quick?BAL.d("acceleration_collapse_multiplier",1.05):1,target,range,
                quick?BAL.i("acceleration_mana",10):target==null?0:BAL.i("homing_mana",10),projectile,ice);
    }
    /** 所有起手预检成功后调用一次，不足不扣，不生成施法/崩解记录。 */
    public boolean pay() {
        if(paid)return true;
        if(moisture>0&&player.getAir()<moisture)return false;
        if(mana>0&&!ResourceBars.consume(player,BarKeys.VANILLA_MANA,mana)) {
            SpellChannelManager.playNoManaSound(player);
            player.sendMessage(net.minecraft.text.Text.translatable("message.ssc_addon.spell.homing_mana",mana),true);
            return false;
        }
        if(moisture>0&&!MoistureWaterRecovery.consume(player,moisture))return false;
        if(frost!=null&&!frost.pay())return false;
        paid=true;return true;
    }
    public int paidMoisture(){return paid?moisture:0;}
    public void effect(Runnable action) {
        var previous=EFFECT.get();EFFECT.set(this);
        try{action.run();}finally{if(previous==null)EFFECT.remove();else EFFECT.set(previous);}
    }
    /** 仅法术的生命回复进入此处；原生装死、自然回血和反转伤害沿用原结算。 */
    public static void heal(ServerPlayerEntity caster,LivingEntity target,float amount) {
        var cast=EFFECT.get();
        if(cast==null||cast.player!=caster||cast.healing==null)target.heal(amount);
        else cast.healing.heal(target,amount,value->target.heal((float)value));
    }
    public static boolean legalTarget(ServerPlayerEntity player,LivingEntity target) {
        return target!=player&&target.isAlive()&&!target.isRemoved()&&!target.isSpectator()
                &&!(target instanceof ArmorStandEntity)&&target.getWorld()==player.getWorld()
                &&!player.isTeammate(target)&&!WhitelistUtils.isProtected(player,target)
                &&(!(target instanceof ServerPlayerEntity other)||player.shouldDamagePlayer(other))
                &&!DomainManager.blocksTargeting(player,target);
    }
    /** 一次施法只搜一次；用夹角余弦排序，距离只用于并列时决胜。 */
    public static LivingEntity findTarget(ServerPlayerEntity player,double range) {
        if(!Double.isFinite(range)||range<=0)return null;
        Vec3d eye=player.getEyePos(),look=player.getRotationVec(1).normalize();
        double bestCos=Math.cos(Math.toRadians(BAL.d("homing_cone_degrees",15))),bestDistance=Double.MAX_VALUE;
        LivingEntity best=null;
        for(var candidate:player.getWorld().getEntitiesByClass(LivingEntity.class,new Box(eye,eye).expand(range),
                e->legalTarget(player,e))) {
            Vec3d delta=candidate.getBoundingBox().getCenter().subtract(eye);
            double distance=delta.lengthSquared();
            if(distance<1e-8||distance>range*range)continue;
            double cosine=look.dotProduct(delta)/Math.sqrt(distance);
            if(cosine+1e-10<bestCos||Math.abs(cosine-bestCos)<1e-10&&distance>=bestDistance)continue;
            if(!player.canSee(candidate))continue;
            bestCos=cosine;bestDistance=distance;best=candidate;
        }
        return best;
    }
    public static void register() {
        FrostSpellLink.register();
        OcelotSpellLink.register();
        SpiderSpellLink.register();
        AllaySpellLink.register();
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            var cast=EFFECT.get();
            if(cast!=null&&entity instanceof ProjectileEntity projectile
                    &&projectile.getOwner()==cast.player&&entity instanceof HomingProjectile homing) {
                if(cast.moonWeaver)((SpiderSpellLink.WebProjectile)projectile).ssca$weave().enable(cast.weave.id);
                if(cast.projectileSpeed!=1||cast.projectileRange!=1)homing.ssca$flight(cast.projectileSpeed,cast.range);
                if(cast.target!=null)homing.ssca$home(cast.target,cast.range);
            }
            if(entity instanceof SpiderSpellLink.WebProjectile projectile)projectile.ssca$weave().loaded(world,entity.getUuid());
        });
    }
    /** 由原版投射体混入提供；只给本次带标签的法术实例启用。 */
    public interface HomingProjectile {
        void ssca$home(LivingEntity target,double range);
        Vec3d ssca$guidedVelocity(Vec3d ordinary);
        boolean ssca$isHoming();
        boolean ssca$hasRangeLimit();
        void ssca$flight(double speedMultiplier,double maximumRange);
        double ssca$flightSpeedMultiplier();
        void ssca$syncFlightVelocity();
    }
}
