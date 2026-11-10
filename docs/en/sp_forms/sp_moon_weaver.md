# Moon Weaver

Moon Weaver is the Moon Essence Cross SP branch of the spider stage III, layering **web control, silk swing and double-jump mobility** on top of vanilla web hunting. The rhythm: control the field with webs first, then harvest with mobility.

!!! warning "Irreversible"
    Unlike vanilla SSC SP forms, all SP forms in this addon **cannot** be reverted with inhibitors.

***

## Form Profile

| Item | Description |
| --- | --- |
| Classification | Spider stage III · Moon Essence Cross branch |
| Combat role | Territory control / High-speed displacement / Trap-and-harvest |
| Note | Sibling branch of [Salticidae](sp_salticidae.md) (Evolution Stone route) |

***

## Evolution Requirements

| Condition | Requirement |
| --- | --- |
| Base form | Spider stage III (final form) |
| Evolution item | Moon Essence Cross |

***

[Watch the bilingual Moon Weaver gameplay tutorial](../../tutorials/moon-weaver/index.html?lang=en_us)

## Addon Abilities and Changes

### 1. Weaving Modes and Gestures

Sneak and quickly double-tap the primary key to switch Bridge and Attack modes.

The mode survives form changes. Hold and release normally to fire the current mode’s projectile. In Bridge mode, sneak-hold or double-tap-and-hold without sneaking to build a flat bridge.

A hold is recognized after about 0.25 seconds; the double-tap window is about 0.3 seconds. Starting requires six web mana, but this is a threshold rather than an extra upfront payment.

### 2. Three Attack Tiers

Select Attack mode, hold the primary key, then release.

Tier cues occur at twenty, forty and sixty ticks. Full charge can be held without further cost. Each charging tick costs 0.25 mana, totaling five, ten and fifteen at those thresholds. Release cooldowns are one, two and three seconds. Patch core radii are three, five and six blocks, with sparse outer splashes.

Within eight blocks of impact, cocoon probability depends on distance, tier and existing Web Bound; unsuccessful rolls apply five seconds of Web Bound. Direct hits also add two hundred, four hundred or six hundred entanglement ticks; five hundred produces a full cocoon, so tier three directly guarantees it. Whitelisted targets are protected. Early release still uses tier one and does not necessarily cost exactly five mana.

### 3. Flat Bridges and Bridge Bolts

In Bridge mode, sneak-hold the primary key and release; a normal hold fires a bridge bolt.

The three flat-bridge tiers build ten, fourteen or eighteen blocks. A bridge bolt can build a silk ladder on impact. Addon bridge and ladder placement can replace existing membranes so an attack patch does not obstruct construction.

Charging costs and tier cooldowns match Attack mode. Footage shows bridge lengths over a real gap and a separate wall ladder. Bridge blocks and thin membranes are different blocks; silk bridges alone do not activate Web Affinity.

### 4. Swinging and Reeling

Aim at a block and press the secondary key. Jump reels in, airborne sneak reels out, and movement keys steer.

Reach is thirty-two blocks. The projectile costs one mana per two blocks flown; extending the rope also costs one per two blocks. Reeling in and merely staying attached add no ongoing fee. Press again to detach; ending or missing starts a five-second cooldown.

At least one mana is needed to fire; insufficient mana starts no cooldown. Excess distance or solid obstruction breaks silk. Jump reels rather than double-jumps while attached. The video preserves actual motion and rope-length changes.

### 5. Tethering Friends and Enemies

Hit a living target with the the secondary key projectile.

Impact costs eight additional web mana, clamped at zero if insufficient. The rope is fixed at sixteen blocks and cannot extend. Soft pulling begins around twelve blocks and strengthens beyond sixteen; nearby movement remains free. Friendly green and hostile blue outlines are visible only to the caster.

Whitelisted allies can also be tethered. Against an unprotected non-player target you tether, deal fifteen percent more damage and take forty percent less damage from it. This extra modifier excludes players and protected targets. Press secondary again to detach.

### 6. Web Affinity

Stand in or touch vanilla cobwebs or addon membranes.

Gain twenty percent movement speed, one extra web mana per second, and twenty percent damage reduction.

Compare ordinary ground, vanilla cobwebs and your membrane. Extra mana stacks with inherited regeneration and pauses during universal spell charging. Inherited conditional modifiers also affect final speed, so cobweb and membrane totals can differ. Temporary silk bridges do not activate this affinity.

### 7. Moon Poison

Approach enemies already Web Bound or fully cocooned.

Every three seconds, inspect trapped targets within roughly eight blocks and apply four seconds of Poison I, refreshed while trapped. Whitelisted targets are protected.

Compare ordinary, Web Bound, cocooned and distant bound targets. This reads their current status and does not require you to have caused the control. The search uses an expanded block box.

### 8. Cocoon Hunter

Melee a fully cocooned target.

Base melee damage rises fifteen percent. An unprotected non-player cocooned target takes another twenty-five percent, multiplying to 1.4375 for melee.

The same wooden sword hits ordinary, cocooned and protected cocooned controls. Protected targets exclude the extra NPC twenty-five percent. Restoring mana when hitting cocooned prey is inherited upstream behavior.

### 9. Silk Double Jump

Release and press jump again after leaving the ground.

Gain one extra jump per airtime; landing restores it. Sprinting also adds forward momentum.

Three presses show the ground jump, double jump and no third boost. While swinging, jump reels in instead of double-jumping.

### 10. Membrane Contact and Immunity

Let living targets touch a membrane.

An affected target has horizontal motion scaled to 0.7, receives five seconds of Web Bound and destroys the touched membrane block. Web Bound additionally slows movement with Mining Fatigue and Weakness; milk cannot clear it.

Whitelisted targets, spider entities, Moon Weaver and the designated Allay forms are immune to contact. Protection uses the web owner. If the owner is unavailable, players and tamed pets are protected by default. Remaining status time counts down after leaving.

### 11. Lifetime and Water

Wait for actual membrane expiry, or place water against it.

Each membrane expires independently after sixty to ninety seconds. Water removes it early with no item drops. Membranes are also flammable.

The video retains the natural wait and shows water separately. Replacing a removed patch records a new deadline; stale expiry tasks cannot remove it early. Deadlines survive saves. The lifetime scene stays empty without accelerating world time.

### 12. Projectile Spell Weaving

Start a projectile spellbook cast with at least eight web mana.

Pay eight web mana once per cast. A top-face ground impact leaves an approximately three-by-three membrane. A direct valid hit leaves no ground patch and has a fifteen percent chance to fully cocoon for fifteen seconds on NPCs or five on players.

Each cast rolls once per target. Whitelisted targets are protected; piercing direct hits disable later ground weaving, and walls receive no floor patch. Insufficient web mana permits an ordinary cast without the extra payment or effects. The video includes one successful probability example, not a guaranteed proc.

### 13. Lunar Affinity and Woven Moon

Hit an enemy with a Lunar-element spell.

Deal fifteen percent more Lunar spell damage and restore two web mana on an eligible hit, subject to the common hit-refund interval.

The same level-one Moonlight Arrow compares human and Moon Weaver. The separate eight-mana projectile-weaving payment still applies; restoring two on hit does not make the cast free. Protected targets do not count as hostile hits.

## Weaknesses

| Limitation | Description |
| --- | --- |
| Armor restriction | No chestplate, leggings, or boots |
| Arthropod weakness | Takes amplified burn damage |
| Ground sluggish | Reduced movement speed on the ground |
| Diet restriction | Mainly eats spider cocoons and string |
