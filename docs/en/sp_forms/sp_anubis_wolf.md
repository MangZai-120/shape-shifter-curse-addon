# SP Anubis — Netherjudge

**SP Anubis / Netherjudge**

> "You are no longer just the leader of an undead wolf pack.\
> When soul sand spreads beneath your feet, judgment has a boundary; the souls that step inside pay the price."

SP Anubis is an area-control form centered on **death domain, Netherwolf summons, and Soul Energy empowerment**. It can temporarily reshape the battlefield into a soul-sand domain, weaken enemies, empower Netherwolves, and expand undead pressure.

---

## Form Profile

| Item | Description |
| --- | --- |
| Registry name | `my_addon:anubis_wolf_netherjudge` |
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

[Watch the bilingual Netherjudge tutorial](../../tutorials/netherjudge/index.html?lang=en_us)

## Addon Abilities and Changes

### 1. Death Domain

Press the primary key while standing on convertible ground and not sneaking.

Charge for two seconds at seventy percent slower movement. The domain expands five blocks per second to a twenty-four-block radius, sustains for fifteen seconds after expansion, then restores the ground from the outside inward. Successful release starts a fifty-second cooldown.

Unprotected targets within roughly three blocks above soul sand or soul soil receive Slowness I, Wither I and a reduction equal to fifteen percent of base maximum health. Leaving removes that reduction. Functional and redstone blocks are protected. No convertible ground means failure and a ten-second cooldown.

### 2. Full-soul Enhanced Domain

Press the primary key after reaching one hundred soul energy.

Spend the full soul bar to shorten charging to one second. Radius increases to thirty-two blocks, sustaining for twenty-five seconds after expansion. Domain Wither becomes level II and six nether wolves are summoned automatically.

Automatic summoning also starts the secondary skill's thirty-second cooldown; the primary retains its fifty-second cooldown. The full expansion, sustain and retraction are shown without a crystal or Perks. Kills inside the enhanced domain do not provide its ordinary domain-kill energy.

### 3. Nether Wolf Court

Press the secondary key while not sneaking.

Howl for one and a half seconds at fifty percent slower movement. Summon two wolves normally, or four while a Death Domain is active. Basic wolves have twenty health and four attack; domain-summoned wolves gain ten health, two attack and Speed I.

Successful release starts a thirty-second cooldown. The summoned batch dissipates after about thirty seconds. The ordinary cap is six; a failed attempt at the cap has a five-second cooldown. Wolf hits heal the owner. Ordinary and domain summoning are shown separately; unchanged upstream passive summoning is excluded.

### 4. Soul Energy

Accumulate souls through direct kills, wolf kills or spellbook hits.

Maximum energy is one hundred. Direct kills outside the domain grant five, direct kills inside an ordinary domain grant twenty, and wolf kills grant ten. Qualifying kills while you have Wither add ten more.

Three real clips compare ordinary kills, kills while withered and ordinary-domain kills. Spellbook hits can also grant two soul energy, subject to the duplicate-hit settlement window. An enhanced domain consumes the full current soul bar.

### 5. Court Command and Spell Souls

Directly hit or successfully apply a harmful effect with a spellbook spell.

Each cast can spend five soul energy once to order one eligible wolf to rush the target. With no wolves, it attempts a safe nearby summon lasting about thirty seconds. The order lasts up to five seconds and ends on a hit.

Equal-level Fire Bolt compares zero with ten starting souls. Insufficient souls spend nothing and summon nothing; sufficient souls pay five and gain two from the spell hit. Invalid targets, unsafe ground or unavailable wolves cause no soul charge. Existing wolves move normally rather than teleporting.

### 6. Soul Scrutiny

Observe living targets within sixteen blocks at no more than twenty percent health.

Qualifying low-health targets gain a cyan outline and periodic soul particles, helping track them behind cover.

Targets with equal maximum health compare nineteen with thirty percent, including their real outlines behind a wall. This twenty-percent visibility threshold differs from Death Sentence's thirty-percent damage threshold. High-health targets do not receive the same outline.

### 7. Death Sentence

Attack a target at no more than thirty percent of maximum health.

Eligible damage to that target increases by twenty percent.

The same wooden sword and equal target maximum health compare hits at eighty and thirty percent health, showing immediate damage. Unchanged passive wolf summoning is paused in this isolated comparison so minions cannot damage the target first. No damage accessories or Perks are used.

### 8. Wither Hunt

Stand away from soul sand or soul soil while a withered living entity is within twenty-four blocks.

Gain twenty percent movement speed. When Wither ends or the entity leaves range, speed returns after the short scan interval.

The same stone floor compares before, during and after nearby Wither. The inherited upstream soul-ground movement bonus has separate rules and is not treated as this same effect.

### 9. Wither Frenzy and Transfer

Gain Wither, keep it active and attack.

While continuously withered, owner and wolf damage multiply by 1.1 below two seconds, 1.2 from two to four seconds and 1.3 afterwards. Each transfer consumes up to five seconds of your remaining Wither and adds the consumed duration to the target, capped at fifteen seconds.

Successive hits show tiers and both Wither timers. Refreshing Wither does not reset an accumulated tier; losing it does. Your own Wither damage is reduced by twenty percent and ticks less frequently, but still costs health. Wither brewing can prepare this state.

### 10. Soul-ground Healing

Stand on soul sand or soul soil.

Restore one health every three seconds.

Matching ten-health starts compare an ordinary stone floor with soul sand. This demonstrates the addon's ground healing. Inherited soul-ground movement and Wither-based healing are separate effects.

### 11. Additional Maximum Health

Compare the upstream third-stage Anubis Wolf with Netherjudge.

Netherjudge adds four maximum health.

Both clips are recorded without Wither, armour or health accessories. Unchanged undead properties, diet, potion reversal and leg or foot armour restrictions are retained without repeated demonstrations.

### 12. Undead Neutrality

Remain unprovoked around undead creatures.

Eligible undead do not naturally target Netherjudge. Attacking breaks this neutrality.

Real husk AI compares the upstream form with Netherjudge, then shows aggro after a deliberate attack. Restoring neutrality requires stopping provocation and staying out of sight for about thirty seconds; it does not happen immediately when you stop attacking.

### 13. Court Protection against Mobs

Enemy non-player creatures attack you, your pets or your summons.

Eligible mob damage is multiplied by 0.5. The protection also covers pets and summons attributed to the owner.

Equal eight-damage hits compare the upstream form, Netherjudge and a real player attacker. Player attacks do not gain this additional reduction. Self-Wither after a hit is a separate interaction; feedback reports the immediate health lost to that hit.

### 14. Wither Brewing

Right-click a potion with a Wither Rose in the inventory, or reverse the two ingredients.

Consume one rose and one potion to create a Wither Potion. Drinking it applies Wither II for twenty seconds.

The first clip shows the real inventory interaction and resulting item; the second shows the actual drinking effect. This is not a regeneration potion, so retain enough health and recovery options. Netherjudge can stack three basic drinkable Wither Potions.
