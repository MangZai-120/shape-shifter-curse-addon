# Salticidae (Jumping Spider)

Salticidae is the Evolution Stone SP branch of the spider stage III, centered on **charged pounce kills + safety-silk recall** as its mobility-output loop, backed by venom AoE. Walls and the air are its true hunting grounds.

!!! warning "Irreversible"
    Unlike vanilla SSC SP forms, all SP forms in this addon **cannot** be reverted with inhibitors.

***

## Form Profile

| Item | Description |
| --- | --- |
| Classification | Spider stage III · Evolution Stone branch |
| Combat role | Charged pounce / Aerial mobility / Venom guerrilla |
| Note | Sibling branch of [Moon Weaver](sp_moon_weaver.md) (Moon Essence Cross route) |

***

## Evolution Requirements

| Condition | Requirement |
| --- | --- |
| Base form | Spider stage III (final form) |
| Evolution item | Evolution Stone |

***

[Watch the bilingual Salticidae gameplay tutorial](../../tutorials/salticidae/index.html?lang=en_us)

## Addon Abilities and Changes

### 1. Charged Pounce and Hit Results

Hold the primary key, aim, then release to pounce.

Charge caps at three seconds, expanding acquisition from three to sixteen blocks while halving movement speed. Base impact is eight physical damage, Poison II for eight seconds and a 0.35-second root. Unprotected NPCs double that impact and both durations. Hit cooldown is twelve seconds; a miss is eight.

The form’s flat two melee-type damage also applies: default unarmored player impact is ten and NPC impact eighteen. Later poison and the automatic curse are separate; eight is the base input rather than final health loss.

### 2. Candidates, Locking and Losing Lock

Move the crosshair while charging; release selects the final target.

Visible candidates gain red outlines; the target nearest the crosshair within roughly twelve degrees turns green, visible only to you. Protected and block-obscured targets are excluded.

Flight predicts the target’s motion. Moving farther than forty percent of the original jump distance from its initial locked position breaks tracking. Without a target, you still pounce along your view. Inertia and landing can affect where you finally stop.

### 3. Safety Silk Recall

Pounce near the ground, then press the primary key after the jump ends.

A full eight-second recall window begins when the pounce ends. Recall works even during primary cooldown and returns along the silk toward the takeoff anchor. Each anchor permits one recall and is consumed afterward.

Recall follows a real path with collision and slows near the anchor. Flight time no longer consumes the post-pounce window; broken or consumed silk is not recreated when the jump ends.

### 4. Silk Reach and Break Conditions

Watch the ground anchor, timer and recall path.

Maximum effective silk distance is forty-eight blocks, measured in a straight three-dimensional line to the ground anchor. Excess distance, expiry, thick solid obstruction or a stalled recall invalidates it.

Takeoff must be near a standable surface to create an anchor. High-air takeoff has no ground return silk. Footage compares expiry with airborne takeoff. The forty-eight-block silk limit is separate from sixteen-block charged acquisition.

### 5. Basic Venom and Friendly Protection

With no safety silk, aim and press the secondary key.

A forward three-and-a-half-block cube deals four base magic damage and applies fifteen seconds of Poison I. Unprotected NPC damage is multiplied by 1.5, giving six by default. Cooldown is ten seconds.

Whitelisted targets are protected. The footage includes player first person and an out-of-range control. The area center follows aim; the old two-block cube description is outdated.

### 6. Silk-enhanced Venom Dash

Press the secondary key while valid safety silk remains after a pounce.

Consume the silk and dash six blocks along the release direction. Enemy collision deals two base magic damage and stops the dash. A four-and-a-half-block burst then deals six base magic damage and fifteen seconds of Poison II. Both damage inputs gain the 1.5 NPC multiplier.

Collision and burst settle separately: eight direct damage to unarmored players or twelve to NPCs, before later poison. The fix bypasses only this dash’s collision immunity. Whitelist protection remains; post-dash inertia is separate from the six-block skill distance.

### 7. Venom Fangs

Deal a valid damaging hit to an unprotected living target.

Fangs apply five seconds of Poison I; ordinary left-click melee demonstrates it here. Stronger or longer poison still follows normal status merging.

Protected targets receive no fang poison. The current hit trigger also permits native skill damage, after which the skill’s stronger poison may replace it. No Venom Gland accessory is equipped.

### 8. Predator Strength

Compare human and Salticidae with the same weapon.

Melee-type damage gains a flat two. Against an unarmored target, the same wooden sword deals four as a human and six as Salticidae.

The modifier checks damage type, so the pounce’s player-melee source also benefits. Weapons, armor and other multipliers can change final health loss.

### 9. Jump Boost, Double Jump and Safe Landing

Jump normally, release, then press jump again in midair.

The first jump gains an extra 1.65 velocity multiplier alongside inherited jump modifiers. One air jump is available per airtime and resets on landing. Salticidae replaces inherited fall protection with complete fall-damage immunity.

Three presses produce only the first jump and one air boost. A separate high fall verifies immunity. The 1.65 multiplier affects launch velocity, rather than directly multiplying final height; the air jump has its own velocity.

### 10. Base Maximum Health

Transform and compare day with night.

Base maximum health is sixteen, or eight hearts.

Footage changes time while retaining the form. Equipment, other attributes and mods can still change the final maximum.

### 11. Automatic Pounce Curses and Affinity

Deal actual pounce damage to a legal enemy while carrying a curse in your equipped spellbook.

Prefer the selected ready non-red curse, otherwise another ready non-red curse slot at random. The automatic cast pays twenty percent of normal collapse, uses ten percent of normal cooldown and consumes no scroll uses. Salticidae also increases curse duration ten percent.

The same level-one Dread Whisper compares eighteen collapse and twenty seconds manually with 3.6 collapse and two seconds automatically. Status duration rises from one hundred twenty to one hundred thirty-two ticks; both manual and automatic casts keep affinity. Automatic aiming preserves your camera. Rejected pounce damage does not trigger a spell.

### 12. Free White-curse Fallback

Land a pounce with no spellbook or no available non-red carried curse.

Automatically cast a random level-one white curse for free, with no added collapse and no scroll item created or consumed.

Fallback never shortens an existing scroll or shared cooldown. Loose inventory scrolls are excluded from equipped-book selection. Projectile fallback spells still fire real projectiles; later hits depend on position and flight rather than forcing a status onto the victim.

### 13. Meat Diet and Resource Tradeoff

Hold meat and hold the use key to eat.

Salticidae uses a meat diet instead of the third-stage spider’s string and fluid-cocoon diet, and does not retain its web-mana resource.

While hungry, the footage first tries string, then actually eats cooked beef and shows food recovery. Native pounce, recall and venom use their cooldowns without Moon Weaver’s weaving-mana cost.

## Weaknesses

| Limitation | Description |
| --- | --- |
| Carnivore | Only gains saturation from meat |
| Armor restriction | No chestplate, leggings, or boots |
| Arthropod weakness | Takes amplified burn damage |
| Ground sluggish | Reduced ground movement — walls and air are the real arena |
