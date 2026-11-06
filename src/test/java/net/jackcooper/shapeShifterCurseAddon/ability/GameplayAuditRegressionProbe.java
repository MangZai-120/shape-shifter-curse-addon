package net.jackcooper.shapeShifterCurseAddon.ability;

import net.jackcooper.shapeShifterCurseAddon.event.LoginResourceRestoreHandler;
import net.jackcooper.shapeShifterCurseAddon.resource.BarKeys;
import net.jackcooper.shapeShifterCurseAddon.spell.FrostArmorAccess;
import net.jackcooper.shapeShifterCurseAddon.spell.FrostArmorState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;

import java.util.Set;

/** Actual lease transitions, saved absorption budgets and item NBT; no live gameplay assertion. */
public final class GameplayAuditRegressionProbe {
    private static int checks;

    public static void run() throws Exception {
        sourceLeases();
        absorptionPersistence();
        totemData();
        for (var bar : BarKeys.ALL) check(LoginResourceRestoreHandler.isPersistentResource(bar.id), "persistent bar allowed");
        check(!LoginResourceRestoreHandler.isPersistentResource(new Identifier("my_addon", "form_upgrade_axolotl_sp_vortex_state")), "charge state excluded");
        check(!LoginResourceRestoreHandler.isPersistentResource(new Identifier("my_addon", "form_snow_fox_sp_regen_cooldown")), "timer excluded");
        System.out.println("Gameplay audit regressions: " + checks + " checks passed. Live gameplay NOT tested.");
    }

    private static void sourceLeases() {
        var leases = new SourceLeaseBook<String, String>();
        leases.renew("target", "first", 20);
        leases.renew("target", "second", 25);
        check(leases.release("first").isEmpty(), "stopping one overlapping jukebox retains other source");
        check(leases.expire(24).isEmpty(), "remaining source lease remains valid");
        check(leases.expire(25).equals(Set.of("target")), "target expires even after teleporting beyond any query range");
        leases.renew("oldTarget", "source", 60);
        leases.renew("newTarget", "source", 60);
        check(leases.retain("source", Set.of("newTarget")).equals(Set.of("oldTarget")), "range change releases only old target");
        check(leases.clear().equals(Set.of("newTarget")), "server shutdown releases tracked benefits");
        check(leases.expire(0).isEmpty(), "next save has no prior leases");
    }

    private static void absorptionPersistence() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) field.get(null);
        TestMob original = (TestMob) unsafe.allocateInstance(TestMob.class);
        original.state = new ParasiticAbsorptionState();
        original.frost = new FrostArmorState();
        original.absorption = 6; // An independent source must survive this grant and expiration.
        original.state.grant(original, 4);
        original.state.refresh(300);
        NbtCompound saved = new NbtCompound();
        original.state.write(saved);
        TestMob loaded = (TestMob) unsafe.allocateInstance(TestMob.class);
        loaded.state = new ParasiticAbsorptionState();
        loaded.frost = new FrostArmorState();
        loaded.absorption = original.absorption;
        loaded.state.read(saved, loaded);
        check(loaded.state.remaining() == 4, "non-player owned budget survives NBT reload");
        check(!loaded.state.expired(299) && loaded.state.expired(300), "absolute expiry survives reload and elapsed unloaded time");
        loaded.state.remove(loaded);
        check(loaded.absorption == 6 && loaded.state.remaining() == 0, "expiry removes own budget and preserves other source");
        loaded.state.grant(loaded, 4);
        loaded.setAbsorptionAmount(8); // Consume two HP from the parasitic share.
        check(loaded.state.remaining() == 2, "damage reduces the owned budget");
        loaded.state.remove(loaded);
        check(loaded.absorption == 6, "expired consumed shield does not take remaining independent absorption");
        loaded.state.grant(loaded, 4);
        loaded.frost.grant(loaded, 14); // Four HP frost on top of ten HP total.
        loaded.setAbsorptionAmount(12);
        check(loaded.frost.remaining() == 2 && loaded.state.remaining() == 4, "damage consumes frost before parasitic share");
        loaded.state.remove(loaded);
        check(loaded.absorption == 8 && loaded.frost.remaining() == 2, "parasitic expiration preserves frost armor");
        loaded.frost.remove(loaded);
        loaded.state.grant(loaded, 4);
        FrostArmorState.removeOther(loaded, 6);
        check(loaded.absorption == 4 && loaded.state.remaining() == 4, "other absorption expiry preserves parasitic budget");
        loaded.frost.grant(loaded, 8);
        loaded.frost.remove(loaded);
        check(loaded.absorption == 4 && loaded.state.remaining() == 4, "frost expiry preserves parasitic budget");
        loaded.state.remove(loaded);
        check(loaded.absorption == 0, "last owned source expires without permanent absorption");
    }

    private static void totemData() {
        ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
        NbtCompound nbt = totem.getOrCreateNbt();
        NbtList enchantments = new NbtList();
        NbtCompound enchantment = new NbtCompound();
        enchantment.putString("id", "minecraft:mending");
        enchantment.putShort("lvl", (short) 1);
        enchantments.add(enchantment);
        nbt.put("Enchantments", enchantments);
        nbt.putInt("HideFlags", 37);
        NbtCompound original = nbt.copy();
        nbt.putBoolean("ssc_totem_active", true);
        nbt.putUuid("ssc_totem_owner", java.util.UUID.randomUUID());
        AllaySPTotem.clearTotemMark(totem);
        check(totem.getNbt().equals(original), "deactivation preserves command/mod enchantments and display flags exactly");
    }

    private static void check(boolean value, String description) {
        checks++;
        if (!value) throw new AssertionError(description);
    }

    public static final class TestMob extends PathAwareEntity implements FrostArmorAccess, ParasiticAbsorptionAccess {
        float absorption;
        ParasiticAbsorptionState state;
        FrostArmorState frost;
        private TestMob() { super(EntityType.PIG, null); }
        @Override public FrostArmorState ssca$frostArmor() { return frost; }
        @Override public ParasiticAbsorptionState ssca$parasiticAbsorption() { return state; }
        @Override public float getAbsorptionAmount() { return absorption; }
        @Override public void setAbsorptionAmount(float amount) {
            state.onChanged(this, absorption, amount);
            frost.onAbsorptionChanged(absorption, amount);
            absorption = amount;
        }
    }
}
