package net.jackcooper.shapeShifterCurseAddon.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.UUID;

public interface SscIgnitedEntityAccessor {
	void sscAddon$setIgniterUuid(UUID uuid);

	UUID sscAddon$getIgniterUuid();

	void sscAddon$setFireOwner(UUID owner);

	static void setSpellFire(LivingEntity target, int ticks, Entity caster) {
		target.setFireTicks(ticks);
		((SscIgnitedEntityAccessor) target).sscAddon$setFireOwner(
				ticks > 0 && caster instanceof ServerPlayerEntity && caster != target ? caster.getUuid() : null);
	}
}
