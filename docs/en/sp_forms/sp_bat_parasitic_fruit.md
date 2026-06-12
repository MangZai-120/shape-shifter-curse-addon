# Parasitic Fruit Bat — Moving Orchard

> "When the Evolution Stone presses the vitality of fruit and echo into a bat's wing bones, it is no longer merely a shadow in the night...\
> You become the sower of a moving orchard, making seeds bloom inside allies and enemies alike."

The Parasitic Fruit Bat is a supportive bat variant born from the **Evolution Stone**. It mutates the bat's fruit-eating instinct into a parasitic sowing power: planting buff fruit on allies, debuff fruit on enemies, and throwing infection spore bombs for sustained area pressure and team healing. It is a support / control form built around the "Seed Charge" resource.

***

## Form Profile

| Item | Description |
| --- | --- |
| Registry name | `my_addon:form_bat_parasitic_fruit` |
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

## Core Resource: Seed Charge

The exclusive resource of the Parasitic Fruit Bat form, and the cost of both active skills:

* Maximum **10**; each active skill costs **1 charge** per cast; skills cannot be cast when charges are insufficient.
* Natural regen: **1 charge every 5 seconds**.
* Active refill: sneak + right-click a **vanilla seed** in the main hand to start a **2-second** eating session (must keep sneaking); up to **8 seeds** per **3 minutes**, beyond which a "can't stomach any more" warning appears.

***

## Active Skills

### Parasitic Spiritfruit (Primary)

Launch a spiritfruit seed toward your crosshair (within **6 blocks**, cannot pass through walls). On hitting a creature it roots, then bears fruit every **2 seconds**:

* **Allied host**: grows Honeydew / Prickly Pear / Wind Berry / Fragrant Fruit according to its state, providing healing, absorption, protection, strength, speed, slow falling, or haste.
* **Hostile host**: grows Vine / Bitter / Rotten / Sour Fruit, inflicting Slowness, Weakness, Glowing, Poison, and similar debuffs.
* **Heal suppression**: while parasitized by a debuff fruit, all healing the target receives is **reduced by 50%**.
* **Spread**: fruit effects spread from the host to nearby same-side creatures with a **halved-duration** version.
* **Parasite limit**: maintain up to **3 spiritfruit seeds** at once; a target carries only 1 seed, hitting it again refreshes the duration.
* **1s cooldown**, costs **1 Seed Charge**; missing a creature triggers a half cooldown without consuming a charge.

### Infection Spore Bomb (Secondary)

Throw an exploding seed that detonates **without dealing damage** on hitting a block or creature, releasing spores:

* **Within the 4-block explosion**: whitelist-protected allies are healed for **1 HP every 3 seconds for 15 seconds**; other creatures are **infected**.
* **Infection** (15s): take **1 magic damage** and Glow for 1 second every 3 seconds, cannot heal from food saturation, and all damage they deal is **reduced by 15%**.
* **Contagion**: non-whitelisted creatures within **1.5 blocks** of an infected target are infected as well, inheriting the source's remaining duration.
* **Poison fog cloud**: on landing it leaves a **4-block-diameter** cloud lasting **15 seconds**; non-whitelisted creatures entering are infected for the cloud's remaining time, while allies entering receive healing spores.
* The Allay's **Purify** clears the infection and dissipates the cloud.
* **1s cooldown**, costs **1 Seed Charge**.

***

## Passive Talents

### Seed Charge Regeneration

Regenerates **1 Seed Charge every 5 seconds**, up to a maximum of **10**.

### Spirit Fruit Bloom

Form passive: every second, each crop within a **5-block** radius has a **5%** chance to advance one growth tick.

### Inherited Bat Abilities

Retains most traits of a Stage III Bat: small body, night vision, slow falling, wall clinging, aerial acceleration, enhanced jumping, fast bare-hand digging, and fruit instinct.

***

## Weaknesses & Limitations

| Limitation | Description |
| --- | --- |
| Must hit | Spiritfruit seeds must hit a creature to take effect; missing only triggers a half cooldown |
| Day/night influence | Under open sky during the day, seed duration is shortened; at night or in low light it is extended |
| Self-reduction | Positive fruits planted on yourself have reduced duration, preventing support from becoming pure self-buffing |
| Armor restriction | A bat's small frame — cannot equip chestplates, leggings, or boots, and cannot use ranged weapons |
| Slow on ground | Walking on the ground is slow |

***

## Exclusive Trinkets

| Trinket | Effect |
| --- | --- |
| Humus Ring | Hostile debuff fruit duration **+50%**; but allied buff fruit duration **-30%** |
| Twin Pod | Each cast also parasitizes the nearest second target; but doubles Seed Charge cost and adds **+1s** cooldown |

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
