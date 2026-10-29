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
        domainSuppressionPacketsAndSounds();
        frostQuoteSynchronization();
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
        near(blink.getAimMaxRange(null,5),19.52,"Caster-aware accessor preserves nonprojectile level scaling");
        near(devour.getAimMaxRange(null,5),22.4,"Caster-aware accessor preserves targeted nonprojectile range");
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
        Object style=unsafe.allocateInstance(FormSpellStyle.class);
        set(FormSpellStyle.class,style,"timing",FormSpellStyle.Timing.of(80,80,-1,1));
        set(FormSpellStyle.class,style,"projectileRange",1.5);
        set(type,channel,"style",style);
        var writer = SpellChannelManager.class.getDeclaredMethod("fullStatePacket", type);
        writer.setAccessible(true);
        PacketByteBuf full = (PacketByteBuf) writer.invoke(null, channel);
        check(full.readBoolean(), "server full packet marks active state");
        SpellCastHud.State decoded = SpellCastHud.readActiveState(full, null);
        check(decoded.level() == 2 && decoded.spell() == spell && decoded.token() == 37, "real server/client full packet carries effective level");
        near(decoded.projectileRange(),1.5,"Full packet carries paid projectile range snapshot");
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
        near(updated.projectileRange(),1.5,"Calibration retains paid projectile range snapshot");
        check(calibration.readableBytes() == 0, "calibration layout remains unchanged");
        calibration.release();
    }

    private static void domainSuppressionPacketsAndSounds() throws Exception {
        Class<?> sequenceType=Class.forName(ExplosionManager.class.getName()+"$Sequence");
        var write=ExplosionManager.class.getDeclaredMethod("writeSequence",PacketByteBuf.class,sequenceType,int.class);write.setAccessible(true);
        Class<?> renderer=net.jackcooper.shapeShifterCurseAddon.client.renderer.ExplosionRenderer.class;
        var read=renderer.getDeclaredMethod("readSequences",PacketByteBuf.class);read.setAccessible(true);
        PacketByteBuf packet=PacketByteBufs.create();packet.writeVarInt(2);UUID[] ids={UUID.randomUUID(),UUID.randomUUID()};
        for(int i=0;i<2;i++){
            Object sequence=unsafe.allocateInstance(sequenceType);
            set(sequenceType,sequence,"id",ids[i]);set(sequenceType,sequence,"center",new net.minecraft.util.math.Vec3d(10,64,30));
            set(sequenceType,sequence,"timing",FormSpellStyle.Timing.of(700,700,540,.75));
            set(sequenceType,sequence,"coreRadius",32d);set(sequenceType,sequence,"outerRadius",64d);
            set(sequenceType,sequence,"suppressedAt",i==0?485:-1);write.invoke(null,packet,sequence,490);
        }
        var parsed=(java.util.Map<?,?>)read.invoke(null,packet);
        check(packet.readableBytes()==0&&parsed.size()==2,"Actual explosion writer/client decoder consumes multiple normal and suppressed sequences exactly");packet.release();
        for(int i=0;i<2;i++){
            Object view=parsed.get(ids[i]);var suppressed=view.getClass().getDeclaredField("suppressedAt");suppressed.setAccessible(true);
            check(suppressed.getInt(view)==(i==0?485:-1),"Suppression timestamp is synchronized per sequence");
            var preparation=view.getClass().getDeclaredField("preparation");preparation.setAccessible(true);
            check(preparation.getInt(view)==405,"Accelerated timing survives suppression packet");
        }
        Object cancelled=parsed.get(ids[0]),normal=parsed.get(ids[1]);
        var held=finaleVertices(cancelled,0);var rising=finaleVertices(cancelled,20);
        check(held.count()>0&&rising.height()>held.height(),"Real cancellation geometry retains existing circles and raises them during finale");
        check(finaleVertices(normal,3).count()>finaleVertices(cancelled,3).count(),"Normal blast shockwave is absent from actual cancellation geometry");
        check(finaleVertices(cancelled,40).count()==0,"All cancelled circle vertices disappear after staggered finale");
        set(cancelled.getClass(),cancelled,"suppressedAt",405); // Logical 540: beam has not reached any layer.
        check(finaleVertices(cancelled,0).count()==0,"Cancelling before layer creation does not conjure seven full circles");
        set(cancelled.getClass(),cancelled,"suppressedAt",415); // Logical 550: only the first layers have appeared.
        int partial=finaleVertices(cancelled,0).count();check(partial>0&&partial<held.count(),"Early cancellation retains only layers that actually appeared");
        var cues=net.jackcooper.shapeShifterCurseAddon.client.ExplosionEffects.class.getDeclaredMethod("suppressionCues",double.class);cues.setAccessible(true);
        var sounds=(List<?>)cues.invoke(null,0d);check(sounds.size()==2,"Exactly two suppression cues");
        check(((net.minecraft.client.sound.SoundInstance)sounds.get(0)).getId().equals(net.minecraft.sound.SoundEvents.BLOCK_GLASS_BREAK.getId()),"First cue is glass break");
        check(((net.minecraft.client.sound.SoundInstance)sounds.get(1)).getId().equals(net.minecraft.sound.SoundEvents.BLOCK_FIRE_EXTINGUISH.getId()),"Second cue is fire extinguish");
        for(Object sound:sounds){var cue=(net.minecraft.client.sound.SoundInstance)sound;check(cue.isRelative()&&cue.getCategory()==net.minecraft.sound.SoundCategory.PLAYERS&&!cue.isRepeatable(),"Single-play player cue bypasses domain world-sound isolation");}
        check(((List<?>)cues.invoke(null,ExplosionRules.audienceRange()+1)).isEmpty(),"Suppression cues retain the existing audio audience limit");
        var effects=new net.jackcooper.shapeShifterCurseAddon.client.ExplosionEffects();effects.interrupt(net.minecraft.util.math.Vec3d.ZERO,false);
        effects.tick(null,net.minecraft.util.math.Vec3d.ZERO,700,true,32d,64d); // Must return before touching world/audio/particles.
        check(true,"Interrupted effects cannot restart theme or emit blast particles even after a late detonation tick");
        var caster=player();set(World.class,caster.testWorld,"registryKey",World.OVERWORLD);
        set(Entity.class,caster,"pos",new net.minecraft.util.math.Vec3d(12,64,30));
        for(UUID sequence:java.util.Arrays.asList(ids[0],null)) {
            var event=SpellChannelManager.suppressionPacket(caster,0x55AAFF,sequence);int bytes=event.readableBytes();
            var decoded=net.jackcooper.shapeShifterCurseAddon.client.CastingVisualState.readSuppression(event);
            check(decoded.caster().equals(caster.getUuid())&&decoded.dimension().equals(World.OVERWORLD.getValue()),"Failure event preserves owner and dimension");
            check(decoded.position().equals(caster.getPos())&&decoded.color()==0x55AAFF,"Failure event preserves actual circle position and color");
            check(java.util.Objects.equals(decoded.explosionSequence(),sequence)&&event.readableBytes()==0&&bytes<100,"General and native-explosion failure events decode exactly within one small packet");event.release();
        }
        var sequences=renderer.getDeclaredField("SEQUENCES");sequences.setAccessible(true);var activeViews=(java.util.Map<UUID,Object>)sequences.get(null);
        var markers=renderer.getDeclaredField("RANGE_SUPPRESSED");markers.setAccessible(true);var pending=(java.util.Map<UUID,Long>)markers.get(null);
        UUID early=UUID.randomUUID();net.jackcooper.shapeShifterCurseAddon.client.renderer.ExplosionRenderer.interruptForRange(early,null,100);
        check(pending.containsKey(early),"Failure before the explosion snapshot retains a sequence-specific silence marker");
        activeViews.put(early,parsed.get(ids[1]));net.jackcooper.shapeShifterCurseAddon.client.renderer.ExplosionRenderer.interruptForRange(early,null,101);
        var effectField=parsed.get(ids[1]).getClass().getDeclaredField("effects");effectField.setAccessible(true);
        Object stopped=effectField.get(parsed.get(ids[1]));var interrupted=stopped.getClass().getDeclaredField("interrupted");interrupted.setAccessible(true);
        check(interrupted.getBoolean(stopped),"Existing native sequence stops audio before shared failure cues and cannot replay them");
        var clear=renderer.getDeclaredMethod("clearSequences");clear.setAccessible(true);clear.invoke(null);
        check(pending.isEmpty()&&activeViews.isEmpty(),"Disconnect/world cleanup clears pending failures and existing sequences");
        double[] smallHeld=cancelCircleVertices(0),smallRise=cancelCircleVertices(20);
        check(smallHeld[0]>0&&smallRise[1]>smallHeld[1],"Ordinary casting circle uses actual upward finale geometry");
        check(smallRise[2]<smallHeld[2]&&cancelCircleVertices(24)[0]==0,"Ordinary cancellation fades while rising and emits no vertices after completion");
    }

    private static double[] cancelCircleVertices(float age) {
        var buffer=new net.minecraft.client.render.BufferBuilder(65536);buffer.begin(net.minecraft.client.render.VertexFormat.DrawMode.QUADS,net.minecraft.client.render.VertexFormats.POSITION_COLOR);
        net.jackcooper.shapeShifterCurseAddon.client.renderer.CastingCircleRenderer.drawCancellation(buffer,new net.minecraft.client.util.math.MatrixStack(),age,0x55AAFF);
        var built=buffer.end();int count=built.getParameters().vertexCount();var bytes=built.getVertexBuffer();int stride=net.minecraft.client.render.VertexFormats.POSITION_COLOR.getVertexSizeByte();
        double height=0,alpha=0;for(int i=0;i<count;i++){height=Math.max(height,bytes.getFloat(i*stride+4));alpha=Math.max(alpha,bytes.get(i*stride+15)&255);}built.release();return new double[]{count,height,alpha};
    }

    private record FinaleVertices(int count,float height) {}
    private static FinaleVertices finaleVertices(Object view,float elapsed)throws Exception {
        var buffer=new net.minecraft.client.render.BufferBuilder(65536);
        buffer.begin(net.minecraft.client.render.VertexFormat.DrawMode.QUADS,net.minecraft.client.render.VertexFormats.POSITION_COLOR);
        var render=net.jackcooper.shapeShifterCurseAddon.client.renderer.ExplosionRenderer.class.getDeclaredMethod("renderFinale",
                net.minecraft.client.util.math.MatrixStack.class,net.minecraft.client.render.VertexConsumer.class,float.class,view.getClass());render.setAccessible(true);
        render.invoke(null,new net.minecraft.client.util.math.MatrixStack(),buffer,elapsed,view);
        var built=buffer.end();int count=built.getParameters().vertexCount();float highest=Float.NEGATIVE_INFINITY;
        var bytes=built.getVertexBuffer().order(java.nio.ByteOrder.nativeOrder());
        int stride=net.minecraft.client.render.VertexFormats.POSITION_COLOR.getVertexSizeByte();
        for(int i=0;i<count;i++)highest=Math.max(highest,bytes.getFloat(i*stride+4));
        built.release();return new FinaleVertices(count,highest);
    }

    private static void frostQuoteSynchronization()throws Exception {
        var method=net.ssca.neomagic.client.NeoClient.class.getDeclaredMethod("frostMultiplier",NbtCompound.class,Identifier.class,long.class);method.setAccessible(true);
        var state=new NbtCompound();state.putDouble("FrostSpellMultiplier",.85);state.putLong("FrostReadyUntil",60);
        var snow=net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.SNOW_FOX_SP;
        var spine=net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.SNOW_FOX_FROSTSPINE;
        state.putString("Form",snow.toString());
        near((double)method.invoke(null,state,snow,59L),.85,"Actual client ice preview uses authoritative preparation multiplier");
        near((double)method.invoke(null,state,snow,60L),1,"Client preparation expires at the same boundary as server");
        state.putDouble("FrostSpellMultiplier",.75);state.putString("Form",spine.toString());near((double)method.invoke(null,state,spine,120L),.75,"Thorn discount is independent of SP preparation expiry");
        near((double)method.invoke(null,state,new Identifier("my_addon","familiar_fox_sp"),0L),1,"Stale frost HUD cannot grant another form the bonus");
        near((double)method.invoke(null,state,snow,0L),1,"A form packet arriving before its HUD cannot reuse the other snow form's multiplier");
        state.putDouble("FrostSpellMultiplier",Double.NaN);near((double)method.invoke(null,state,spine,0L),1,"Malformed nonfinite multiplier is ignored");
        state.putDouble("FrostSpellMultiplier",-1);near((double)method.invoke(null,state,spine,0L),1,"Negative multiplier is ignored");
        near((double)method.invoke(null,new NbtCompound(),spine,0L),1,"Missing old snapshot fields retain ordinary preview");
        var novaMethod=net.ssca.neomagic.client.NeoClient.class.getDeclaredMethod("novaQuickUntil",NbtCompound.class,Identifier.class,long.class);novaMethod.setAccessible(true);
        var nova=net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers.OCELOT_NOVA;
        var echo=new NbtCompound();echo.putString("Form",nova.toString());echo.putLong("NovaQuickCastUntil",100);
        check((long)novaMethod.invoke(null,echo,nova,99L)==100,"Client revival shortcut keeps authoritative absolute expiry");
        check((long)novaMethod.invoke(null,echo,nova,100L)==0,"Client revival shortcut expires at server boundary");
        check((long)novaMethod.invoke(null,echo,snow,0L)==0,"Stale revival HUD cannot grant another form instantaneous casting");
        echo.putString("Form",snow.toString());check((long)novaMethod.invoke(null,echo,nova,0L)==0,"Form arriving before matching revival HUD cannot reuse a stale window");
        check((long)novaMethod.invoke(null,new NbtCompound(),nova,0L)==0,"Older HUD without revival expiry retains ordinary casting");
    }

    private static void previewWiring() throws Exception {
        ClassNode node = new ClassNode();
        try (var in = SpellAuditIntegrationProbe.class.getResourceAsStream("/net/jackcooper/shapeShifterCurseAddon/client/SpellcastClient.class")) {
            new ClassReader(in).accept(node, 0);
        }
        int calls = 0;
        for (MethodNode method : node.methods) for (var insn : method.instructions)
            if (insn instanceof MethodInsnNode call && call.name.equals("getAimMaxRange")
                    && call.desc.equals("(Lnet/minecraft/entity/player/PlayerEntity;I)D")) calls++;
        check(calls == 2, "both client preview paths call caster- and level-aware production accessor");
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
