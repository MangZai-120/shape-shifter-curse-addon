package net.jackcooper.shapeShifterCurseAddon.network;

import java.util.EnumSet;

/** The balance ACK also confirms both registries from the announced configuration transfer. */
public final class ConfigurationReceipt {
    private long session = -1, revision = -1, appearanceSession = -1;
    private final EnumSet<JsonSnapshotWire.Kind> applied = EnumSet.noneOf(JsonSnapshotWire.Kind.class);

    public void begin(long session, long revision) {
        if (session < 0 || revision < 0) throw new IllegalArgumentException("Invalid configuration session");
        if (session < this.session) return;
        this.session = session; this.revision = revision; applied.clear();
    }

    public void applied(JsonSnapshotWire.Kind kind) { applied.add(kind); }

    public boolean canCommit(long session, long revision) {
        // Older servers have no BEGIN packet and retain their original balance protocol.
        return this.session < 0 || this.session == session && this.revision == revision
                && applied.size() == JsonSnapshotWire.Kind.values().length;
    }

    public boolean needsAppearanceRefresh(long session) {
        if (appearanceSession == session) return false;
        appearanceSession = session;
        return true;
    }

    public void clear() { session = revision = appearanceSession = -1; applied.clear(); }
}
