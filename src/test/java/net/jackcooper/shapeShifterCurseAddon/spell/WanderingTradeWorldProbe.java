package net.jackcooper.shapeShifterCurseAddon.spell;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.poi.PointOfInterestTypes;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;

public final class WanderingTradeWorldProbe {
    private record Price(int level, int low, int high, int crystalLow, int crystalHigh, int stock) {}
    private static final EnumMap<SpellRarity, Price> PRICES = new EnumMap<>(SpellRarity.class);
    static {
        PRICES.put(SpellRarity.WHITE, new Price(1,2,2,0,0,2));
        PRICES.put(SpellRarity.GREEN, new Price(2,4,4,0,0,2));
        PRICES.put(SpellRarity.BLUE, new Price(3,7,9,0,0,2));
        PRICES.put(SpellRarity.PURPLE, new Price(4,14,18,2,2,2));
        PRICES.put(SpellRarity.ORANGE, new Price(5,21,27,4,4,1));
        PRICES.put(SpellRarity.RED, new Price(1,28,36,7,9,1));
    }
    private static void check(boolean ok,String why) { if (!ok) throw new AssertionError(why); }
    private static void ratio(int actual,int total,double expected,double tolerance) {
        check(Math.abs((double)actual/total-expected)<tolerance,actual+"/"+total+" vs "+expected);
    }
    private static String distribution(boolean librarian) {
        final int count=20_000;
        Random random=Random.create(83002189L);
        int[] counts=new int[3];
        int[] tiers=new int[SpellRarity.values().length];
        var spells=new EnumMap<SpellRarity,Set<String>>(SpellRarity.class);
        var emeraldPrices=new EnumMap<SpellRarity,Set<Integer>>(SpellRarity.class);
        var crystalPrices=new EnumMap<SpellRarity,Set<Integer>>(SpellRarity.class);
        int doubleRed=0;
        for(int i=0;i<count;i++) {
            TradeOfferList offers=new TradeOfferList();
            if (librarian) MerchantSpellTrades.addLibrarianOffers(offers, random);
            else WanderingSpellTrades.addOffers(offers, random);
            check(offers.size()<=2,"more than two scrolls");
            counts[offers.size()]++;
            Set<String> local=new HashSet<>();
            int reds=0;
            for(TradeOffer offer:offers) {
                var stack=offer.getSellItem();
                var spell=ScrollData.getSpell(stack);
                check(stack.isOf(SscAddon.MAGIC_SCROLL)&&stack.getCount()==1&&spell!=null&&!ArcaneAnalysis.isUnanalyzed(stack),"invalid merchandise");
                int level=ScrollData.getLevel(stack);
                var rarity=spell.getRarity(level);
                var p=PRICES.get(rarity);
                String id=spell.getId().getPath();
                check(p!=null&&p.level==level&&local.add(id),"invalid or duplicate "+id);
                tiers[rarity.ordinal()]++;
                if(rarity==SpellRarity.RED) reds++;
                spells.computeIfAbsent(rarity,k->new HashSet<>()).add(id);
                var first=offer.getOriginalFirstBuyItem();
                var second=offer.getSecondBuyItem();
                check(first.isOf(Items.EMERALD)&&first.getCount()>=p.low&&first.getCount()<=p.high,"emerald "+first);
                check((second.isEmpty()||second.isOf(RegCustomItem.MOONDUST_CRYSTAL_SHARD))&&second.getCount()>=p.crystalLow&&second.getCount()<=p.crystalHigh,"crystal "+second);
                emeraldPrices.computeIfAbsent(rarity,k->new HashSet<>()).add(first.getCount());
                crystalPrices.computeIfAbsent(rarity,k->new HashSet<>()).add(second.getCount());
                check(offer.getUses()==0&&offer.getMaxUses()==(offers.size()==1?p.stock:1),"stock "+id);
            }
            if(reds==2)doubleRed++;
        }
        ratio(counts[0],count,.50,.015);ratio(counts[1],count,.25,.012);ratio(counts[2],count,.25,.012);
        int slots=counts[1]+2*counts[2];
        double[] probabilities = librarian ? new double[]{.30,.25,.20,.15,.075,.025}
                : new double[]{0,0,.10,.60,.25,.05};
        int index=0;
        for(var e:PRICES.entrySet()){
            var r=e.getKey();var p=e.getValue();
            double expected = probabilities[index++];
            if (expected == 0) {
                check(tiers[r.ordinal()] == 0, "unexpected rarity " + r);
                continue;
            }
            ratio(tiers[r.ordinal()],slots,expected,.013);
            if (librarian && r.ordinal() > 0) {
                check(tiers[r.ordinal() - 1] > tiers[r.ordinal()], "librarian rarity did not decrease at " + r);
            }
            Set<String> eligible = new HashSet<>();
            for (Spell spell : SpellRegistry.all()) {
                for (int level = 1; level <= spell.getMaxLevel(); level++) {
                    if (spell.getRarity(level) == r) eligible.add(spell.getId().getPath());
                }
            }
            check(eligible.equals(spells.get(r)), "candidate pool " + r + " " + spells.get(r));
            check(emeraldPrices.get(r).size()==p.high-p.low+1&&crystalPrices.get(r).size()==p.crystalHigh-p.crystalLow+1,"unreached integer prices "+r);
        }
        check(doubleRed>0,"no two different red scrolls in one merchant");
        return (librarian ? "librarian " : "wandering ")+"sample="+count+" count[0,1,2]="+Arrays.toString(counts)+" slots="+slots+" tiers[white,green,blue,purple,orange,red]="+Arrays.toString(tiers)+" doubleRed="+doubleRed+" emeraldPrices="+emeraldPrices+" crystalPrices="+crystalPrices;
    }

    private static void librarianOffers(ServerWorld world) {
        Random seeds = Random.create(270019);
        var seenCounts = new HashSet<Integer>();
        var seenTiers = new HashSet<SpellRarity>();
        var legacyCounts = new HashSet<Integer>();
        for (int i = 0; i < 256; i++) {
            VillagerEntity villager = EntityType.VILLAGER.create(world);
            villager.getRandom().setSeed(seeds.nextLong());
            villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.FARMER));
            check(villager.getOffers().stream().noneMatch(o -> o.getSellItem().isOf(SscAddon.MAGIC_SCROLL)), "farmer sold a scroll");
            villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.LIBRARIAN));
            TradeOfferList offers = villager.getOffers();
            int scrollCount = 0;
            for (TradeOffer offer : offers) {
                ItemStack stack = offer.getSellItem();
                if (stack.isOf(SscAddon.MAGIC_SCROLL)) {
                    seenTiers.add(ScrollData.getSpell(stack).getRarity(ScrollData.getLevel(stack)));
                    scrollCount++;
                    while (!offer.isDisabled()) offer.use();
                }
            }
            check(offers.size() - scrollCount == 2 && scrollCount <= 2, "novice vanilla offers or scroll count");
            seenCounts.add(scrollCount);
            VillagerEntity loaded = EntityType.VILLAGER.create(world);
            NbtCompound saved = villager.writeNbt(new NbtCompound());
            loaded.readNbt(saved);
            check(offers.toNbt().equals(loaded.getOffers().toNbt()), "reload changed price, spell, or uses");
            loaded.restock();
            check(loaded.getOffers().size() == offers.size(), "restock added offers");
            for (int slot = 0; slot < offers.size(); slot++) {
                TradeOffer before = offers.get(slot);
                if (before.getSellItem().isOf(SscAddon.MAGIC_SCROLL)) checkRestocked(before, loaded.getOffers().get(slot));
            }

            // An already traded, master librarian from a save predating scroll offers.
            TradeOfferList legacy = new TradeOfferList();
            var original = new TradeOffer(new ItemStack(Items.PAPER, 9), new ItemStack(Items.EMERALD), 7, 2, .05F);
            original.use();
            legacy.add(original);
            saved.put("Offers", legacy.toNbt());
            saved.getCompound("VillagerData").putInt("level", 5);
            loaded.readNbt(saved);
            TradeOfferList migrated = loaded.getOffers();
            check(migrated.get(0).toNbt().equals(original.toNbt()), "migration changed an existing offer");
            legacyCounts.add(migrated.size() - 1);
            NbtCompound expected = migrated.toNbt();
            loaded.readNbt(loaded.writeNbt(new NbtCompound()));
            check(expected.equals(loaded.getOffers().toNbt()), "legacy librarian rolled again");
        }
        check(seenCounts.equals(Set.of(0, 1, 2)) && seenTiers.size() == 6, "librarian pool " + seenCounts + seenTiers);
        check(legacyCounts.equals(Set.of(0, 1, 2)), "legacy pool " + legacyCounts);
        System.out.println("Librarian entities: 256 new and 256 legacy, all six rarities and all offer counts reached, reload stable");
    }
    public static String vanillaOffers(ServerWorld world){
        int count=2000,crystalOnly=0,both=0,neither=0,scrollOnly=0;
        var seeds=Random.create(83551);
        for(int i=0;i<count;i++){
            var t=EntityType.WANDERING_TRADER.create(world);
            t.getRandom().setSeed(seeds.nextLong());
            var offers=t.getOffers();
            boolean crystal=false,scroll=false;
            for(var o:offers){
                if(o.getSellItem().isOf(SscAddon.ANUBIS_CRYSTAL)){
                    crystal=true;
                    var emerald=o.getSecondBuyItem();var diamond=o.getOriginalFirstBuyItem();
                    check(emerald.isOf(Items.EMERALD)&&emerald.getCount()>=27&&emerald.getCount()<=37&&diamond.isOf(Items.DIAMOND)&&diamond.getCount()>=15&&diamond.getCount()<=25&&o.getMaxUses()==1,"old Anubis trade");
                }
                if(o.getSellItem().isOf(SscAddon.MAGIC_SCROLL))scroll=true;
            }
            if(crystal&&scroll)both++; else if(crystal)crystalOnly++; else if(scroll)scrollOnly++; else neither++;
        }
        check(both>0&&crystalOnly>0&&scrollOnly>0&&neither>0,"independent outcomes");
        ratio(both+crystalOnly,count,.01,.009);
        return "vanillaFill="+count+" neither="+neither+" scrollOnly="+scrollOnly+" crystalOnly="+crystalOnly+" both="+both;
    }
    private static void checkRestocked(TradeOffer before, TradeOffer after) {
        check(before.isDisabled() && after.getUses() == 0 && before.getMaxUses() == after.getMaxUses()
                        && ItemStack.areEqual(before.getSellItem(), after.getSellItem())
                        && ItemStack.areEqual(before.getOriginalFirstBuyItem(), after.getOriginalFirstBuyItem())
                        && ItemStack.areEqual(before.getSecondBuyItem(), after.getSecondBuyItem()),
                "restock changed a scroll, its price, or its stock limit");
    }

    private static void lecternRestock(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos lectern = context.getAbsolutePos(new BlockPos(6, 2, 6));
        world.setBlockState(lectern, Blocks.LECTERN.getDefaultState());
        world.setBlockState(lectern.east().down(), Blocks.STONE.getDefaultState());
        world.setTimeOfDay(3000);
        var jobSites = world.getPointOfInterestStorage();
        // The accelerated GameTest can tick villagers before the queued POI update runs.
        if (!jobSites.hasTypeAt(PointOfInterestTypes.LIBRARIAN, lectern)) {
            jobSites.add(lectern, world.getRegistryManager().get(RegistryKeys.POINT_OF_INTEREST_TYPE)
                    .entryOf(PointOfInterestTypes.LIBRARIAN));
        }
        check(jobSites.getPosition(type -> type.matchesKey(PointOfInterestTypes.LIBRARIAN),
                (type, pos) -> pos.equals(lectern), lectern, 1).isPresent(), "reserve lectern job site");
        for (int i = 0; i < 100; i++) {
            VillagerEntity librarian = EntityType.VILLAGER.create(world);
            librarian.setVillagerData(librarian.getVillagerData().withProfession(VillagerProfession.LIBRARIAN));
            TradeOfferList offers = librarian.getOffers();
            if (offers.size() != 4) continue;
            for (TradeOffer offer : offers) {
                if (offer.getSellItem().isOf(SscAddon.MAGIC_SCROLL)) librarian.trade(offer);
            }
            TradeOfferList soldOut = new TradeOfferList(offers.toNbt());
            librarian.refreshPositionAndAngles(lectern.getX() + 1.5, lectern.getY(), lectern.getZ() + .5, 0, 0);
            librarian.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
            librarian.getBrain().remember(MemoryModuleType.JOB_SITE, GlobalPos.create(world.getRegistryKey(), lectern));
            librarian.reinitializeBrain(world);
            librarian.setExperience(10);
            check(world.spawnEntity(librarian), "spawn lectern librarian");
            librarian.trade(offers.get(0));
            context.waitAndRun(1100, () -> {
                throw new IllegalStateException("Librarian work timeout: " + librarian.getVillagerData().getLevel()
                        + ", job site=" + librarian.getBrain().getOptionalRegisteredMemory(MemoryModuleType.JOB_SITE)
                        + ", activities=" + librarian.getBrain().getPossibleActivities()
                        + ", work time=" + librarian.getBrain().getOptionalRegisteredMemory(MemoryModuleType.LAST_WORKED_AT_POI));
            });
            context.runAtEveryTick(() -> {
                if (librarian.getVillagerData().getLevel() < 2
                        || !librarian.getBrain().hasMemoryModule(MemoryModuleType.LAST_WORKED_AT_POI)) return;
                List<TradeOffer> scrolls = librarian.getOffers().stream()
                        .filter(o -> o.getSellItem().isOf(SscAddon.MAGIC_SCROLL)).toList();
                check(scrolls.size() == 2 && librarian.getOffers().size() == soldOut.size() + 2, "level-up rerolled offers");
                checkRestocked(soldOut.get(2), scrolls.get(0));
                checkRestocked(soldOut.get(3), scrolls.get(1));
                System.out.println("Lectern work AI replenished both sold-out scrolls; apprentice level retained both offers");
                context.complete();
            });
            return;
        }
        throw new AssertionError("no librarian with two scrolls");
    }

    public static String raiding(ServerPlayerEntity player){
        var world=player.getServerWorld();
        FormUtils._setForm(player,RegPlayerForms.getPlayerForm(FormIdentifiers.FAMILIAR_FOX_MANCIANIMA));
        check(net.jackcooper.shapeShifterCurseAddon.util.FormUtils.isForm(player,FormIdentifiers.FAMILIAR_FOX_MANCIANIMA),"test player's contract spirit form");
        for (var profession : List.of(VillagerProfession.NONE, VillagerProfession.FARMER, VillagerProfession.LIBRARIAN))
        for (boolean sold : new boolean[]{false,true}) {
            MerchantEntity entity = profession == VillagerProfession.NONE
                    ? EntityType.WANDERING_TRADER.create(world) : EntityType.VILLAGER.create(world);
            if (entity instanceof VillagerEntity villager)
                villager.setVillagerData(villager.getVillagerData().withProfession(profession));
            entity.setPosition(player.getX(),player.getY()+8,player.getZ());
            var offers=entity.getOffers();offers.clear();
            var scroll=new TradeOffer(new ItemStack(Items.EMERALD),ScrollData.create("frost_spike",4),1,0,0F);
            if(sold)scroll.use();
            offers.add(scroll);
            var normal=new TradeOffer(new ItemStack(Items.EMERALD),new ItemStack(Items.BRICK,3),1,0,0F);
            normal.use();offers.add(normal);
            var box=entity.getBoundingBox().expand(2);
            var prior=new HashSet<>(world.getEntitiesByClass(ItemEntity.class,box,e->true));
            entity.setAiDisabled(true);
            check(world.spawnEntity(entity),"raid merchant spawn");
            check(entity.damage(player.getDamageSources().playerAttack(player),10000.0F)&&!entity.isAlive(),"actual contract spirit kill");
            var drops=world.getEntitiesByClass(ItemEntity.class,box,e->!prior.contains(e));
            int found=0,bricks=0;
            for(var drop:drops){
                var stack=drop.getStack();
                if(stack.isOf(SscAddon.MAGIC_SCROLL))found+=stack.getCount();
                if(stack.isOf(Items.BRICK))bricks+=stack.getCount();
                drop.discard();
            }
            check(found==(profession==VillagerProfession.FARMER||!sold?1:0)&&bricks==3,"raid "+profession+" sold="+sold+" scrolls="+found+" bricks="+bricks);
        }
        return "raiding=6/6 in-stock/sold-out x wandering/librarian/farmer; sold-out ordinary brick x3 preserved";
    }
    public static void start(TestContext context){
        try {
            if(Boolean.getBoolean("neomagic.resume")){
                reopen(context);
                return;
            }
            System.out.println(distribution(false));
            System.out.println(distribution(true));
            System.out.println(vanillaOffers(context.getWorld()));
            librarianOffers(context.getWorld());
            if(io.github.apace100.apoli.Apoli.config==null)
                io.github.apace100.apoli.Apoli.config=new io.github.apace100.apoli.util.ApoliConfig();
            var p=context.createMockCreativeServerPlayerInWorld();
            var pos=context.getAbsolutePos(new BlockPos(2,2,2));
            p.setPosition(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            System.out.println(raiding(p));
            if (!Boolean.getBoolean("neomagic.stability")) {
                p.setPosition(pos.getX() + 40, pos.getY(), pos.getZ());
                lecternRestock(context);
                return;
            }
            var world=context.getWorld();
            var savedPos=new BlockPos(48,85,48);
            world.setChunkForced(3,3,true);
            world.getChunk(savedPos);
            WanderingTraderEntity merchant=null;
            for(int i=0;i<500&&merchant==null;i++){
                var candidate=EntityType.WANDERING_TRADER.create(world);
                for(var o:candidate.getOffers()) if(o.getSellItem().isOf(SscAddon.MAGIC_SCROLL)){
                    o.use();merchant=candidate;break;
                }
            }
            check(merchant!=null,"missing save specimen");
            merchant.setPosition(savedPos.getX()+.5,savedPos.getY(),savedPos.getZ()+.5);
            merchant.setAiDisabled(true);merchant.setNoGravity(true);merchant.setInvulnerable(true);merchant.setPersistent();merchant.setDespawnDelay(24000);
            check(world.spawnEntity(merchant),"cannot spawn persistent fixture");
            var original=merchant;
            context.waitAndRun(24,()->{
                try{
                    world.getServer().save(false,true,true);
                    NbtIo.writeCompressed(original.getOffers().toNbt(),Path.of("saved-offers.nbt").toFile());
                    Files.writeString(Path.of("trader-uuid.txt"),original.getUuidAsString());
                    System.out.println("savedTrader="+original.getUuidAsString()+" offers="+original.getOffers().size());
                    context.complete();
                }catch(Exception error){throw new AssertionError(error);}
            });
        } catch (Throwable error) {throw new AssertionError("Wandering trades did not pass",error);}
    }
    private static void reopen(TestContext context)throws Exception{
        var world=context.getWorld();
        UUID id=UUID.fromString(Files.readString(Path.of("trader-uuid.txt")).trim());
        var expected=NbtIo.readCompressed(Path.of("saved-offers.nbt").toFile());
        world.setChunkForced(3,3,true);world.getChunk(new BlockPos(48,85,48));
        context.runAtEveryTick(()->{
            if(!(world.getEntity(id) instanceof WanderingTraderEntity actual))return;
            check(actual.getOffers().toNbt().equals(expected),"full server restart changed merchant price/stock/spells");
            check(actual.getOffers().stream().anyMatch(o->o.getSellItem().isOf(SscAddon.MAGIC_SCROLL)&&o.getUses()>0),"missing purchased scroll trade");
            System.out.println("reopenedTrader="+id+" all persisted offers match; used scroll kept uses");
            context.complete();
        });
    }
}
