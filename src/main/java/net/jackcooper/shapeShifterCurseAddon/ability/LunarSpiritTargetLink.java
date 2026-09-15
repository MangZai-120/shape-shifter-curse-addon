package net.jackcooper.shapeShifterCurseAddon.ability;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.jackcooper.shapeShifterCurseAddon.entity.LunarSpiritBoltEntity;
import net.jackcooper.shapeShifterCurseAddon.entity.LunarSpiritEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

/**
 * 月灵目标联动（jackcooper，服务端权威）。挂 {@code ALLOW_DAMAGE}（伤害结算前记录，return true 不拦截）：
 * <ul>
 *   <li>主人（玩家）亲手对生物造成伤害 → 该玩家场上全部月灵以最高优先级集火受害者（「你打谁月灵就打谁」）。
 *       月灵光弹的伤害源是光弹本体，明确排除，避免多只月灵每次命中都互相抢目标；</li>
 *   <li>主人被生物攻击 → 没有更高优先级目标的月灵反击攻击者（护主）。</li>
 * </ul>
 * 合法性（白名单 / 同主人月灵 / 创造玩家 / 14 格内）由 {@link LunarSpiritEntity#offerTarget} 统一校验。
 * 月灵始终在主人 3 格内，故只需在主人周围 8 格找月灵。
 */
public final class LunarSpiritTargetLink {
	private LunarSpiritTargetLink() {
	}

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
			if (!(victim.getWorld() instanceof ServerWorld serverWorld)) {
				return true;
			}
			Entity attackerEntity = source.getAttacker();
			// 情形一：主人亲手造成伤害（近战 / 箭 / 法术，attacker 都是玩家）→ 集火；光弹命中排除
			if (attackerEntity instanceof ServerPlayerEntity player
					&& !(source.getSource() instanceof LunarSpiritBoltEntity)) {
				if (victim instanceof LunarSpiritEntity spirit && player.getUuid().equals(spirit.getOwnerUuid())) {
					return true; // 主人误击自己的月灵不算
				}
				for (LunarSpiritEntity spirit : spiritsOf(serverWorld, player)) {
					spirit.offerTarget(victim, LunarSpiritEntity.TIER_OWNER_ATTACKED);
				}
				return true;
			}
			// 情形二：主人被攻击 → 月灵护主反击
			if (victim instanceof ServerPlayerEntity owner
					&& attackerEntity instanceof LivingEntity attacker && attacker != owner) {
				for (LunarSpiritEntity spirit : spiritsOf(serverWorld, owner)) {
					spirit.offerTarget(attacker, LunarSpiritEntity.TIER_DEFENSE);
				}
			}
			return true; // 不拦截伤害本身
		});
	}

	private static List<LunarSpiritEntity> spiritsOf(ServerWorld serverWorld, PlayerEntity owner) {
		return serverWorld.getEntitiesByClass(LunarSpiritEntity.class,
				owner.getBoundingBox().expand(8.0),
				spirit -> owner.getUuid().equals(spirit.getOwnerUuid()));
	}
}
