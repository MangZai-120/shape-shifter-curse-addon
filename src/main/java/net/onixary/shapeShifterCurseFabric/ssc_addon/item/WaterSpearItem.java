package net.onixary.shapeShifterCurseFabric.ssc_addon.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.EquipmentSlot;

import java.util.List;

import net.onixary.shapeShifterCurseFabric.player_form.ability.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormPhase;

public class WaterSpearItem extends TridentItem {
	// 60 durability consumed over 60 seconds = 1 durability per second = 20 ticks per durability
	private static final int TICKS_PER_DURABILITY = 20;

	public WaterSpearItem(Settings settings) {
		super(settings);
	}

	@Override
	public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		if (user instanceof PlayerEntity playerEntity) {
			int i = this.getMaxUseTime(stack) - remainingUseTicks;
			if (i >= 10) {
				float f = playerEntity.getYaw();
				float g = playerEntity.getPitch();
				float h = -MathHelper.sin(f * ((float) Math.PI / 180)) * MathHelper.cos(g * ((float) Math.PI / 180));
				float j = -MathHelper.sin(g * ((float) Math.PI / 180));
				float k = MathHelper.cos(f * ((float) Math.PI / 180)) * MathHelper.cos(g * ((float) Math.PI / 180));
				float l = MathHelper.sqrt(h * h + j * j + k * k);
				float m = 2.5F;
				h *= m / l;
				j *= m / l;
				k *= m / l;

				if (!world.isClient) {
					// Create custom water spear entity
					WaterSpearEntity waterSpear = new WaterSpearEntity(world, playerEntity, stack);
					waterSpear.setVelocity(playerEntity, playerEntity.getPitch(), playerEntity.getYaw(), 0.0F, m, 1.0F);

					// Remove item immediately after throwing
					stack.decrement(1);

					world.spawnEntity(waterSpear);

					// Play throw sound
					world.playSound(null, playerEntity.getX(), playerEntity.getY(), playerEntity.getZ(), SoundEvents.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 1.0F, 1.0F);
					world.playSound(null, playerEntity.getX(), playerEntity.getY(), playerEntity.getZ(), SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 0.5F, 1.2F);
				}

				playerEntity.incrementStat(Stats.USED.getOrCreateStat(this));
			}
		}
	}

	@Override
	public int getMaxUseTime(ItemStack stack) {
		return 72000;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.SPEAR;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack itemStack = user.getStackInHand(hand);

		PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(user);
		if (component != null && component.getCurrentForm() != null && component.getCurrentForm().FormID != null &&
				component.getCurrentForm().getPhase() == PlayerFormPhase.PHASE_SP && component.getCurrentForm().FormID.getPath().contains("axolotl")) {
			user.setCurrentHand(hand);
			return TypedActionResult.success(itemStack);
		}

		return TypedActionResult.fail(itemStack);
	}


	// Called when hitting an entity with melee
	@Override
	public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		// Apply slowness effect
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 1));

		// Spawn water particles
		if (!target.getWorld().isClient) {
			World world = target.getWorld();
			double x = target.getX();
			double y = target.getY() + target.getHeight() / 2;
			double z = target.getZ();

			// Area damage
			List<Entity> nearbyEntities = world.getOtherEntities(attacker, new Box(x - 1.5, y - 1.5, z - 1.5, x + 1.5, y + 1.5, z + 1.5));
			for (Entity entity : nearbyEntities) {
				if (entity instanceof LivingEntity living && entity != attacker && entity != target) {
					living.damage(world.getDamageSources().mobAttack(attacker), 4.0f);
				}
			}

			world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 1.0F, 0.8F);
		}

		// Damage the item
		stack.damage(1, attacker, e -> e.sendToolBreakStatus(attacker.getActiveHand()));

		return true;
	}

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		super.inventoryTick(stack, world, entity, slot, selected);

		if (!world.isClient) {
			// Check if it is a player
			if (entity instanceof PlayerEntity player) {
				PlayerFormComponent component = RegPlayerFormComponent.PLAYER_FORM.get(player);
				boolean isSpAxolotl = false;

                // Allow creative mode players to hold it for testing
				if (player.isCreative()) {
					isSpAxolotl = true;
				} else if (component != null) {
					PlayerFormBase currentForm = component.getCurrentForm();
					if (currentForm != null && currentForm.FormID != null) {
						// 合并条件判断
						isSpAxolotl = (currentForm.getPhase() == PlayerFormPhase.PHASE_SP && currentForm.FormID.getPath().contains("axolotl"))
								|| currentForm.FormID.getPath().contains("form_axolotl_sp");
					}
				}

				if (!isSpAxolotl) {
					stack.setCount(0);
					return;
				}

			} else {
				// Non-player entities (including TLM Maids) cannot hold this item
				stack.setCount(0);
				return;
			}
		}

		// Auto-consume durability: 60 durability over 60 seconds = 1 per second = every 20 ticks
		if (!world.isClient && entity instanceof LivingEntity livingEntity && world.getTime() % TICKS_PER_DURABILITY == 0) {
			stack.damage(1, livingEntity, e -> {
				// 当耐久度耗尽时，物品将自动断裂
			});
		}

	}

	@Override
	public int getEnchantability() {
		return 0;
	}

	@Override
	public boolean canRepair(ItemStack stack, ItemStack ingredient) {
		return false;
	}

	@Override
	public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
		if (slot == EquipmentSlot.MAINHAND) {
			ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
			builder.put(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(ATTACK_DAMAGE_MODIFIER_ID, "Tool modifier", 7.0, EntityAttributeModifier.Operation.ADDITION));
			builder.put(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(ATTACK_SPEED_MODIFIER_ID, "Tool modifier", -3.0, EntityAttributeModifier.Operation.ADDITION));
			return builder.build();
		}
		return super.getAttributeModifiers(slot);
	}
}
