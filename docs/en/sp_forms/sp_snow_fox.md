# SP Snow Fox — Frost Spirit

**Snow Fox SP / Frost Spirit**

> "Frost is not mere slow.\
> It decides whether enemies can reach you, whether they can flee from you, and whether they live to see the storm end."

SP Snow Fox is a dual-mode frost form. Melee mode lets you teleport-strike for execute kills; ranged mode uses ice orbs and ice storms to control large areas. Every skill runs on **Frost**, and mode-switching itself is part of the resource management.

---

## Form Profile

| Item | Description |
| --- | --- |
| Registry name | `my_addon:form_snow_fox_sp` |
| Classification | SP evolution of the Snow Fox Stage 3 line |
| Affinity | Frost / Snowfield |
| Combat role | Dual-mode control / Melee insertion / Ranged storm |

---

## Evolution Requirements

| Condition | Requirement |
| --- | --- |
| Base form | Snow Fox Stage 3 permanent form (`shape-shifter-curse:snow_fox_3`) |
| Item | Evolution Stone |
| Time | No Cursed Moon requirement |

---

## Active Skills

### Mode Switch

Sneak and press the primary key to switch between melee and ranged modes:

* Switch cooldown **5 seconds**.
* If the Frost gain cooldown is 0, switching grants an extra **60 Frost** and starts a **15 second** gain cooldown.
* Melee mode: movement speed +**10%**.
* Ranged mode: movement speed −**10%**.

### Melee — Teleport Strike (Primary)

Lock nearby targets and chain-teleport strike them:

* Search radius **10 blocks**.
* Hits up to **3 targets**.
* Base damage **6**.
* +**3** bonus damage against targets with Frost Freeze.
* On success: costs **30 Frost**, cooldown **20 seconds**.
* No valid target: fails, costs **20 Frost**, cooldown **5 seconds**.
* Self damage reduction **65%** during the skill.

### Melee — Snow Pierce Dash (Secondary)

Dash forward and pierce targets:

* Dash distance **8 blocks**.
* Dash speed **1.5 blocks/tick**.
* **8 damage** on hit.
* Applies **3 seconds of Frost Freeze**.
* Costs **15 Frost**, cooldown **6 seconds**.

### Ranged — Ice Storm (Primary)

Charge, then summon a sustained storm at range:

* Charge time **1.5 seconds**.
* Max range **30 blocks**.
* Costs **30 Frost**, cooldown **30 seconds**.
* Storm lasts **10 seconds**.
* Damage radius **3.5 blocks**.
* **2 damage every 0.5 seconds**.
* Strong pull within **6 blocks**, weak pull within **10 blocks**, pull speed **0.1/tick**.

### Ranged — Ice Orb (Secondary)

Fire an ice orb applying Frost Fall:

* Costs **15 Frost**, cooldown **5 seconds**.
* Travel speed **0.75 blocks/tick**.
* Max distance **50 blocks**, max lifetime **5 seconds**.
* Applies **4 seconds of Frost Fall** on hit.
* With Frost Amulet, the orb splits into up to **2** tracking shards, search radius **5 blocks**.

---

## Passive Talents

### Frost

Frost ranges **0–100**:

* Normal environment: regen **3 per 20 ticks**.
* Cold environment or certain conditions: **5 per 20 ticks**.
* Mode switching can grant a **60-point burst** refill, with a **15 second** gain cooldown.

### Cold Adaptation & Heat Weakness

Cold environments strengthen SP Snow Fox: max HP +**4**, movement speed +**15%**. Hot biomes and the Nether weaken it: max HP −**4**, movement speed −**15%**. The Inverted Thermometer flips this cold/hot detection.

### Frost Shield

When Frost ≥ **20** and you take more than **0.5 damage**, consume **20 Frost** to reduce that hit by **50%**.

### Frost Freeze vs Frost Fall

* **Frost Freeze**: heavy control + vulnerability window; melee skills mainly build burst around it.
* **Frost Fall**: persistent slow from ranged orbs, used to drag targets and set up storm zones.

### Frail Body

Max HP reduced by **8**. Strong control and mobility, but not a frontline tank.

### Snow Fox Base Abilities

Inherits triple jump, fall-related mobility, powder snow walking, ice non-slip, snowball/sweet berry adaptations — making it more suited to snowfields and complex terrain than standing-cast play.

---

## Weaknesses & Limits

| Limit | Notes |
| --- | --- |
| Very low HP | −**8** max HP, melee focus-fire is dangerous |
| Frost dependency | Both skill rows burn Frost; empty = no output/control |
| Mode restriction | Melee and ranged skills are separate, wrong mode = missed window |
| Storm charge | 1.5 s charge under pressure is hard to complete |
| Environmental penalty | Hot biomes / Nether trigger heat weakness unless using Inverted Thermometer |

---

## Combat Role

**Frost Control / Dual-Mode Burst**

Melee mode for fast insertion: Snow Pierce Dash applies Frost Freeze, Teleport Strike reaps multiple targets. Ranged mode for holding ground: Ice Orb applies Frost Fall, Ice Storm creates a 10-second sustained damage + pull zone. The ceiling is high, but HP and resources are tight — constantly judge when to switch.

---

## Inner Monologue

> "You are not the blizzard itself.\
> You are the first crack of ice the prey hears before the storm arrives."
