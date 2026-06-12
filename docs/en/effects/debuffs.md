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
