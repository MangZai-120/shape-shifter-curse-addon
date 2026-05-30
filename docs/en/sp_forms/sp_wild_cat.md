# Wild Cat (SP) — Moonlight Phantom

**Wild Cat SP / Moonlight Phantom**

> "Moonlight has no footsteps.\
> When you vanish from your enemy's sight, the real threat isn't the invisibility itself — it's that they don't know where you'll reappear."

Wild Cat (SP) is an assassin form centered on **True Invisibility, reveal burst, and short-range dash control**. It doesn't fight head-on — it uses invisibility to dodge aggro, slips behind targets, creates brief stuns, then closes the kill with a guaranteed-crit window.

---

## Form Profile

| Item | Description |
| --- | --- |
| Registry name | `my_addon:form_wild_cat_sp` |
| Classification | SP evolution of the Wild Cat / Ocelot line |
| Affinity | Moonlight / Jungle |
| Combat role | Stealth assassin / Reveal burst / Disengage control |

---

## Evolution Requirements

| Condition | Requirement |
| --- | --- |
| Base form | Vanilla Wild Cat SP / Ocelot branch (`shape-shifter-curse:feral_cat_sp`) |
| Item | Evolution Stone |
| Time | No Cursed Moon requirement |

---

## Active Skills

### True Invisibility (Primary)

Pressing the primary key first enters a brief prep, then grants genuine invisibility:

* Prep lasts **1 second**, movement speed reduced **50%** during prep.
* On completion, grants **5 seconds of True Invisibility**.
* Cooldown **12 seconds**.
* Wearing a Cloak of Invisibility extends duration by **2 seconds** but also adds **2 seconds** to cooldown.
* Using items, attacking, hand-swing, or mining breaks invisibility.
* Mob targeting selection takes effect: mobs do not target a Wild Cat (SP) in True Invisibility and lose aggro on tick.

While invisible, pressing the primary key again actively cancels it, granting **5 seconds of guaranteed critical hits** and **Speed II for 5 seconds**.

### Stun Dash (Secondary)

Usable only during True Invisibility:

* Immediately removes True Invisibility.
* Self gains **Slowness III for 1 second** as startup lock.
* Dashes forward at horizontal velocity **1.5**, vertical **0.5**.
* After 1 second, stuns non-whitelisted targets in a **5-block** radius for **1.5 seconds**.
* **12 second** cooldown.

### Ink Sac Blindness

Right-click with an Ink Sac in main hand:

* Consumes **1 ink sac**.
* Blinds non-whitelisted targets in a **4.5-block** radius for **3 seconds**.
* Grants self **1 second of True Invisibility**.
* Cooldown **4 seconds**.

---

## Passive Talents

### Moonlit Instinct

* Night: continuously refreshes Speed I.
* Day: continuously refreshes Slowness I.
* Night attack damage +**1**.

### Carnivore Diet

Eating raw meat doubles both hunger and saturation gain (**+100%**).

### Night Vision & Disengage

Stable Night Vision; True Invisibility blocks mob targeting — perfect for repeated cut-ins and pull-outs.

### Feline Base Abilities

Inherits silent step, fall immunity, wall climbing, and creeper repulsion; keeps the leggings/boots fitting restriction.

### Lifesaving Cat Tail Synergy

With a Lifesaving Cat Tail equipped and no Totem of Undying in hand, triggers one fatal-damage prevention:

* Heals **6 HP**, grants Absorption I for **15 seconds**.
* Grants True Invisibility for **5 seconds**.
* Brief Slowness/Resistance/Weakness for **5 seconds** as protection/cost.
* Item enters **3 minute** cooldown.

---

## Weaknesses & Limits

| Limit | Notes |
| --- | --- |
| Low HP | Max HP reduced by **4** |
| Daytime slow | Constant Slowness I during day |
| Reveal triggers | Item use, hand swing, attack, mining all break invisibility |
| Dash tied to stealth | Stun Dash only fires from True Invisibility |
| Armor limits | Leggings and boots limited |

---

## Combat Role

**Stealth Assassin / Night Insertion**

Recommended loop:

1. Open **True Invisibility** to dodge aggro/sightlines.
2. Approach target, use **Stun Dash** for 1.5 s control.
3. Manually cancel invisibility → **Guaranteed Crit + Speed II**.
4. Retreat via terrain, Ink Sac blindness, or next invisibility window.

Wild Cat (SP) excels at initiative and tempo, not at standing fights.

---

## Inner Monologue

> "You never had to prove you were still there.\
> The last moonlight your enemy sees as they fall — that's the trace you leave behind."
