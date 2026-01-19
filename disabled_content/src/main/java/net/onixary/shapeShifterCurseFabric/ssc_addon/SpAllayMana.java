package net.onixary.shapeShifterCurseFabric.ssc_addon;

import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.mana.InstinctBarLikeManaBar;
import net.onixary.shapeShifterCurseFabric.mana.ManaRegistries;
import net.onixary.shapeShifterCurseFabric.mana.ManaUtils;

public class SpAllayMana {
    public static Identifier INSTANCE;

    public static void register() {
         INSTANCE = ManaRegistries.registerManaType(new Identifier("ssc_addon", "sp_allay_mana"),
            new ManaUtils.ModifierList(
                    new Pair<>(
                            new Identifier("ssc_addon", "base_value"),
                            new Pair<>(
                                    ManaRegistries.MC_AlwaysTrue,
                                    new ManaUtils.Modifier(100d, 1.0d, 0d)
                            )
                    )
            ),
            new ManaUtils.ModifierList(
                    new Pair<>(
                            new Identifier("ssc_addon", "natural_regen"),
                            new Pair<>(
                                    ManaRegistries.MC_AlwaysTrue,
                                    new ManaUtils.Modifier(0.5d, 1.0d, 0d)
                            )
                    )
            ),
            ManaRegistries.EMPTY_MANA_HANDLER
        );
    }
}
