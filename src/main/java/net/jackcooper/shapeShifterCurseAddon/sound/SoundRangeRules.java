package net.jackcooper.shapeShifterCurseAddon.sound;

/** Only distances change: call volume, material volume, pitch and recipient rules stay intact. */
public final class SoundRangeRules {
    public static final int MULTIPLIER = 2;
    private static final String ROOT = "net.jackcooper.shapeShifterCurseAddon.";
    private static final StackWalker WALKER = StackWalker.getInstance();
    private static final ThreadLocal<Boolean> ACTION_SCOPE = ThreadLocal.withInitial(() -> false);

    private SoundRangeRules() {}

    public static double distance(double original) { return original * MULTIPLIER; }
    public static boolean inActionScope() { return ACTION_SCOPE.get(); }

    public static void withActionScope(Runnable action) {
        boolean previous = ACTION_SCOPE.get();
        ACTION_SCOPE.set(true);
        try { action.run(); }
        finally {
            if (previous) ACTION_SCOPE.set(true);
            else ACTION_SCOPE.remove();
        }
    }

    public static boolean isAddonSource(String className) {
        if (!className.startsWith(ROOT)) return false;
        String name = className.substring(ROOT.length());
        return name.startsWith("spell.") || name.startsWith("ability.") || name.startsWith("power.")
                || name.startsWith("entity.") || name.startsWith("action.")
                || name.equals("item.WaterSpearItem") || name.equals("item.WaterSpearEntity")
                || name.equals("item.AllayJukeboxItem") || name.equals("item.AllayHealWandItem")
                || name.equals("effect.SheepFormEffect") || name.equals("effect.PreInvisibilityEffect")
                || name.equals("block.WebMembraneBlock") || name.equals("event.FluorescentDodgeHandler")
                || name.equals("client.NightmareFearClient")
                || name.equals("client.sound.SpellChargeSoundInstance");
    }

    public static boolean isAddonPlayback() {
        return inActionScope() || WALKER.walk(frames -> frames.anyMatch(frame -> isAddonSource(frame.getClassName())));
    }
}
