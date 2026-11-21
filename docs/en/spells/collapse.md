# Disintegration

Casting from a scroll or a Moon Dust Spellbook adds to the same player-owned burden. Native form abilities use their own resources.

## Safety and casting risk

- Your safety threshold comes from your current form, player casting XP, and the capacity formation in your equipped book. By default, every 600 player casting XP adds 50 safety. Capacity formations add 10 per level and use the highest level equipped.
- Casts in progress pay disintegration in stages. Any burden already paid remains after interruption. Repeated successful use builds spell proficiency and reduces that spell's disintegration cost.
- Above your safety threshold, casting can fail, deviate, or cause backlash. Random failure can occur during interruptible preparation. Protected release stages retain their protection.
- The white line on the HUD marks the safety threshold. The bar fills at twice that value. The number and overflow marker show burden above the visual range.

## Overload penalties

C is current disintegration and N is the current safety threshold. Two health points equal one heart.

| Disintegration | Movement reduction | Health loss | Screen effects |
| --- | ---: | --- | --- |
| C ≤ N | 0% | None | None |
| N < C ≤ 1.2N | 15% | None | None |
| 1.2N < C ≤ 1.4N | 30% | None | None |
| 1.4N < C ≤ 1.6N | 40% | 1 point per second | None |
| 1.6N < C ≤ 1.8N | 60% | 1 point per 0.5 seconds | Nausea |
| C > 1.8N | 80% | 2 points per 0.5 seconds | Nausea, blindness, and wither visuals |

Overload drains health directly, bypassing armor and absorption. Lethal drain can trigger a Totem of Undying or an available Nova revival. Creative players are exempt from the continuous drain.

## Recovery

Recovery begins once you stop casting and about six seconds have passed since the last increase. The default rate without a book is five points per second. With a book it is `5 × (book level + 2) ÷ 3 × (1 + 0.20 × total recovery formation levels)` points per second. Changing form or respawning clears the current burden and recovery delay.

[Moon Dust Potions](../items/tools.md#moon-dust-potions) can reduce existing burden. [MCMod illustrations and examples](https://www.mcmod.cn/item/1036560.html)
