# SP Axolotl — Abyssal Overlord

**Axolotl SP / Abyssal Overlord**

> "The deep is not quiet water.\
> It drags enemies to your side, then tells them with one detonation what underwater royalty really means."

SP Axolotl is a water-themed form built around **moisture, survival reduction, and pull-and-burst combat**. It can vortex enemies into melee range, suspend mob aggro with Playing Dead, and carries crowd control plus emergency-buffer duties in multiplayer.

---

## Form Profile

| Item | Description |
| --- | --- |
| Registry name | `my_addon:form_axolotl_sp` |
| Classification | SP evolution of the Axolotl Stage 3 line |
| Affinity | Water / Abyss |
| Combat role | Aquatic control / Melee burst / High survival |

---

## Evolution Requirements

| Condition | Requirement |
| --- | --- |
| Base form | Axolotl Stage 3 permanent form (`shape-shifter-curse:axolotl_3`) |
| Item | Moon Essence Cross |
| Time | Night of the Cursed Moon |

---

## Active Skills

### Vortex Burst (Primary)

Create a vortex centered on yourself — pull first, then detonate:

* Cooldown **20 seconds**, costs **20% moisture**.
* Vortex lasts **3 seconds**, pulls non-whitelisted targets within **6 blocks**.
* Self takes **50% reduced damage**, moves at **50%** speed during it.
* On end, detonates in a **3-block** radius for **12 damage**.
* Knockback parameters: vertical **0.6**, horizontal impact **0.8**.
* Applies **Slowness III for 4 seconds** on hit.

Uses default whitelist behavior — whitelisted targets not pulled, damaged, or controlled.

### Playing Dead (Secondary)

Manually enter a 6-second Playing Dead state:

* Cooldown **36 seconds**, duration **6 seconds**.
* **90% damage reduction**.
* Forbids jumping, attacking, mining, item use.
* Self gains Regeneration III, Blindness, and extreme Slowness.
* Clears aggro from mobs targeting you within **64 blocks**.
* With Active Coral Necklace, Regeneration drops to I but you gain Absorption V for **25 seconds**.

### Water Spear Crafting

Leave your main hand empty, hold a normal arrow in your off hand, and right-click while carrying no [Water Spear](../items/tools.md#water-spear). This uses one arrow. In 8.0.0, the crafting cooldown is **3.5 seconds** after the spear is gone. In 8.0.1 development builds, block and entity interactions take priority, and crafting also costs 15 moisture.

### Enhanced Water Burst

Triggered when switching from sprint to sneak on the ground:

* Explosion power **3**, costs **5% moisture**.
* Uses default whitelist behavior to avoid hitting protected targets.

---

## Passive Talents

### Rain Hydration

In rain, restores **60 air every 20 ticks**.

### Fast Metabolism

Restore an extra **0.75 HP every 40 ticks (2 seconds)**, independently of normal natural regeneration.

### Moisture Loop

Vortex and Water Burst consume moisture. Restore it on land with rain, the Axolotl Humidifier, or nearby water.

### Base Axolotl Abilities

Inherits underwater breathing, slippery/swimming stance, aquatic mobility, leaping out of water, the oxygen-life link, and food restrictions. More complete underwater; on land requires constant moisture/pacing management.

---

## Weaknesses & Limits

| Limit | Notes |
| --- | --- |
| Moisture dependency | Long land fights need hydration items |
| Vortex positioning | Centered on you — full control needs melee |
| Locked-out actions | Playing Dead disables attack/mine/jump/item use — emergency only |
| Burst trigger conditions | Water Burst needs sprint→sneak on ground |
| Equipment limits | Helmets and some normal gear don't fit |

---

## Combat Role

**Abyssal Control Tank**

Vortex enemies to you, convert with detonation damage + Slowness III for teammate damage windows. Playing Dead is a strong panic button. Water Spear / Burst cover mid-short range. Worst case is running dry — prepare hydration tools.

---

## Inner Monologue

> "Water remembers every struggle.\
> When an enemy is dragged into the vortex's heart, what you hear isn't waves — it's the deep closing its jaws."
