package net.jackcooper.shapeShifterCurseAddon.mixin.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.util.SscIgnitedEntityAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(Entity.class)
public abstract class SscAddonEntityIgnitionMixin implements SscIgnitedEntityAccessor {

	@Unique
	private UUID sscAddon$igniterUuid;

	// Vanilla fire ticks and the fox-fire status effect can have different casters.
	@Unique
	private UUID sscAddon$fireOwner;

	@Override
	public void sscAddon$setFireOwner(UUID owner) {
		sscAddon$fireOwner = owner;
	}

	@Inject(method = "setFireTicks", at = @At("HEAD"))
	private void sscAddon$clearReplacedFire(int ticks, CallbackInfo ci) {
		if (sscAddon$fireOwner != null && (ticks <= 0 || ticks > ((Entity)(Object)this).getFireTicks())) {
			sscAddon$fireOwner = null;
		}
	}

	@Override
	public void sscAddon$setIgniterUuid(UUID uuid) {
		this.sscAddon$igniterUuid = uuid;
	}

	@Override
	public UUID sscAddon$getIgniterUuid() {
		return this.sscAddon$igniterUuid;
	}

	@Inject(method = "writeNbt", at = @At("HEAD"))
	private void injectWriteNbt(NbtCompound nbt, CallbackInfoReturnable<NbtCompound> cir) {
		if (this.sscAddon$igniterUuid != null) {
			nbt.putUuid("SscAddonIgniter", this.sscAddon$igniterUuid);
		}
		if (sscAddon$fireOwner != null) {
			nbt.putUuid("SscAddonFireOwner", sscAddon$fireOwner);
		} else if (sscAddon$igniterUuid != null && ((Entity)(Object)this).getFireTicks() > 0) {
			// Older saves used a shared igniter. Record explicitly when this fire has no owner.
			nbt.putBoolean("SscAddonFireOwner", false);
		}
	}

	@Inject(method = "readNbt", at = @At("HEAD"))
	private void injectReadNbt(NbtCompound nbt, CallbackInfo ci) {
		sscAddon$igniterUuid = nbt.containsUuid("SscAddonIgniter") ? nbt.getUuid("SscAddonIgniter") : null;
		sscAddon$fireOwner = null;
		if (nbt.getShort("Fire") > 0) {
			if (nbt.containsUuid("SscAddonFireOwner")) sscAddon$fireOwner = nbt.getUuid("SscAddonFireOwner");
			else if (!nbt.contains("SscAddonFireOwner")) sscAddon$fireOwner = sscAddon$igniterUuid;
		}
	}

	@ModifyArg(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"), index = 0)
	private DamageSource modifyFireDamageSource(DamageSource source) {
		if (source.isOf(DamageTypes.ON_FIRE)) {
			UUID igniter = sscAddon$fireOwner;
			if (igniter != null) {
				Entity entity = (Entity) (Object) this;
				if (!entity.getWorld().isClient) {
					PlayerEntity player = entity.getWorld().getPlayerByUuid(igniter);
					if (player != null) {
						return new DamageSource(source.getTypeRegistryEntry(), null, player);
					}
				}
			}
		}
		return source;
	}

	/**
	 * 装死（PLAYING_DEAD）期间锁定视角，取消视角输入。（原 SscAddonEntityMixin 合并至此；行为不变。）
	 */
	@Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
	private void ssc_addon$onChangeLookDirectionPlayDead(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		if ((Object) this instanceof LivingEntity entity && entity.hasStatusEffect(SscAddon.PLAYING_DEAD)) {
			ci.cancel();
		}
	}
}
