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
