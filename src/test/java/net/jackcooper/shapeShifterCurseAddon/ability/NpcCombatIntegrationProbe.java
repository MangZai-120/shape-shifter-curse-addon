package net.jackcooper.shapeShifterCurseAddon.ability;

import com.mojang.authlib.GameProfile;
import dev.onyxstudios.cca.api.v3.component.ComponentContainer;
import dev.onyxstudios.cca.api.v3.component.ComponentProvider;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceIntegration;
import net.jackcooper.shapeShifterCurseAddon.balance.BalanceSnapshot;
import net.jackcooper.shapeShifterCurseAddon.balance.SscBalanceSchema;
import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonServerConfig;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownHolder;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldownSpec;
import net.jackcooper.shapeShifterCurseAddon.cooldown.SkillCooldowns;
import net.jackcooper.shapeShifterCurseAddon.effect.FoxFireBurnEffect;
import net.jackcooper.shapeShifterCurseAddon.effect.FoxFireBurnOwnership;
import net.jackcooper.shapeShifterCurseAddon.util.SscIgnitedEntityAccessor;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.jackcooper.shapeShifterCurseAddon.util.WhitelistUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedPlayerManager;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.onixary.shapeShifterCurseFabric.player_form.NormalForm;
import net.onixary.shapeShifterCurseFabric.player_form.utils.PlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.utils.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.status_effects.RegOtherStatusEffects;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Uses actual Minecraft status instances, CCA form lookup, whitelist and applied Mixin hooks.
 * Entity/world I/O is isolated. This is not live health/AI/multiplayer acceptance. */
public final class NpcCombatIntegrationProbe {
    private static int checks;
    private static int nextEntityId = 1;
    private static sun.misc.Unsafe unsafe;
    private static TestWorld world;
    private static TestPlayers players;
    private static Method damageHook;

    public static void run() throws Exception {
        var uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); uf.setAccessible(true);
        unsafe = (sun.misc.Unsafe) uf.get(null);
        AutoConfig.register(SSCAddonServerConfig.class, GsonConfigSerializer::new);
        var published = BalanceIntegration.class.getDeclaredField("publishedServer"); published.setAccessible(true);
        published.set(null, BalanceSnapshot.defaults(SscBalanceSchema.create()));
        players = alloc(TestPlayers.class); players.online = new HashMap<>();
        var server = alloc(MinecraftDedicatedServer.class);
        var pm = MinecraftServer.class.getDeclaredField("playerManager"); pm.setAccessible(true); pm.set(server, players);
        world = alloc(TestWorld.class); world.server = server; world.now = 100;
        world.states = new TestStates();
        var worlds = MinecraftServer.class.getDeclaredField("worlds"); worlds.setAccessible(true);
        worlds.set(server, new HashMap<>(Map.of(World.OVERWORLD, world)));
        damageHook = hook("ssca$npcCombatDamage");
        TestNpc npc = npc();
        TestPlayer playerTarget = player(FormIdentifiers.SHEEP_FORM);
        for (Identifier form : List.of(FormIdentifiers.AXOLOTL_FLUORESCENT, FormIdentifiers.AXOLOTL_ALING,
                FormIdentifiers.SNOW_FOX_SP, FormIdentifiers.SNOW_FOX_FROSTSPINE, FormIdentifiers.FALLEN_ALLAY_SP)) {
            TestPlayer caster = player(form);
            near(damage(npc, caster, 8), 12, "form NPC damage " + form);
            near(damage(playerTarget, caster, 8), 8, "player damage unchanged " + form);
            protect(caster, npc);
            near(damage(npc, caster, 8), 8, "whitelisted NPC gets no bonus " + form);
        }
        TestPlayer fallen = player(FormIdentifiers.FALLEN_ALLAY_SP);
        NpcCombatEnhancements.applyFallenScream(fallen, npc, 160);
        check(npc.getStatusEffect(StatusEffects.GLOWING).getDuration() == 320, "fallen glow lasts 16s for NPC");
        near(damage(npc, fallen, 8), 15, "fallen 1.5 and mark 1.25 multiply once");
        near(damage(fallen, npc, 8), 6, "marked NPC deals 25 percent less to its caster");
        TestPlayer otherFallen = player(FormIdentifiers.FALLEN_ALLAY_SP);
        near(damage(npc, otherFallen, 8), 12, "another fallen does not inherit this caster's mark");
        TestVex vex = alloc(TestVex.class); vex.tags = new HashSet<>(Set.of("ssc_fallen_allay_vex", "owner:" + fallen.id));
        near(damage(npc, vex, 8), 12, "owned vex gets 1.5, no owner's personal mark bonus");
        TestVex taggedSummon = alloc(TestVex.class); taggedSummon.tags = new HashSet<>(Set.of("ssc_owner:" + fallen.id));
        near(damage(npc, taggedSummon, 8), 12, "other owned summons also get fallen damage bonus");
        protect(fallen, npc);
        near(damage(npc, vex, 8), 8, "summon respects owner's updated whitelist");
        fallen.tags.clear();
        npc.effects.clear();
        near(damage(npc, fallen, 8), 12, "clearing glow immediately stops mark bonus");
        NpcCombatEnhancements.applyFallenScream(fallen, npc, 160);
        world.now = 420;
        near(damage(npc, fallen, 8), 12, "mark stops at expiry even if another glow remains");
        world.now = 100;
        TestPlayer cat = player(FormIdentifiers.WILD_CAT_SP);
        npc.effects.clear();
        NpcCombatEnhancements.applyWildCatStun(cat, npc, 100);
        check(npc.getStatusEffect(SscAddon.STUN).getDuration() == 200, "wild-cat NPC stun doubles");
        near(damage(npc, null, 8), 16, "wild-cat exposure also doubles environmental damage");
        TestPlayer snow = player(FormIdentifiers.SNOW_FOX_SP);
        near(damage(npc, snow, 8), 24, "wild-cat exposure stacks with snow form multiplier");
        cat.form.nowForm = new NormalForm(FormIdentifiers.SHEEP_FORM);
        near(damage(npc, null, 8), 16, "caster changing form does not erase active stun exposure");
        players.online.remove(cat.id);
        near(damage(npc, null, 8), 16, "caster disconnecting does not erase active stun exposure");
        players.online.put(cat.id, cat); cat.form.nowForm = new NormalForm(FormIdentifiers.WILD_CAT_SP);
        NpcCombatEnhancements.state(npc).markWildCat(player(FormIdentifiers.WILD_CAT_SP).id, world.now, 200);
        near(damage(npc, null, 8), 16, "multiple wild-cat casters do not multiply exposure again");
        world.now = 300;
        near(damage(npc, null, 8), 8, "unrelated later stun cannot extend exposure");
        world.now = 100;
        npc.effects.clear();
        protect(cat, npc);
        NpcCombatEnhancements.applyWildCatStun(cat, npc, 100);
        check(!npc.hasStatusEffect(SscAddon.STUN), "wild-cat helper protects whitelist");
        cat.tags.clear();
        NpcCombatEnhancements.applyWildCatStun(cat, playerTarget, 100);
        check(playerTarget.effects.isEmpty(), "default player whitelist remains protected");
        cat.tags.add(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX + UUID.randomUUID());
        NpcCombatEnhancements.applyWildCatStun(cat, playerTarget, 100);
        check(playerTarget.getStatusEffect(SscAddon.STUN).getDuration() == 100, "enemy player still stunned only 5s");
        npc.effects.clear();
        TestPlayer allay = player(FormIdentifiers.ALLAY_SP);
        NpcCombatEnhancements.applyPurification(allay, npc);
        check(npc.getStatusEffect(StatusEffects.WEAKNESS).getAmplifier() == 1
                && npc.getStatusEffect(StatusEffects.WEAKNESS).getDuration() == 300, "purification weakness II lasts 15s");
        check(npc.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier() == 0
                && npc.getStatusEffect(StatusEffects.SLOWNESS).getDuration() == 300, "purification slowness I lasts 15s");
        TestPlayer fruit = player(FormIdentifiers.BAT_PARASITIC_FRUIT);
        Method effectHook = hook("ssca$npcFruitDebuff");
        for (StatusEffect type : List.of(StatusEffects.POISON, StatusEffects.SLOWNESS, StatusEffects.WEAKNESS, SscAddon.BAT_POISON)) {
            StatusEffectInstance initial = new StatusEffectInstance(type, 160, 1, true, false, true);
            StatusEffectInstance actual = (StatusEffectInstance) effectHook.invoke(npc, initial, initial, fruit);
            check(actual.getAmplifier() == 2 && actual.getDuration() == 160 && actual.isAmbient()
                    && !actual.shouldShowParticles() && actual.shouldShowIcon(), "fruit raises debuff one level and retains flags");
            check(effectHook.invoke(playerTarget, initial, initial, fruit) == initial, "fruit player debuff unchanged");
        }
        var speed = new StatusEffectInstance(StatusEffects.SPEED, 160, 1);
        check(effectHook.invoke(npc, speed, speed, fruit) == speed, "fruit friendly buffs retain level");
        protect(fruit, npc);
        var poison = new StatusEffectInstance(StatusEffects.POISON, 160, 1);
        check(effectHook.invoke(npc, poison, poison, fruit) == poison, "fruit whitelist receives no extra debuff level");
        TestPlayer nova = player(FormIdentifiers.OCELOT_NOVA);
        near(damage(nova, npc, 8), 6, "nova incoming NPC damage 0.75");
        near(damage(nova, playerTarget, 8), 8, "nova incoming player damage unchanged");
        near(NpcCombatEnhancements.novaMaxDamage(), 100, "nova NPC explosion maximum");
        TestPlayer anubis = player(FormIdentifiers.ANUBIS_WOLF_SP);
        near(damage(anubis, npc, 8), 4, "anubis incoming NPC damage 0.5");
        TestWolf pet = alloc(TestWolf.class); pet.ownerId = anubis.id;
        near(damage(pet, npc, 8), 4, "anubis pet shares incoming protection");
        near(damage(pet, playerTarget, 8), 8, "anubis pet incoming player damage unchanged");
        TestPlayer contract = player(FormIdentifiers.FAMILIAR_FOX_MANCIANIMA);
        near(NpcCombatEnhancements.fixedDamage(contract, npc, 2), 4, "contract bare-hand NPC damage doubles");
        near(NpcCombatEnhancements.fixedDamage(contract, playerTarget, 2), 2, "contract fixed player damage unchanged");
        Method primary = MancianimaPrimary.class.getDeclaredMethod("channelDamage", ServerPlayerEntity.class, LivingEntity.class, float.class);
        primary.setAccessible(true);
        near((float) primary.invoke(null, contract, npc, 1000f), 40, "contract NPC primary cap 40");
        near((float) primary.invoke(null, contract, playerTarget, 1000f), 37, "player splash recipient retains original cap");
        near(NpcCombatEnhancements.scale(contract, npc, 37, "mancianima_link_cap_mul", 2), 74, "contract link NPC cap doubles");
        for (String key : List.of("wind_primary_mul", "salticidae_secondary_damage")) {
            near(NpcCombatEnhancements.scale(contract, npc, 8, key, 1.5), 12, "NPC skill scale " + key);
            near(NpcCombatEnhancements.scale(contract, playerTarget, 8, key, 1.5), 8, "player skill scale unchanged " + key);
        }
        for (String key : List.of("salticidae_primary_damage", "desmodus_skill_damage"))
            near(NpcCombatEnhancements.scale(contract, npc, 8, key, 2), 16, "NPC skill scale " + key);
        check(NpcCombatEnhancements.duration(contract, npc, 160, "salticidae_primary_duration", 2) == 320,
                "salticidae primary NPC debuff duration doubles");
        TestPlayer wind = player(FormIdentifiers.OCELOT_SP);
        putMap(WindSpiritClawManager.class, "BUFF_TICKS", wind.id, 10);
        near(WindSpiritClawManager.getNormalMeleeMultiplier(wind, npc), 3, "wind secondary doubles existing 1.5 multiplier against NPC");
        near(WindSpiritClawManager.getNormalMeleeMultiplier(wind, playerTarget), 1.5f, "wind secondary player multiplier 1.5");
        checkState(npc, fallen, cat);
        checkExistingEffects(fallen, cat);
        checkEffectRemoval(fallen, cat);
        checkMoonWeaver();
        checkDomain(anubis, npc);
        checkVortex(npc);
        checkUpgradeAxolotl();
        checkSkillCooldownSnapshots();
        checkFoxFireOwnership();
        System.out.println("NPC combat integration: " + checks + " transformed hook/rule, whitelist, owner, stacking, duration, NBT and skill-risk checks passed. Live world/AI/multiplayer NOT tested.");
    }

    private static void checkUpgradeAxolotl() throws Exception {
        TestPlayer caster = player(FormIdentifiers.UPGRADE_AXOLOTL);
        TestNpc target = npc();
        TestPlayer enemyPlayer = player(FormIdentifiers.SHEEP_FORM);
        // Nonempty whitelist makes the player an eligible enemy; the NPC bonus must still skip it.
        caster.tags.add(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX + UUID.randomUUID());
        String key = "upgrade_axolotl_primary_damage";
        near(NpcCombatEnhancements.scale(caster, target, 12, key, 1.5), 18, "upgrade axolotl direct NPC damage");
        near(NpcCombatEnhancements.scale(caster, target, 5, key, 1.5), 7.5f, "upgrade axolotl area NPC damage");
        near(NpcCombatEnhancements.scale(caster, enemyPlayer, 12, key, 1.5), 12, "upgrade axolotl direct player damage unchanged");
        near(NpcCombatEnhancements.scale(caster, enemyPlayer, 5, key, 1.5), 5, "upgrade axolotl area player damage unchanged");
        near(damage(target, caster, 8), 8, "upgrade axolotl regular attacks get no primary skill bonus");
        protect(caster, target);
        near(NpcCombatEnhancements.scale(caster, target, 12, key, 1.5), 12, "upgrade axolotl direct bonus rechecks whitelist");
        near(NpcCombatEnhancements.scale(caster, target, 5, key, 1.5), 5, "upgrade axolotl area bonus rechecks whitelist");
    }

    private static void checkState(TestNpc npc, TestPlayer fallen, TestPlayer cat) throws Exception {
        var state = NpcCombatEnhancements.state(npc);
        state.markFallen(fallen.id, 100, 320); state.markWildCat(cat.id, 100, 200);
        NbtCompound nbt = new NbtCompound();
        hook("ssca$saveNpcCombat").invoke(npc, nbt, new CallbackInfo("writeCustomDataToNbt", false));
        TestNpc restored = npc();
        hook("ssca$loadNpcCombat").invoke(restored, nbt, new CallbackInfo("readCustomDataFromNbt", false));
        check(NpcCombatEnhancements.state(restored).hasFallen(fallen.id, 419), "saved mark retains caster and remaining window");
        check(!NpcCombatEnhancements.state(restored).hasFallen(fallen.id, 420), "restored mark expires at boundary");
        check(NpcCombatEnhancements.state(restored).wildCats(299).contains(cat.id), "saved stun retains independent caster");
        check(NpcCombatEnhancements.state(restored).wildCats(300).isEmpty(), "restored stun expires at boundary");
        hook("ssca$loadNpcCombat").invoke(restored, new NbtCompound(), new CallbackInfo("readCustomDataFromNbt", false));
        check(NpcCombatEnhancements.state(restored).wildCats(100).isEmpty(), "legacy targets have no invented vulnerability");
    }
    private static void checkExistingEffects(TestPlayer fallen, TestPlayer cat) throws Exception {
        TestNpc glowing = npc(); glowing.retainLongerEffects = true;
        glowing.effects.put(StatusEffects.GLOWING, new StatusEffectInstance(StatusEffects.GLOWING, 600, 0));
        NpcCombatEnhancements.applyFallenScream(fallen, glowing, 160);
        near(damage(glowing, fallen, 8), 15, "existing longer glow does not prevent personal fallen mark");
        TestPlayer other = player(FormIdentifiers.FALLEN_ALLAY_SP);
        NpcCombatEnhancements.applyFallenScream(other, glowing, 160);
        near(damage(glowing, other, 8), 15, "simultaneous fallen casters each receive their own mark");
        check(glowing.getStatusEffect(StatusEffects.GLOWING).getDuration() == 600, "existing longer glow is retained");
        TestNpc stunned = npc(); stunned.retainLongerEffects = true;
        stunned.effects.put(SscAddon.STUN, new StatusEffectInstance(SscAddon.STUN, 600, 0));
        NpcCombatEnhancements.applyWildCatStun(cat, stunned, 100);
        near(damage(stunned, null, 8), 16, "existing longer stun does not prevent wild-cat exposure");
        world.now = 300;
        near(damage(stunned, null, 8), 8, "existing longer stun cannot extend exposure beyond its own 10s window");
        world.now = 100;
    }
    private static void checkEffectRemoval(TestPlayer fallen, TestPlayer cat) throws Exception {
        TestNpc target = npc();
        NpcCombatEnhancements.applyFallenScream(fallen, target, 160);
        hook("ssca$clearNpcCombatEffect").invoke(target, target.getStatusEffect(StatusEffects.GLOWING),
                new CallbackInfo("onStatusEffectRemoved", false));
        target.effects.put(StatusEffects.GLOWING, new StatusEffectInstance(StatusEffects.GLOWING, 600, 0));
        near(damage(target, fallen, 8), 12, "unrelated glow cannot reactivate a removed fallen mark");
        NpcCombatEnhancements.applyWildCatStun(cat, target, 100);
        hook("ssca$clearNpcCombatEffect").invoke(target, target.getStatusEffect(SscAddon.STUN),
                new CallbackInfo("onStatusEffectRemoved", false));
        target.effects.put(SscAddon.STUN, new StatusEffectInstance(SscAddon.STUN, 600, 0));
        near(damage(target, null, 8), 8, "unrelated stun cannot reactivate removed wild-cat exposure");
    }
    private static void checkMoonWeaver() throws Exception {
        TestPlayer caster = player(FormIdentifiers.SPIDER_MOON_WEAVER);
        TestNpc target = npc();
        target.effects.put(RegOtherStatusEffects.ENTANGLED_FULL_EFFECT,
                new StatusEffectInstance(RegOtherStatusEffects.ENTANGLED_FULL_EFFECT, 160, 0));
        near(damage(target, caster, 8), 10, "cocooned NPC gets extra 25 percent damage");
        protect(caster, target);
        near(damage(target, caster, 8), 8, "cocooned whitelist target gets no new damage bonus");
        caster.tags.clear();
        Method tether = hook("ssc_addon$spiderTetherDamage");
        Object swing = alloc(Class.forName(SpiderMoonWeaverSwingManager.class.getName() + "$SwingState"));
        set(swing, "state", SpiderMoonWeaverSwingManager.STATE_TETHER);
        set(swing, "tetherEntityId", target.getId());
        putMap(SpiderMoonWeaverSwingManager.class, "STATES", caster.id, swing);
        near((float) tether.invoke(target, 10f, source(caster)), 11.5f, "NPC tether owner damage uses 15 percent");
        near((float) tether.invoke(caster, 10f, source(target)), 6, "NPC tether attacker damage falls 40 percent");
        near((float) tether.invoke(target, damage(target, caster, 8), source(caster)), 11.5f,
                "cocoon and NPC tether multipliers stack once");
        protect(caster, target);
        near((float) tether.invoke(caster, 10f, source(target)), 10, "tether defense skips whitelisted NPC");
        TestPlayer enemy = player(FormIdentifiers.SHEEP_FORM);
        set(swing, "tetherEntityId", enemy.getId());
        near((float) tether.invoke(enemy, 8f, source(caster)), 10, "player tether owner damage retains 25 percent");
        near((float) tether.invoke(caster, 8f, source(enemy)), 6, "player tether attacker damage retains 25 percent reduction");
        putMap(SpiderMoonWeaverSwingManager.class, "STATES", caster.id, null);
        near((float) tether.invoke(caster, 8f, source(target)), 8, "breaking tether immediately removes NPC reduction");
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void checkDomain(TestPlayer caster, TestNpc npc) throws Exception {
        Class<?> dataClass = Class.forName(AnubisWolfSpDeathDomain.class.getName() + "$DomainData");
        Object data = alloc(dataClass);
        set(data, "world", world); set(data, "center", net.minecraft.util.math.BlockPos.ORIGIN); set(data, "centerY", 0);
        set(data, "currentRadius", 6.0);
        Class<? extends Enum> phase = (Class<? extends Enum>) Class.forName(AnubisWolfSpDeathDomain.class.getName() + "$Phase");
        set(data, "phase", Enum.valueOf(phase, "EXPANDING"));
        putMap(AnubisWolfSpDeathDomain.class, "ACTIVE_DOMAINS", caster.id, data);
        position(npc, new Vec3d(5, 0, 0));
        near(damage(npc, caster, 10), 12, "domain inside current radius gets 20 percent more damage");
        position(npc, new Vec3d(7, 0, 0));
        near(damage(npc, caster, 10), 10, "not-yet-expanded domain ring grants no bonus");
        position(npc, new Vec3d(0, 10, 0));
        near(damage(npc, caster, 10), 10, "outside domain height grants no bonus");
        position(npc, Vec3d.ZERO);
        set(data, "phase", Enum.valueOf(phase, "CLEANUP"));
        near(damage(npc, caster, 10), 10, "cleanup cannot retain domain bonus");
    }
    private static void checkVortex(TestNpc npc) throws Exception {
        TestPlayer caster = player(FormIdentifiers.AXOLOTL_SP);
        Object charge = alloc(Class.forName(VortexChargeManager.class.getName() + "$ChargeState"));
        putMap(VortexChargeManager.class, "CHARGING", caster.id, charge);
        position(caster, Vec3d.ZERO); position(npc, new Vec3d(2, 0, 0));
        check(VortexChargeManager.suppressesNpcAttack(caster, npc), "pulled NPC cannot damage vortex caster");
        position(npc, new Vec3d(20, 0, 0));
        check(!VortexChargeManager.suppressesNpcAttack(caster, npc), "outside pull area NPC damage remains");
        position(npc, Vec3d.ZERO); protect(caster, npc);
        check(!VortexChargeManager.suppressesNpcAttack(caster, npc), "whitelisted NPC is not suppressed");
        caster.tags.clear(); npc.attributes = attributes(1.0);
        check(!VortexChargeManager.suppressesNpcAttack(caster, npc), "unpullable NPC is not suppressed");
        npc.attributes = attributes(0.0);
        putMap(VortexChargeManager.class, "CHARGING", caster.id, null);
        check(!VortexChargeManager.suppressesNpcAttack(caster, npc), "release/cancel removes suppression immediately");
    }

    private static void checkSkillCooldownSnapshots() throws Exception {
        String leapId = "my_addon:form_ocelot_nova_leap";
        var leapClass = Class.forName(NovaSkillManager.class.getName() + "$LeapSession");
        Method beginLeap = method(NovaSkillManager.class, "beginLeap", ServerPlayerEntity.class, long.class);
        Method finishLeap = method(NovaSkillManager.class, "finishSingleLeap", ServerPlayerEntity.class, leapClass);
        for (int single : new int[]{0, 120}) {
            TestPlayer caster = player(FormIdentifiers.OCELOT_NOVA);
            attach(caster, leapId, new SkillCooldownSpec(200, 0, "on_end", Map.of("single_use", single)));
            Object leap = beginLeap.invoke(null, caster, world.now);
            check(leap != null, "Nova accepts configured first leap");
            caster.powerMap.clear();
            check(SkillCooldowns.extra(caster, leapId, "single_use") == 0, "removed Nova power reproduces missing-spec sentinel");
            finishLeap.invoke(null, caster, leap);
            check(SkillCooldowns.remaining(caster, leapId) == single, "Nova retains captured single-use CD including explicit zero");
        }
        TestPlayer reloaded = player(FormIdentifiers.OCELOT_NOVA);
        attach(reloaded, leapId, new SkillCooldownSpec(200, 0, "on_end", Map.of("single_use", 120)));
        Object previous = beginLeap.invoke(null, reloaded, world.now);
        attach(reloaded, leapId, new SkillCooldownSpec(900, 0, "on_end", Map.of("single_use", 0)));
        finishLeap.invoke(null, reloaded, previous);
        check(SkillCooldowns.remaining(reloaded, leapId) == 120, "reload changes next Nova cast, not current window");
        world.now += 121;
        Object next = beginLeap.invoke(null, reloaded, world.now);
        long nextId = SkillCooldowns.currentCastId(reloaded, leapId);
        finishLeap.invoke(null, reloaded, previous);
        check(next != null && SkillCooldowns.currentCastId(reloaded, leapId) == nextId, "stale Nova window cannot settle a newer cast");
        finishLeap.invoke(null, reloaded, next);
        check(SkillCooldowns.remaining(reloaded, leapId) == 0, "next Nova cast uses reloaded explicit zero");
        TestPlayer missing = player(FormIdentifiers.OCELOT_NOVA);
        check(beginLeap.invoke(null, missing, world.now) == null, "missing Nova config cannot create a cast");

        String summonId = AnubisWolfSpSummonWolves.SKILL_ID;
        var domainClass = Class.forName(AnubisWolfSpDeathDomain.class.getName() + "$DomainData");
        var domainCtor = domainClass.getDeclaredConstructor(ServerWorld.class, net.minecraft.util.math.BlockPos.class, int.class);
        domainCtor.setAccessible(true);
        Method capture = method(AnubisWolfSpDeathDomain.class, "captureSummonCooldown", ServerPlayerEntity.class);
        Method apply = method(AnubisWolfSpDeathDomain.class, "applySummonCooldown", ServerPlayerEntity.class, domainClass);
        for (Integer configured : new Integer[]{null, 0, 600}) {
            TestPlayer caster = player(FormIdentifiers.ANUBIS_WOLF_SP);
            if (configured != null) attach(caster, summonId, new SkillCooldownSpec(configured, 100, "on_release", Map.of()));
            Object data = domainCtor.newInstance(world, net.minecraft.util.math.BlockPos.ORIGIN, 0);
            set(data, "summonCooldownTicks", capture.invoke(null, caster));
            SkillCooldowns.force(caster, summonId, 333);
            caster.powerMap.clear();
            apply.invoke(null, caster, data);
            check(SkillCooldowns.remaining(caster, summonId) == (configured == null ? 333 : configured),
                    "enhanced domain distinguishes missing summon config, explicit zero and captured positive CD");
        }
        TestPlayer interrupted = player(FormIdentifiers.ANUBIS_WOLF_SP);
        String domainId = "my_addon:form_anubis_wolf_sp_death_domain";
        attach(interrupted, domainId, new SkillCooldownSpec(400, 70, "on_end", Map.of()));
        check(SkillCooldowns.begin(interrupted, domainId), "domain charge accepted");
        Object data = domainCtor.newInstance(world, net.minecraft.util.math.BlockPos.ORIGIN, 0);
        set(data, "castId", SkillCooldowns.currentCastId(interrupted, domainId));
        set(data, "enhanced", true);
        set(data, "ticksElapsed", 19);
        putMap(AnubisWolfSpDeathDomain.class, "ACTIVE_DOMAINS", interrupted.id, data);
        interrupted.powerMap.clear(); interrupted.form.nowForm = new NormalForm(FormIdentifiers.SHEEP_FORM);
        SkillCooldowns.force(interrupted, summonId, 333);
        AnubisWolfSpDeathDomain.tick(interrupted);
        var phase = domainClass.getDeclaredField("phase"); phase.setAccessible(true);
        check(phase.get(data).toString().equals("CLEANUP"), "lost domain power stops charging before release");
        check(SkillCooldowns.remaining(interrupted, summonId) == 333, "interrupted enhanced domain preserves existing summon cooldown");
        check(SkillCooldowns.remaining(interrupted, domainId) == 70, "domain interruption settles captured failure cooldown");
        check(SkillCooldowns.currentCastId(interrupted, domainId) < 0, "interrupted domain frees cast gate");
        putMap(AnubisWolfSpDeathDomain.class, "ACTIVE_DOMAINS", interrupted.id, null);

        String webId = "my_addon:form_spider_moon_weaver_web";
        var chargeClass = Class.forName(SpiderMoonWeaverWebManager.class.getName() + "$ChargeState");
        Method complete = method(SpiderMoonWeaverWebManager.class, "completeCharge", ServerPlayerEntity.class, chargeClass, int.class);
        for (int full : new int[]{0, 601}) for (int tier = 1; tier <= 3; tier++) {
            TestPlayer caster = player(FormIdentifiers.SPIDER_MOON_WEAVER);
            attach(caster, webId, new SkillCooldownSpec(full, 0, "on_release", Map.of()));
            check(SkillCooldowns.begin(caster, webId), "web charge accepted");
            Object charge = alloc(chargeClass);
            set(charge, "castId", SkillCooldowns.currentCastId(caster, webId));
            set(charge, "cooldownTicks", full);
            attach(caster, webId, new SkillCooldownSpec(900, 0, "on_release", Map.of()));
            complete.invoke(null, caster, charge, tier);
            check(SkillCooldowns.remaining(caster, webId) == (int) Math.ceil((double) full * tier / 3),
                    "web tier uses captured cooldown, rounds up and honors explicit zero");
        }
        TestPlayer revokedWeb = player(FormIdentifiers.SPIDER_MOON_WEAVER);
        attach(revokedWeb, webId, new SkillCooldownSpec(600, 55, "on_release", Map.of()));
        check(SkillCooldowns.begin(revokedWeb, webId), "revoked web charge accepted");
        Object revokedCharge = alloc(chargeClass);
        set(revokedCharge, "castId", SkillCooldowns.currentCastId(revokedWeb, webId));
        set(revokedCharge, "cooldownTicks", 600);
        putMap(SpiderMoonWeaverWebManager.class, "CHARGING", revokedWeb.id, revokedCharge);
        revokedWeb.powerMap.clear();
        SpiderMoonWeaverWebManager.release(revokedWeb);
        check(SkillCooldowns.remaining(revokedWeb, webId) == 55, "revoked web release uses failure cooldown without firing");
        System.out.println("Skill risk integration: removed powers, reload snapshots, zero cooldowns, stale callbacks and domain/web interruption PASS.");
    }

    private static void checkFoxFireOwnership() throws Exception {
        TestPlayer oldOwner = player(FormIdentifiers.FAMILIAR_FOX_SP);
        TestPlayer latestOwner = player(FormIdentifiers.FAMILIAR_FOX_SP);
        TestNpc target = npc();
        Method damageSource = method(FoxFireBurnEffect.class, "damageSource", LivingEntity.class);
        target.lastAttacker = oldOwner;
        target.tags.add("ssc_burn:" + oldOwner.id);
        target.tags.add("ssc_owner:" + oldOwner.id);
        FoxFireBurnOwnership.record(target, latestOwner.id);
        check(!target.tags.contains("ssc_burn:" + oldOwner.id) && target.tags.contains("ssc_burn:" + latestOwner.id),
                "new burn replaces old attribution tags");
        check(target.tags.contains("ssc_owner:" + oldOwner.id), "recording burn preserves summon ownership tags");
        check(((DamageSource) damageSource.invoke(null, target)).getAttacker() == latestOwner, "latest online burn owner receives attribution");
        target.tags.add("ssc_burn:" + oldOwner.id); // Legacy saves may still contain historical tags.
        players.online.remove(latestOwner.id);
        check(((DamageSource) damageSource.invoke(null, target)).getAttacker() == null,
                "latest owner disconnecting never credits old tags or recent attackers");
        players.online.put(latestOwner.id, latestOwner);
        NbtCompound saved = new NbtCompound();
        entityHook("injectWriteNbt").invoke(target, saved, new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<NbtCompound>("writeNbt", false));
        TestNpc restored = npc();
        entityHook("injectReadNbt").invoke(restored, saved, new CallbackInfo("readNbt", false));
        check(FoxFireBurnOwnership.resolve(restored).uuid().equals(latestOwner.id), "latest burn owner survives actual ignition NBT hooks");

        ((SscIgnitedEntityAccessor) (Entity) target).sscAddon$setIgniterUuid(null);
        target.tags.clear(); target.tags.add("ssc_burn:" + latestOwner.id); target.tags.add("ssc_owner:" + oldOwner.id);
        check(((DamageSource) damageSource.invoke(null, target)).getAttacker() == latestOwner, "legacy burn prefix takes priority over summon prefix");
        target.tags.add("ssc_burn:" + oldOwner.id);
        check(((DamageSource) damageSource.invoke(null, target)).getAttacker() == null, "ambiguous old burn tags never guess a caster");
        target.tags.clear(); target.tags.add("ssc_owner:" + oldOwner.id);
        check(((DamageSource) damageSource.invoke(null, target)).getAttacker() == oldOwner, "single legacy owner tag remains compatible");
        FoxFireBurnOwnership.record(target, null);
        check(((DamageSource) damageSource.invoke(null, target)).getAttacker() == null, "new unowned burn never inherits summon owner or recent attacker");
        check(target.tags.contains("ssc_owner:" + oldOwner.id), "unowned burn still preserves actual summon ownership");
        target.tags.clear(); target.tags.add("ssc_burn:invalid-uuid");
        check(((DamageSource) damageSource.invoke(null, target)).getAttacker() == oldOwner, "without valid recorded ownership, vanilla recent-attacker fallback remains");
        System.out.println("Fox-fire ownership integration: latest/offline caster, stale and ambiguous tags, summon-tag preservation and NBT round trip PASS.");
    }

    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception {
        Method method = type.getDeclaredMethod(name, args); method.setAccessible(true); return method;
    }
    private static Method entityHook(String name) {
        for (Method m : Entity.class.getDeclaredMethods()) if (m.getName().endsWith(name)) { m.setAccessible(true); return m; }
        throw new AssertionError("missing applied Entity Mixin hook " + name);
    }
    private static void attach(TestPlayer player, String id, SkillCooldownSpec spec) {
        Identifier key = new Identifier(id);
        if (!PowerTypeRegistry.contains(key)) PowerTypeRegistry.register(key, new PowerType<>(key, null));
        PowerType<?> type = PowerTypeRegistry.get(key);
        player.powerMap.put(type, new TestCooldownPower(type, player, spec));
    }
    private static float damage(LivingEntity victim, Entity attacker, float amount) throws Exception {
        return (float) damageHook.invoke(victim, amount, source(attacker));
    }
    private static DamageSource source(Entity attacker) { return new DamageSource(RegistryEntry.of(new DamageType("test", 0)), attacker); }
    private static Method hook(String name) {
        for (Method m : LivingEntity.class.getDeclaredMethods()) if (m.getName().endsWith(name)) { m.setAccessible(true); return m; }
        throw new AssertionError("missing applied Mixin hook " + name);
    }
    private static TestPlayer player(Identifier id) throws Exception {
        TestPlayer p = alloc(TestPlayer.class); p.id = UUID.randomUUID(); p.tags = new HashSet<>(); p.effects = new HashMap<>();
        p.powerMap = new HashMap<>();
        p.powers = (PowerHolderComponent) java.lang.reflect.Proxy.newProxyInstance(PowerHolderComponent.class.getClassLoader(),
                new Class<?>[]{PowerHolderComponent.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getPower" -> p.powerMap.get(args[0]);
                    case "getPowers" -> List.copyOf(p.powerMap.values());
                    case "hasPower" -> p.powerMap.containsKey(args[0]);
                    default -> null;
                });
        p.setId(nextEntityId++);
        p.form = alloc(PlayerFormComponent.class); p.form.nowForm = new NormalForm(id);
        p.components = ComponentContainer.Factory.builder(TestPlayer.class).component(RegPlayerFormComponent.PLAYER_FORM, x -> x.form)
                .component(PowerHolderComponent.KEY, x -> x.powers).build().createContainer(p);
        position(p, Vec3d.ZERO); players.online.put(p.id, p); return p;
    }
    private static TestNpc npc() throws Exception {
        TestNpc npc = alloc(TestNpc.class); npc.id = UUID.randomUUID(); npc.effects = new HashMap<>(); npc.attributes = attributes(0);
        npc.tags = new HashSet<>(); npc.damageSources = alloc(TestDamageSources.class);
        npc.setId(nextEntityId++);
        position(npc, Vec3d.ZERO); return npc;
    }
    private static AttributeContainer attributes(double resistance) {
        return new AttributeContainer(DefaultAttributeContainer.builder().add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, resistance).build());
    }
    private static void position(Entity e, Vec3d pos) throws Exception {
        var f = Entity.class.getDeclaredField("pos"); f.setAccessible(true); f.set(e, pos);
        e.setBoundingBox(new Box(pos.x - 0.3, pos.y, pos.z - 0.3, pos.x + 0.3, pos.y + 1.8, pos.z + 0.3));
    }
    private static void protect(TestPlayer caster, LivingEntity target) { caster.tags.add(WhitelistUtils.WHITELIST_MOB_TAG_PREFIX + target.getUuid()); }
    @SuppressWarnings("unchecked") private static void putMap(Class<?> type, String name, UUID id, Object value) throws Exception {
        var f = type.getDeclaredField(name); f.setAccessible(true); var map = (Map<UUID, Object>) f.get(null);
        if (value == null) map.remove(id); else map.put(id, value);
    }
    private static void set(Object obj, String name, Object value) throws Exception { var f = obj.getClass().getDeclaredField(name); f.setAccessible(true); f.set(obj, value); }
    @SuppressWarnings("unchecked") private static <T> T alloc(Class<T> type) throws InstantiationException { return (T) unsafe.allocateInstance(type); }
    private static void near(float actual, float expected, String message) { check(Math.abs(actual - expected) < 0.0001, message + ": " + actual + " expected " + expected); }
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }

    public static final class TestWorld extends ServerWorld {
        MinecraftServer server; long now;
        PersistentStateManager states;
        private TestWorld() { super(null, null, null, null, World.OVERWORLD, null, null, false, 0, List.of(), false, null); }
        @Override public MinecraftServer getServer() { return server; }
        @Override public long getTime() { return now; }
        @Override public ServerPlayerEntity getPlayerByUuid(UUID id) { return players.online.get(id); }
        @Override public PersistentStateManager getPersistentStateManager() { return states; }
    }
    public static final class TestStates extends PersistentStateManager {
        private final Map<String, PersistentState> states = new HashMap<>();
        TestStates() { super(null, null); }
        @Override @SuppressWarnings("unchecked") public <T extends PersistentState> T getOrCreate(
                java.util.function.Function<NbtCompound, T> read, java.util.function.Supplier<T> create, String id) {
            return (T) states.computeIfAbsent(id, ignored -> create.get());
        }
    }
    public static final class TestCooldownPower extends Power implements SkillCooldownHolder {
        private final SkillCooldownSpec spec;
        TestCooldownPower(PowerType<?> type, LivingEntity owner, SkillCooldownSpec spec) { super(type, owner); this.spec = spec; }
        @Override public SkillCooldownSpec cooldownSpec() { return spec; }
    }
    public static final class TestDamageSources extends DamageSources {
        private TestDamageSources() { super(null); }
        @Override public DamageSource inFire() { return source(null); }
        @Override public DamageSource playerAttack(PlayerEntity player) { return source(player); }
    }
    public static final class TestPlayers extends DedicatedPlayerManager {
        Map<UUID, ServerPlayerEntity> online;
        private TestPlayers() { super(null, null, null); }
        @Override public ServerPlayerEntity getPlayer(UUID id) { return online.get(id); }
    }
    public static final class TestPlayer extends ServerPlayerEntity implements ComponentProvider {
        UUID id; Set<String> tags; Map<StatusEffect, StatusEffectInstance> effects; PlayerFormComponent form; ComponentContainer components;
        Map<PowerType<?>, Power> powerMap; PowerHolderComponent powers;
        private TestPlayer() { super(null, null, new GameProfile(UUID.randomUUID(), "test")); }
        @Override public ComponentContainer getComponentContainer() { return components; }
        @Override public World getWorld() { return world; }
        @Override public ServerWorld getServerWorld() { return world; }
        @Override public UUID getUuid() { return id; }
        @Override public net.minecraft.text.Text getName() { return net.minecraft.text.Text.literal("test-" + id); }
        @Override public net.minecraft.entity.attribute.EntityAttributeInstance getAttributeInstance(net.minecraft.entity.attribute.EntityAttribute attribute) { return null; }
        @Override public Set<String> getCommandTags() { return tags; }
        @Override public boolean isAlive() { return true; }
        @Override public boolean hasStatusEffect(StatusEffect effect) { return effects.containsKey(effect); }
        @Override public StatusEffectInstance getStatusEffect(StatusEffect effect) { return effects.get(effect); }
        @Override public boolean addStatusEffect(StatusEffectInstance effect, Entity source) { effects.put(effect.getEffectType(), effect); return true; }
        @Override public ItemStack getMainHandStack() { return ItemStack.EMPTY; }
    }
    public static final class TestNpc extends LivingEntity {
        UUID id; Map<StatusEffect, StatusEffectInstance> effects; AttributeContainer attributes; boolean retainLongerEffects;
        Set<String> tags; DamageSources damageSources; LivingEntity lastAttacker;
        private TestNpc() { super(EntityType.ZOMBIE, null); }
        @Override public World getWorld() { return world; }
        @Override public UUID getUuid() { return id; }
        @Override public EntityType<?> getType() { return EntityType.ZOMBIE; }
        @Override public Set<String> getCommandTags() { return tags; }
        @Override public boolean addCommandTag(String tag) { return tags.add(tag); }
        @Override public DamageSources getDamageSources() { return damageSources; }
        @Override public LivingEntity getLastAttacker() { return lastAttacker; }
        @Override public LivingEntity getAttacker() { return lastAttacker; }
        @Override public MinecraftServer getServer() { return world.server; }
        @Override public boolean canHaveStatusEffect(StatusEffectInstance effect) { return true; }
        @Override public boolean hasStatusEffect(StatusEffect effect) { return effects.containsKey(effect); }
        @Override public StatusEffectInstance getStatusEffect(StatusEffect effect) { return effects.get(effect); }
        @Override public boolean addStatusEffect(StatusEffectInstance effect, Entity source) {
            StatusEffectInstance old = effects.get(effect.getEffectType());
            if (retainLongerEffects && old != null && old.getDuration() >= effect.getDuration()) return false;
            effects.put(effect.getEffectType(), effect); return true;
        }
        @Override public AttributeContainer getAttributes() { return attributes; }
        @Override public Iterable<ItemStack> getArmorItems() { return List.of(); }
        @Override public ItemStack getEquippedStack(net.minecraft.entity.EquipmentSlot slot) { return ItemStack.EMPTY; }
        @Override public void equipStack(net.minecraft.entity.EquipmentSlot slot, ItemStack stack) { }
        @Override public net.minecraft.util.Arm getMainArm() { return net.minecraft.util.Arm.RIGHT; }
    }
    public static final class TestVex extends VexEntity {
        Set<String> tags;
        private TestVex() { super(EntityType.VEX, null); }
        @Override public World getWorld() { return world; }
        @Override public Set<String> getCommandTags() { return tags; }
        @Override public MinecraftServer getServer() { return world.server; }
    }
    public static final class TestWolf extends WolfEntity {
        UUID ownerId;
        private TestWolf() { super(EntityType.WOLF, null); }
        @Override public World getWorld() { return world; }
        @Override public UUID getOwnerUuid() { return ownerId; }
        @Override public boolean hasStatusEffect(StatusEffect effect) { return false; }
        @Override public MinecraftServer getServer() { return world.server; }
    }
}
