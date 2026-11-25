package net.jackcooper.shapeShifterCurseAddon.ability.snowfox;

import io.github.apace100.apoli.component.PowerHolderComponent;
import net.jackcooper.shapeShifterCurseAddon.SpUpgradeItem;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionComponent;
import net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionManager;
import net.jackcooper.shapeShifterCurseAddon.spell.NeoFormCastingProbe;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;

public final class SnowFoxWorldProbe {
    private static int checks;
    private static void check(boolean ok,String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }

    public static void start(TestContext test) {
        test.waitAndRun(65,() -> {
            try {
                var p=NeoFormCastingProbe.player(test,FormIdentifiers.UPGRADE_SNOW_FOX,0);
                net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.setForm(p,
                        RegPlayerForms.getPlayerForm(FormIdentifiers.UPGRADE_SNOW_FOX));
                p.age=100;
                EvolutionManager.tickPlayer(p);
                var progress=EvolutionManager.get(p);
                check("snow_fox".equals(progress.getRoute()),"Form enters the route");
                check(SpUpgradeItem.getEvolutionTarget(FormIdentifiers.UPGRADE_SNOW_FOX)
                        .equals(FormIdentifiers.SNOW_FOX_FROSTSPINE),"Moon ring endpoint");
                var state=SnowFoxMotion.STATE.get(p);
                state.serverTick();
                var source=new Identifier("my_addon","snow_fox_talents");
                check(PowerHolderComponent.KEY.get(p).getPowersFromSource(source).isEmpty(),"No unearned native powers");
                var nodes=java.util.List.of("running_jumps","air_control","ground_rebound","falling_strike","snowball_throw","snowball_freeze");
                progress.setExp(EvolutionComponent.levelToTotalExp(30)*2);
                EvolutionManager.tickPlayer(p);
                check(progress.getPoints()==6,"Level 30 has six spendable points");
                for(String node:nodes) {
                    check(!EvolutionManager.tryUnlock(p,"super_dive"),"Cannot skip prerequisite "+node);
                    check(EvolutionManager.tryUnlock(p,node),"Unlock "+node);
                }
                state.serverTick();
                check(PowerHolderComponent.KEY.get(p).getPowersFromSource(source).size()==12,"All 12 original powers granted");
                check(!EvolutionManager.tryUnlock(p,"rolling_charge"),"No seventh point at 30");
                progress.setExp(EvolutionComponent.levelToTotalExp(40)*2);
                EvolutionManager.tickPlayer(p);
                check(EvolutionManager.tryUnlock(p,"rolling_charge") && !EvolutionManager.tryUnlock(p,"super_dive"),"First active at 40");
                progress.setExp(EvolutionComponent.levelToTotalExp(45)*2);
                EvolutionManager.tickPlayer(p);
                check(EvolutionManager.tryUnlock(p,"super_dive") && !EvolutionManager.canUpgradeFoxEvolve(p),"Second at 45, endpoints locked");
                progress.setExp(EvolutionComponent.levelToTotalExp(50)*2);
                EvolutionManager.tickPlayer(p);
                check(EvolutionManager.canUpgradeFoxEvolve(p),"Both branches at 50");

                var w=p.getServerWorld();
                BlockPos base=BlockPos.ofFloored(p.getPos()).up(32);
                Vec3d feet=Vec3d.ofBottomCenter(base);
                for (BlockPos b:BlockPos.iterate(base.add(-9,-1,-10),base.add(11,-1,45)))
                    w.setBlockState(b,Blocks.STONE.getDefaultState());
                p.refreshPositionAndAngles(feet.x,feet.y,feet.z,0,0);
                p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
                p.getAbilities().invulnerable=false;
                state.start(p,true);
                check(state.phase()==SnowFoxState.Phase.SHELL && state.mass()==10,"Initial shell");
                var shells=w.getEntitiesByClass(net.jackcooper.shapeShifterCurseAddon.entity.SnowFoxShellEntity.class,
                        p.getBoundingBox().expand(3),e->true);
                check(shells.size()==1,"One targetable outer shell");
                var hitBox=shells.get(0).getBoundingBox();
                check(Math.abs(hitBox.maxX-hitBox.minX-1.5)<.001,"Initial shell hitbox spans its visible diameter");
                float hp=p.getHealth();
                var attacker=cow(p,feet.add(3,0,0));
                check(shells.get(0).damage(p.getDamageSources().mobAttack(attacker),4) && state.mass()==6,"Hit on outer shell costs four mass");
                attacker.discard();
                check(state.absorb(p,6) && state.mass()==0 && state.phase()==SnowFoxState.Phase.SHELL,"Zero survives");
                p.damage(p.getDamageSources().outOfWorld(),11);
                check(state.phase()==SnowFoxState.Phase.RECOVERY && p.getHealth()==hp,"Actual player hit breaks shell without overflow");
                check(shells.get(0).isRemoved(),"Broken shell stops intercepting hits");
                check(SkillCooldowns.remaining(p,SnowFoxMotion.ROLL.toString())==240,"Cooldown begins on shell break");

                var roll=new SnowFoxRoll(p);
                roll.steer(0,1);
                Vec3d at=feet;
                for (int i=0;i<100;i++) {
                    var step=roll.advance(p,at);check(step.contact()==null,"Flat ground clears shell");
                    check(step.feet().distanceTo(at)<=.4001,"Speed capped");
                    if(i==0)check(Math.abs(step.feet().z-at.z-.04)<1e-5,"Initial acceleration");
                    at=step.feet();
                }
                check(roll.mass==50 && SnowFoxRoll.radius(roll.mass)==2.5,"Grows to diameter 5");
                check(Math.abs(at.z-feet.z)>28,"Rolls past growth cap");
                check(SnowFoxRoll.radius(0)==.75 && SnowFoxRoll.radius(10)==.75,"Zero through ten share one shell size");
                var skin=new SnowFoxRoll(p);
                skin.mass=50;
                var fullFirst=skin.advance(p,feet);
                check(Math.abs(fullFirst.feet().z-feet.z-.02)<1e-5,"Full shell acceleration is eight blocks per second squared");
                Vec3d impact=feet.add(6,0,16);
                var direct=cow(p,impact);
                var insideRange=cow(p,impact.add(0,0,2.8));
                var beyond=cow(p,impact.add(0,0,3.9));
                SnowFoxRoll.burst(p,impact.subtract(1,.75,0),10,impact,direct);
                check(direct.getHealth()==4 && insideRange.getHealth()==4 && beyond.getHealth()==10,"Six base damage in three blocks of impact");
                direct.discard(); insideRange.discard(); beyond.discard();
                var fullTarget=cow(p,impact);
                fullTarget.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(40);
                fullTarget.setHealth(40);
                SnowFoxRoll.burst(p,impact.subtract(2.5,2.5,0),50,impact,fullTarget);
                check(fullTarget.getHealth()==22,"Full shell deals exactly 18");
                fullTarget.discard();
                for(BlockPos b:BlockPos.iterate(base.add(4,0,4),base.add(8,0,8))) w.setBlockState(b,Blocks.STONE.getDefaultState());
                for(BlockPos b:BlockPos.iterate(base.add(4,0,13),base.add(8,2,13))) w.setBlockState(b,Blocks.STONE.getDefaultState());
                var stepRoll=new SnowFoxRoll(p);
                var onStep=feet.add(6,0,1);
                boolean climbed=false, hitWall=false;
                for(int i=0;i<65;i++) {
                    var step=stepRoll.advance(p,onStep);
                    if(step.contact()!=null) {
                        hitWall=step.contact().z>=base.getZ()+12.99;
                        if(!hitWall) throw new AssertionError("Early shell contact "+step.contact().subtract(feet)+" at "+onStep.subtract(feet));
                        break;
                    }
                    onStep=step.feet();
                    climbed|=onStep.y>=feet.y+.99;
                }
                check(climbed && hitWall,"Shell crosses a single step then breaks on tall wall, climbed="+climbed+", at="+onStep.subtract(feet));
                var close=cow(p,feet.add(1,0,0));
                var edge=cow(p,feet.add(0,0,5.8));
                var blocked=cow(p,feet.add(-4,0,0));
                for(BlockPos b:BlockPos.iterate(base.add(-2,0,-4),base.add(-2,1,4)))w.setBlockState(b,Blocks.STONE.getDefaultState());
                var wave=new SnowFoxWave(p,feet);
                check(close.getHealth()==2 && edge.getHealth()==10,"Immediate eight-point shock only nearby");
                for(int i=1;i<=20;i++) {
                    wave.tick(p);
                    if(i==12)check(edge.getHealth()==10,"Outer target waits for ice");
                }
                check(close.getHealth()==0 && edge.getHealth()==4 && blocked.getHealth()==10,"Ice reaches outer target once and stops at tall wall");
                System.out.println("SNOW_FOX_WORLD_PASS checks="+checks);
                test.complete();
            } catch(Exception e) { throw new AssertionError(e); }
        });
    }
    private static CowEntity cow(ServerPlayerEntity p,Vec3d pos) {
        var t=EntityType.COW.create(p.getServerWorld());
        t.setPosition(pos);t.setAiDisabled(true);p.getServerWorld().spawnEntity(t);return t;
    }
}
