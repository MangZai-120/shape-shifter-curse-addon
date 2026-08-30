# Frostspine — Ice Spine

Frostspine is the Moon Essence Cross SP branch of the snow fox stage III, a sibling of the Evolution Stone SP Snow Fox. Its core loop is **condensing orbiting ice spikes and rapid-firing them**: fan them out for defense, snipe for chip damage, or charge a Frostspine spike for one heavy blow — an offensive-defensive ice turret form.

!!! warning "Irreversible"
    Unlike vanilla SSC SP forms, all SP forms in this addon **cannot** be reverted with inhibitors.

***

## Form Profile

| Item | Description |
| --- | --- |
| Classification | Snow fox stage III · Moon Essence Cross branch |
| Combat role | Ice turret / Anti-melee / Charged heavy shot |
| Note | Sibling branch of [SP Snow Fox (Frost Spirit)](sp_snow_fox.md) (Evolution Stone route) |

***

## Evolution Requirements

| Condition | Requirement |
| --- | --- |
| Base form | Snow fox stage III (final form) |
| Evolution item | Moon Essence Cross |

***

## Active Skills

### Ice Spikes (Primary Skill Key)

* Hold to condense orbiting spikes: one per **24 ticks (1.2s)**, up to **5**, arranged in a **150° fan** on your back (up → left → right → upper-left → upper-right). Condensation pauses while charging Frostspine.
* Tap to fire the oldest spike along the crosshair at **16 blocks/s**: straight within 16 blocks, then drops; max flight 128 blocks; **8 damage** on hit; self-destructs after 5s without a hit.
* Built-in fire cooldown of only **4 ticks (0.2s)** — rapid fire is possible.
* Impact points **freeze the surroundings**: water → ice, lava source → obsidian, flowing lava → cobblestone (1.5-block radius for normal spikes; up to 4 for charged).
* Each spike lasts up to **1200 ticks (60s)**; condensing past 5 replaces the oldest; spikes can be cleansed by the SP Allay's Purification.

### Frostspine (Secondary Skill Key)

* Requires orbiting spikes. Hold to charge (movement reduced to 10%); every **20 ticks (1s)** consumes one spike into the overhead circle (head-top first, then soonest-to-expire); each consumed spike raises the charge level (**cap 5**).
* Release to fire one charged spike: damage = **8 × (1 + consumed)**, up to **48**; flight speed +50% per level (base 16 blocks/s); no drop, no range destroy — flies straight for 200 ticks (10s); freeze radius scales to 4 blocks.
* Releasing without consuming any spike cancels without firing (spikes retained).

***

## Passive Talents

### Frost Thorn Guard

* **Spine armor**: each orbiting spike reduces melee damage taken by 4% (20% at full 5); firing spikes weakens the defense.
* **Thorns**: melee attackers gain 1 stack of frost thorn (Slowness I, 2.5s); 3 stacks trigger a **thorn burst** — attacker frozen 1s + 2 retaliation damage, stacks reset (2.5s idle also resets).

### Ice Strider

+15% movement speed on ice, snow, and powder snow.

### Frost Metabolism

In cold biomes (temperature < 0.2) or on powder snow: restore 1 HP per 60 ticks (3s); paused while skills are on cooldown.

### Inherited Snow Fox Passives

Triple leap, snow-walking (no sinking in powder snow), cold resistance, fox affinity, bottled-snowfall tools, etc.

***

## Exclusive Trinket: Frost Spine Collar

* Ice Spike hits instantly refund **1 free** orbiting spike (follows condensation rules: empty slot first, replace oldest at full).
* Cost: normal spike (primary tap-fire) damage reduced to **80%** (8 → 6.4); condensation interval ×1.75 (1.2s → 2.1s). Charged Frostspine spikes are unaffected.
* Found in igloo loot chests at 15%, dungeon chests at 10%.

***

## Weaknesses

| Limitation | Description |
| --- | --- |
| Fire vulnerability | Burns longer and takes amplified fire damage — extinguish with water bottles |
| Armor restriction | No leggings or boots |
| Sluggish | Slower mining and water movement |
| Biome sensitivity | Warm biomes (desert/plains) reduce max health |
