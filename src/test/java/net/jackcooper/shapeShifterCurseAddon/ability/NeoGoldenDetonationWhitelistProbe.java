package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.spell.NeoFormCastingProbe;

import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import java.util.Map;

/** Existing melee marks must obey the current whitelist before either detonation damage path. */
public final class NeoGoldenDetonationWhitelistProbe {
    private static int checks;
    private static void check(boolean ok, String reason) {
        checks++; if (!ok) throw new AssertionError(reason);
    }
    private static void near(float actual, float expected, String reason) {
        check(Math.abs(actual-expected)<1e-4, reason+": "+actual+" != "+expected);
    }
    private static CowEntity target(ServerPlayerEntity p, double offset) {
        // Model the reentrant hit notification observed in the real dual-client capture.
        // Mock-player GameTests do not consistently dispatch Apoli's player hit callback.
        var cow=new CowEntity(EntityType.COW,p.getServerWorld()) {
            @Override public boolean damage(net.minecraft.entity.damage.DamageSource source,float amount) {
                boolean accepted=super.damage(source,amount);
                if(accepted&&source.getAttacker()==p)GoldenSandstormErosionBrand.onPlayerAttack(p,this);
                return accepted;
            }
        };
        cow.setAiDisabled(true);cow.setNoGravity(true);
        cow.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);cow.setHealth(100);
        cow.setPosition(p.getX()+offset,p.getY(),p.getZ());
        check(p.getServerWorld().spawnEntity(cow),"Actual target spawned");return cow;
    }
    private static void allowNextLayer(ServerPlayerEntity p, CowEntity target) throws Exception {
        var field=GoldenSandstormErosionBrand.class.getDeclaredField("ACTIVE_BRANDS");field.setAccessible(true);
        if (!(field.get(null) instanceof Map<?, ?> owners)
                || !(owners.get(p.getUuid()) instanceof Map<?, ?> playerBrands)) {
            throw new IllegalStateException("Missing erosion brand map for the test player");
        }
        Object state=playerBrands.get(target.getUuid());
        if (state==null) throw new IllegalStateException("Missing erosion brand state for the test target");
        var last=state.getClass().getDeclaredField("lastStackTick");last.setAccessible(true);
        last.setLong(state,p.getWorld().getTime()-40);
    }
    public static void start(TestContext t) {
        t.waitAndRun(70,()->{try{
            var p=NeoFormCastingProbe.player(t,FormIdentifiers.GOLDEN_SANDSTORM_SP,0);
            net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.applyPower(p,
                    new net.minecraft.util.Identifier("my_addon:form_golden_sandstorm_sp_erosion_brand_hit"),
                    new net.minecraft.util.Identifier("ssc_addon:detonation_probe"));
            p.setHealth(10);var friendly=target(p,2);var hostile=target(p,4);
            GoldenSandstormErosionBrand.onPlayerAttack(p,friendly);
            check("yellow".equals(GoldenSandstormErosionBrand.getBrandColor(p.getUuid(),friendly.getUuid())),"Real public melee path creates a one-layer mark");
            p.addCommandTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX+friendly.getUuid());
            check(WhitelistUtils.isProtected(p,friendly),"Current custom whitelist protects a previously marked target");
            var result=GoldenSandstormErosionBrand.detonateAll(p);
            check(result[0]==0&&result[1]==0,"Protected one-layer mark contributes no settlement");
            near(friendly.getHealth(),100,"Protected one-layer target takes no damage");near(p.getHealth(),10,"Protected-only attempt provides no healing");
            check(!GoldenSandstormDetonate.execute(p),"Protected-only active skill follows its failed/no-cooldown result");
            check("yellow".equals(GoldenSandstormErosionBrand.getBrandColor(p.getUuid(),friendly.getUuid())),"Skipped mark stays unchanged");
            for(int i=0;i<2;i++){allowNextLayer(p,friendly);GoldenSandstormErosionBrand.onPlayerAttack(p,friendly);}
            check("red".equals(GoldenSandstormErosionBrand.getBrandColor(p.getUuid(),friendly.getUuid())),"Melee still allows protected targets to accumulate three layers");
            result=GoldenSandstormErosionBrand.detonateAll(p);
            check(result[0]==0&&result[1]==0,"Protected three-layer mark is skipped before the passive burst");
            near(friendly.getHealth(),100,"Protected three-layer target takes neither burst nor detonation damage");near(p.getHealth(),10,"Skipped full layers grant no burst or detonation healing");
            GoldenSandstormErosionBrand.onPlayerAttack(p,hostile);result=GoldenSandstormErosionBrand.detonateAll(p);
            check(result[0]==1&&result[1]==1,"Mixed friendly and hostile marks count only the hostile settlement");
            near(hostile.getHealth(),80,"Hostile target retains twenty-percent current-health damage");near(p.getHealth(),12,"Hostile settlement retains missing-health healing");
            near(friendly.getHealth(),100,"Mixed settlement leaves friendly health intact");
            check("red".equals(GoldenSandstormErosionBrand.getBrandColor(p.getUuid(),friendly.getUuid())),"Mixed settlement preserves the friendly mark rather than making it green");
            WhitelistUtils.removeMobFromWhitelist(p,friendly.getUuid());
            allowNextLayer(p,friendly);p.age+=20;
            result=GoldenSandstormErosionBrand.detonateAll(p);
            check(result[0]==1&&result[1]==3,"Removing protection makes the existing full-layer mark eligible again");
            near(friendly.getHealth(),64,"Unprotected full layers retain ordered burst and detonation damage");
            near(p.getHealth(),15.2f,"Full-layer settlement grants one burst heal and one missing-health heal");
            check("green".equals(GoldenSandstormErosionBrand.getBrandColor(p.getUuid(),friendly.getUuid())),"Accepted full-layer settlement still enters green cooldown");
            GoldenSandstormErosionBrand.clearPlayer(p);friendly.discard();hostile.discard();
            System.out.println("GOLDEN_DETONATION_WHITELIST_PASS "+checks+" checks");t.complete();
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}
