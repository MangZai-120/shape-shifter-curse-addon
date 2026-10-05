package net.jackcooper.shapeShifterCurseAddon.loot;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.function.ConditionalLootFunction;
import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Kept registered for old loot tables; natural scrolls no longer gain rune enhancements. */
public final class RandomRuneLootFunction extends ConditionalLootFunction {
    private static final LootFunctionType TYPE = Registry.register(Registries.LOOT_FUNCTION_TYPE,
            new Identifier("ssc_addon", "random_rune_formation"), new LootFunctionType(new Serializer()));

    private RandomRuneLootFunction(LootCondition[] conditions) { super(conditions); }
    public static void register() { /* Initialize the type before loot tables are loaded. */ }
    public static ConditionalLootFunction.Builder<?> builder() { return builder(RandomRuneLootFunction::new); }
    @Override public LootFunctionType getType() { return TYPE; }
    @Override protected ItemStack process(ItemStack stack, LootContext context) {
        return stack;
    }
    public static final class Serializer extends ConditionalLootFunction.Serializer<RandomRuneLootFunction> {
        @Override public RandomRuneLootFunction fromJson(JsonObject json, JsonDeserializationContext context,
                                                         LootCondition[] conditions) {
            return new RandomRuneLootFunction(conditions);
        }
    }
}
