# Red — A Mere Legend

> "Red is not regression, but another answer.\
> When the Spirit Realm Lord's flame breaks free of its given vow, it grows sharper, shorter — more like a freedom about to slip out of control."

Red is a limited-time rare branch SP Familiar Fox can trigger during the Cursed Moon. It inherits the mana, ring, and reaping loop of SP Familiar Fox, but with a thinner frame, higher-damage Fox Fire Breath and amulet ring — a higher-risk version focused on pressuring enemies in short windows.

---

## Form Profile

| Item | Description |
| --- | --- |
| Registry name | `my_addon:form_familiar_fox_red` |
| Classification | Parallel branch of SP Familiar Fox |
| Affinity | Familiar Fox / Limited-time Red branch |
| Combat role | High-risk Sustained AOE / Empowered Fox Fire Breath |

---

## Evolution Requirements

Red has two acquisition paths: a **limited-time random branch** and the **Power of the Moon Scar story path (permanent)**.

### Path 1: Limited-time Random Branch

| Condition | Requirement |
| --- | --- |
| Base form | SP Familiar Fox (`my_addon:form_familiar_fox_sp`) |
| Trigger | During the Cursed Moon |
| Branch check | First check per Cursed Moon has a **5%** chance to convert to Red |
| Duration | Receives the `ssc_addon_red_expire` tag, lasts **12000 ticks (10 minutes)**, then reverts to SP Familiar Fox |
| Note | The Moon Essence Cross upgrade code also has a Red branch check, but still proceeds to standard SP conversion afterwards; this path documents the stable Cursed-Moon SP conversion logic |

### Path 2: Power of the Moon Scar Story Path (Permanent)

| Condition | Requirement |
| --- | --- |
| Prerequisite form | SP Familiar Fox (`my_addon:form_familiar_fox_sp`) |
| Prerequisite advancement | Unlock the hidden advancement "**Power of the Moon Scar**" — collect all books related to the Lord of the Spirit Realm: Moon Scar: Lord of the Spirit Realm Chapters 1–8 + Epilogue (9 books) plus the Familiar Research Record FF-E-013 |
| Guidance hint | Once the advancement is unlocked while in SP Familiar Fox form, you receive a one-time whisper hint guiding you to sleep |
| Trigger | **Sleep during a Cursed Moon night**: the SP Familiar Fox truly falls asleep (using the feral sleeping animation) and transforms into Red upon waking |
| Duration | **Permanent** — it does not expire and revert like the limited-time branch |
| Reversion | While in the story Red state, use the **Moon Essence Cross** at any time to freely revert to SP Familiar Fox (without consuming the item) |
| Note | This path does not apply the `ssc_addon_red_expire` tag, so it is never reverted by the limited-time branch's expiry logic; the two paths are independent |

---

## Active Skills

### Blue Fire Ring (Primary)

Red's basic ring is close to SP Familiar Fox's, but the foxfire duration differs:

* Requires **99 mana** to activate, **20 second** cooldown after shutdown/drain/purification.
* Affects targets within **6 blocks**.
* Sustain check every **4 ticks** consumes **1.7 mana** (requires mana ≥ 16).
* Damage settles roughly every **16 ticks**: **4 damage** to non-whitelisted targets plus **4 seconds of Fox Fire Burn**.
* Kills during the ring restore an extra **5 mana** and heal **3 HP**.

With a Blue Fire Amulet, Red switches to its empowered amulet ring: requires **119 mana** to activate, sustain cost **2.8 mana per 4 ticks**, radius **3.6 blocks**, damage settles roughly every **16 ticks** for **7 damage** plus **3 seconds of Fox Fire Burn**, **28 second** cooldown.

### Crimson Fox Fire Breath (Secondary)

Red's breath is far more vicious:

* Costs **20 mana**.
* Range **8 blocks**.
* **7 damage** on hit.
* Applies **6 seconds of Fox Fire Burn**.
* **1 second** cooldown.

---

## Passive Talents

### Soul Reaping

* Kills restore **10 mana** baseline.
* While the Blue Fire Ring is active, kills restore an extra **5 mana** and heal **3 HP**.

### Psionic Veil

Red inherits the SP Familiar Fox mana-based reduction: **35% damage reduction** at mana ≥ 10, with an additional **20%** at high mana.

### Spirit Incarnation

Red inherits the advanced-spirit status immunities and base Familiar Fox abilities — but this doesn't fully offset its thinner HP pool.

### Potion Bag

While in Red form, hotbar slot 9 auto-receives a Potion Bag. Leaving Red form removes the bag and drops its contents.

### Frail Constitution

Red gains an additional **−4 max HP** adjustment. Higher Breath bursts, smaller margin for error.

---

## Weaknesses & Limits

| Limit | Notes |
| --- | --- |
| Lower HP | Max HP further reduced by **4** |
| Mana dependency | Same imbalance penalties as SP Familiar Fox |
| Engagement risk | Ring needs melee; HP penalty amplifies mistakes |
| Armor limits | No leggings or boots |
| Underwater | Bad for long underwater fights |

---

## Combat Role

| Dimension | SP Familiar Fox | Red |
| --- | --- | --- |
| Acquisition | Moon Essence Cross | 5% Cursed Moon conversion |
| Basic ring | 6 blocks, ~4 dmg / 16 t, 3 s Fox Fire | 6 blocks, ~4 dmg / 16 t, 4 s Fox Fire |
| Amulet ring | 3.6 blocks, ~6 dmg / 20 t | 3.6 blocks, ~7 dmg / 16 t |
| Breath | 8 blocks, 6 dmg | 8 blocks, 7 dmg, 6 s Fox Fire |
| Survival | No extra HP penalty | Max HP −4 |

Red emphasizes using empowered Breath to push HP down, then rolling Blue Fire Ring + Soul Reaping for sustain. Don't drag it into low-mana states, don't eat frontal bursts.

---

## Inner Monologue

> "Vow Three: protect your allies."
