package net.jackcooper.shapeShifterCurseAddon.cooldown;

import net.minecraft.network.PacketByteBuf;
import java.util.ArrayList;
import java.util.List;

/** Shared wire codec used by both the server and the client. */
public final class SkillCooldownPacket {
    private SkillCooldownPacket() {}

    public static void write(PacketByteBuf buf, List<SkillCastManager.View> views) {
        if (views.size() > 4096) throw new IllegalArgumentException("Too many skill states");
        buf.writeVarInt(views.size());
        for (var view : views) {
            buf.writeString(view.skill());
            buf.writeVarInt(view.phase());
            buf.writeVarInt(view.remaining());
            buf.writeVarInt(view.total());
        }
    }

    public static List<SkillCastManager.View> read(PacketByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > 4096) throw new IllegalArgumentException("Invalid skill snapshot size");
        List<SkillCastManager.View> views = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String skill = buf.readString();
            int phase = buf.readVarInt(), remaining = buf.readVarInt(), total = buf.readVarInt();
            if (phase < 0 || phase > 2 || remaining < 0 || total < 0 || remaining > total)
                throw new IllegalArgumentException("Invalid skill state: " + skill);
            views.add(new SkillCastManager.View(skill, phase, remaining, total));
        }
        return List.copyOf(views);
    }
}
