# Mancianima — Forbidden Weapon

> "Mancianima is not the gods' blessing, but an answer torn from a god's corpse...\
> Witches weld souls with Evolution Stones, forging the Familiar Fox into the Pillager faction's quietest, costliest forbidden weapon."

It still retains the Familiar Fox silhouette, but its fur, eye light, and spiritual pressure feel recalibrated by the Evolution Stone. Its motions are calm and precise — like a living weapon finally awakened after countless stabilization rituals.

---

## Form Profile

| Item | Description |
| --- | --- |
| Registry name | `my_addon:form_familiar_fox_mancianima` |
| Classification | Sibling SP Familiar Fox branch (parallel Spirit Realm Lord lineage) |
| Affinity | **Pillager (Illager) faction** — hostile to iron golems, villagers, wandering traders |
| Combat role | Single-target hunter / weaponized assassin (chase + brand + true damage execute) |

---

## Evolution Requirements

| Condition | Requirement |
| --- | --- |
| Base form | Familiar Fox (any permanent form) |
| Item | Evolution Stone (a high-tier weaponized crystal stabilized from Spirit Realm Lord corpses) |
| Note | Parallel to SP Familiar Fox — **different path**; transformation imprints the Pillager faction brand on you |

---

## Active Skills

### Mancianima Brand (Primary)

Sustained aim on a creature performs weapon calibration:

1. **Stage 1 (Yellow Brand)**: Costs **15 mana**, marks a creature you aim at within 32 blocks; first-stage cooldown 5 s. When a yellow-marked target approaches within 24 blocks, the mark upgrades to orange.
2. **Stage 2 (Red Brand)**: Upgrades to red, applies **Fear + Wither sound**, advancing once per second.
3. **Execute (2 s channel, true damage)**: Within the brand window, channel for 2 seconds to deal **20% of target's remaining HP as true damage**, capped at **20 damage**; on hit, chase cooldown extends by **+15 seconds**.
4. **Empty trigger**: With no target under reticle, degrades to a **5-mana** empty trigger.

### Soul Leap (Secondary)

Short-range teleport: costs **15 mana**, **5 second** cooldown. Two client-config modes:

* **Raycast**: Teleport straight along reticle, up to **8 blocks**, can't penetrate blocks.
* **Platform**: Hold the skill key to preview the landing spot (purple particles, only you see them); release to teleport. No landing platform → rejected, **no cooldown/mana** consumed.

> If your teleport lands on a red-branded target and kills them, the teleport cooldown is reset and all damage-reduction stacks and Mana are refilled.

### Bell Raid

Ringing a bell inside a village triggers a Mancianima raid:

* Nearby iron golems **turn hostile against you**.
* A boss bar appears showing remaining villagers; clear all in range to receive rewards.
* Cooldown: **1 MC day**.

Not suitable for players living near villages long-term.

---

## Passive Talents

### Mancianima Mana System

A **dedicated mana bar** independent of other forms:

* Independent mana cap and regen curve.
* Regen pauses for **5 seconds** after damage or active skill use.
* HUD top displays a resistance bar: yellow/red mark tier + channel progress.

### Weaponized Body Tuning

* **Extended unarmed reach.**
* **Weapon attack speed / reach adaptation.**
* **Negative effect immunity** as a heavily stabilized spirit.
* **Custom i-frames** preventing multi-hit one-shots.

### Villager Plunder

* **Cannot** trade with villagers or wandering traders.
* Killing villagers / wandering traders plunders items from their trade entries; **30%** chance to drop 1–3 extra emeralds.
* Nearby villagers and wandering traders flee instinctively, auto-slowed.
* Ranged projectile tracking mixin: ranged attacks track fleeing targets more accurately.

### Mana Drain

* Kills recover mana from fading life force.
* Slowly absorbs residual Cursed Moon energy from the atmosphere.

### Inherited Familiar Fox Abilities

* See through creatures (marked targets auto-glow, only you see).
* Sweet berry bush traversal without damage.
* Light frame, agile movement.

---

## Weaknesses & Limits

| Limit | Notes |
| --- | --- |
| Mana dependency | All actives run on mana; without mana, teleport fails and brand advance slows |
| High-pressure spirit | Mana must be sustained by killing and absorbing |
| Faction brand | Can't trade with villagers/wandering traders |
| Bell cost | Pulls aggro from every nearby iron golem; 1 MC day cooldown |
| Armor limits | No leggings or boots |
| Underwater | Slow in water |
| Food efficiency | Less saturation from food — modified soul shell needs more |

---

## Combat Role

**Single-target hunter / weaponized assassin**

Mancianima's core loop:

1. Lock target with **yellow → red brand**.
2. Begin **2-second channel for true damage** to execute.
3. Use **Soul Leap** to swap targets or chase.

Unlike the team-fight-oriented Spirit Realm Lord, Mancianima is best at **focusing one high-HP target**: the 20-damage true-damage cap pays off heavily against high-HP bosses / players / iron golems.

---

## Inner Monologue

> "The stabilization ritual still echoes in your soul: obey, lock, chase, clear.\
> If the master is absent, the weapon will seek its next order on its own."
