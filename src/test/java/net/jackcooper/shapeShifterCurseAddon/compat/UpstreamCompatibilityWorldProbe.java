package net.jackcooper.shapeShifterCurseAddon.compat;

import com.google.gson.*;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.onixary.shapeShifterCurseFabric.integration.origins.component.OriginComponent;
import net.onixary.shapeShifterCurseFabric.integration.origins.registry.ModComponents;
import net.onixary.shapeShifterCurseFabric.perk.*;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.*;

/** 在真实World、已加载Origins/Apoli及Mixin中模拟旧能力存档与原版同形态登录路径。 */
public final class UpstreamCompatibilityWorldProbe {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static ServerPlayerEntity player(TestContext t,Identifier id){
        var p=t.createMockCreativeServerPlayerInWorld();p.changeGameMode(GameMode.SURVIVAL);p.setNoGravity(true);
        FormUtils._setForm(p,RegPlayerForms.getPlayerForm(id));return p;
    }
    private static JsonObject json(String name)throws Exception{
        try(var stream=UpstreamCompatibilityWorldProbe.class.getClassLoader().getResourceAsStream("data/my_addon/powers/"+name+".json")){
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    public static void start(TestContext t){t.waitAndRun(70,()->{try{
        // This headless GameTest launcher does not run SSC's client config setup.
        if (io.github.apace100.apoli.Apoli.config == null)
            io.github.apace100.apoli.Apoli.config = new io.github.apace100.apoli.util.ApoliConfig();
        var world=t.getWorld();world.setTimeOfDay(6000);
        var undead=PowerTypeRegistry.get(new Identifier("shape-shifter-curse","undead_group"));
        for(var id:List.of(FormIdentifiers.ANUBIS_WOLF_SP,FormIdentifiers.GOLDEN_SANDSTORM_SP)){
            var p=player(t,id);var holder=PowerHolderComponent.KEY.get(p);
            var form=FormUtils.getPlayerForm(p);var source=form.getFormLayer().getRight();
            check(p.getGroup()==EntityGroup.UNDEAD,"亡灵分组实际生效："+id);
            var resource=PowerHolderComponent.getPowers(p,VariableIntPower.class).stream().findFirst().orElseThrow();
            resource.setValue(7);int value=resource.getValue();
            var timer=PowerHolderComponent.getPowers(p,CooldownPower.class).stream().findFirst().orElseThrow();
            timer.use();var timerTag=timer.toTag().copy();
            p.setAir(133);float health=p.getHealth();int count=holder.getPowers().size();
            // 仅让保存的Origins仍指向同一实例，模拟旧Apoli数据缺少新加基础能力。
            OriginComponent origins=ModComponents.ORIGIN.get(p);
            NbtCompound originSave=new NbtCompound();origins.writeToNbt(originSave);
            holder.removePower(undead,source);origins.readFromNbt(originSave);
            check(!holder.hasPower(undead,source),"旧能力存档确实缺少亡灵分组："+id);
            FormUtils._loadForm(p,form,false);
            check(holder.hasPower(undead,source)&&p.getGroup()==EntityGroup.UNDEAD,"同形态非强制重载补回能力："+id);
            check(holder.getPower(resource.getType())==resource&&resource.getValue()==value,"保留资源能力实例及数值："+id);
            check(holder.getPower(timer.getType())==timer&&timer.toTag().equals(timerTag),"保留正在运行的冷却："+id);
            check(p.getAir()==133&&p.getHealth()==health,"保留气量和生命值："+id);
            var foreign=new Identifier("compat_test","external_item");holder.addPower(undead,foreign);
            PerkUtils.loadAllPerk(p,form.getPerkTreeID());
            check(holder.hasPower(undead,foreign),"不删除其它来源："+id);
            for(int i=0;i<3;i++)PerkUtils.loadAllPerk(p,form.getPerkTreeID());
            check(holder.getPowers().size()==count,"重复修复不叠加能力："+id);
            holder.removePower(undead,source);OriginPowerCompatibility.restore(p);
            check(holder.hasPower(undead,source),"重生/成功reload使用的恢复入口补齐能力："+id);
            p.setHealth(2);
            net.minecraft.entity.effect.StatusEffects.INSTANT_HEALTH.applyInstantEffect(null,null,p,0,1);
            check(p.getHealth()==2,"保留原有瞬间治疗免疫："+id);
            for(int amplifier=0;amplifier<=1;amplifier++)for(double proximity:List.of(1.0,.5)){
                p.setHealth(2);
                float expected=Math.min(p.getMaxHealth(),2+(float)(proximity*(-6<<amplifier)+.5)*-.67f);
                net.minecraft.entity.effect.StatusEffects.INSTANT_DAMAGE.applyInstantEffect(null,null,p,amplifier,proximity);
                check(Math.abs(p.getHealth()-expected)<.0001,"瞬间伤害保留旧治疗量/等级/溅射衰减："+id+" "+amplifier+" "+proximity+" actual="+p.getHealth()+" expected="+expected+" undead="+p.isUndead()+" form="+FormUtils.getPlayerForm(p).getFormID()+" powers="+PowerHolderComponent.getPowers(p,net.onixary.shapeShifterCurseFabric.additional_power.ModifyInstantDamagePower.class).stream().map(v->v.getType().getIdentifier()+"="+v.ApplyMulScale(1)).toList());
            }
            check(p.isUndead(),"药水兼容不能取消真正的亡灵分类："+id);
            p.setHealth(2);
            net.minecraft.entity.effect.StatusEffects.INSTANT_DAMAGE.applyUpdateEffect(p,1);
            check(Math.abs(p.getHealth()-Math.min(p.getMaxHealth(),2+12*.67f))<.0001,"直接状态效果入口沿用旧治疗量："+id);
            var reversal=PowerTypeRegistry.get(new Identifier("shape-shifter-curse","form_anubis_wolf_3_reverse_instant_damage"));
            holder.removePower(reversal,source);p.setHealth(2);
            net.minecraft.entity.effect.StatusEffects.INSTANT_DAMAGE.applyInstantEffect(null,null,p,0,1);
            check(Math.abs(p.getHealth()-Math.min(p.getMaxHealth(),6))<.0001,"没有旧反转能力时遵守原版亡灵药水："+id);
            holder.addPower(reversal,source);
        }
        for(var id:List.of(FormIdentifiers.OCELOT_SP,FormIdentifiers.OCELOT_NOVA)){
            var p=player(t,id);var holder=PowerHolderComponent.KEY.get(p);
            var form=FormUtils.getPlayerForm(p);var source=form.getFormLayer().getRight();
            var stale=id.equals(FormIdentifiers.OCELOT_SP)?WindSpiritPerks.LONG_POUNCE:NovaPerks.LONG_POUNCE;
            holder.addPower(PowerTypeRegistry.get(stale),source);
            PlayerFormComponent.COMPONENT.get(p).formPerkMap.put(form.getPerkTreeID(),new ArrayList<>(List.of(
                RegPerks.P_OcelotMetabolism_1,RegPerks.P_OcelotMetabolism_2,SscPerkIds.OCELOT_LONG_POUNCE)));
            check(!WindSpiritPerks.hasLongPounce(p)&&!NovaPerks.hasLongPounce(p),"旧存档残留不能继续提供远距扑袭："+id);
            p.totalExperience=1234;FormUtils._loadForm(p,form,false);
            check(!holder.hasPower(PowerTypeRegistry.get(stale),source),"清理停用能力的本形态来源："+id);
            check(!PlayerFormComponent.COMPONENT.get(p).formPerkMap.get(form.getPerkTreeID()).contains(SscPerkIds.OCELOT_LONG_POUNCE),"不恢复上游删除的节点："+id);
            check((id.equals(FormIdentifiers.OCELOT_SP)?WindSpiritPerks.hungerDiscount(p):NovaPerks.hungerDiscount(p))==25,"其它已购买高级Perk仍然生效："+id);
            check(p.totalExperience==1234,"不重复扣经验："+id);
        }
        for(var id:List.of(FormIdentifiers.AXOLOTL_SP,FormIdentifiers.AXOLOTL_FLUORESCENT,FormIdentifiers.AXOLOTL_ALING,FormIdentifiers.UPGRADE_AXOLOTL)){
            var p=player(t,id);
            var data=json(id.equals(FormIdentifiers.UPGRADE_AXOLOTL)?"perk_upgrade_axolotl_tidal_pull":"perk_axolotl_sp_tidal_pull").getAsJsonObject("charge");
            p.setAir(20);ApoliDataTypes.ENTITY_ACTION.read(data.get("start_action")).accept(p);
            check(p.getAir()==15,"真实扣费动作只消耗五点："+id);
            check(data.getAsJsonObject("key").get("key").getAsString().equals("key.shape-shifter-curse.active_skill_3"),"保留技能键3："+id);
        }
        var p=player(t,FormIdentifiers.AXOLOTL_SP);var start=json("perk_axolotl_sp_tidal_pull").getAsJsonObject("charge").get("start_condition");
        p.setAir(19);check(!ApoliDataTypes.ENTITY_CONDITION.read(start).test(p),"十九点湿润度不能起手");
        p.setAir(20);check(ApoliDataTypes.ENTITY_CONDITION.read(start).test(p),"二十点湿润度可以起手");
        check(json("perk_snow_fox_sp_frost_dive").get("speed").getAsDouble()==1.6,"俯冲速度对齐为一点六");
        var target=t.spawnEntity(EntityType.PIG,2,2,2);p.setPosition(target.getPos().add(1,0,0));
        var husk=t.spawnEntity(EntityType.HUSK,3,2,3);husk.setHealth(2);
        net.minecraft.entity.effect.StatusEffects.INSTANT_DAMAGE.applyInstantEffect(null,null,husk,0,1);
        check(husk.getHealth()==6,"普通亡灵生物的原版药水行为不受影响");
        for(int i=0;i<8;i++)check(BatEchoFeedback.play(p,target,8,.35f),"本轮允许有界振动反馈："+i);
        check(!BatEchoFeedback.play(p,target,8,.35f),"同一轮第九次反馈不发包");
        check(!BatEchoFeedback.play(p,p,8,.35f),"不给自身发送回声轨迹");
        for(var name:List.of("perk_bat_desmodus_echolocation","perk_bat_parasitic_fruit_echolocation")){
            var data=json(name);
            check(data.get("interval").getAsInt()==60,"保留三秒被动探测："+name);
            check(data.getAsJsonObject("condition").getAsJsonArray("conditions").size()==2,"保留夜间限制："+name);
            check(data.getAsJsonObject("entity_action").getAsJsonArray("actions").size()==2,"保留发光与虚弱两项结算："+name);
        }
        Files.writeString(Path.of("upstream-compatibility-result.json"),new Gson().toJson(Map.of("checks",checks,"real_server_world",true,"old_origin_save_repaired",true,"resources_and_cooldowns_retained",true)));
        System.out.println("SSC_UPSTREAM_COMPATIBILITY_WORLD_PASS checks="+checks);t.complete();
    }catch(Throwable e){e.printStackTrace();t.throwGameTestException(e.toString());}});}
}
