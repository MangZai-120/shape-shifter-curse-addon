# SP Allay — Crystal Sound

**Allay SP / Crystal Sound**

> "An Allay's song doesn't only soothe wounds.\
> When the crystal resonance is pushed to its limit, it can purify a battlefield, hold a dying ally upright, and turn a lone fight into a brief blessing."

SP Allay is a support form centered on **mass purification, mass healing, and ranged buffs**. Its damage doesn't come from melee — it powers a team through energy bars and channeled skills, providing recovery, dispels, panic-save, and ranged damage amplification.

---

## Form Profile

| Item | Description |
| --- | --- |
| Registry name | `my_addon:form_allay_sp` |
| Classification | SP evolution of the Allay form |
| Affinity | Crystal / Allay |
| Combat role | Aerial support / Mass heal / Purify & dispel |

---

## Evolution Requirements

| Condition | Requirement |
| --- | --- |
| Base form | Vanilla Allay form (`shape-shifter-curse:allay_sp`) |
| Item | Evolution Stone |
| Time | No Cursed Moon requirement |

---

## Active Skills

### Mass Purification (Primary)

Channel to clear effects in range and interrupt other SP skills:

* Channel **1.5 seconds**.
* Radius **20 blocks**.
* Cooldown **30 seconds** on success.
* Clears potion effects on self and targets, applies a short Purification mark.
* Can interrupt SP Axolotl's Vortex, Red Familiar Fox ring, Allay purification channel, and other active states.
* Damaged during channel → enters **5 second** short cooldown.

### Mass Heal (Secondary)

Spend full energy for a large-area heal:

* Requires energy = **100**.
* Channel **3 seconds**.
* On success, consumes all energy, cooldown **30 seconds**.
* Heals targets passing the buff whitelist within **20 blocks** for **20 HP**.
* Recipients gain Resistance I for **10 seconds**.
* Self additionally gains Absorption II for **20 seconds** and Resistance I for **10 seconds**.

If no other healable players are in range, the **Lone Blessing** triggers:

* Self fully healed.
* Strength II for **20 s**, Resistance II for **20 s**, Absorption III for **30 s**.
* +**30%** ranged damage for **20 seconds**.

Damaged during channel → heal is interrupted, self gains Resistance I + Speed I for 10 s, enters **5 second** short cooldown.

---

## Passive Talents

### Crystal Energy

SP Allay has a 0–100 energy bar:

* Regen **1 energy per 4 ticks**.
* Mass Heal requires full energy and consumes it all in one shot.
* When hunger is 0 and energy ≥ 50, consumes **50 energy per second** to restore **1 hunger point**, pausing energy regen for **3.5 seconds**.

### Form Tools

While in SP Allay form, hotbar slot 1 auto-receives the Healing Spell and slot 2 auto-receives the Allay Jukebox. Leaving the form removes these form-exclusive items. If slots were occupied, those items try to return to inventory; if full, they drop on the ground.

### Ranged Resonance

Ranged damage gains a flat +**6**. During Lone Blessing, gains an extra +**30%** ranged damage.

### Crystal Regeneration

When dealing ≥ **4 damage** in a single hit, heal self for **2 HP**; trigger interval **0.5 seconds**.

### Totem Guard

SP Allay can activate a Totem of Undying for fatal-damage protection on targets within **20 blocks** that pass the buff whitelist:

* Consumes 1 activated Totem of Undying.
* Sets target HP to **1**.
* Clears target effects.
* Grants Absorption II for **5 s**, Regeneration II for **45 s**, Fire Resistance for **40 s**.
* Self-rescue is always allowed; rescuing others uses the buff whitelist.

### Aerial Support Body

Inherits Allay's flight, slow-fall, powder-snow walking, no footstep sounds — but max HP is reduced by **10**, leggings and boots don't fit either.

---

## Weaknesses & Limits

| Limit | Notes |
| --- | --- |
| Very low HP | −**10** max HP, focus-fire forces reliance on flight and heal windows |
| Channel interruption | Both Purify and Heal need channels; damage flips them to short cooldown |
| Heal needs full energy | Mass Heal requires full energy, can't be cast on demand |
| Diet restriction | Main food = amethyst shards; normal food fits poorly |
| Armor limits | Leggings and boots unsupported |

---

## Combat Role

**Aerial Support / Team Lifeline**

SP Allay belongs in the back line or aerial perspective. The primary purifies/interrupts key skills, the secondary mass-heals, Totem Guard buys allies one extra mistake. Solo, Mass Heal converts to Lone Blessing — short-term high self-sustain and ranged output.

---

## Inner Monologue

> "Crystal echoes vibrate in your chest.\
> You know you are not hard, but as long as the song continues, the fallen still have one more chance to rise."
