package net.jackcooper.shapeShifterCurseAddon.spell;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.onyxstudios.cca.api.v3.component.ComponentContainer;
import io.github.apace100.apoli.component.PowerHolderComponent;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.client.hud.SpellCastHud;
import net.jackcooper.shapeShifterCurseAddon.item.MagicScrollItem;
import net.jackcooper.shapeShifterCurseAddon.spell.config.SpellConfig;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneCastContext;
import net.jackcooper.shapeShifterCurseAddon.spell.research.RuneModifiers;
import net.jackcooper.shapeShifterCurseAddon.spell.spells.FrostArmorSpell;
import net.jackcooper.shapeShifterCurseAddon.spell.spells.SpaceBlinkSpell;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public final class SpellAuditIntegrationProbe {
    private static sun.misc.Unsafe unsafe;
    private static int checks;

    public static void run() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (sun.misc.Unsafe) field.get(null);
        SpellRegistry.init();
        for (Spell spell : SpellRegistry.all()) spell.ssc_addon$applyConfig(SpellConfig.fromJson(json(spell.getId().getPath())));
        malformedLevels();
        rangesAndProfile();
        scrollRejection();
        frostArmor();
        spellProtection();
        statePackets();
        previewWiring();
        System.out.println("Spell audit integration: " + checks + " checks passed (real parser/item/effect/absorption/protection mixins and packet roundtrip). No live UI/network acceptance.");
    }

    private static void spellProtection() throws Exception {
        var resistance = net.jackcooper.shapeShifterCurseAddon.enchantment.RegAddonEnchantments.SPELL_RESISTANCE;
        if (!Registries.ENCHANTMENT.containsId(new Identifier("ssc_addon", "spell_resistance"))) {
            net.jackcooper.shapeShifterCurseAddon.enchantment.RegAddonEnchantments.init();
        }
        check(resistance.getMaxLevel() == 5, "spell protection maximum level remains five");
        for (var protection : List.of(Enchantments.PROTECTION, Enchantments.FIRE_PROTECTION,
                Enchantments.BLAST_PROTECTION, Enchantments.PROJECTILE_PROTECTION)) {
            check(!resistance.canCombine(protection), "magic/protection anvil conflict forward " + protection);
            check(!protection.canCombine(resistance), "magic/protection anvil conflict reverse " + protection);
        }
        check(resistance.canCombine(Enchantments.UNBREAKING) && Enchantments.UNBREAKING.canCombine(resistance),
                "unbreaking compatibility is preserved");
        check(resistance.canCombine(Enchantments.FEATHER_FALLING), "feather falling remains compatible");

        var registry = new SimpleRegistry<DamageType>(RegistryKeys.DAMAGE_TYPE, com.mojang.serialization.Lifecycle.stable());
        Registry.register(registry, SpellDamageSource.SPELL_DAMAGE, new DamageType("ssc_addon.spell_damage", 0));
        Registry.register(registry, DamageTypes.MAGIC, new DamageType("magic", 0));
        Registry.register(registry, DamageTypes.PLAYER_ATTACK, new DamageType("player", 0));
        var spellEntry = registry.getEntry(SpellDamageSource.SPELL_DAMAGE).orElseThrow();
        java.util.Map<TagKey<DamageType>, List<net.minecraft.registry.entry.RegistryEntry<DamageType>>> tags = new HashMap<>();
        for (var tag : List.of(DamageTypeTags.BYPASSES_ARMOR, DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
            String path = "data/" + tag.id().getNamespace() + "/tags/damage_type/" + tag.id().getPath() + ".json";
            var urls = SpellAuditIntegrationProbe.class.getClassLoader().getResources(path);
            boolean found = false;
            while (urls.hasMoreElements()) {
                try (var input = urls.nextElement().openStream()) {
                    var data = JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    if (data.getAsJsonArray("values").contains(new com.google.gson.JsonPrimitive("ssc_addon:spell_damage"))) {
                        check(!data.get("replace").getAsBoolean(), "spell tag appends without replacing vanilla " + tag.id());
                        found = true;
                    }
                }
            }
            check(found, "actual packaged tag contains SSCA spell damage " + tag.id());
            tags.put(tag, List.of(spellEntry));
        }
        registry.populateTags(tags);
        DamageSource spell = new DamageSource(spellEntry);
        DamageSource vanillaMagic = new DamageSource(registry.getEntry(DamageTypes.MAGIC).orElseThrow());
        DamageSource physical = new DamageSource(registry.getEntry(DamageTypes.PLAYER_ATTACK).orElseThrow());
        var reducer = java.util.Arrays.stream(LivingEntity.class.getDeclaredMethods())
                .filter(m -> m.getName().contains("spellResistanceReduce")).findFirst().orElseThrow();
        reducer.setAccessible(true);
        TestPlayer target = player();
        for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
            armor.addEnchantment(Enchantments.PROTECTION, 4);
            target.equipment.put(slot, armor);
        }
        near(EnchantmentHelper.getProtectionAmount(target.getArmorItems(), vanillaMagic), 16, "fixture has real four-piece Protection IV");
        near(target.afterProtection(spell, 100), 100, "Protection IV cannot reduce SSCA spell damage");
        near(target.afterProtection(vanillaMagic, 100), 36, "vanilla magic still receives ordinary protection");
        near(target.afterProtection(physical, 100), 36, "physical damage protection remains unchanged");
        target.equipment.clear();
        for (int level = 0; level <= 5; level++) {
            ItemStack armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
            if (level > 0) armor.addEnchantment(resistance, level);
            target.equipment.put(EquipmentSlot.CHEST, armor);
            float reduced = (float) reducer.invoke(target, 100F, spell);
            near(reduced, new float[]{100, 87, 74, 61, 48, 35}[level], "actual injected spell reducer level " + level);
            near(target.afterProtection(spell, reduced), reduced, "spell bypass does not disable custom protection " + level);
            near((float) reducer.invoke(target, 100F, vanillaMagic), 100, "custom protection excludes vanilla/form magic " + level);
            near((float) reducer.invoke(target, 100F, physical), 100, "custom protection excludes physical damage " + level);
        }
        ItemStack helm = new ItemStack(Items.DIAMOND_HELMET);
        helm.addEnchantment(resistance, 2);
        target.equipment.put(EquipmentSlot.HEAD, helm);
        target.equipment.get(EquipmentSlot.CHEST).addEnchantment(Enchantments.PROTECTION, 4);
        float capped = (float) reducer.invoke(target, 100F, spell);
        near(capped, 35, "multiple armor pieces cannot exceed 65 percent reduction");
        near(target.afterProtection(spell, capped), 35, "forced incompatible protection does not stack on magic protection");
        target.equipment.clear();
        target.stack = new ItemStack(Items.ENCHANTED_BOOK);
        target.stack.addEnchantment(resistance, 5);
        near((float) reducer.invoke(target, 100F, spell), 100, "held enchanted book provides no armor protection");
    }

    private static void malformedLevels() {
        SpellConfig config = SpellConfig.fromJson(JsonParser.parseString(
                "{\"mana_cost\":10,\"levels\":[3,{},null,[],{\"damage_multiplier\":2,\"cooldown_multiplier\":0.5,\"speed_multiplier\":1.5}]}").getAsJsonObject());
        for (int level : List.of(1, 2, 3, 4, 6)) {
            near(config.damageMultiplier(level), 1, "malformed/missing damage level " + level);
            near(config.cooldownMultiplier(level), 1, "malformed/missing CD level " + level);
            near(config.speedMultiplier(level), 1, "malformed/missing speed level " + level);
            near(config.manaCostMultiplier(level), 1, "malformed/missing mana level " + level);
        }
        near(config.damageMultiplier(5), 2, "valid damage multiplier preserved");
        near(config.cooldownMultiplier(5), .5, "valid CD multiplier preserved");
        near(config.speedMultiplier(5), 1.5, "valid speed multiplier preserved");
    }

    private static void rangesAndProfile() throws Exception {
        SpaceBlinkSpell blink = (SpaceBlinkSpell) SpellRegistry.get("space_blink");
        Spell devour = SpellRegistry.get("void_devour");
        near(blink.getAimMaxRange(1), 8, "L1 blink preview remains L1");
        near(blink.getAimMaxRange(5), 19.52, "L5 blink preview scales");
        near(devour.getAimMaxRange(1), 16, "L1 devour preview remains L1");
        near(devour.getAimMaxRange(5), 22.4, "L5 devour preview scales");
        int[] modifiers = new int[RuneModifiers.Stat.values().length];
        modifiers[RuneModifiers.Stat.DISTANCE.ordinal()] = 25;
        RuneCastContext.run(new RuneModifiers(modifiers), () -> {
            for (int level = 1; level <= 5; level++) {
                near(blink.getAimMaxRange(level), blink.getBlinkRange(level), "blink effective/preview range " + level);
                near(blink.getAimMaxRange(level), 8 * blink.getSpeedMultiplier(level) * 1.25, "blink rune distance " + level);
                near(devour.getAimMaxRange(level), 16 * devour.getSpeedMultiplier(level) * 1.25, "devour rune distance " + level);
            }
        });
        Spell meteor = SpellRegistry.get("meteor");
        near(meteor.getAimMaxRange(5), meteor.getAimMaxRange(), "legacy fixed-range spell unchanged");
        Spell finale = SpellRegistry.get("death_finale");
        check(finale.getCastingProfile(null, 1, false).ticks() == 390, "death finale default remains JSON 390t");
        JsonObject changed = json("death_finale");
        changed.addProperty("base_cast_time_ticks", 600);
        finale.ssc_addon$applyConfig(SpellConfig.fromJson(changed));
        check(finale.getCastingProfile(null, 1, false).ticks() == 600, "death finale JSON override remains authoritative");
        finale.ssc_addon$applyConfig(SpellConfig.fromJson(json("death_finale")));
    }

    private static void scrollRejection() throws Exception {
        TestPlayer player = player();
        player.purified = true;
        MagicScrollItem item = new MagicScrollItem(new Item.Settings());
        player.stack = new ItemStack(item);
        player.stack.getOrCreateNbt().putString(ScrollData.NBT_SPELL, "fire_bolt");
        player.stack.getOrCreateNbt().putInt(ScrollData.NBT_LEVEL, 1);
        player.stack.getOrCreateNbt().putInt(ScrollData.NBT_USES, 4);
        var result = item.use(player.testWorld, player, Hand.MAIN_HAND);
        check(result.getResult() == ActionResult.FAIL, "late rejected solo returns FAIL");
        check(!SpellChannelManager.isCasting(player), "late rejected solo creates no channel");
        check(!player.isUsingItem() && player.getItemUseTimeLeft() == 0, "late rejected solo never starts vanilla use state");
        check(ScrollData.getUses(player.stack) == 4 && player.stack.getCount() == 1, "late rejection preserves uses and stack");
        check(ScrollData.getCooldownEnd(player.stack) == 0, "late rejection does not start scroll cooldown");
        player.setCurrentHand(Hand.MAIN_HAND);
        check(player.isUsingItem() && player.getItemUseTimeLeft() == 72000, "fixture uses real vanilla use-state tracker");
    }

    private static void frostArmor() throws Exception {
        for (float power : new float[]{8, 12, 16, 20, 24, 2.5f, 27.6f}) {
            TestPlayer player = player();
            castArmor(player, power);
            near(player.getAbsorptionAmount(), power, "exact shield amount " + power);
            player.removeStatusEffect(SscAddon.FROST_ARMOR);
            near(player.getAbsorptionAmount(), 0, "expiry removes complete shield " + power);
            near(state(player).remaining(), 0, "expiry clears ownership " + power);
        }
        TestPlayer player = player();
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 1000, 1));
        castArmor(player, 24);
        near(player.getAbsorptionAmount(), 24, "pre-existing golden hearts do not stack frost");
        near(state(player).remaining(), 16, "only frost top-up is owned");
        check(player.getStatusEffect(StatusEffects.ABSORPTION).getDuration() == 1000, "frost does not change golden-heart duration");
        player.setAbsorptionAmount(20); // Same production setter used by actual absorption damage.
        near(state(player).remaining(), 12, "real PlayerEntity setter consumes frost first");
        player.removeStatusEffect(SscAddon.FROST_ARMOR);
        near(player.getAbsorptionAmount(), 8, "expiry preserves pre-existing other absorption after damage");

        player = player();
        castArmor(player, 24);
        castArmor(player, 24);
        near(player.getAbsorptionAmount(), 24, "recast never stacks");
        castArmor(player, 8);
        near(player.getAbsorptionAmount(), 24, "lower-level refresh never lowers existing shield");
        StatusEffectInstance refreshing = player.getStatusEffect(SscAddon.FROST_ARMOR);
        for (int tick = 0; tick < 100; tick++) refreshing.update(player, () -> {});
        castArmor(player, 8);
        near(player.getAbsorptionAmount(), 24, "lower-level duration extension does not clear stronger shield");
        near(state(player).remaining(), 24, "duration extension retains ownership");
        check(player.getStatusEffect(SscAddon.FROST_ARMOR).getDuration() == 400, "recast actually extends duration");
        player.setAbsorptionAmount(4);
        castArmor(player, 12);
        near(player.getAbsorptionAmount(), 12, "recast refills spent frost to target");
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 1000, 0));
        player.removeStatusEffect(SscAddon.FROST_ARMOR);
        near(player.getAbsorptionAmount(), 4, "later golden hearts survive frost removal");

        for (int damage : new int[]{0, 4, 20, 30}) {
            player = player();
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 1000, 1));
            castArmor(player, 24);
            player.setAbsorptionAmount(Math.max(0, player.getAbsorptionAmount() - damage));
            float owned = state(player).remaining();
            player.removeStatusEffect(StatusEffects.ABSORPTION);
            near(player.getAbsorptionAmount(), owned, "golden effect expires first, preserving frost " + damage);
            player.removeStatusEffect(SscAddon.FROST_ARMOR);
            near(player.getAbsorptionAmount(), 0, "neither expiry order leaves ghosts " + damage);
        }
        player = player();
        castArmor(player, 24);
        player.setAbsorptionAmount(0);
        player.setAbsorptionAmount(6);
        player.removeStatusEffect(SscAddon.FROST_ARMOR);
        near(player.getAbsorptionAmount(), 6, "exhausted frost never removes a later grant");

        player = player();
        castArmor(player, 24);
        player.setAbsorptionAmount(17);
        NbtCompound nbt = new NbtCompound();
        state(player).write(nbt);
        TestPlayer restored = player();
        restored.setAbsorptionAmount(17);
        state(restored).read(nbt, restored, true);
        restored.setAbsorptionAmount(12);
        FrostArmorState.removeFrom(restored);
        near(restored.getAbsorptionAmount(), 0, "persisted shield retains ownership after reload and damage");
        restored.setAbsorptionAmount(20);
        state(restored).read(nbt, restored, false);
        near(restored.getAbsorptionAmount(), 3, "orphan saved ownership is removed without stripping other hearts");
        nbt.putFloat("SSCA.FrostArmorRemaining", Float.NaN);
        state(restored).read(nbt, restored, true);
        near(state(restored).remaining(), 0, "malformed shield ownership is rejected");

        player = player();
        player.setAbsorptionAmount(6);
        castArmor(player, 24);
        player.setAbsorptionAmount(20);
        FrostArmorState.removeOther(player, 6);
        near(player.getAbsorptionAmount(), 14, "independent absorption cleanup preserves remaining frost");
        near(state(player).remaining(), 14, "independent cleanup does not consume frost ownership");
        player.removeStatusEffect(SscAddon.FROST_ARMOR);
        near(player.getAbsorptionAmount(), 0, "independent cleanup and frost expiry leave no ghosts");

        player = player();
        castArmor(player, 24);
        StatusEffectInstance effect = player.getStatusEffect(SscAddon.FROST_ARMOR);
        boolean active = true;
        for (int tick = 0; tick < 399; tick++) active &= effect.update(player, () -> {});
        check(active && effect.getDuration() == 1, "frost remains active through tick 399");
        check(!effect.update(player, () -> {}), "actual effect timer expires at tick 400");
        player.removeStatusEffect(SscAddon.FROST_ARMOR);
        near(player.getAbsorptionAmount(), 0, "effect expiry removes remaining frost");
    }

    private static void statePackets() throws Exception {
        Class<?> type = Class.forName(SpellChannelManager.class.getName() + "$Channel");
        Object channel = unsafe.allocateInstance(type);
        TestPlayer player = player();
        Spell spell = SpellRegistry.get("void_devour");
        set(type, channel, "player", player);
        set(type, channel, "spell", spell);
        set(type, channel, "token", 37);
        set(type, channel, "level", 2); // A level-five scroll temporarily downgraded to level two.
        set(type, channel, "profile", new SpellCastingRules.Profile(80, .35, false));
        set(type, channel, "mode", SpellCastingRules.Mode.RELEASE);
        set(type, channel, "progress", new SpellCastingRules.Progress<>(SpellCastingRules.Mode.RELEASE, 80, 37));
        var writer = SpellChannelManager.class.getDeclaredMethod("fullStatePacket", type);
        writer.setAccessible(true);
        PacketByteBuf full = (PacketByteBuf) writer.invoke(null, channel);
        check(full.readBoolean(), "server full packet marks active state");
        SpellCastHud.State decoded = SpellCastHud.readActiveState(full, null);
        check(decoded.level() == 2 && decoded.spell() == spell && decoded.token() == 37, "real server/client full packet carries effective level");
        check(full.readableBytes() == 0, "full packet consumed exactly");
        full.release();
        PacketByteBuf calibration = PacketByteBufs.create();
        calibration.writeBoolean(false);
        calibration.writeVarInt(37);
        calibration.writeVarInt(20);
        calibration.writeBoolean(false);
        calibration.writeVarInt(0);
        calibration.writeBoolean(false);
        SpellCastHud.State updated = SpellCastHud.readActiveState(calibration, decoded);
        check(updated.level() == 2 && updated.elapsed() == 20, "calibration preserves effective downgrade level");
        check(calibration.readableBytes() == 0, "calibration layout remains unchanged");
        calibration.release();
    }

    private static void previewWiring() throws Exception {
        ClassNode node = new ClassNode();
        try (var in = SpellAuditIntegrationProbe.class.getResourceAsStream("/net/jackcooper/shapeShifterCurseAddon/client/SpellcastClient.class")) {
            new ClassReader(in).accept(node, 0);
        }
        int calls = 0;
        for (MethodNode method : node.methods) for (var insn : method.instructions)
            if (insn instanceof MethodInsnNode call && call.name.equals("getAimMaxRange") && call.desc.equals("(I)D")) calls++;
        check(calls == 2, "both client preview paths call level-aware production accessor");
        ClassNode mixin = new ClassNode();
        try (var in = SpellAuditIntegrationProbe.class.getResourceAsStream("/net/jackcooper/shapeShifterCurseAddon/mixin/entity/SscAddonLivingEntityMixin.class")) {
            new ClassReader(in).accept(mixin, 0);
        }
        check(mixin.methods.stream().filter(m -> m.name.equals("ssca$clearNpcCombatEffect"))
                .anyMatch(m -> java.util.stream.StreamSupport.stream(m.instructions.spliterator(), false)
                        .anyMatch(i -> i instanceof MethodInsnNode call && call.name.equals("removeFrom"))),
                "actual effect-removal injection calls frost cleanup, distinct from duration refresh");
    }

    private static void castArmor(TestPlayer player, float power) {
        World saved = player.testWorld;
        player.testWorld = player.nonVisualWorld; // Non-ServerWorld skips visual/network I/O, with real effect map/hooks.
        try { ((FrostArmorSpell) SpellRegistry.get("frost_armor")).cast(player, power, false); }
        finally { player.testWorld = saved; }
    }
    private static FrostArmorState state(TestPlayer player) { return ((FrostArmorAccess) (Object) player).ssca$frostArmor(); }
    private static JsonObject json(String id) throws Exception {
        try (var input = SpellAuditIntegrationProbe.class.getResourceAsStream("/data/ssc_addon/spells/" + id + ".json")) {
            return JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static TestPlayer player() throws Exception {
        TestPlayer player = (TestPlayer) unsafe.allocateInstance(TestPlayer.class);
        player.id = UUID.randomUUID();
        player.equipment = new HashMap<>();
        var powers = (PowerHolderComponent) java.lang.reflect.Proxy.newProxyInstance(PowerHolderComponent.class.getClassLoader(),
                new Class<?>[]{PowerHolderComponent.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getPowers" -> List.of();
                    case "hasPower" -> false;
                    default -> null;
                });
        player.components = ComponentContainer.Factory.builder(TestPlayer.class)
                .component(PowerHolderComponent.KEY, ignored -> powers).build().createContainer(player);
        player.testWorld = (TestWorld) unsafe.allocateInstance(TestWorld.class);
        player.nonVisualWorld = (World) unsafe.allocateInstance(net.minecraft.client.world.ClientWorld.class);
        set(Entity.class, player, "dataTracker", new DataTracker(player));
        set(LivingEntity.class, player, "attributes", new net.minecraft.entity.attribute.AttributeContainer(
                net.minecraft.entity.player.PlayerEntity.createPlayerAttributes().build()));
        set(LivingEntity.class, player, "activeStatusEffects", new HashMap<>());
        player.initializeTracker();
        return player;
    }
    private static void set(Class<?> type, Object target, String field, Object value) throws Exception {
        var f = type.getDeclaredField(field); f.setAccessible(true); f.set(target, value);
    }
    private static void near(double actual, double expected, String reason) { check(Math.abs(actual - expected) < .00001, reason + ": " + actual + " expected " + expected); }
    private static void check(boolean passed, String reason) { checks++; if (!passed) throw new AssertionError(reason); }

    public static final class TestWorld extends ServerWorld {
        private TestWorld() { super(null, null, null, null, World.OVERWORLD, null, null, false, 0, List.of(), false, null); }
        @Override public long getTime() { return 100; }
    }
    public static final class TestPlayer extends ServerPlayerEntity implements dev.onyxstudios.cca.api.v3.component.ComponentProvider {
        UUID id; World testWorld, nonVisualWorld; ItemStack stack; boolean purified; ComponentContainer components;
        java.util.Map<EquipmentSlot, ItemStack> equipment;
        private TestPlayer() { super(null, null, new GameProfile(UUID.randomUUID(), "spell-audit")); }
        @Override public World getWorld() { return testWorld; }
        @Override public ComponentContainer getComponentContainer() { return components; }
        @Override public UUID getUuid() { return id; }
        @Override public String getUuidAsString() { return id.toString(); }
        @Override public boolean hasStatusEffect(StatusEffect effect) { return purified && effect == SscAddon.PURIFIED || super.hasStatusEffect(effect); }
        @Override public boolean canHaveStatusEffect(StatusEffectInstance effect) { return true; }
        @Override protected void onStatusEffectApplied(StatusEffectInstance effect, Entity source) { effect.getEffectType().onApplied(this, null, effect.getAmplifier()); }
        @Override protected void onStatusEffectUpgraded(StatusEffectInstance effect, boolean reapply, Entity source) {
            if (reapply) { effect.getEffectType().onRemoved(this, null, effect.getAmplifier()); effect.getEffectType().onApplied(this, null, effect.getAmplifier()); }
        }
        @Override protected void onStatusEffectRemoved(StatusEffectInstance effect) {
            effect.getEffectType().onRemoved(this, null, effect.getAmplifier());
            if (effect.getEffectType() == SscAddon.FROST_ARMOR) FrostArmorState.removeFrom(this);
        }
        @Override public boolean isSneaking() { return false; }
        @Override public boolean isAlive() { return true; }
        @Override public boolean isSpectator() { return false; }
        @Override public ItemStack getStackInHand(Hand hand) { return stack; }
        @Override public ItemStack getEquippedStack(EquipmentSlot slot) { return equipment.getOrDefault(slot, ItemStack.EMPTY); }
        @Override public Iterable<ItemStack> getArmorItems() { return equipment.values(); }
        float afterProtection(DamageSource source, float amount) { return super.modifyAppliedDamage(source, amount); }
        void initializeTracker() { super.initDataTracker(); }
        @Override public void onTrackedDataSet(net.minecraft.entity.data.TrackedData<?> data) {}
        @Override public void emitGameEvent(GameEvent event) {}
        @Override public void sendMessage(Text message, boolean overlay) {}
    }
}
