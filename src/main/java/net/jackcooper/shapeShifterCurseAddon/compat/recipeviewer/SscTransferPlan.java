package net.jackcooper.shapeShifterCurseAddon.compat.recipeviewer;

import net.minecraft.item.ItemStack;
import java.util.*;

/** Plans on copies before sending any clicks. Slot indices are grid first, then the main inventory. */
public final class SscTransferPlan {
    public enum Outcome { SUCCESS, NOT_ENOUGH, NO_ROOM, CURSOR_NOT_EMPTY, FAILED }
    public record Click(int slot, int button) {}
    public record Plan(Outcome outcome, List<Click> clicks, List<ItemStack> result) {}
    private SscTransferPlan() {}

    public static Plan create(List<List<ItemStack>> required, List<ItemStack> grid, List<ItemStack> inventory, ItemStack cursor) {
        if (!cursor.isEmpty()) return failure(Outcome.CURSOR_NOT_EMPTY);
        if (required.size() != grid.size()) return failure(Outcome.FAILED);
        var slots = new ArrayList<ItemStack>();
        grid.forEach(stack -> slots.add(stack.copy()));
        inventory.forEach(stack -> slots.add(stack.copy()));
        int[] available = slots.stream().mapToInt(ItemStack::getCount).toArray();
        if (!allocate(required, slots, available, new int[required.size()], 0)) return failure(Outcome.NOT_ENOUGH);
        boolean complete = true;
        for (int i = 0; i < grid.size(); i++)
            complete &= required.get(i).isEmpty() ? grid.get(i).isEmpty() : matchesAny(grid.get(i), required.get(i));
        if (complete) return new Plan(Outcome.SUCCESS, List.of(), slots);

        var clicks = new ArrayList<Click>();
        // Simulate clearing, including NBT-aware merge limits. Failure never picks up a real stack.
        for (int i = 0; i < grid.size(); i++) {
            ItemStack remaining = slots.get(i).copy();
            if (remaining.isEmpty()) continue;
            clicks.add(new Click(i, 0));
            slots.set(i, ItemStack.EMPTY);
            for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
                for (int j = grid.size(); j < slots.size() && !remaining.isEmpty(); j++) {
                    ItemStack destination = slots.get(j);
                    if (pass == 0 && (destination.isEmpty() || !ItemStack.canCombine(destination, remaining))) continue;
                    if (pass == 1 && !destination.isEmpty()) continue;
                    int capacity = destination.isEmpty() ? remaining.getMaxCount() : destination.getMaxCount() - destination.getCount();
                    int count = Math.min(capacity, remaining.getCount());
                    if (count <= 0) continue;
                    clicks.add(new Click(j, 0));
                    if (destination.isEmpty()) { destination = remaining.copy(); destination.setCount(count); slots.set(j, destination); }
                    else destination.increment(count);
                    remaining.decrement(count);
                }
            }
            if (!remaining.isEmpty()) return failure(Outcome.NO_ROOM);
        }
        int[] sources = new int[required.size()];
        available = slots.stream().mapToInt(ItemStack::getCount).toArray();
        if (!allocate(required, slots, available, sources, 0)) return failure(Outcome.NOT_ENOUGH);
        for (int i = 0; i < sources.length; i++) {
            if (required.get(i).isEmpty()) continue;
            int source = sources[i];
            ItemStack stack = slots.get(source);
            clicks.add(new Click(source, 0));
            clicks.add(new Click(i, 1));
            ItemStack one = stack.copy(); one.setCount(1); slots.set(i, one);
            stack.decrement(1);
            if (!stack.isEmpty()) clicks.add(new Click(source, 0));
            else slots.set(source, ItemStack.EMPTY);
        }
        return new Plan(Outcome.SUCCESS, List.copyOf(clicks), slots);
    }

    // Bipartite matching over at most nine units per source avoids exponential retries on missing items.
    private static boolean allocate(List<List<ItemStack>> required, List<ItemStack> slots, int[] available, int[] chosen, int ignored) {
        var units = new ArrayList<Integer>();
        for (int i = 0; i < available.length; i++)
            for (int count = 0; count < Math.min(required.size(), available[i]); count++) units.add(i);
        int[] owners = new int[units.size()]; Arrays.fill(owners, -1);
        for (int target = 0; target < required.size(); target++) {
            if (required.get(target).isEmpty()) continue;
            if (!assign(target, required, slots, units, owners, new boolean[units.size()])) return false;
        }
        for (int i = 0; i < owners.length; i++) if (owners[i] >= 0) chosen[owners[i]] = units.get(i);
        return true;
    }

    private static boolean assign(int target, List<List<ItemStack>> required, List<ItemStack> slots,
                                  List<Integer> units, int[] owners, boolean[] visited) {
        for (int unit = 0; unit < units.size(); unit++) {
            if (visited[unit] || !matchesAny(slots.get(units.get(unit)), required.get(target))) continue;
            visited[unit] = true;
            if (owners[unit] < 0 || assign(owners[unit], required, slots, units, owners, visited)) {
                owners[unit] = target; return true;
            }
        }
        return false;
    }

    private static boolean matchesAny(ItemStack stack, List<ItemStack> alternatives) {
        return alternatives.stream().anyMatch(want -> SscViewerRecipes.matches(stack, want));
    }
    private static Plan failure(Outcome outcome) { return new Plan(outcome, List.of(), List.of()); }
}
