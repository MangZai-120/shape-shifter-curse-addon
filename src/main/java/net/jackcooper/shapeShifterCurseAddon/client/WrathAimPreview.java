package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.jackcooper.shapeShifterCurseAddon.spell.ScrollData;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneCastContext;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneScheme;
import net.jackcooper.shapeShifterCurseAddon.spell.spells.LockedTargetSpell;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

/** The caster sees one pointer and outline after their own server whitelist accepts the candidate. */
public final class WrathAimPreview {
    private static LivingEntity candidate;
    private static ClientPlayerEntity viewer;
    private static ItemStack scroll;
    private static int sequence;
    private static boolean allowed;

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(LockedTargetSpell.AIM_CHECK,(client,handler,buf,sender)->{
            int reply=buf.readVarInt();
            boolean accepted=buf.readBoolean();
            client.execute(()->{ if (client.getNetworkHandler()==handler && reply==sequence) allowed=accepted; });
        });
        ClientTickEvents.END_CLIENT_TICK.register(WrathAimPreview::tick);
    }

    private static void tick(MinecraftClient client) {
        var player=client.player;
        ItemStack held=player!=null && player.isAlive() && client.currentScreen==null ? SpellcastClient.heldAimScroll(player) : ItemStack.EMPTY;
        LivingEntity next=null;
        if (!held.isEmpty() && ScrollData.getSpell(held) instanceof LockedTargetSpell) {
            int level=player.isUsingItem() && held==player.getActiveItem() ? ScrollData.getLevel(held) : ScrollData.getCastLevel(held);
            next=RuneCastContext.with(RuneScheme.modifiers(player,held,level),()->LockedTargetSpell.aim(player));
        }
        boolean changed=next!=candidate || viewer!=player || scroll!=held;
        viewer=player; candidate=next; scroll=held;
        if (changed) { sequence++; allowed=false; }
        if (next!=null && (changed || player.age%10==0)) {
            var buf=PacketByteBufs.create();
            buf.writeVarInt(next.getId()); buf.writeVarInt(sequence);
            buf.writeIdentifier(ScrollData.getSpell(held).getId());
            ClientPlayNetworking.send(LockedTargetSpell.AIM_CHECK,buf);
        }
    }

    public static LivingEntity target() {
        if (!allowed || candidate==null) return null;
        var client=MinecraftClient.getInstance();
        return client.player==viewer && client.world==candidate.getWorld() && client.currentScreen==null
                && candidate.isAlive() && !candidate.isRemoved() && SpellcastClient.heldAimScroll(viewer)==scroll ? candidate : null;
    }

    public static boolean isTarget(Entity entity) { return entity!=null && entity==target(); }
}
