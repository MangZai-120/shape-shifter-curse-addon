# SP Fallen Allay — Spirit of the Fallen

**Fallen Allay SP / Spirit of the Fallen**

> "If the Allay's song is dragged into the shadow of plunder, it is no longer healing.\
> It summons shrieks, marks prey, and lets Vexes tear the battlefield apart for it."

SP Fallen Allay is the dark-side evolution of the Allay line, aligned with the Pillager faction. It abandons SP Allay's mass healing and instead gains the battlefield pressure of **summoning Vexes, marking targets, and clearing projectiles**.

---

## Form Profile

| Item | Description |
| --- | --- |
| Registry name | `my_addon:form_fallen_allay_sp` |
| Classification | Fallen branch of SP Allay |
| Affinity | Pillager faction |
| Combat role | Vex summoning / Target marking / Battlefield disruption |

---

## Evolution Requirements

| Condition | Requirement |
| --- | --- |
| Base form | Vanilla Allay form (`shape-shifter-curse:allay_sp`) |
| Item | Moon Essence Cross |
| Time | Night of the Cursed Moon |

---

## Active Skills

### Summon Vex (Primary)

Channel to summon Fallen Vexes under your control:

* Channel **1 second**.
* If no Fallen Vex owned by you exists within **128 blocks**, summon **2**.
* Vexes carry an `owner:<uuid>` tag, last **35 seconds**.
* While your Vexes live, the primary cooldown is pinned at ≥ **20 seconds** to prevent re-summoning.
* When the last Vex dies, the primary enters **20 second** cooldown.
* Channel interrupted by damage or purification → **10 second** short cooldown.

Fallen Vexes prioritize glowing-marked targets, then players, hostile mobs, and other targets. They skip the owner, the owner's tamed creatures, their own Vexes, Pillager-faction mobs, and whitelisted protected targets.

### Shadow Shriek (Secondary)

Channel to expose enemies and clear projectiles:

* Channel **1 second**.
* Radius **25 blocks**.
* Applies **Glowing for 8 seconds** to non-whitelisted creatures.
* Clears projectiles in range.
* Cooldown **20 seconds** on success.
* Interrupted → **5 second** short cooldown.

---

## Passive Talents

### Hate Mark

When SP Fallen Allay attacks a target or is attacked by one, the target gains **Glowing for 10 seconds**. Fallen Vexes prioritize attacking these marked targets.

### Shadow Edge

When main-hand weapon mining level > 1, melee attacks gain +**2 damage**.

### Pillager Faction

* Pillagers, witches, and other Pillager-faction mobs do not attack it on sight.
* It cannot normally attack Pillager-faction mobs either.
* Nearby villagers are frightened by its presence.

### Fallen Spirit Body

Inherits Allay's flight, slow-fall, powder-snow walking, no footstep sounds, and high-friction walking — plus fire immunity. It also inherits the **−10 max HP** frail body.

### Carnivore Diet

Diet shifts from amethyst to cooked meat; other foods fit the fallen spirit poorly.

---

## Weaknesses & Limits

| Limit | Notes |
| --- | --- |
| Very low HP | −**10** max HP; focus-fire forces reliance on flight and Vex pull |
| Summon cap | Can't re-summon while your Vexes live; wait for death/expiry |
| Channel interruption | Both skills need 1 s channel; damage/purify → short cooldown |
| Faction limit | Can't normally attack Pillager-faction mobs |
| Armor limits | Leggings and boots unsupported |

---

## Combat Role

**Summon-Disruption Aerial Oppressor**

The core loop is to apply Glowing with Shadow Shriek, then summon Vexes for sustained chase. It excels at disrupting back-line enemies, clearing projectiles, and forcing target movement. Not built for absorbing damage — use flight and Vex pressure to keep distance.

---

## Inner Monologue

> "After the crystal sound shatters, it still echoes.\
> Only this time, what answers you is no longer a companion, but the shrieking wings within the shadow."
