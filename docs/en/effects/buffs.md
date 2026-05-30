# Buffs

This mod adds several buff status effects.

---

## Playing Dead

**Playing Dead** (`effect.ssc_addon.playing_dead`)

The signature survival skill of SP Axolotl.

- **Type**: Defensive / Recovery
- **Source**: SP Axolotl active skill
- **Effects**:
    - All mob aggro is removed
    - Comes with **Blindness**
    - **90% damage reduction**
    - Rapid health regeneration

---

## True Invisibility

**True Invisibility** (`effect.ssc_addon.true_invisibility`)

The signature stealth ability of Wild Cat (SP).

- **Type**: Stealth
- **Source**: Wild Cat (SP) primary skill key
- **Effects**:
    - Completely invisible to **all creatures**
    - Attacking or using items **breaks the invisibility**
    - Manual cancel triggers **Weakness Spotting**

---

## Invisibility Prep

**Invisibility Prep** (`effect.ssc_addon.pre_invisibility`)

The pre-cast state before True Invisibility.

- **Type**: Preparation
- **Source**: Wild Cat (SP) when activating the stealth skill
- **Effects**: Brief preparation phase before True Invisibility kicks in

---

## Blue Fire Ring

**Blue Fire Ring** (`effect.ssc_addon.blue_fire_ring`)

The sustained AOE state of SP Familiar Fox.

- **Type**: Offensive Enhancement
- **Source**: SP Familiar Fox toggle skill
- **Effects**:
    - A blue fire ring orbiting your body
    - Continuously burns nearby enemies
    - Kills restore extra mana and health
    - Drains mana over time

---

## Weakness Spotting

**Weakness Spotting** (`effect.ssc_addon.guaranteed_crit`)

The burst buff Wild Cat (SP) gets when actively cancelling stealth.

- **Type**: Offensive Enhancement
- **Source**: Manually cancelling True Invisibility while in stealth
- **Effects**:
    - **5 seconds of guaranteed critical hits**
    - **Speed II** effect
- **Duration**: 5 seconds

---

## Purification

**Purification**

A buff that removes negative status effects.

- **Type**: Cleanse
- **Source**: Specific skills or items
- **Effects**: Removes some negative status effects from the target
