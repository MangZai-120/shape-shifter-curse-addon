package net.ssca.neomagic;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.*;
import net.minecraft.registry.*;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.*;

/** 正式法术书共用的崩解度后端与月尘恢复药水；独立研究入口已移除。 */
public final class NeoMagic implements ModInitializer {
    public static final Logger LOG=LoggerFactory.getLogger("ssca_neomagic");
    public static Identifier id(String path){return new Identifier("ssca_neomagic",path);}
    public static net.minecraft.text.MutableText text(String key,Object... args){return Text.translatable("neomagic."+key,args);}
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
    @Override public void onInitialize() {
        net.jackcooper.shapeShifterCurseAddon.item.MoonDustReliefItem.register();
        net.jackcooper.shapeShifterCurseAddon.item.PotionTestTubes.register();
        net.jackcooper.shapeShifterCurseAddon.recipe.TestTubeFillingRecipe.register();
        Registry.register(Registries.STATUS_EFFECT,id("disorder"),DISORDER);
        Registry.register(Registries.STATUS_EFFECT,id("disintegration"),DISINTEGRATION);
        Registry.register(Registries.STATUS_EFFECT,id("breakdown"),BREAKDOWN);
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new NeoRules());
        NeoNetwork.register(); NeoRuntime.register(); NeoEffects.register();
        net.jackcooper.shapeShifterCurseAddon.spell.FormSpellStyle.register();
    }
}
