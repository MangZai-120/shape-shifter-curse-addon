# Golden Sandstorm

**Golden Sandstorm**

> "Golden sand is not warm.\
> It clings to wounds and becomes a brand; when you detonate it, enemies finally realize the storm remembered them long ago."

Golden Sandstorm is the other evolution path of the Anubis Wolf Stage 3 line. Instead of relying on a Death Domain to set up the field, it snowballs in melee through **Erosion Brand, Wither Sand, percent-based burst, and combat sustain**.

---

## Form Profile

| Item | Description |
| --- | --- |
| Registry name | `my_addon:anubis_wolf_golden_sandstorm` |
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

[Watch the bilingual Golden Sandstorm tutorial](../../tutorials/golden-sandstorm/index.html?lang=en_us)

## Addon Abilities and Changes

### 1. Withering Sand

Press the primary key while not sneaking and complete the charge.

Charge for one second at fifty percent slower movement. Eligible unprotected targets within fifteen blocks receive three seconds of Sand Blindness and one erosion layer. Successful release starts a twenty-six-second cooldown.

Losing health during charging interrupts the cast and starts a seven-second cooldown. Clips compare range and whitelist protection, a real affected player's view, and an actual damage interruption. No Perks add layers.

### 2. Detonate Marks

Press the secondary key while not sneaking to settle existing non-green marks.

Each eligible target takes twenty percent of current health, capped at twenty and floored at one. A three-layer target first takes a passive burst, then detonation is calculated from remaining health. Success has a ten-second cooldown and heals twenty percent of your missing health once.

No eligible unprotected marks means no cooldown or healing. Compare one layer, three layers, an empty attempt and a whitelisted control. Melee can still mark protected targets; manual detonation skips them and preserves their existing marks. No damage-cap ring or spreading prism is equipped.

### 3. Erosion Layers and Colours

Land melee hits, Withering Sand or qualifying spell hits.

Up to three layers last ten seconds, with repeated hits able to refresh the duration. Under the default form-casting settings, melee, sandstorm and spell layers share a roughly two-second gate per target.

Yellow, orange and red indicate one, two and three layers. Actual hits one second apart show that not every hit adds a layer. A separate clip shows an unrefreshed layer expiring naturally. Whitelisting does not prevent direct melee layers.

### 4. Full-layer Passive Burst

Hit a target already carrying three layers after the shared gate allows another application.

Trigger extra damage equal to twenty percent of current health, capped at twenty and floored at one, and heal ten percent of your maximum health. Layers clear and the target turns green.

Green lasts ten seconds; the first five seconds block new layers, after which stacking can restart. Complete observer footage shows layers, burst, healing and the end of green without damage or spreading accessories. The controlled healing scenes temporarily isolate unchanged upstream on-hit healing to show the addon recovery separately.

### 5. Dedicated Recovery and Hunger Healing

Recover through resting, your own attributed Wither ticks or direct kills.

Hunger-based natural healing is disabled. Resting restores one health every ten seconds outside combat or six seconds in combat. Each attributed Wither damage tick heals one; direct player kills heal four and nether wolf kills can heal three.

Full hunger and saturation compare a human with Golden Sandstorm, followed by combat resting, counter-applied Wither and a direct kill. Combat includes recent attacks or damage and nearby combat counterparts; it does not use the old guide's fixed five-second exit rule. The controlled healing scenes temporarily isolate unchanged upstream on-hit healing to show the addon recovery separately.

### 6. Counter Burst

Automatically triggers on a qualifying incoming attack.

Push unprotected living targets outward within four blocks, add a slight upward lift and apply Wither I for five seconds. Internal cooldown is fifteen seconds.

The cooldown starts even if nothing is hit. Two equal attacks show that another hit during cooldown does not refresh the counter. Whitelisted and out-of-range targets provide controls. Its attributed Wither ticks can also trigger dedicated recovery.

### 7. Wither and Sand Adaptation

Attempt to apply Wither, Blindness and Sand Blindness.

Golden Sandstorm additionally blocks Wither, Blindness and Sand Blindness while retaining the inherited Regeneration immunity.

Real effect attempts compare the upstream third-stage Anubis Wolf with Golden Sandstorm. Regeneration refers to the status effect; dedicated resting, burst and kill healing still work. Unchanged undead potion reversal is not filmed again.

### 8. Fire Affinity and Wildfire Refund

Hit with a spellbook Fire spell, including a target already burning before impact.

Fire damage is multiplied by 1.15. Hitting an already-burning target refunds twenty percent of the cast cost; a Fire kill refunds fifty percent. Per-cast budgets and duplicate-settlement windows limit refunds.

Equal-level Fire Bolt compares a human, Golden Sandstorm, a pre-burning target and a kill. Refunds reduce paid collapse; they do not make spells free or shorten form-skill cooldowns.

### 9. Spell Erosion Marks

Directly hit or successfully apply a harmful effect with a spellbook spell.

One cast can contribute at most one erosion application per target; targets are tracked independently. Different casts still share the target's two-second layer gate with melee and Withering Sand.

A real Ice Barrage contributes one layer despite multiple hits, followed by Fire Bolt from another book slot to add a second. Spell cooldowns are not reset to create the comparison, and individual barrage projectiles are not counted as separate casts.

### 10. Husk and Jackal Neutrality

Remain unprovoked around husks or qualifying transformative jackals.

Neutrality covers husks and qualifying jackals rather than all undead. Ordinary zombies can still naturally acquire you as a target.

Real AI clips show a neutral husk, retaliation after a deliberate attack, and natural zombie aggro. Stopping provocation and leaving sight still requires a recovery period; stopping attacks does not immediately erase aggro.
