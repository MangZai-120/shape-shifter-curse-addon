package net.jackcooper.shapeShifterCurseAddon.item;

import java.util.List;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceReader;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.*;
import net.minecraft.potion.PotionUtil;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.ssca.neomagic.NeoRuntime;
import org.jetbrains.annotations.Nullable;

/** 月尘药水只降低现有崩解度，不改变安全阈值、形态资源或精通。 */
public final class MoonDustReliefItem extends Item {
    private static final BalanceReader BAL = new BalanceReader("systems.moon_dust_potions");
    private static final String DOSE_KEY = "MoonDustReliefAmount";
    public static final int COLOR = 0xA7EDF5;
    public static final MoonDustReliefItem VIAL = new MoonDustReliefItem("vial", 20, 16, false);
    public static final MoonDustReliefItem BOTTLE = new MoonDustReliefItem("bottle", 60, 40, false);
    public static final MoonDustReliefItem SPLASH = new MoonDustReliefItem("splash", 35, 0, true);
    // 原版药水实体负责碰撞、半径四格衰减、客户端追踪和保存恢复；不用匿名实体或全服玩家扫描。
    public static final StatusEffect RELIEF = new StatusEffect(StatusEffectCategory.BENEFICIAL, COLOR) {
        @Override public boolean isInstant() { return true; }
        @Override public boolean canApplyUpdateEffect(int duration, int amplifier) { return false; }
        @Override public void applyInstantEffect(@Nullable Entity source, @Nullable Entity attacker,
                                                LivingEntity target, int amplifier, double proximity) {
            if (!(target instanceof ServerPlayerEntity player)) return;
            double amount = SPLASH.relief();
            if (source instanceof PotionEntity potion && potion.getStack().hasNbt()
                    && potion.getStack().getNbt().contains(DOSE_KEY)) {
                amount = potion.getStack().getNbt().getDouble(DOSE_KEY);
            }
            // 投掷时快照剂量；重载不会改动已经飞出的药水。
            if (Double.isFinite(amount) && amount > 0 && Double.isFinite(proximity) && proximity > 0) {
                NeoRuntime.relieve(player, amount * Math.min(1, proximity));
            }
        }
    };

    private final String kind;
    private final double defaultRelief;
    private final int defaultTicks;
    private final boolean splash;

    private MoonDustReliefItem(String kind, double relief, int ticks, boolean splash) {
        super(new Item.Settings().maxCount("vial".equals(kind) ? PotionTestTubes.DEFAULT_STACK_LIMIT : 16));
        this.kind = kind;
        this.defaultRelief = relief;
        this.defaultTicks = ticks;
        this.splash = splash;
    }

    public double relief() { return this == VIAL ? BOTTLE.relief() / PotionTestTubes.PORTIONS : BAL.d(kind + "_relief", defaultRelief); }
    @Override public UseAction getUseAction(ItemStack stack) { return splash ? UseAction.NONE : UseAction.DRINK; }
    @Override public int getMaxUseTime(ItemStack stack) {
        return splash ? 0 : this == VIAL ? PotionTestTubes.drinkTicks(BOTTLE.getMaxUseTime(new ItemStack(BOTTLE))) : BAL.i(kind + "_drink_ticks", defaultTicks);
    }

    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (!splash) return ItemUsage.consumeHeldItem(world, player, hand);
        ItemStack held = player.getStackInHand(hand);
        if (!world.isClient) {
            var potion = new PotionEntity(world, player);
            ItemStack dose = held.copy();
            dose.setCount(1);
            dose.getOrCreateNbt().remove("Potion");
            dose.getNbt().remove("CustomPotionEffects");
            PotionUtil.setCustomPotionEffects(dose, List.of(new StatusEffectInstance(RELIEF, 1)));
            if (!dose.getOrCreateNbt().contains("CustomPotionColor")) dose.getNbt().putInt("CustomPotionColor", COLOR);
            dose.getOrCreateNbt().putDouble(DOSE_KEY, relief());
            potion.setItem(dose);
            potion.setVelocity(player, player.getPitch(), player.getYaw(), -20, .5f, 1);
            if (!world.spawnEntity(potion)) return TypedActionResult.fail(held);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_SPLASH_POTION_THROW, SoundCategory.PLAYERS, .5f,
                    .4f / (world.getRandom().nextFloat() * .4f + .8f));
            if (!player.getAbilities().creativeMode) held.decrement(1);
            player.getItemCooldownManager().set(this, BAL.i("throw_cooldown_ticks", 10));
            player.incrementStat(Stats.USED.getOrCreateStat(this));
        }
        return TypedActionResult.success(held, world.isClient);
    }

    @Override public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (splash || stack.isEmpty() || !stack.isOf(this)) return stack;
        if (user instanceof ServerPlayerEntity player) {
            NeoRuntime.relieve(player, relief());
            player.incrementStat(Stats.USED.getOrCreateStat(this));
            if (!player.getAbilities().creativeMode) {
                stack.decrement(1);
                Item container = this == VIAL ? PotionTestTubes.EMPTY : Items.GLASS_BOTTLE;
                if (stack.isEmpty()) return new ItemStack(container);
                player.getInventory().offerOrDrop(new ItemStack(container));
            }
        }
        return stack;
    }

    @Override public void appendTooltip(ItemStack stack, @Nullable World world,
                                        List<Text> lines, TooltipContext context) {
        lines.add(Text.translatable(splash ? "tooltip.ssc_addon.moon_dust_splash"
                        : "tooltip.ssc_addon.moon_dust_relief", (int) relief()).formatted(Formatting.AQUA));
        if (this == VIAL) lines.add(Text.translatable("tooltip.ssc_addon.test_tube_stack").formatted(Formatting.GRAY));
    }

    public static void register() {
        Registry.register(Registries.STATUS_EFFECT, new Identifier("ssc_addon", "moon_dust_relief"), RELIEF);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "moon_dust_vial"), VIAL);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "moon_dust_bottle"), BOTTLE);
        Registry.register(Registries.ITEM, new Identifier("ssc_addon", "moon_dust_splash"), SPLASH);
    }
}
