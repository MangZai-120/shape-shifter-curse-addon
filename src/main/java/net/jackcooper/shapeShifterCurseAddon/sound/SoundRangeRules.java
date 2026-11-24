package net.jackcooper.shapeShifterCurseAddon.sound;

/** Only distances change: call volume, material volume, pitch and recipient rules stay intact. */
public final class SoundRangeRules {
    public static final int MULTIPLIER = 2;
    private static final String ROOT = "net.jackcooper.shapeShifterCurseAddon.";
    private static final StackWalker WALKER = StackWalker.getInstance();
    private static final ThreadLocal<Boolean> ACTION_SCOPE = ThreadLocal.withInitial(() -> false);
    private static final ClassValue<Boolean> ADDON_SOURCE_TYPE = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            return isAddonSource(type.getNestHost().getName())
                    || type.getSuperclass() != null && get(type.getSuperclass());
        }
    };

    private SoundRangeRules() {}

    public static double distance(double original) { return original * MULTIPLIER; }
    public static boolean inActionScope() { return ACTION_SCOPE.get(); }

    public static void withActionScope(Runnable action) {
        if (inActionScope()) {
            action.run();
            return;
        }
        ACTION_SCOPE.set(true);
        try { action.run(); }
        finally { ACTION_SCOPE.set(false); }
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

    /** An addon's entity may delegate movement or impact audio to a vanilla superclass. */
    public static boolean isInheritedEntityPlayback(Class<?> type) {
        return !inActionScope() && isAddonSourceType(type) && isAddonPlayback();
    }

    public static boolean isAddonSourceType(Class<?> type) {
        return ADDON_SOURCE_TYPE.get(type);
    }

    /** Compatibility for sound delegated to commands, damage handlers or entity superclasses. */
    public static boolean isAddonPlayback() {
        return inActionScope() || WALKER.walk(frames -> frames.anyMatch(frame -> isAddonSource(frame.getClassName())));
    }

    public static void withInheritedSource(Runnable action) {
        if (isAddonPlayback()) withActionScope(action);
        else action.run();
    }
}
