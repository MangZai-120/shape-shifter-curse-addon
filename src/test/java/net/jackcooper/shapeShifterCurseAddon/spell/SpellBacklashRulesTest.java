package net.jackcooper.shapeShifterCurseAddon.spell;

public final class SpellBacklashRulesTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static void near(double actual, double expected, String why) { check(Math.abs(actual-expected)<1e-5, why+": "+actual+" != "+expected); }
    public static void main(String[] args) {
        double[] quarter = {4.5,4.7,4.9,5.1,5.3,5.5};
        for (int rarity=1;rarity<=6;rarity++) {
            near(SpellBacklashRules.damage(20,.25,rarity),quarter[rarity-1],"Agreed 25 percent anchor "+rarity);
            near(SpellBacklashRules.damage(20,.5,rarity),2*quarter[rarity-1],"Linear excess scaling");
            near(SpellBacklashRules.damage(0,2,rarity),0,"Non-damage spells have no minimum damage");
            near(SpellBacklashRules.damage(20,0,rarity),0,"Safe body has no backlash");
            near(SpellBacklashRules.damage(.01,.01,rarity),1,"Minimum is a floor, not an additive base");
            near(SpellBacklashRules.damage(5000,.25,rarity),40,"Large red-spell damage is capped");
            double previous=0;
            for (int step=1;step<=400;step++) {
                double r=step/100d,damage=SpellBacklashRules.damage(20,r,rarity);
                check(damage>=previous&&damage>=1&&damage<=40,"Monotonic bounded damage");previous=damage;
                int ticks=SpellBacklashRules.duration(200,r,rarity);
                check(ticks>=40&&ticks<=200,"Debuff duration stays at 20-100 percent");
            }
        }
        near(SpellBacklashRules.excess(125,100),.25,"Excess is relative to safety, not to current collapse");
        near(SpellBacklashRules.excess(100,100),0,"Threshold itself is safe");
        near(SpellBacklashRules.excess(50,100),0,"Below threshold is safe");
        near(SpellBacklashRules.excess(125,0),0,"Malformed safety cannot divide by zero");
        near(SpellBacklashRules.duration(200,.25,3),105,"Blue quarter-excess duration");
        near(SpellBacklashRules.duration(200,.5,3),130,"Previously agreed blue half-excess duration");
        near(SpellBacklashRules.duration(200,0,1),40,"Minimum duration");
        near(SpellBacklashRules.duration(200,3,1),200,"Maximum duration");
        near(SpellBacklashRules.damage(Double.NaN,.25,3),0,"Invalid damage rejected");
        System.out.println("Spell backlash rules: "+checks+" checks passed.");
    }
}
