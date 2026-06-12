# SP Forms Overview

**SP Forms (Special Forms)** are the core advanced-form system of SSCA. They are not simple stat boosts — each one rewrites the combat loop of its base form: some revolve around resource bars, some rely on summons, others focus on crowd control, stealth, healing, or percent-based bursts.

---

## Evolution Methods

### Moon Essence Cross

The Moon Essence Cross route requires the **night of the Cursed Moon**, plus the matching base form.

| Base Form | Target Form |
| --- | --- |
| Familiar Fox (Red Fox) Stage 3 | [SP Familiar Fox (Spirit Realm Lord)](sp_familiar_fox.md) |
| Axolotl Stage 3 | [SP Axolotl (Abyssal Overlord)](sp_axolotl.md) |
| Vanilla Allay form | [SP Fallen Allay (Spirit of the Fallen)](sp_fallen_allay.md) |
| Anubis Wolf Stage 3 | [SP Anubis Wolf (Netherjudge)](sp_anubis_wolf.md) |
| Bat Stage 3 | [Desmodus (Bloodthirster)](sp_bat_desmodus.md) |

Familiar Fox (Red) is **not** a stable Moon Essence Cross target; instead, an SP Familiar Fox under the Cursed Moon has a **5%** chance of a temporary transformation into Red, lasting **12000 ticks (10 minutes)** before reverting back to SP Familiar Fox.

### Evolution Stone

The Evolution Stone route does not require a Cursed Moon — long-press to use it and attempt to evolve.

| Base Form | Target Form |
| --- | --- |
| Vanilla Wild Cat SP / Ocelot branch | [Wild Cat (SP) (Moonlight Phantom)](sp_wild_cat.md) |
| Snow Fox Stage 3 | [SP Snow Fox (Frost Spirit)](sp_snow_fox.md) |
| Vanilla Allay form | [SP Allay (Crystal Sound)](sp_allay.md) |
| Anubis Wolf Stage 3 | [Golden Sandstorm (SP)](sp_golden_sandstorm.md) |
| Familiar Fox (Red Fox) special branch | [Mancianima (Forbidden Weapon)](sp_familiar_fox_mancianima.md) |
| Bat Stage 3 | [Parasitic Fruit Bat (Moving Orchard)](sp_bat_parasitic_fruit.md) |

---

## SP Forms at a Glance

| Form | Title | Evolution | Combat Role |
| --- | --- | --- | --- |
| [SP Familiar Fox](sp_familiar_fox.md) | Spirit Realm Lord | Moon Essence Cross | Mana Mage / Sustained AOE |
| [Familiar Fox · Red](sp_familiar_fox_red.md) | Spirit Realm Lord variant | SP Familiar Fox temporary form under Cursed Moon | High-risk Fire Ring / Empowered Breath |
| [Mancianima](sp_familiar_fox_mancianima.md) | Forbidden Weapon | Evolution Stone special branch | Single-target Hunter / Brand True Damage |
| [SP Axolotl](sp_axolotl.md) | Abyssal Overlord | Moon Essence Cross | Aquatic Control / Close-range Burst |
| [Wild Cat (SP)](sp_wild_cat.md) | Moonlight Phantom | Evolution Stone | True Stealth / Reveal Burst |
| [SP Snow Fox](sp_snow_fox.md) | Frost Spirit | Evolution Stone | Dual-mode Frost Control |
| [SP Allay](sp_allay.md) | Crystal Sound | Evolution Stone | Aerial Support / Group Heal |
| [SP Fallen Allay](sp_fallen_allay.md) | Spirit of the Fallen | Moon Essence Cross | Vex Summons / Battlefield Disruption |
| [SP Anubis Wolf](sp_anubis_wolf.md) | Netherjudge | Moon Essence Cross | Death Domain / Netherwolf Summons |
| [Golden Sandstorm](sp_golden_sandstorm.md) | Golden Sandstorm | Evolution Stone | Brand Burst / Wither Sustain |
| [Desmodus](sp_bat_desmodus.md) | Bloodthirster | Moon Essence Cross | High-mobility Melee / Blood Thirst Lifesteal |
| [Parasitic Fruit Bat](sp_bat_parasitic_fruit.md) | Moving Orchard | Evolution Stone | Team Support / Sustained Control |

---

## Playstyle Groups

### Resource-loop type

SP Familiar Fox, Red, SP Axolotl, SP Snow Fox, and SP Allay all rely heavily on resource bars. Mana, Moisture, Frost, and Crystal Energy decide whether they can keep fighting.

### Control & Summon type

SP Snow Fox, SP Fallen Allay, and SP Anubis Wolf focus on reshaping the battlefield: Frost Storm pull, Vex chase, and Death Domain terrain shift all force enemies out of position.

### Assassin & Burst type

Wild Cat (SP), Mancianima, and Golden Sandstorm focus on locking in a target and finishing the burst. Their power comes from stealth openers, brand pressure, or percent-based execute — not from standing toe-to-toe. Desmodus is a classic night-fighting melee hunter: it grows stronger the more Blood Thirst it stacks, but is especially fragile by day and against iron weapons.

### Team Support type

SP Allay is the closest thing to a pure support: purify, heal, totem guard, and ranged damage boost make it extremely valuable in multiplayer, but its low HP means it needs teammate protection during cast windows. The Parasitic Fruit Bat runs on a "seed" economy: buffing allies, debuffing enemies, and using infection spore bombs to provide sustained group pressure and team healing.

---

## Whitelist Notes

Most AOE damage, control, healing, and summon-targeting plugs into the SSCA whitelist logic: protected targets are not friendly-fired, and buff skills also use a buff-side whitelist to decide who can be healed. Boundaries vary slightly per skill — see each form's page for the authoritative description.
