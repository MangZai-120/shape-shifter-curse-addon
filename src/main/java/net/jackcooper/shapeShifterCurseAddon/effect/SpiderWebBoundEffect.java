package net.jackcooper.shapeShifterCurseAddon.effect;

import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

/**
 * 蛛网缠身：非白名单生物踩过魔法蜘蛛的减速蛛网（web_membrane）时施加，或被蛛网弹爆炸范围波及时直接施加。
 * <ul>
 *   <li>大幅降低移速（-50%，MULTIPLY_TOTAL，属性修饰不显示图标）；</li>
 *   <li>挖掘疲劳 + 虚弱：在 effect 内持续刷新（vanilla 效果，随蜘网缠身联动，三者同进退）；</li>
 *   <li>喝牛奶不可解除（见 {@code MilkBucketWebBoundMixin}）；</li>
 *   <li>不在任何形态的 buff 免疫列表里（使魔等亦挡不住）；</li>
 *   <li>持续期间脚下持续冒蛛网碎屑粒子，形成「脚上覆盖一层蛛网」的视觉。</li>
 * </ul>
 */
public class SpiderWebBoundEffect extends StatusEffect {

	/** 联动的挖掘疲劳/虚弱持续时长（tick）：略大于刷新间隔 4t，保证无缝衔接。 */
	private static final int SUB_DURATION = 40;

	public SpiderWebBoundEffect() {
		super(StatusEffectCategory.HARMFUL, 0xBFC4CC);
		this.addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED,
				"6C9E2A1F-8B3D-4A7E-9F21-3D5C7A0B1E44", -0.5D, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return duration % 4 == 0; // 每 4 tick 冒蛛网粒子 + 刷新挖掘疲劳/虚弱
	}

	@Override
	public void applyUpdateEffect(LivingEntity entity, int amplifier) {
		if (entity.getWorld() instanceof ServerWorld sw) {
			double spread = Math.max(0.15, entity.getWidth() * 0.35);
			sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.COBWEB.getDefaultState()),
					entity.getX(), entity.getY() + 0.1, entity.getZ(),
					3, spread, 0.06, spread, 0.0);
		}
		// 挖掘疲劳 + 虚弱：放在蜘网缠身内联动，随蜘网缠身同进退（环境/ambient=false，粒子=false）
		entity.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, SUB_DURATION, 0, false, false, false));
		entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, SUB_DURATION, 0, false, false, false));
	}
}
