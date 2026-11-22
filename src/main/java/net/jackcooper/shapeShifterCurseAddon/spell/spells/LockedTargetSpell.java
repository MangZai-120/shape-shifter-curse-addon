package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneCastContext;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneModifiers;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** A single living target selected on release. The server authorizes the private aim preview and the cast independently. */
public abstract class LockedTargetSpell extends Spell {
    public static final Identifier AIM_CHECK = new Identifier("ssc_addon", "wrath_aim_check");
    private static final double RANGE = 24;
    private final WeakHashMap<ServerPlayerEntity, UUID> locks = new WeakHashMap<>();

    protected LockedTargetSpell(String path) {
        super(new Identifier("ssc_addon", path), SpellRarity.WHITE);
    }

    @Override public Identifier getIconTexture() { return new Identifier("ssc_addon", "textures/item/scroll_mask_base.png"); }
    @Override public boolean requiresTargetBeforeChannel() { return true; }
    @Override public SpellCastingRules.Mode getCastingMode() { return SpellCastingRules.Mode.RELEASE; }
    @Override public Text getCastFailureMessage(ServerPlayerEntity caster) { return Text.translatable("message.ssc_addon.spellbook.no_entity_target"); }

    public static void registerAimCheck() {
        ServerPlayNetworking.registerGlobalReceiver(AIM_CHECK, (server, player, handler, buf, sender) -> {
            int id = buf.readVarInt();
            int sequence = buf.readVarInt();
            Identifier spellId = buf.isReadable() ? buf.readIdentifier() : new Identifier("ssc_addon", "wrath_mark");
            server.execute(() -> {
                var entity = player.getWorld().getEntityById(id);
                boolean allowed = SpellRegistry.get(spellId) instanceof LockedTargetSpell spell
                        && entity instanceof LivingEntity living && spell.eligible(player, living)
                        && player.squaredDistanceTo(living) <= Math.pow(RANGE * 1.6, 2);
                var reply = PacketByteBufs.create();
                reply.writeVarInt(sequence);
                reply.writeBoolean(allowed);
                ServerPlayNetworking.send(player, AIM_CHECK, reply);
            });
        });
    }

    private static double range() { return RuneCastContext.current().scale(RANGE, RuneModifiers.Stat.DISTANCE); }
    public static LivingEntity aim(PlayerEntity caster) {
        double reach = range();
        LivingEntity hit = LunarPhaseSpell.raycastEntity(caster, reach, 0.75);
        return hit != null && caster.squaredDistanceTo(hit) <= reach * reach ? hit : null;
    }

    protected boolean eligible(ServerPlayerEntity caster, LivingEntity target) {
        return target != null && target != caster && !(target instanceof ArmorStandEntity)
                && target.isAlive() && !target.isSpectator()
                && !WhitelistUtils.isProtected(caster, target) && !DomainManager.blocksTargeting(caster, target);
    }

    @Override public double getCastingLockRange(ServerPlayerEntity caster, int level) { return range(); }
    @Override public Vec3d captureCastTarget(ServerPlayerEntity caster, int level) {
        LivingEntity target = aim(caster);
        if (!eligible(caster, target)) {
            locks.remove(caster);
            return null;
        }
        locks.put(caster, target.getUuid());
        return target.getPos();
    }

    @Override public LivingEntity getCastingLockEntity(ServerPlayerEntity caster) {
        UUID id = locks.get(caster);
        return id != null && caster.getServerWorld().getEntity(id) instanceof LivingEntity target ? target : null;
    }

    protected LivingEntity takeTarget(ServerPlayerEntity caster) {
        LivingEntity target = getCastingLockEntity(caster);
        locks.remove(caster);
        return eligible(caster, target) ? target : null;
    }

    @Override public boolean canContinueCasting(ServerPlayerEntity caster, ItemStack scroll) {
        return eligible(caster, getCastingLockEntity(caster));
    }
    @Override public void onChannelEnded(ServerPlayerEntity caster, boolean interrupted) {
        if (interrupted) locks.remove(caster);
    }
}
