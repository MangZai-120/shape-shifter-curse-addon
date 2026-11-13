package net.jackcooper.shapeShifterCurseAddon.ability;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.github.apace100.apoli.Apoli;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.data.ApoliDataTypes;
import io.github.apace100.apoli.power.Active;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.util.ApoliConfig;
import io.github.apace100.apoli.util.HudRender;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCastManager;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec;
import net.jackcooper.shapeShifterCurseAddon.power.BatSonicWaveAbilityPower;
import net.jackcooper.shapeShifterCurseAddon.power.MistFormAbilityPower;
import net.jackcooper.shapeShifterCurseAddon.power.TrueInvisibilityDashAbilityPower;
import net.jackcooper.shapeShifterCurseAddon.resource.BarKeys;
import net.jackcooper.shapeShifterCurseAddon.resource.ResourceBars;
import net.jackcooper.shapeShifterCurseAddon.spell.NeoFormCastingProbe;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils;

/** Native server players, actual skill effects, whitelist modes and blood accounting. */
public final class SpectatorSkillWorldProbe {
    private static int checks;
    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }
    @FunctionalInterface private interface Cast { void run() throws Exception; }
    private record Skill(String name, Cast cast, Predicate<LivingEntity> affected) { }

    public static void start(TestContext test) {
        test.waitAndRun(70, () -> {
            boolean whitelist = SSCAddonConfig.server().whitelistEnabled;
            try {
                if (Apoli.config == null) Apoli.config = new ApoliConfig();
                run(test);
                System.out.println("SPECTATOR_SKILL_WORLD_PASS checks=" + checks);
                test.complete();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            } finally {
                SSCAddonConfig.server().whitelistEnabled = whitelist;
            }
        });
    }

    private static void run(TestContext test) throws Exception {
        ServerWorld world = test.getWorld();
        ServerPlayerEntity caster = NeoFormCastingProbe.player(test, FormIdentifiers.BAT_DESMODUS, 0);
        FormUtils.setForm(caster, RegPlayerForms.playerForms.get(FormIdentifiers.BAT_DESMODUS));
        check(PowerHolderComponent.KEY.get(caster).getPower(PowerTypeRegistry.get(BarKeys.BAT_BLOOD.id)) != null,
                "Caster has the real blood resource power");
        Vec3d origin = caster.getPos();
        ServerPlayerEntity spectator = player(world, GameMode.SPECTATOR);
        ServerPlayerEntity opponent = player(world, GameMode.SURVIVAL);
        PigEntity enemy = pig(world);
        PigEntity friend = pig(world);
        PigEntity dead = pig(world);
        PigEntity removed = pig(world);
        PigEntity outside = pig(world);
        List<LivingEntity> targets = List.of(spectator, opponent, enemy, friend, dead, removed, outside);
        place(spectator, origin.add(-.4, 0, 1.75));
        place(opponent, origin.add(.4, 0, 1.75));
        place(enemy, origin.add(0, 0, 1.75));
        place(friend, origin.add(.3, 0, 1.75));
        place(dead, origin.add(-.3, 0, 1.75));
        place(removed, origin.add(.2, 0, 1.75));
        place(outside, origin.add(0, 0, 40));
        dead.setHealth(0);
        removed.discard();
        check(spectator.isSpectator() && !opponent.isSpectator(), "Native game modes distinguish the player targets");
        check(!dead.isAlive() && removed.isRemoved(), "Invalid living targets are present in the fixture");
        check(world.getEntitiesByClass(LivingEntity.class, caster.getBoundingBox().expand(5), e -> true).contains(spectator),
                "The broad entity query really includes the native spectator player");

        SkillCooldownSpec spec = new SkillCooldownSpec(160, 0, SkillCastManager.START_ON_CAST, Map.of());
        var dash = new TrueInvisibilityDashAbilityPower(null, caster, 240, HudRender.DONT_RENDER, new Active.Key());
        var sonic = new BatSonicWaveAbilityPower(null, caster, spec, HudRender.DONT_RENDER, new Active.Key());
        var mist = new MistFormAbilityPower(null, caster, 90, HudRender.DONT_RENDER, new Active.Key(), spec);
        List<Skill> skills = List.of(
                new Skill("wild-cat stun", () -> invoke(dash, "performStunEffect"), t -> t.hasStatusEffect(SscAddon.STUN)),
                new Skill("sonic wave", () -> invoke(sonic, "fire"), t -> t.hasStatusEffect(StatusEffects.NAUSEA)),
                new Skill("mist burst", () -> invoke(mist, "detonate"), t -> t.getVelocity().lengthSquared() > 1e-6),
                new Skill("fallen scream", () -> action("{\"type\":\"my_addon:fallen_allay_scream\"}", caster),
                        t -> t.hasStatusEffect(StatusEffects.GLOWING)),
                new Skill("fox fire breath", () -> action("{\"type\":\"my_addon:fire_breath\",\"distance\":8,\"damage\":4,\"duration\":100}", caster),
                        t -> t.hasStatusEffect(SscAddon.FOX_FIRE_BURN)));
        for (Skill skill : skills) {
            for (int mode = 0; mode < 3; mode++) {
                reset(caster, targets);
                SSCAddonConfig.server().whitelistEnabled = mode != 0;
                caster.getCommandTags().clear();
                if (mode == 1) caster.addCommandTag(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX + friend.getUuid());
                if (mode < 2) check(!WhitelistUtils.isProtected(caster, spectator),
                        skill.name + " fixture exposes spectators without whitelist protection in mode " + mode);
                skill.cast.run();
                String label = skill.name + " whitelist mode " + mode;
                untouched(spectator, label + " spectator");
                untouched(dead, label + " dead target");
                untouched(removed, label + " removed target");
                untouched(outside, label + " outside target");
                check(skill.affected.test(enemy), label + " ordinary NPC still receives the skill");
                check(skill.affected.test(opponent) == (mode != 2), label + " default player protection remains unchanged");
                check(skill.affected.test(friend) == (mode != 1), label + " explicit NPC whitelist remains unchanged");
                check(caster.getVelocity().lengthSquared() == 0 && !skill.affected.test(caster), label + " skill does not hit its caster");
                if (skill.name.equals("sonic wave") && mode != 2)
                    check(opponent.hasStatusEffect(SscAddon.DEAFEN), label + " living enemy players are still deafened");
            }
        }

        // Isolate the spectator from valid targets: an invulnerable observer must
        // neither be counted as a skill hit nor generate the bat's blood resource.
        SSCAddonConfig.server().whitelistEnabled = false;
        for (LivingEntity target : List.of(opponent, enemy, friend)) place(target, origin.add(0, 0, 40));
        for (Skill skill : List.of(skills.get(1), skills.get(2))) {
            reset(caster, targets);
            skill.cast.run();
            untouched(spectator, skill.name + " isolated spectator");
            check(ResourceBars.get(caster, BarKeys.BAT_BLOOD) == 0, skill.name + " spectator produces no blood gain");
        }
        place(enemy, origin.add(0, 0, 1.75));
        reset(caster, targets);
        skills.get(2).cast.run();
        check(ResourceBars.get(caster, BarKeys.BAT_BLOOD) > 0, "Mist burst still rewards a real living target");

        // The continuous mist aura uses a separate query from the detonation.
        reset(caster, targets);
        caster.setHealth(caster.getMaxHealth() / 2);
        ResourceBars.set(caster, BarKeys.BAT_BLOOD, 80);
        check(BatDesmodusBloodThirst.getStage(caster) == 3, "Mist aura's high-blood stage is actually active");
        caster.addStatusEffect(new StatusEffectInstance(SscAddon.MIST_FORM, 200));
        var aura = new MistFormAbilityPower(null, caster, 90, HudRender.DONT_RENDER, new Active.Key(), spec);
        aura.tick();
        untouched(spectator, "continuous mist aura spectator");
        untouched(dead, "continuous mist aura dead target");
        check(enemy.getHealth() < enemy.getMaxHealth(), "Continuous mist aura still damages a living NPC");
        check(caster.getHealth() > caster.getMaxHealth() / 2, "Continuous mist aura still heals after real damage");
    }

    private static ServerPlayerEntity player(ServerWorld world, GameMode mode) {
        // GameTest's creative mock hardcodes isSpectator() to false. Use the
        // actual ServerPlayerEntity and interaction manager for both game modes.
        var target = new ServerPlayerEntity(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "observer-" + mode.getName()));
        world.getServer().getPlayerManager().onPlayerConnect(new ClientConnection(NetworkSide.SERVERBOUND), target);
        FormUtils.setForm(target, RegPlayerForms.playerForms.get(FormIdentifiers.SHEEP_FORM));
        target.changeGameMode(mode);
        target.setNoGravity(true);
        return target;
    }

    private static PigEntity pig(ServerWorld world) {
        PigEntity target = java.util.Objects.requireNonNull(EntityType.PIG.create(world));
        target.setNoGravity(true);
        target.setAiDisabled(true);
        target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);
        target.setHealth(100);
        check(world.spawnEntity(target), "Fixture NPC is tracked by the real world");
        return target;
    }

    private static void place(LivingEntity target, Vec3d position) {
        ((ServerWorld) target.getWorld()).getChunk(BlockPos.ofFloored(position));
        target.refreshPositionAndAngles(position.x, position.y, position.z, 0, 0);
    }

    private static void reset(ServerPlayerEntity caster, List<LivingEntity> targets) {
        caster.clearStatusEffects();
        caster.setVelocity(Vec3d.ZERO);
        caster.setHealth(caster.getMaxHealth());
        ResourceBars.set(caster, BarKeys.BAT_BLOOD, 0);
        for (LivingEntity target : targets) {
            target.clearStatusEffects();
            target.setVelocity(Vec3d.ZERO);
            target.timeUntilRegen = 0;
            if (target.isAlive()) target.setHealth(target.getMaxHealth());
        }
    }

    private static void untouched(LivingEntity target, String label) {
        check(target.getStatusEffects().isEmpty() && target.getVelocity().lengthSquared() == 0
                        && (!target.isAlive() || target.getHealth() == target.getMaxHealth()),
                label + " receives no effects, knockback or damage");
    }

    private static void invoke(Object power, String name) throws Exception {
        Method method = power.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(power);
    }

    private static void action(String json, ServerPlayerEntity caster) {
        ApoliDataTypes.ENTITY_ACTION.read(JsonParser.parseString(json)).accept(caster);
    }
}
