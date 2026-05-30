# SP Familiar Fox — Spirit Realm Lord

**Familiar Fox SP / Spirit Realm Lord**

> "When the Moon Essence Cross pushes the Familiar Fox's soul deeper, the pale-blue foxfire is no longer just spellcraft.\
> It orbits you, protects you — and when your mana runs dry, it turns to gnaw at your body in return."

SP Familiar Fox is the advanced spirit form of the Familiar Fox (Red Fox) line. Its core loop revolves around **mana, the Blue Fire Ring, and kill-restore**. It can keep burning crowds in melee range, fill mid-range damage with Fox Fire Breath, but every bit of strength depends on the mana loop never collapsing.

---

## Form Profile

| Item | Description |
| --- | --- |
| Registry name | `my_addon:form_familiar_fox_sp` |
| Classification | SP evolution of the Familiar Fox (Red Fox) line |
| Affinity | Familiar Fox / Mana Loop |
| Combat role | Mana Mage / Sustained AOE / Kill-based Sustain |

---

## Evolution Requirements

| Condition | Requirement |
| --- | --- |
| Base form | Familiar Fox (Red Fox) Stage 3 permanent form (`shape-shifter-curse:familiar_fox_3`) |
| Item | Moon Essence Cross |
| Time | Night of the Cursed Moon |
| Note | Using a Moon Essence Cross again while already in SP form triggers the backlash branch |

---

## Active Skills

### Blue Fire Ring (Primary)

Open an orbiting ring of pale-blue foxfire when mana is near full:

* Requires **99 mana** to activate; enters cooldown for **20 seconds** if turned off, mana-drained, or purified.
* The ring affects targets within **6 blocks** of you.
* Every **4 ticks** a sustain check runs: if mana ≥ 16, it costs **1.7 mana**; otherwise the ring shuts down and enters cooldown.
* Damage settles roughly every **16 ticks**: **4 damage** to non-whitelisted targets plus **3 seconds of Fox Fire Burn**.
* While the ring is active, Soul Reaping restores an extra **5 mana** and heals you for **3 HP** on kill.

While wearing a Blue Fire Amulet, the ring switches to the amulet-empowered version: radius shrinks to **3.6 blocks**, sustain cost rises to **2.8 mana per 4 ticks**, damage settles roughly every **20 ticks** for **6 damage**, and cooldown after shutdown extends to **28 seconds**.

This skill uses the default whitelist behavior: whitelisted targets take no ring damage and no foxfire effect.

### Fox Fire Breath (Secondary)

Spew soul foxfire forward, a short-cooldown mid-range finisher:

* Costs **20 mana**.
* Range **8 blocks**, **6 damage** on hit.
* Pauses natural mana regen for **6 seconds**.
* **1 second** cooldown, shown on the SP secondary cooldown bar.

---

## Passive Talents

### Mana System

SP Familiar Fox has a 0–100 mana loop. Mana drives the ring, the breath, your damage reduction, and your low-mana penalties:

* Mana is initialized to full when the form is granted.
* Regenerates **1 mana every 10 ticks** by default; certain skills pause regen.
* Kills trigger **Soul Reaping**, restoring **10 mana** baseline.
* While the ring is active, kills restore an extra **5 mana** and heal **3 HP**.

### Psionic Veil

While mana ≥ **10**, you gain **35% damage reduction**. When mana is above roughly **80%**, an additional **20% reduction** stacks on top.

### Spirit Incarnation

As an advanced spirit, SP Familiar Fox is immune to a wide range of vanilla statuses and several SSCA custom effects — Poison, Hunger, Speed, Strength, Regeneration, Resistance, Weakness, Slowness, Blindness, Fox Fire Burn, Frost Freeze, Frost Fall, Sand Blindness, and more. The trade-off is reduced conventional instant healing, and this spirit framework leans heavily on mana — once mana drops too low, penalties bite hard.

### Mana Imbalance

When mana is below **10%** but above **3%**:

* Attack damage −**20%**, movement speed −**20%**, mining speed −**20%**.

When mana drops below **3%**:

* Attack damage −**60%**, movement speed −**50%**, mining speed −**50%**, incoming damage +**20%**, plus extra exhaustion drain.

---

## Weaknesses & Limits

| Limit | Notes |
| --- | --- |
| Mana dependency | Ring, breath, reduction, and low-mana penalty all hinge on mana — going empty kills your rhythm |
| Regen windows | Fox Fire Breath etc. pause regen; spamming pushes you into imbalance |
| Melee exposure | The ring needs you next to enemy clusters; high-burst targets demand careful entry |
| Armor limits | Inherits Familiar Fox size limits — no leggings or boots |
| Underwater | Familiar Fox forms aren't comfortable in water |

---

## Combat Role

**Sustained AOE Mana Mage**

The ideal loop:

1. Open the **Blue Fire Ring** at full mana.
2. Sustain pressure in the 6-block bubble around enemy groups.
3. Trigger **Soul Reaping** through kills to top up your ring costs.
4. Use **Fox Fire Breath** to finish or interrupt mid-range targets.

The ring is not a passive thing to leave running. When it doesn't convert into kills, mana slides; once below 10%, damage, mobility, and survival collapse together.

---

## Inner Monologue

> "The pale-blue flame circles you like a gentle shackle.\
> As long as a soul still burns, you remain the Spirit Realm Lord; if the flame dies, you will hear your own soul devour itself."
