package net.jackcooper.shapeShifterCurseAddon.item;

import java.util.ArrayList;
import java.util.List;
import io.github.apace100.apoli.component.PowerHolderComponent;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.potion.*;
import net.minecraft.registry.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import net.ssca.neomagic.NeoRuntime;
import net.onixary.shapeShifterCurseFabric.additional_power.ModifyPotionStackPower;
import org.jetbrains.annotations.Nullable;

/** One bottle is divided into three doses. Original potion NBT stays intact; scaling occurs on use. */
public final class PotionTestTubes {
    public static final int PORTIONS = 3;
    public static final int DEFAULT_STACK_LIMIT = 3;
    public static final int POWER_STACK_LIMIT = 16;
    private static final String BOTTLE_TICKS = "TestTubeBottleUseTicks";
    public static final Item EMPTY = new Item(new Item.Settings().maxCount(64));
    public static final FilledTube FILLED = new FilledTube();
    public static final StatusEffect RELIEF = new StatusEffect(StatusEffectCategory.BENEFICIAL, MoonDustReliefItem.COLOR) {
        @Override public boolean isInstant() { return true; }
        @Override public void applyInstantEffect(@Nullable Entity source, @Nullable Entity attacker,
                                                LivingEntity target, int amplifier, double proximity) {
            if (target instanceof ServerPlayerEntity player && Double.isFinite(proximity) && proximity > 0) {
                NeoRuntime.relieve(player, MoonDustReliefItem.BOTTLE.relief() / PORTIONS * Math.min(1, proximity));
            }
        }
    };
    public static final Potion MOON_DUST = new Potion(new StatusEffectInstance(RELIEF, 1));

    private PotionTestTubes() {}

    /** 空试管是普通材料；只有装药试管使用玩家的药水堆叠资格。 */
    public static boolean isFilledTube(ItemStack stack) {
        return stack.isOf(FILLED) || stack.isOf(MoonDustReliefItem.VIAL);
    }

    /** 使魔原有的八瓶 power 在试管上对应十六管，水瓶限定 power 不扩大到其它药液。 */
    public static int stackLimit(ItemStack stack, @Nullable PlayerEntity player) {
        if (!isFilledTube(stack)) return stack.getMaxCount();
        if (player == null) return DEFAULT_STACK_LIMIT;
        boolean water = stack.isOf(FILLED) && PotionUtil.getPotion(stack) == Potions.WATER;
        boolean qualified = PowerHolderComponent.getPowers(player, ModifyPotionStackPower.class).stream()
                .anyMatch(power -> power.isActive() && power.getCount() > 1 && (!power.isOnlyWaterPotion() || water));
        return qualified ? POWER_STACK_LIMIT : DEFAULT_STACK_LIMIT;
    }

    public static void register() {
        Registry.register(Registries.ITEM, new net.minecraft.util.Identifier("ssc_addon", "test_tube"), EMPTY);
        Registry.register(Registries.ITEM, new net.minecraft.util.Identifier("ssc_addon", "test_tube_potion"), FILLED);
        Registry.register(Registries.STATUS_EFFECT, new net.minecraft.util.Identifier("ssc_addon", "moon_dust_tube_relief"), RELIEF);
        Registry.register(Registries.POTION, new net.minecraft.util.Identifier("ssc_addon", "moon_dust_tube"), MOON_DUST);
        PotionCarrierVisibility.register();
    }

    public static boolean isBottle(ItemStack stack) {
        return stack.isOf(MoonDustReliefItem.BOTTLE) || stack.getItem() instanceof PotionItem
                && !(stack.getItem() instanceof SplashPotionItem)
                && !(stack.getItem() instanceof LingeringPotionItem) && !stack.isOf(FILLED)
                && PotionUtil.getPotion(stack) != MOON_DUST
                && PotionUtil.getPotion(stack) != net.jackcooper.shapeShifterCurseAddon.effect.RegAddonEffects.UNIVERSAL_ENERGY_POTION
                && PotionTubeDose.isDivisible(stack);
    }

    public static ItemStack divide(ItemStack bottle) {
        if (!isBottle(bottle)) return ItemStack.EMPTY;
        boolean moon = bottle.isOf(MoonDustReliefItem.BOTTLE);
        ItemStack out = new ItemStack(moon ? MoonDustReliefItem.VIAL : FILLED, PORTIONS);
        if (bottle.hasNbt()) out.setNbt(bottle.getNbt().copy());
        out.getOrCreateNbt().putInt(BOTTLE_TICKS, bottle.getMaxUseTime());
        if (moon) {
            out.getOrCreateNbt().remove("Potion");
            // The old moon-dust bottle has no ordinary potion payload. Never double its relief effect.
            out.getOrCreateNbt().remove("CustomPotionEffects");
            if (!out.getNbt().contains("CustomPotionColor")) out.getNbt().putInt("CustomPotionColor", MoonDustReliefItem.COLOR);
        }
        return out;
    }

    public static int drinkTicks(int bottleTicks) {
        return Math.max(1, (int) Math.ceil(Math.max(1, bottleTicks) * .4));
    }

    /** 查看器按实际药水区分试管；改名、药液染色和分装计时不应拆分同一种药水。 */
    public static NbtCompound subtypeData(ItemStack stack) {
        NbtCompound result = new NbtCompound();
        result.putString("Potion", Registries.POTION.getId(PotionUtil.getPotion(stack)).toString());
        if (stack.hasNbt()) {
            var effects = stack.getNbt().getList("CustomPotionEffects", 10);
            if (!effects.isEmpty()) result.put("CustomPotionEffects", effects.copy());
            if (stack.getNbt().contains("targetForm", 8)) result.putString("targetForm", stack.getNbt().getString("targetForm"));
        }
        return result;
    }

    /** 每种基础药水都有对应分装示例，避免仅三种示例让其它药水查不到配方。 */
    public static List<ItemStack> fillingBottles() {
        List<ItemStack> result = new ArrayList<>();
        result.add(new ItemStack(MoonDustReliefItem.BOTTLE));
        Registries.POTION.stream().filter(p -> p != Potions.EMPTY && p != MOON_DUST).forEach(p -> {
            var bottle = PotionUtil.setPotion(new ItemStack(Items.POTION), p);
            if (isBottle(bottle)) result.add(bottle);
        });
        return result;
    }

    public static ItemStack moonDustStack() {
        ItemStack result = divide(new ItemStack(MoonDustReliefItem.BOTTLE));
        result.setCount(1);
        return result;
    }

    /** 无药效的水管、粗制药水管不占侧栏；持有它们时仍能查询实际分装配方。 */
    public static List<ItemStack> viewerStacks() {
        return fillingBottles().stream().filter(b -> b.isOf(MoonDustReliefItem.BOTTLE)
                || !PotionUtil.getPotionEffects(b).isEmpty()).map(b -> {
                    ItemStack tube = divide(b); tube.setCount(1); return tube;
                }).toList();
    }

    public static List<StatusEffectInstance> doseEffects(ItemStack stack) {
        List<StatusEffectInstance> result = new ArrayList<>();
        for (var effect : PotionUtil.getPotionEffects(stack)) {
            if (effect.getEffectType().isInstant()) result.add(new StatusEffectInstance(effect));
            else {
                NbtCompound nbt = effect.writeNbt(new NbtCompound());
                // Preserve flags and any hidden weaker effect, scaling its duration as well.
                for (NbtCompound part = nbt;; part = part.getCompound("HiddenEffect")) {
                    int duration = part.getInt("Duration");
                    if (duration > 0) part.putInt("Duration", Math.max(1, (int)(((long)duration + PORTIONS - 1) / PORTIONS)));
                    if (!part.contains("HiddenEffect", 10)) break;
                }
                var scaled = StatusEffectInstance.fromNbt(nbt);
                if (scaled != null) result.add(scaled);
            }
        }
        return result;
    }

    public static final class FilledTube extends PotionItem {
        private FilledTube() { super(new Item.Settings().maxCount(DEFAULT_STACK_LIMIT)); }

        @Override public ItemStack getDefaultStack() {
            var result = divide(PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.HEALING));
            result.setCount(1);
            return result;
        }

        @Override public int getMaxUseTime(ItemStack stack) {
            if (PotionUtil.getPotion(stack) == MOON_DUST) return drinkTicks(MoonDustReliefItem.BOTTLE.getMaxUseTime(new ItemStack(MoonDustReliefItem.BOTTLE)));
            int ticks = stack.hasNbt() && stack.getNbt().contains(BOTTLE_TICKS) ? stack.getNbt().getInt(BOTTLE_TICKS) : 32;
            return drinkTicks(ticks);
        }

        @Override public Text getName(ItemStack stack) {
            if (PotionUtil.getPotion(stack) == MOON_DUST) return Text.translatable("item.ssc_addon.moon_dust_vial");
            if (PotionUtil.getPotion(stack) == Potions.WATER) return Text.translatable("item.ssc_addon.test_tube_water");
            if (PotionUtil.getPotion(stack) == Potions.EMPTY) return Text.translatable("item.ssc_addon.test_tube_potion_empty");
            ItemStack bottle = new ItemStack(Items.POTION);
            if (stack.hasNbt()) bottle.setNbt(stack.getNbt().copy());
            return Text.translatable("item.ssc_addon.test_tube_potion", bottle.getName());
        }

        @Override public ActionResult useOnBlock(ItemUsageContext context) {
            // Vanilla PotionItem turns water and dirt into mud, returning a glass bottle. Tubes only drink.
            return ActionResult.PASS;
        }

        @Override public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
            if (stack.isEmpty() || !stack.isOf(this)) return stack;
            PlayerEntity player = user instanceof PlayerEntity p ? p : null;
            if (player instanceof ServerPlayerEntity serverPlayer) Criteria.CONSUME_ITEM.trigger(serverPlayer, stack);
            if (!world.isClient) {
                if (player != null) {
                    var form = net.onixary.shapeShifterCurseFabric.status_effects.CTPUtils.getCTPFormIDFromNBT(stack.getNbt());
                    if (form != null) net.onixary.shapeShifterCurseFabric.status_effects.CTPUtils.setTransformativePotionForm(player, form);
                }
                for (var effect : doseEffects(stack)) {
                    if (effect.getEffectType().isInstant()) {
                        double dose = effect.getEffectType() == RELIEF ? 1 : 1.0 / PORTIONS;
                        PotionTubeDose.applyInstant(effect, player, user, dose);
                    } else user.addStatusEffect(effect);
                }
            }
            if (player != null) player.incrementStat(Stats.USED.getOrCreateStat(this));
            user.emitGameEvent(GameEvent.DRINK);
            if (player == null || !player.getAbilities().creativeMode) {
                stack.decrement(1);
                if (stack.isEmpty()) return new ItemStack(EMPTY);
                if (player != null) player.getInventory().offerOrDrop(new ItemStack(EMPTY));
            }
            return stack;
        }

        @Override public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> lines, TooltipContext context) {
            if (PotionUtil.getPotion(stack) == MOON_DUST) {
                lines.add(Text.translatable("tooltip.ssc_addon.moon_dust_relief", (int)(MoonDustReliefItem.BOTTLE.relief() / PORTIONS)).formatted(Formatting.AQUA));
            } else {
                ItemStack shown = new ItemStack(Items.POTION);
                PotionUtil.setCustomPotionEffects(shown, doseEffects(stack));
                PotionUtil.buildTooltip(shown, lines, 1f);
                if (PotionUtil.getPotion(stack) == net.onixary.shapeShifterCurseFabric.items.RegCustomPotions.CUSTOM_STATUE_FORM_POTION) {
                    var form = net.onixary.shapeShifterCurseFabric.status_effects.CTPUtils.getCTPFormIDFromNBT(stack.getNbt());
                    if (form != null) {
                        var target = net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.getPlayerFormOrDefault(form,
                                net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.ORIGINAL_BEFORE_ENABLE);
                        lines.add(Text.translatable("tooltip.shape_shifter_curse.potion_target_form").append(
                                target.getContentText(net.onixary.shapeShifterCurseFabric.data.CodexData.ContentType.NAME)));
                    }
                }
            }
            lines.add(Text.translatable("tooltip.ssc_addon.test_tube_dose").formatted(Formatting.GRAY));
            lines.add(Text.translatable("tooltip.ssc_addon.test_tube_stack").formatted(Formatting.GRAY));
            if (PotionUtil.getPotion(stack) == Potions.WATER && PotionUtil.getPotionEffects(stack).isEmpty()) {
                lines.add(Text.translatable("tooltip.ssc_addon.test_tube_water").formatted(Formatting.GRAY));
            }
        }
    }
}
