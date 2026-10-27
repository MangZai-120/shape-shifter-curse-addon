package net.ssca.neomagic;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import org.slf4j.*;
import java.util.*;

public final class NeoMagic implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("ssca_neomagic");
    public static Identifier id(String path) { return new Identifier("ssca_neomagic",path); }
    public static net.minecraft.text.MutableText text(String key,Object... args) { return Text.translatable("neomagic."+key,args); }
    public static final Item WAND = new Item(new Item.Settings().maxCount(1)) {
        @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,net.minecraft.client.item.TooltipContext context) {
            lines.add(text("wand_help").formatted(Formatting.GRAY));var b=NeoNetwork.bound(stack);
            if(b!=null)lines.add(text("loaded",b.name().isEmpty()?text("unnamed_blueprint").getString():b.name()).formatted(Formatting.AQUA));
        }
        @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
            if(player instanceof ServerPlayerEntity p) {
                if(p.isSneaking()) NeoRuntime.releaseOrCancel(p); else NeoRuntime.start(p,hand);
            }
            return TypedActionResult.success(player.getStackInHand(hand),world.isClient);
        }
    };
    public static final Item NOTEBOOK = new Item(new Item.Settings().maxCount(1)) {
        @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,net.minecraft.client.item.TooltipContext context) {lines.add(text("notebook_help").formatted(Formatting.GRAY));}
        @Override public TypedActionResult<ItemStack> use(World w, PlayerEntity p, Hand h) {
            if(p instanceof ServerPlayerEntity s) NeoNetwork.open(s,null);
            return TypedActionResult.success(p.getStackInHand(h),w.isClient);
        }
    };
    public static final Item BLUEPRINT = new Item(new Item.Settings().maxCount(1)) {
        @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,net.minecraft.client.item.TooltipContext context) {
            lines.add(text("blueprint_help").formatted(Formatting.GRAY));
            if(stack.hasNbt()&&!stack.getNbt().getString("ResearchNote").isEmpty())lines.add(Text.literal(stack.getNbt().getString("ResearchNote")).formatted(Formatting.AQUA));
            if(stack.hasNbt()&&stack.getNbt().getByteArray("Evidence").length>0)try {
                var evidence=NeoState.JSON.fromJson(new String(stack.getNbt().getByteArray("Evidence"),java.nio.charset.StandardCharsets.UTF_8),NeoState.Experiment[].class);
                lines.add(text("shared_evidence",evidence.length).formatted(Formatting.GRAY));
                for(int i=0;i<Math.min(4,evidence.length);i++){var e=evidence[i];lines.add(text("evidence_summary",e.id.substring(0,8),String.format(java.util.Locale.ROOT,"%.1f",e.initial),String.format(java.util.Locale.ROOT,"%.1f",e.actual),String.format(java.util.Locale.ROOT,"%.1f",e.arrived)).formatted(Formatting.GRAY));}
            }catch(RuntimeException invalid){lines.add(text("invalid").formatted(Formatting.RED));}
        }
        @Override public TypedActionResult<ItemStack> use(World w,PlayerEntity p,Hand h) {
            if(p instanceof ServerPlayerEntity s) NeoNetwork.importBlueprint(s,p.getStackInHand(h));
            return TypedActionResult.success(p.getStackInHand(h),w.isClient);
        }
    };
    public static final Block TABLE = new Block(FabricBlockSettings.copyOf(Blocks.ENCHANTING_TABLE)) {
        @Override public ActionResult onUse(BlockState state,World w,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit) {
            if(player instanceof ServerPlayerEntity p) NeoNetwork.open(p,pos);
            return ActionResult.SUCCESS;
        }
    };
    public static final Block TARGET = new Block(FabricBlockSettings.copyOf(Blocks.TARGET).strength(2)) {
        @Override public ActionResult onUse(BlockState state,World w,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit) {
            if(player instanceof ServerPlayerEntity p) {
                NeoNetwork.discover(p,"carrier:AREA"); NeoNetwork.discover(p,"control:SPLIT");
                p.sendMessage(text("target_help"),false);
            }
            return ActionResult.SUCCESS;
        }
    };
    public static final StatusEffect DISORDER = new CollapseEffect(0xC79254,.15);
    public static final StatusEffect DISINTEGRATION = new CollapseEffect(0xA13C63,.35);
    public static final StatusEffect BREAKDOWN = new CollapseEffect(0x681C62,.35) {
        @Override public boolean canApplyUpdateEffect(int duration,int amplifier) { return duration%20==0; }
        @Override public void applyUpdateEffect(LivingEntity entity,int amplifier) {
            entity.damage(entity.getDamageSources().magic(),2+amplifier);
        }
    };
    public static class CollapseEffect extends StatusEffect {
        public CollapseEffect(int color,double slow) {
            super(StatusEffectCategory.HARMFUL,color);
            addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED,"2a36537d-c648-48b2-9bd1-6d420f2fdf7b",-slow,net.minecraft.entity.attribute.EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
        }
    }
    public static final Map<String,Item> SAMPLES = new LinkedHashMap<>();
    public static final Item VIAL = new ReliefItem(20,16,false), BOTTLE = new ReliefItem(60,40,false), SPLASH = new ReliefItem(35,20,true);
    private static final class ReliefItem extends Item {
        private final double relief; private final int ticks; private final boolean splash;
        ReliefItem(double amount,int ticks,boolean splash) { super(new Item.Settings().maxCount(16));relief=amount;this.ticks=ticks;this.splash=splash; }
        @Override public UseAction getUseAction(ItemStack stack) { return UseAction.DRINK; }
        @Override public int getMaxUseTime(ItemStack stack) { return ticks; }
        @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity player,Hand hand) {
            if(splash) {
                if(player instanceof ServerPlayerEntity p) {
                    PotionEntity thrown=new PotionEntity(world,p) {
                        @Override protected void onCollision(HitResult hit) {
                            if(!getWorld().isClient) {
                                for(ServerPlayerEntity other:p.getServerWorld().getPlayers()) {
                                    double distance=other.getPos().distanceTo(getPos());
                                    if(distance<4)NeoRuntime.relieve(other,relief*(1-distance/4));
                                }
                                getWorld().syncWorldEvent(2002,getBlockPos(),0xA9D9E8);discard();
                            }
                        }
                    };
                    ItemStack dose=p.getStackInHand(hand).copy();dose.setCount(1);thrown.setItem(dose);
                    thrown.setVelocity(p,p.getPitch(),p.getYaw(),-20,.5f,1);world.spawnEntity(thrown);
                    if(!p.isCreative())p.getStackInHand(hand).decrement(1);
                    p.getItemCooldownManager().set(this,10);
                }
                return TypedActionResult.success(player.getStackInHand(hand),world.isClient);
            }
            return ItemUsage.consumeHeldItem(world,player,hand);
        }
        @Override public ItemStack finishUsing(ItemStack stack,World world,LivingEntity user) {
            if(user instanceof ServerPlayerEntity p) {
                if(splash) {
                    var center=NeoEffects.aim(p,8);
                    for(ServerPlayerEntity other:p.getServerWorld().getPlayers())
                        if(other.squaredDistanceTo(center)<16) NeoRuntime.relieve(other,relief);
                    p.getServerWorld().spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,center.x,center.y,center.z,30,1,1,1,.03);
                } else NeoRuntime.relieve(p,relief);
                world.playSound(null,p.getBlockPos(),SoundEvents.ENTITY_GENERIC_DRINK,SoundCategory.PLAYERS,1,1);
                if(!p.isCreative()) { stack.decrement(1); if(stack.isEmpty())return new ItemStack(Items.GLASS_BOTTLE); p.getInventory().offerOrDrop(new ItemStack(Items.GLASS_BOTTLE)); }
            }
            return stack;
        }
    }
    private static void item(String name,Item i) { Registry.register(Registries.ITEM,id(name),i); }
    @Override public void onInitialize() {
        item("guiding_wand",WAND); item("observation_notebook",NOTEBOOK); item("blueprint",BLUEPRINT);
        Registry.register(Registries.BLOCK,id("research_table"),TABLE); item("research_table",new BlockItem(TABLE,new Item.Settings()));
        Registry.register(Registries.BLOCK,id("observation_target"),TARGET); item("observation_target",new BlockItem(TARGET,new Item.Settings()));
        item("moon_dust_vial",VIAL); item("moon_dust_bottle",BOTTLE); item("moon_dust_splash",SPLASH);
        Registry.register(Registries.STATUS_EFFECT,id("disorder"),DISORDER);
        Registry.register(Registries.STATUS_EFFECT,id("disintegration"),DISINTEGRATION);
        Registry.register(Registries.STATUS_EFFECT,id("breakdown"),BREAKDOWN);
        for(NeoModel.Core c:NeoModel.Core.values()) sample("core:"+c,"sample_"+c.name().toLowerCase(Locale.ROOT));
        for(NeoModel.Carrier c:NeoModel.Carrier.values()) sample("carrier:"+c,"carrier_"+c.name().toLowerCase(Locale.ROOT));
        for(NeoModel.Control c:NeoModel.Control.values()) sample("control:"+c,"control_"+c.name().toLowerCase(Locale.ROOT));
        Registry.register(Registries.ITEM_GROUP,id("research"),FabricItemGroup.builder().displayName(text("group"))
                .icon(() -> new ItemStack(NOTEBOOK)).entries((context,entries) -> {
                    for(Item i:List.of(WAND,NOTEBOOK,BLUEPRINT,VIAL,BOTTLE,SPLASH,TABLE.asItem(),TARGET.asItem()))entries.add(i);
                    SAMPLES.values().forEach(entries::add);
                }).build());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new NeoRules());
        LootTableEvents.MODIFY.register((manager,lootManager,id,builder,source)->{
            if(source.isBuiltin()&&id.getNamespace().equals("minecraft")&&Set.of("chests/simple_dungeon","chests/abandoned_mineshaft","chests/stronghold_library").contains(id.getPath())) {
                var pool=LootPool.builder().rolls(ConstantLootNumberProvider.create(1)).conditionally(RandomChanceLootCondition.builder(.35f));
                SAMPLES.values().forEach(item->pool.with(ItemEntry.builder(item)));builder.pool(pool);
            }
        });
        NeoNetwork.register(); NeoRuntime.register(); NeoEffects.register();
    }
    private static void sample(String key,String name) {
        Item sample=new Item(new Item.Settings().maxCount(16)) {
            @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity p,Hand hand) {
                if(p instanceof ServerPlayerEntity s) {
                    if(!NeoNetwork.nearTable(s)) {s.sendMessage(text("need_table"),true);return TypedActionResult.fail(p.getStackInHand(hand));}
                    if(NeoNetwork.discover(s,key) && !s.isCreative())p.getStackInHand(hand).decrement(1);
                }
                return TypedActionResult.success(p.getStackInHand(hand),world.isClient);
            }
        };
        SAMPLES.put(key,sample);item(name,sample);
    }
}
