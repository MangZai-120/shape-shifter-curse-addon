# Debuffs

This mod adds several debuff / damage status effects.

---

## Fox Fire Burn

**Fox Fire Burn** (`effect.ssc_addon.fox_fire_burn`)

The signature debuff of SP Familiar Fox.

- **Type**: Damage (DoT)
- **Source**: SP Familiar Fox's Blue Fire Ring and Fox Fire Breath
- **Quirk**: Inflicts both **Burn and Frostbite** simultaneously
- **Property**: Cannot be put out by water (soul fire)

---

## Stun

**Stun** (`effect.ssc_addon.stun`)

Wild Cat (SP)'s control effect.

- **Type**: Control
- **Source**: Wild Cat (SP)'s Stun Dash
- **Effect**: Brief disable / can't act
- **Trigger**: Caused on landing of the secondary-key dash performed during True Invisibility

---

## Frost Freeze

**Frost Freeze** (`effect.ssc_addon.frost_freeze`)

SP Snow Fox's strongest crowd-control debuff.

- **Type**: Slow / Damage Amplify / Anti-heal
- **Source**: SP Snow Fox's frost skills

| Effect | Value |
|--------|-------|
| Severe Slow | Movement speed -**35%** |
| Attack Slow | Attack speed -**40%** |
| Anti-heal | Cannot regenerate naturally |
| Vulnerable | Physical and magic damage taken +**35%** |

!!! note "Immunity"
    SP Snow Fox is immune to this effect.

---

## Frost Fall

**Frost Fall** (`effect.ssc_addon.frost_fall`)

SP Snow Fox's persistent slowing debuff.

- **Type**: Slow
- **Source**: SP Snow Fox's frost-shield abilities

| Effect | Value |
|--------|-------|
| Slow | Movement speed -**30%** |
| Visual | Trailing snow particles |

!!! note "Immunity"
    SP Snow Fox is immune to this effect.

---

## Deafen

**Deafen** (`effect.ssc_addon.deafen`)

The special control Desmodus's Sonic Burst applies to player targets.

- **Type**: Sense Deprivation
- **Source**: Desmodus's Sonic Burst (on hitting a player target)
- **Effect**: Temporarily cannot hear any sound (client-side mute)
- **Duration**: 3 seconds
- **Note**: Non-player targets instead gain 3 seconds of Blindness

---

## Poison

**Poison** (`effect.ssc_addon.bat_poison`)

One of the debuff-fruit effects the Parasitic Fruit Bat plants on enemies.

- **Type**: Damage (DoT)
- **Source**: Parasitic Fruit Bat's Parasitic Spiritfruit (Sour Fruit etc. grown on hostile hosts)
- **Effect**: Periodic damage over time
- **Note**: A separate custom poison effect; some "no-buff" forms (e.g. SP Familiar Fox) are immune to it

---

## Infection

**Infection**

The sustained debuff and contagion state caused by the Parasitic Fruit Bat's Infection Spore Bomb.

- **Type**: DoT / Debuff / Contagion
- **Source**: Parasitic Fruit Bat's Infection Spore Bomb (non-whitelisted creatures within the explosion and poison fog cloud)
- **Effects**:
    - Over 15 seconds, takes **1 magic damage** and Glows for 1 second every 3 seconds
    - **Cannot heal from food saturation**
    - All damage dealt is **reduced by 15%**
    - Spreads to non-whitelisted creatures within 1.5 blocks
- **Cleanse**: The Allay's Purify clears the infection and dissipates the cloud

## Spider Web Bound

Moon Weaver's webs can slow unprotected targets by 50%. The effect refreshes two-second Mining Fatigue I and Weakness I, which can briefly remain after the web effect ends. [MCMod entry](https://www.mcmod.cn/item/987035.html)

## Other Spell and Form Effects

| Effect | Use |
| --- | --- |
| [Erosion Brand I–III](https://www.mcmod.cn/item/942559.html) | Displays the number of brands applied by [Golden Sandstorm](../sp_forms/sp_golden_sandstorm.md). |
| [Frost Shatter](https://www.mcmod.cn/item/1017632.html) | Frost Nova halves armor by default. A new hit refreshes the effect. |
| [Curse Mark](https://www.mcmod.cn/item/1017633.html) | Increases damage taken and reduces damage dealt. Strength and duration scale with spell tier. |
| [Sheep Form](https://www.mcmod.cn/item/1038990.html) | Beep Sheep temporarily changes the target's appearance and restricts attacking and block interactions. |

[Elemental anomalies](../spells/anomalies.md) produce Freeze, Lunar Eclipse, Curse Weakness, and other reactions. Disorder, Disintegration, and Breakdown remain registered for older data. In 8.0.0, movement and health penalties use the [current overload tier](../spells/collapse.md).
