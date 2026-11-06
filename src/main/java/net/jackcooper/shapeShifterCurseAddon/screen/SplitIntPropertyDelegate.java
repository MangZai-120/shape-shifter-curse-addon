package net.jackcooper.shapeShifterCurseAddon.screen;

import net.minecraft.screen.PropertyDelegate;

/** Encodes the first wideCount integers as two unsigned 16-bit screen properties. */
public final class SplitIntPropertyDelegate implements PropertyDelegate {
    private final PropertyDelegate source;
    private final int wideCount;

    public SplitIntPropertyDelegate(PropertyDelegate source, int wideCount) {
        if (wideCount < 0 || wideCount > source.size()) throw new IllegalArgumentException("wideCount");
        this.source = source;
        this.wideCount = wideCount;
    }

    @Override
    public int get(int index) {
        if (index < wideCount * 2) return (source.get(index / 2) >>> ((index & 1) * 16)) & 0xffff;
        return source.get(index - wideCount);
    }

    @Override
    public void set(int index, int value) {
        if (index < wideCount * 2) {
            int shift = (index & 1) * 16;
            int previous = source.get(index / 2);
            source.set(index / 2, (previous & ~(0xffff << shift)) | ((value & 0xffff) << shift));
        } else source.set(index - wideCount, value);
    }

    @Override
    public int size() { return source.size() + wideCount; }
}
