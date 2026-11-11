# Parasitic Fruit Bat — Moving Orchard

> "When the Evolution Stone presses the vitality of fruit and echo into a bat's wing bones, it is no longer merely a shadow in the night...\
> You become the sower of a moving orchard, making seeds bloom inside allies and enemies alike."

The Parasitic Fruit Bat is a supportive bat variant born from the **Evolution Stone**. It mutates the bat's fruit-eating instinct into a parasitic sowing power: planting buff fruit on allies, debuff fruit on enemies, and throwing infection spore bombs for sustained area pressure and team healing. It is a support / control form built around the "Seed Charge" resource.

***

## Form Profile

| Item | Description |
| --- | --- |
| Registry name | `my_addon:bat_parasitic_fruit` |
| Classification | Bat Evolution Stone variant (a separate branch from Desmodus) |
| Affinity | Parasitic Sower / Support |
| Combat role | Team Support / Sustained Control / Area Debuffs |

***

## Evolution Requirements

| Condition | Requirement |
| --- | --- |
| Base form | Bat (Stage III) |
| Evolution item | Evolution Stone |
| Note | A **different evolution path** from Desmodus (Moon Essence Cross variant); the Parasitic Fruit Bat follows the Evolution Stone route |

***

[Watch the bilingual Parasitic Fruit Bat tutorial](../../tutorials/parasitic-fruit/index.html?lang=en_us)

## Addon Abilities and Changes

### 1. Parasitic Seed: Charging, Selection, and Friendly Support

Aim and hold the primary key for 0.5–1.5 seconds, then release to throw. More charge increases launch speed and range. A cue sounds at minimum charge and another at full charge; a full charge can be held. Releasing too soon, opening a screen, or being interrupted cancels charge.

Quickly double tap the primary key to cycle **Automatic, Vine, Bitter, Rotten, Sour**. The lower HUD slot shows the selection. Automatic decides from movement, attacking, health, and armor on impact; manual modes fix the hostile fruit type. Selection survives reconnecting and respawning, and resets on changing form.

| Hostile fruit | Effect |
| --- | --- |
| Vine | Slowness; slows airborne hosts and pulls them downward |
| Bitter | Weakness and Glowing |
| Rotten | Poison on ordinary living hosts; Slowness and Weakness on undead |
| Sour | Slowness and Glowing |

A successful throw costs one seed energy and starts a one-second cooldown. Seeds root for one second, then fruit every 1.25 seconds by default. Friends always receive support based on health and combat state, regardless of manual hostile-fruit selection.

Outside combat, friends above seventy percent health receive Haste I; otherwise they heal two health. In combat, above eighty percent gives Speed II, fifty to eighty gives Absorption I plus two health, and below fifty gives Absorption II plus four health. Healing and absorption do not multiply with layers.

### 2. Ground Seed Fields and Pickup

A seed that misses living targets leaves a green field, lasting twelve seconds by default. Valid living hosts within one block can pick it up, including players, mobs, animals, and the caster. The caster must wait half a second after the seed lands before triggering it.

Pickup grants a **fresh full five- or fifteen-second duration**, based on the host's current combat state; waiting time on the ground is not deducted. Seeds retain their manual fruit choice. Missing still spends energy and starts cooldown. A field disappears on pickup or expiry.

### 3. Shared Layers, Independent Expiry, and Host Limit

All casters share a cap of three layers on one host. Identical fruit types stack separately, and different types coexist. A fourth layer replaces the oldest. Each layer roots and expires independently; later hits do not add lifetime to old layers.

Each hit gives its layer five seconds in combat or fifteen outside combat. Identical status effects use the highest applicable level. Friendly healing and absorption do not multiply with layers or caster count. Full layers have a faction-colored outline; infection outlines have higher display priority.

Each caster can sustain at most three independent hosts. A fourth host removes only that caster's layers from their earliest host, preserving other casters' layers there.

### 4. Infection Spore Bomb

Press the secondary key to throw a spore bomb.

Costs one seed energy with a one-second cooldown. On impact, whitelisted targets within four blocks receive ten seconds of healing spores, restoring one health every three seconds. Others become infected, take periodic damage, briefly glow, lose hunger healing and deal fifteen percent less damage.

The explosion itself deals no direct damage. Infection spreads to unprotected targets within 1.5 blocks using the source's remaining duration. Non-player enemies also receive enhanced harmful-effect levels, which can affect periodic damage. Infection poison follows poison ticks and cannot by itself reduce health below one.

### 5. Lingering Clouds and Purification

Land a spore bomb on a block and let targets enter its cloud.

A block impact also leaves a cloud two blocks in radius for ten seconds. Unprotected entrants become infected and protected entrants receive healing, using the cloud's remaining lifetime.

Explosion infection and cloud pulses are separate effects; entering the cloud does not itself copy explosion infection. Allay Purification can cure infection and disperse nearby clouds. A second real player demonstrates it as Crystal Sound.

### 6. Seed Energy and Combat Recovery

Watch seed energy, or try both skills while empty.

Energy is capped at ten. Both skills cost one. Restore one every five seconds outside combat or every eight seconds in combat; recovery stops at the cap.

Insufficient energy produces no projectile or new skill cooldown. Recent damage dealt or taken counts as combat, and a nearby attacker can keep combat active longer.

### 7. Eating Vanilla Seeds

Hold vanilla seeds in the main hand, sneak and hold the use key to eat.

Finishing an actual eating action restores one seed energy. Wheat, beetroot, pumpkin, melon and torchflower seeds, plus pitcher pods, qualify.

At most eight seed meals may finish in a rolling three-minute window. Wait until eating completes; natural energy regeneration may happen during the same footage.

### 8. Plant Growth

Stand near plants that can still grow.

Every second, plants within five blocks each have a five percent chance to receive an extra growth opportunity.

Side-by-side crops inside and outside the radius provide a control. Vanilla random growth is disabled in the film set to isolate this passive. Growth is probabilistic.

### 9. Spell Debuff Transmission

Apply an eligible spellbook debuff and keep other enemies near its host.

Eligible harmful spells cost twenty-five percent more collapse. An enemy continuously within three blocks of a host for 2.5 seconds can inherit its remaining debuffs. Each host creates at most two children, across at most three generations.

This is a separate spellbook interaction; ordinary fruit pulses do not spread to neighbors. Whitelist protection remains active. Fire, native skills and expired effects do not enter this spell transmission chain.

### 10. NPC Debuff Boost and Healing Reduction

Apply harmful effects to enemy non-player living targets, or heal an enemy fruit host.

Harmful effects gain one extra level against enemy non-player living targets by default. An enemy fruit host receives fifty percent less healing.

Stacks further raise applicable fruit levels. An eight-health heal only restores four while fruit healing reduction is active. Transmission copies the existing boosted level without repeatedly boosting each generation.

### 11. Summoning Affinity

Cast a Summon-element spellbook spell.

Effective spell level gains one, capped at five.

The same level-two Lunar Spirit spell compares a human with Parasitic Fruit Bat, crossing a summon-count threshold. The stored scroll level is not permanently changed.

### 12. Fixed Base Health

Transform into Parasitic Fruit Bat and compare day with night.

Base maximum health is sixteen, replacing the inherited bat sunlight-dependent health configuration.

The clip switches between daylight and night. Equipment, extra attributes and other mods can still alter final maximum health.

## Exclusive Trinkets

| Trinket | Effect |
| --- | --- |
| Humus Ring | Hostile debuff fruit duration **+50%**; but allied buff fruit duration **-30%** |
| Twin Pod | Also plants on another valid host within four blocks, or adds a layer to the first host if none is available. Costs **two seeds** with a **two-second** cooldown |

***

## Combat Role

**A seed-economy team support / controller**

The Parasitic Fruit Bat's core loop:

1. Use **Parasitic Spiritfruit** to buff allies and debuff enemies, covering more units through the spread.
2. Use **Infection Spore Bomb** to apply sustained magic damage, heal suppression, and damage reduction to enemy groups, while providing healing spores to allies.
3. Maintain the Seed Charge cycle with **Spirit Fruit Bloom** and by eating vanilla seeds, keeping skills available.

It is not a frontline bruiser but a form that amplifies allies and weakens enemies through continuous buff / debuff management. The Humus Ring strengthens the debuff route, while the Twin Pod strengthens coverage — choose based on your team role.

***

## Inner Monologue

> "You can hear the faint flow of moisture inside flesh, and smell different scents from wounds, fear, and fatigue. Your instinct no longer seeks only fruit — it seeks bodies suitable for seeds to root in. Allies are moving orchards. So are enemies."
