# Golden Sandstorm

**Golden Sandstorm**

> "Golden sand is not warm.\
> It clings to wounds and becomes a brand; when you detonate it, enemies finally realize the storm remembered them long ago."

Golden Sandstorm is the other evolution path of the Anubis Wolf Stage 3 line. Instead of relying on a Death Domain to set up the field, it snowballs in melee through **Erosion Brand, Wither Sand, percent-based burst, and combat sustain**.

---

## Form Profile

| Item | Description |
| --- | --- |
| Registry name | `my_addon:form_golden_sandstorm_sp` |
| Classification | Sandstorm branch of Anubis Wolf Stage 3 |
| Affinity | Undead / Desert |
| Combat role | Brand-burst melee / Percent damage / Wither sustain |

---

## Evolution Requirements

| Condition | Requirement |
| --- | --- |
| Base form | Anubis Wolf Stage 3 permanent form (`shape-shifter-curse:anubis_wolf_3`) |
| Item | Evolution Stone |
| Time | No Cursed Moon requirement |
| Note | Parallel to SP Anubis (Netherjudge), different evolution path |

---

## Active Skills

### Wither Sand (Primary)

Channel to release a golden sandstorm, applying the first stack of Erosion Brand:

* Channel **1 second**.
* Movement speed −**50%** during channel.
* Radius **15 blocks**.
* Applies **Sand Blindness for 3 seconds** to non-whitelisted targets.
* Adds **1 stack of Erosion Brand** on hit.
* Cooldown **26 seconds** on success.
* Interrupted by damage → **7 second** short cooldown.

### Detonate Marks (Secondary)

Detonate all Erosion-Branded targets not in green cooldown state:

* No valid targets → does not enter cooldown.
* Success cooldown **10 seconds**.
* Each target takes **20% of current HP** as damage, capped at **20**, minimum **1**.
* Wearing a Withered Sand Ring raises the per-hit cap to **26**.
* 3-stack targets first trigger the passive burst, then the detonation settles.
* Self restores **20% of missing HP**.
* Wearing an Erosion Sand Prism: main target takes **60%** damage; remaining **40%** splashes to surrounding targets.

---

## Passive Talents

### Erosion Brand

Melee attacks auto-stack Erosion Brand on the target:

* Maximum **3 stacks**.
* Lasts **10 seconds**, restacking refreshes duration.
* By default, the same target can only stack 1 layer per **1 second**.
* With Erosion Sand Prism, stack interval becomes **1.3 seconds**.
* At 3 stacks, the next attack triggers passive burst: **20% of target's current HP**, capped at **20**; Withered Sand Ring raises cap to **26**.
* Passive burst heals self for **10% of max HP**.
* After burst, target enters green state for **10 seconds**; the first **5 seconds** block restacking.

Erosion Sand Prism strengthens burst splash: bursts or detonations splash **40%** damage to non-whitelisted targets within **5 blocks**, and add 1 extra brand stack within **4 blocks**.

### Brand Awareness

Branded targets display colors based on state:

| Color | Meaning |
| --- | --- |
| Yellow | 1 stack |
| Orange | 2 stacks |
| Red | 3 stacks, can trigger burst |
| Green | Post-burst stack cooldown |

### Backlash Strike

When attacked, auto-triggers a counter:

* Radius **4 blocks**.
* Horizontal knockback **0.5**, vertical **0.15**.
* Applies **Wither I for 5 seconds** to non-whitelisted targets.
* Cooldown **15 seconds**; the cooldown still triggers even on no hit.

### Wither Adaptation

Immune to Wither, Blindness, Regeneration status effects, and Sand Blindness. "Regeneration" here means the vanilla Regeneration status; Golden Sandstorm can still recover HP via its dedicated mechanism.

### Special Recovery

Natural hunger-based regen is disabled, replaced by dedicated recovery:

| Source | Heal Amount |
| --- | --- |
| Self-applied Wither damage | **1 HP** per tick of Wither damage |
| Player direct kill | **4 HP** |
| Netherwolf kill | **3 HP** |
| Out-of-combat meditation | **1 HP** per **10 seconds** |
| In-combat meditation | **1 HP** per **6 seconds** |

---

## Weaknesses & Limits

| Limit | Notes |
| --- | --- |
| Stacking required | Percent burst depends on Erosion Brand; frequent target disengage cuts output |
| Channel interrupt | Wither Sand needs 1 s channel; damage → short cooldown |
| No natural regen | Cannot rely on hunger-based regen; must use Wither / kill / meditation recovery |
| Whitelist boundary | Melee stacking itself doesn't check whitelist, but splash/detonate/backlash AOE does protect whitelisted targets |
| Armor limits | Quadruped body unsuited to normal leggings/boots |

---

## Combat Role

**Brand-Burst Melee**

Standard loop:

1. **Wither Sand** to apply Sand Blindness + 1 brand stack on a group.
2. Melee continuously stack to 3, triggering **20% current HP** passive burst.
3. **Detonate Marks** to settle all non-green branded targets and restore missing HP.
4. Under siege, use **Backlash Strike** and Wither-based regen to stabilize HP.

Suited for mid-close range sustained pressure, not for empty swings or chasing scattered targets.

---

## Inner Monologue

> "Sand grains burrow into armor seams — and into the cracks of a soul.\
> You don't rush to kill prey, because the storm has already written their ending on them."
