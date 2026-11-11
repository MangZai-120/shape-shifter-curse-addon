package net.jackcooper.shapeShifterCurseAddon.ability.fruit;

import java.util.*;

/** Public gameplay invariants, independent of Minecraft bootstrapping. */
public final class FruitLayerLedgerTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), host = UUID.randomUUID();
        var ledger = new FruitLayerLedger();
        ledger.add(host,a,FruitSelection.VINE,0,100,20,3);
        ledger.add(host,b,FruitSelection.BITTER,10,100,20,3);
        ledger.add(host,a,FruitSelection.BITTER,20,300,20,3);
        ledger.add(host,b,FruitSelection.ROTTEN,30,100,20,3);
        check(ledger.layers(host).stream().map(FruitLayerLedger.Layer::fruit).toList()
                .equals(List.of(FruitSelection.BITTER,FruitSelection.BITTER,FruitSelection.ROTTEN)), "Fourth mixed hit evicts exactly the oldest layer across casters");
        check(ledger.layers(host).get(0).expiresAt()==110 && ledger.layers(host).get(1).expiresAt()==320,
                "Each layer keeps its own lifetime without extension");
        check(!ledger.layers(host).get(2).rooted(49) && ledger.layers(host).get(2).rooted(50), "Independent rooting boundary");
        ledger.prune(110, ignored -> true);
        check(ledger.layers(host).size()==2 && ledger.layers(host).get(0).owner().equals(a), "Independent expiry removes only the expired layer");
        ledger.removeOwner(b);
        check(ledger.layers(host).size()==1 && ledger.layers(host).get(0).owner().equals(a), "Disconnect cleans only that caster");
        UUID x=UUID.randomUUID(),y=UUID.randomUUID(),z=UUID.randomUUID();
        ledger.add(x,a,FruitSelection.SOUR,111,100,20,3);
        ledger.add(x,b,FruitSelection.VINE,112,100,20,3);
        ledger.add(y,a,FruitSelection.SOUR,113,100,20,3);
        ledger.add(z,a,FruitSelection.SOUR,114,100,20,3);
        check(ledger.layers(host).isEmpty() && ledger.layers(x).size()==2, "Fourth independent host evicts oldest host for that caster");
        ledger.add(host,a,FruitSelection.BITTER,115,100,20,3);
        check(ledger.layers(x).size()==1 && ledger.layers(x).get(0).owner().equals(b), "Host budget preserves other caster's layers");
        var otherWorld = new FruitLayerLedger();
        otherWorld.add(host,b,FruitSelection.VINE,0,100,0,3);
        ledger.removeHost(host);
        check(otherWorld.layers(host).size()==1, "World ledgers never share hosts");
        var random = new Random(417);
        for(int i=0;i<1000;i++) {
            long now=i*2L;
            ledger.add(host,random.nextBoolean()?a:b,FruitSelection.values()[1+random.nextInt(4)],now,5+random.nextInt(50),20,3);
            var layers=ledger.layers(host);
            check(layers.size()<=3 && layers.stream().allMatch(l -> l.expiresAt()>now), "Shared total cap and expiration invariant");
            for(int j=1;j<layers.size();j++) check(layers.get(j-1).sequence()<layers.get(j).sequence(), "FIFO order is stable");
        }
        check(FruitSelection.fromId(-1)==FruitSelection.AUTO && FruitSelection.fromId(500)==FruitSelection.AUTO, "Old or invalid saves default to Automatic");
        var selected=FruitSelection.AUTO;
        for(var expected:List.of(FruitSelection.VINE,FruitSelection.BITTER,FruitSelection.ROTTEN,FruitSelection.SOUR,FruitSelection.AUTO)) {
            selected=selected.next();check(selected==expected,"Approved cycle order");
        }
        check(!FruitSelection.canRelease(9) && FruitSelection.canRelease(10), "Half-second minimum charge");
        for(int ticks=10;ticks<=30;ticks++) {
            check(Math.abs(FruitSelection.speed(ticks)-(0.5+0.9*(ticks-10)/20))<0.00001,"Linear range scaling");
        }
        check(FruitSelection.speed(0)==0.5f && Math.abs(FruitSelection.speed(500)-1.4f)<0.00001,"Charge saturates and waits for release");
        System.out.println("FRUIT_LEDGER_PASS checks="+checks);
    }
}
