package net.jackcooper.shapeShifterCurseAddon.spell;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.enchantment.RegAddonEnchantments;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneCastContext;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneModifiers;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;

/** Uses real survival players, the production damage pipeline and the authoritative casting channel. */
public final class SpellBacklashWorldProbe {
    private static int checks;
    private static void check(boolean ok,String why) { checks++; if(!ok)throw new AssertionError(why); }
    private static void near(double actual,double expected,String why) { check(Math.abs(actual-expected)<1e-4,why+": "+actual+" != "+expected); }
    private static ServerPlayerEntity player(TestContext t) {
        var p=new ServerPlayerEntity(t.getWorld().getServer(),t.getWorld(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"backlash-probe"));
        p.getServer().getPlayerManager().onPlayerConnect(new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND),p);
        p.changeGameMode(GameMode.SURVIVAL);p.setNoGravity(true);p.getAbilities().invulnerable=false;
        try {
            var join=ServerPlayerEntity.class.getDeclaredField("joinInvulnerabilityTicks");join.setAccessible(true);join.setInt(p,0);
            var field=net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration.class.getDeclaredField("server");field.setAccessible(true);
            var state=(net.jackcooper.shapeShifterCurseAddon.balance.ServerBalanceState)field.get(null);
            state.buildSendSequence(p.getUuid(),"{}");state.playerAcked(p.getUuid(),state.revision());
        } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
        RegPlayerFormComponent.PLAYER_FORM.get(p).nowForm=RegPlayerForms.playerForms.get(new Identifier("shape-shifter-curse:original_shifter"));
        NeoRuntime.refreshForm(p);p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);reset(p);
        return p;
    }
    private static void reset(ServerPlayerEntity p) {
        SpellChannelManager.cancelSelf(p);p.clearStatusEffects();p.setFireTicks(0);p.timeUntilRegen=0;
        p.setAbsorptionAmount(0);p.setHealth(p.getMaxHealth());NeoRuntime.data(p).burden=0;
        for(var slot:EquipmentSlot.values())p.equipStack(slot,ItemStack.EMPTY);
    }
    private static SpellBacklash.Pending payload(ServerPlayerEntity p,String spell,float power,int level) {
        var s=SpellRegistry.get(spell);return new SpellBacklash.Pending(s.backlashEffects(p,power,false,level),s.getRarity(level));
    }
    private static void fail(ServerPlayerEntity p,String id,int level,float power,boolean solo) {
        double safety=NeoRuntime.profile(p).safety();NeoRuntime.data(p).burden=safety*2.2;
        // Failure is scheduled randomly within preparation. Pick a real seed whose scheduled tick is zero.
        int duration=SpellRegistry.get(id).getCastingProfile(p,level,solo).ticks();
        check(duration>0,"Failure fixture has a preparation window");
        boolean found=false;
        for(int seed=0;seed<10000;seed++) {
            p.getRandom().setSeed(seed);var random=new java.util.Random(p.getRandom().nextLong());random.nextDouble();
            if(random.nextInt(duration+1)==0){p.getRandom().setSeed(seed);found=true;break;}
        }
        check(found,"Deterministic real failure draw found");
        int[] releases={0},cooldowns={0},uses={0};
        check(SpellChannelManager.start(p,SpellRegistry.get(id),ScrollData.create(id,level),level,solo,9311,1000,80,
                ()->true,at->releases[0]++,cd->cooldowns[0]++,()->uses[0]++,RuneModifiers.NONE,power),"Accepted failing channel "+id);
        check(!SpellChannelManager.isCasting(p)&&NeoLegacyCasting.active(p)==null,"Failure clears both channel owners");
        check(releases[0]==0&&cooldowns[0]==1&&uses[0]==0,"Exactly one cooldown, no released effect or scroll use");
    }
    public static void start(TestContext t) {
        t.waitAndRun(70,()->{try { run(t); }catch(Exception e){throw new RuntimeException(e);}});
    }
    private static void run(TestContext t) throws Exception {
        var p=player(t);
        t.getWorld().getServer().setPvpEnabled(false);
        var damaging=Set.of("fire_bolt","flame_nova","frost_spike","ice_barrage","frost_nova","moonlight_arrow","meteor","void_devour","explosion","death_finale","corrupt_mist","summon_lunar_spirit");
        var negative=Set.of("frost_nova","curse_mark","dread_whisper","void_erosion","void_devour","corrupt_mist","beep_sheep");
        var burning=Set.of("fire_bolt","flame_nova","meteor","explosion");
        for(var spell:SpellRegistry.all())for(int level=1;level<=spell.getMaxLevel();level++) {
            var e=spell.backlashEffects(p,20,false,level);String id=spell.getId().getPath();
            check((e.normalHit()>0)==damaging.contains(id),"Damage classification "+id+" L"+level);
            check((!e.debuffs().isEmpty())==negative.contains(id),"Negative effect classification "+id+" L"+level);
            check((e.fireTicks()>0)==burning.contains(id),"Burn classification "+id+" L"+level);
        }
        var shot=payload(p,"moonlight_arrow",20,3);shot.apply(p,.25);
        near(p.getHealth(),95.1,"Blue spell at 25 percent excess loses 4.9 health even with PvP disabled");
        p.timeUntilRegen=0;shot.apply(p,.25);near(p.getHealth(),95.1,"One cast cannot backlash twice");
        reset(p);payload(p,"moonlight_arrow",20,3).apply(p,0);near(p.getHealth(),100,"Safe state cannot backlash");
        reset(p);var helmet=new ItemStack(Items.DIAMOND_HELMET);helmet.addEnchantment(RegAddonEnchantments.SPELL_RESISTANCE,5);
        p.equipStack(EquipmentSlot.HEAD,helmet);payload(p,"moonlight_arrow",20,3).apply(p,.25);
        near(p.getHealth(),98.285,"Real spell resistance reduces backlash by 65 percent");
        reset(p);p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,200,0));
        payload(p,"moonlight_arrow",20,3).apply(p,.25);near(p.getHealth(),96.08,"Resistance status still reduces magic damage");
        reset(p);p.setAbsorptionAmount(10);payload(p,"moonlight_arrow",20,3).apply(p,.25);
        near(p.getHealth(),100,"Absorption protects health");near(p.getAbsorptionAmount(),5.1,"Absorption pays reflected damage");
        reset(p);p.addStatusEffect(new StatusEffectInstance(SscAddon.CURSE_MARK,200,4));
        payload(p,"moonlight_arrow",20,3).apply(p,.25);near(p.getHealth(),92.16,"Existing vulnerability is retained");
        reset(p);payload(p,"frost_nova",20,3).apply(p,.25);
        near(p.getHealth(),95.1,"Damage settles once before debuffs");
        check(p.getStatusEffect(StatusEffects.SLOWNESS).getDuration()==53&&p.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier()==1,"Frost slow keeps level and scales duration");
        check(p.getStatusEffect(SscAddon.FROST_SHATTER).getDuration()==53,"Frost shatter reflects too");
        reset(p);payload(p,"curse_mark",999,3).apply(p,.25);
        near(p.getHealth(),100,"Pure curse does not reflect a misleading power value");
        check(p.getStatusEffect(SscAddon.CURSE_MARK).getDuration()==126&&p.getStatusEffect(SscAddon.CURSE_MARK).getAmplifier()==2,"Mark level and duration");
        reset(p);payload(p,"fire_bolt",20,3).apply(p,.25);check(p.getFireTicks()==53,"Burning uses tick precision");
        reset(p);p.setFireTicks(200);payload(p,"fire_bolt",20,3).apply(p,.25);check(p.getFireTicks()==200,"Backlash cannot shorten existing burning");
        reset(p);payload(p,"summon_lunar_spirit",999,3).apply(p,.25);
        near(p.getHealth(),98.8975,"Summon uses one strongest normal bolt, not count, lifetime or generic power");
        check(p.getStatusEffects().isEmpty()&&p.getFireTicks()<=0,"Summon random secondary debuffs are not reflected");
        reset(p);payload(p,"corrupt_mist",999,5).apply(p,.25);near(p.getHealth(),99,"Poison uses one damage pulse");
        check(p.getStatusEffect(StatusEffects.POISON).getDuration()==58&&p.getStatusEffect(StatusEffects.POISON).getAmplifier()==1,"Poison duration and level");
        check(p.getStatusEffect(StatusEffects.SLOWNESS).getDuration()==29&&p.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier()==1,"Mist slow has its own duration");
        for(String id:new String[]{"lunar_mend","frost_armor","lunar_veil","space_stride","domain"}){
            reset(p);payload(p,id,999,1).apply(p,2);near(p.getHealth(),100,"Non-damage payload "+id);check(p.getStatusEffects().isEmpty(),"No invented harmful effect "+id);
        }
        int[] values=new int[RuneModifiers.Stat.values().length];values[RuneModifiers.Stat.NEGATIVE.ordinal()]=60;
        reset(p);var boosted=RuneCastContext.with(new RuneModifiers(values),()->payload(p,"frost_nova",20,3));
        boosted.apply(p,.25);check(p.getStatusEffect(StatusEffects.SLOWNESS).getDuration()==84,"Rune duration captured before scope exits");
        reset(p);fail(p,"fire_bolt",3,20,false);
        near(p.getHealth(),76.48,"Actual failed-cast C is used, not initial C plus the full 1000 cost");
        check(p.getFireTicks()==100,"Actual production failure reflects fire");
        double after=p.getHealth();SpellChannelManager.cancelSelf(p);near(p.getHealth(),after,"Cleanup does not repeat backlash");
        reset(p);fail(p,"frost_nova",1,4,true);near(p.getHealth(),95.68,"Standalone scroll channel uses its supplied normal damage");
        reset(p);int[] releases={0};
        check(SpellChannelManager.start(p,SpellRegistry.get("fire_bolt"),ScrollData.create("fire_bolt"),1,false,12,1,20,()->true,at->releases[0]++,cd->{}),"Safe channel starts");
        SpellChannelManager.cancelSelf(p);near(p.getHealth(),100,"Voluntary cancellation has no backlash");check(p.getFireTicks()<=0,"Cancellation never burns caster");
        reset(p);NeoRuntime.data(p).burden=NeoRuntime.profile(p).safety()*2.2;
        SpellChannelManager.start(p,SpellRegistry.get("fire_bolt"),ScrollData.create("fire_bolt"),1,false,13,1,20,()->false,at->releases[0]++,cd->{});
        near(p.getHealth(),100,"Invalid source has no backlash even while over threshold");
        var doomed=player(t);doomed.setHealth(1);payload(doomed,"moonlight_arrow",20,3).apply(doomed,.25);
        check(!doomed.isAlive(),"Backlash can kill the caster");
        var sheep=player(t);var original=RegPlayerFormComponent.PLAYER_FORM.get(sheep).nowForm;
        fail(sheep,"beep_sheep",1,0,false);
        check(sheep.hasStatusEffect(SscAddon.SHEEP_FORM),"Actual failed sheep spell transforms its caster");
        check(RegPlayerFormComponent.PLAYER_FORM.get(sheep).nowForm!=original,"Sheep uses the existing form swap");
        check(net.jackcooper.shapeShifterCurseAddon.util.AbilityUseGuard.blocked(sheep),"Sheep retains action restrictions");
        sheep.removeStatusEffect(SscAddon.SHEEP_FORM);
        check(RegPlayerFormComponent.PLAYER_FORM.get(sheep).nowForm==original,"Sheep cleanup restores original form");
        Files.writeString(Path.of("backlash-result.json"),NeoState.JSON.toJson(Map.of("passed",true,"checks",checks,"world","disposable survival GameTest","original_instance_gameplay_verified",false)));
        System.out.println("Spell backlash GameTest: "+checks+" checks passed.");t.complete();
    }
}
