# Desmodus (Vampire Bat) — Bloodthirster

> "When the power of the Moon Essence Cross soaks into the essence of a bat, it is no longer merely a wanderer of the night…\
> You are the blood-craving vampire, hunter of the moonlit hours, the unheard whisper."

Desmodus is a special bat variant reforged by the power of the **Moon Essence Cross**. It pushes the bat's nocturnal instincts to the extreme: a melee loop driven by Blood Thirst, a mist-form escape that nullifies damage, and an ultrasonic wave that leaves enemies "unable to hear a sound." It is a high-mobility melee form that grows stronger the longer it fights at night — but fears sunlight and iron.

***

## Form Profile

| Item | Description |
| --- | --- |
| Registry name | `my_addon:bat_desmodus` |
| Classification | Bat Moon Essence Cross variant (a separate branch from the Parasitic Fruit Bat) |
| Affinity | Vampire / Hunter of the moonlit hours |
| Combat role | High-mobility Melee / Blood Thirst Lifesteal / Sonic Control |

***

## Evolution Requirements

| Condition | Requirement |
| --- | --- |
| Base form | Bat (Stage III) |
| Evolution item | Moon Essence Cross |
| Trigger | At night during the Cursed Moon |
| Note | A **different evolution path** from the Parasitic Fruit Bat (Evolution Stone variant); Desmodus follows the Moon Essence Cross route |

***

[Watch the bilingual Desmodus tutorial](../../tutorials/desmodus/index.html?lang=en_us)

## Addon Abilities and Changes

### 1. Mist Form

Press the primary key to become mist.

Hide your model and ignore damage other than the void for about 4.5 seconds. The ten-second cooldown begins when mist ends. Entering and maintaining mist costs no blood.

Normal right-click interactions and Sonic Wave are blocked during mist. Equal incoming damage compares mist with the period after it ends. The void remains dangerous.

### 2. Condensed Burst

After one second in mist, press the primary key again.

Charge for another second, end mist and damage unprotected targets within four blocks with knockback. Base damage is twelve, doubled to twenty-four against hostile non-player living targets by default. High-blood damage bonuses apply separately.

Movement is halved while charging. Whitelisted and distant targets provide controls. The first three hits also grant twelve, six and three blood. Mist cooldown starts when the burst ends.

### 3. Sonic Wave

Aim and press the secondary key.

Send a cylinder eight blocks long and 3.5 blocks wide along your view. Base physical damage is six, or twelve against hostile non-player living targets by default. Apply three seconds of Nausea plus Deafness to players or Blindness to other living targets. Cooldown: eight seconds.

Whitelisted targets are protected. Real target-player audio demonstrates a bell becoming inaudible during Deafness and returning afterward. Sonic hits also grant blood.

### 4. Blood Gain and Decay

Land melee or skill hits, or kill living targets.

Blood is capped at one hundred. Unprotected melee hits grant eight with a 0.3-second internal gate. Burst and sonic hits grant twelve, six and three for the first three targets; kills grant fifteen.

Decay starts twelve seconds after the last combat event: four per second under cover, six in open daylight or two in open night. Simply remaining in combat does not generate blood every second. Burst and sonic damage also trigger the generic melee-type gain; a single target gives twenty total in the recorded default setup.

### 5. Blood Stages and Lifesteal

Attack or take damage at different blood levels.

Below twenty-five blood, take fifteen percent less damage. At twenty-five to forty-nine, neither that reduction nor stage lifesteal applies. Fifty to seventy-four gives thirty percent lifesteal. At seventy-five or more, outgoing damage rises fifteen percent and lifesteal reaches 52.5 percent.

Equal incoming damage and the same wooden sword provide comparisons. Hits also raise blood, so crossing a threshold can change the stage during a hit. No blood accessories are equipped.

### 6. High-blood Mist Aura

Enter mist with at least seventy-five blood.

Each second, damage unprotected targets within two blocks for two, or four against hostile non-player living targets by default. Each successfully damaged target heals you for two health.

Compare starting blood of fifty-five and eighty. The aura only operates in mist and excludes the high-blood fifteen percent outgoing bonus. Its fixed healing is separate from ordinary hit lifesteal.

### 7. Daylight Weakness

Stand under an unobstructed sky during daytime.

Attack attribute falls twenty percent and movement speed attribute falls ten percent. Out-of-combat blood decay increases to six per second.

Solid overhead cover removes these additional daylight penalties. Inherited ground movement and sun-related health behavior can still affect final attributes; this lesson covers the addon daylight modifiers.

### 8. Moonlit Excitement

Remain outside at night with the sky visible.

Gain fifteen percent attack and five percent movement speed. Out-of-combat blood decay falls to two per second.

Compare the same form under overhead cover. Both nighttime and sky visibility are required; indoor nighttime does not grant these outdoor attribute bonuses.

### 9. Iron Vulnerability

Take a hit from an attacker holding an iron weapon or tool.

Take fifteen percent extra damage. Iron swords, axes, pickaxes, shovels and hoes qualify.

The attacker holds a wooden sword or iron sword while the input damage and blood stage remain equal. Other mechanics such as low-blood reduction and armor can still affect final damage.

### 10. Night Echolocation

Automatically activates at night, including indoors.

Every three seconds, living targets within twelve blocks glow for about one second.

Indoor night footage compares targets inside and outside the radius. This is the form's native addon echo without the enhanced echolocation Perk.

### 11. Blood Casting

Begin a spellbook cast with at least fifteen blood.

Pay fifteen blood at the start to reduce collapse growth for that cast by thirty-five percent. Insufficient blood still permits ordinary-cost casting.

Equal-level Fire Bolt compares sufficient and insufficient blood. Payment happens once per cast and is not refunded on interruption. Native form skills do not inherit this blood cost.

### 12. Lunar Mend Affinity

Cast Lunar Mend.

Lunar Mend healing is multiplied by 1.25 in Desmodus form.

Compare equal-level self-healing with a human from the same starting health. This bonus belongs specifically to Lunar Mend, rather than every healing source.

## Exclusive Trinkets

| Trinket | Effect |
| --- | --- |
| Blood Garnet | Blood Thirst gains **+50% faster**; but out-of-combat decay **+50%**, and passively loses 1 Blood Thirst per second |
| Bloodlust Ring | **+15% extra lifesteal** at high Blood Thirst stages; but when at full health, lifesteal backfires for 1 true damage per second |

***

## Combat Role

**A high-mobility lifesteal melee that hunts by night**

Desmodus's core loop:

1. Engage **at night** with Moonlit Frenzy and Echolocation, rapidly stacking **Blood Thirst** with attacks and skills.
2. The higher your thirst, the more damage and lifesteal you gain — a snowball that grows stronger the longer you fight.
3. When threatened, use **Mist Form** to negate damage and escape (or burst in melee), and use **Sonic Burst** to break enemy rhythm with Nausea / Deafness / Blindness.

It is a top-tier melee hunter at night, but especially fragile by day and against iron-wielding foes — choosing when to fight is the key to survival.

***

## Inner Monologue

> "The lure of blood calls to you at every moment. Each time night falls and moonlight pierces the clouds, your instincts surge to life — you hear every heartbeat, smell every trace of blood, and crave the bite and the feast. When the sun rises, you want only to hide in the deepest shadow, curling up to wait for night to return. You no longer belong to the world of light."
