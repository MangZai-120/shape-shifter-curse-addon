package net.jackcooper.shapeShifterCurseAddon.client.renderer;

public final class WeakEntityStateMapTest {
    private record EntityId(int id) {}

    public static int run() {
        var states = new WeakEntityStateMap<EntityId, int[]>();
        var oldWorld = new EntityId(1);
        var newWorld = new EntityId(1);
        int[] oldState = states.computeIfAbsent(oldWorld, entity -> new int[]{42});
        int[] newState = states.computeIfAbsent(newWorld, entity -> new int[]{7});
        require(oldWorld.equals(newWorld), "Fixture reproduces equal entity IDs in different worlds");
        require(oldState != newState && newState[0] == 7, "Equal numeric IDs cannot share render state");
        require(states.computeIfAbsent(oldWorld, entity -> new int[]{0}) == oldState, "Same instance retains its state");
        states.remove(oldWorld);
        require(states.getOrDefault(oldWorld, null) == null, "Removed instance releases its state");
        require(states.getOrDefault(newWorld, null) == newState, "Removal cannot erase a replacement instance");
        return 5;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
