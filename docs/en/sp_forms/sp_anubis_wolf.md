# SP Anubis — Netherjudge

**SP Anubis / Netherjudge**

> "You are no longer just the leader of an undead wolf pack.\
> When soul sand spreads beneath your feet, judgment has a boundary; the souls that step inside pay the price."

SP Anubis is an area-control form centered on **death domain, Netherwolf summons, and Soul Energy empowerment**. It can temporarily reshape the battlefield into a soul-sand domain, weaken enemies, empower Netherwolves, and expand undead pressure.

---

## Form Profile

| Item | Description |
| --- | --- |
| Registry name | `my_addon:form_anubis_wolf_sp` |
| Classification | SP evolution of the Anubis Wolf Stage 3 line |
| Affinity | Undead / Nether |
| Combat role | Area reshaping / Summon control / Wither pressure |

---

## Evolution Requirements

| Condition | Requirement |
| --- | --- |
| Base form | Anubis Wolf Stage 3 permanent form (`shape-shifter-curse:anubis_wolf_3`) |
| Item | Moon Essence Cross |
| Time | Night of the Cursed Moon |

---

## Active Skills

### Death Domain (Primary)

Channel to temporarily reshape the surroundings into an undead home turf:

* Normal channel **2 seconds**, movement speed −**70%** during channel.
* Normal radius **24 blocks**, height range **9 blocks**.
* Domain expands outward at about **5 blocks/second**.
* Normal duration **15 seconds**.
* Cooldown **50 seconds** on success.
* If no convertible blocks exist, casting fails → **10 second** penalty cooldown.
* Block conversion avoids bedrock, functional blocks, redstone, and direction-connected blocks; restores when the domain ends.

Within the domain, non-whitelisted targets standing within ~3 blocks above soul sand or soul soil receive:

* Slowness I refresh.
* Wither I; in empowered domain, upgraded to Wither II.
* Max HP −**15%**.
* Wither strikes capped at about **10** to prevent runaway damage.

Casting at full Soul Energy becomes Empowered Death Domain:

* Consumes all Soul Energy.
* Channel shortened to **1 second**.
* Radius expanded to **32 blocks**.
* Duration **25 seconds**.
* Auto-summons **6 Netherwolves**; with Anubis Crystal, +2 more.
* Also locks Netherwolf Court into **30 second** cooldown.

### Netherwolf Court (Secondary)

Summon Netherwolves to fight for you:

* Channel **1.5 seconds**, movement speed −**50%** during channel.
* Base summon: **2** Netherwolves.
* Inside Death Domain: **4** Netherwolves.
* Netherwolves last **30 seconds**.
* Cooldown **30 seconds** on success.
* At summon cap → casting fails, **5 second** short cooldown.
* Default cap: up to **6** simultaneously; with Anubis Crystal, cap +2.

Base Netherwolf stats: HP **20**, attack **4**. Their hits heal the owner for **2 HP**. Netherwolves summoned in Death Domain gain +**10 HP**, +**2 attack**, and Speed I. The Anubis Crystal raises the cap and per-summon count, but Netherwolf attack −**25%**, HP −**35%**.

---

## Passive Talents

### Soul Energy

Soul Energy ranges **0–100**. Full Soul Energy empowers the next Death Domain, upgrading normal control into a large-area domain and Netherwolf burst window.

### Soul Sight

Creatures within **16 blocks** at or below **20%** HP gain Glowing and periodic soul particle hints.

### Death Sentence

When attacking targets at or below **30%** HP, damage +**20%**.

### Wither Hunt

When a creature with Wither exists within **24 blocks**, gain +**20% movement speed** in areas outside soul sand/soul soil.

### Undead Body

Max HP +**4**, inherits Anubis Wolf-line undead instincts and soul sand-related abilities.

---

## Weaknesses & Limits

| Limit | Notes |
| --- | --- |
| Domain dependency | Main pressure comes from Death Domain; empty / forced out = noticeably weaker |
| Heavy channel | Normal domain channels 2 s at −70% speed; ranged or burst interrupts rhythm |
| Block protection | No convertible blocks → fail + short cooldown |
| Summon cap | Netherwolves can't infinitely stack |
| Armor limits | Quadruped body unsuited to normal leggings/boots |

---

## Combat Role

**Undead Domain Summoner**

SP Anubis's strongest window is the Empowered Death Domain: bigger area, longer duration, Wither II, and auto-summoned Netherwolves push together. In normal combat, Death Domain can also restrict positioning while Netherwolf Court adds damage and lifesteal. Suited for fortress fights, boss fights, and multiplayer where restricting enemy movement matters.

---

## Inner Monologue

> "Judgment never needs a roar.\
> Just spread the soul sand — the order of the dead will finish your sentence for you."
