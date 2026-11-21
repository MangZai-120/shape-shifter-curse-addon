# Spell Anomalies and Elemental Reactions

Effective spell hits can build elemental anomalies on the target. Each element has a separate meter with a cap of 500. The caster's own burden is tracked as [disintegration](collapse.md).

- Incomplete meters share a 20-second retention timer, refreshed by new effective contributions. Reaching 500 triggers an anomaly and displays the full meter for five seconds.
- Repeated hits of the same school cannot retrigger a full meter. If another school fills, all full meters refresh to five seconds.
- When two schools fill within the five-second reaction window, their reaction replaces the second school's base anomaly. The two triggers are consumed for pairing. Full-meter and status durations are tracked separately.

## Base anomalies

| Element | Default effect |
| --- | --- |
| Fire | Immediately deals 8 points of fire reaction damage. |
| Ice | Freezes for five seconds. A subsequent effective hit breaks the freeze and leaves a three-second slow. |
| Lunar | Lunar Eclipse I reduces healing by 50% for six seconds. |
| Curse | Curse Weakness I reduces damage dealt by 20% for six seconds. |
| Summon | Summon Mark I increases summon damage taken by 25% for eight seconds. |
| Void | Void Erosion I increases spell damage taken by 15% for six seconds. |
| Space | Space Bind slows by 30% for five seconds and restricts Space Blink, Space Recall, and Pocket Space. |

## Two-element reactions

| Pair | Default effect |
| --- | --- |
| Fire + Ice | Deals 8 magic reaction damage in five blocks. Surviving targets are slowed by 50% for five seconds. |
| Fire + Lunar | Lunar Eclipse II for six seconds. |
| Fire + Curse | Curse Weakness I in three blocks for five seconds. |
| Fire + Summon | Flame Spirit lasts six seconds. Up to three effective summon hits add 2 fire reaction damage each. |
| Fire + Void | Immediately deals 4 magic reaction damage. |
| Fire + Space | Leaves a three-block Fire Zone for five seconds, dealing 1 fire reaction damage per second. |
| Ice + Lunar | Extends Lunar Eclipse to at least nine seconds. |
| Ice + Curse | Curse Weakness III reduces damage dealt by 35% for six seconds. |
| Ice + Summon | Frost Hunt lasts six seconds. The next effective summon hit adds 6 magic reaction damage. |
| Ice + Void | Immediately deals 6 magic reaction damage. |
| Ice + Space | Blocks sprinting for five seconds. |
| Lunar + Curse | Extends Lunar Eclipse and Curse Weakness to at least nine seconds each. |
| Lunar + Summon | Spirit Moon lasts six seconds. Effective summon hits heal their owner by up to 1 point each, with a total healing budget of 4 points. |
| Lunar + Void | Void Erosion II for six seconds. |
| Lunar + Space | Lunar Eclipse I in three blocks for five seconds. |
| Curse + Summon | Reduces damage dealt to summons by 50% for six seconds. |
| Curse + Void | Curse Weakness II and Void Erosion II for six seconds each. |
| Curse + Space | Curse Weakness I and sprint lock in three blocks for five seconds. |
| Summon + Void | Summon Mark II for eight seconds. |
| Summon + Space | Summon Mark I in three blocks for eight seconds. |
| Void + Space | Void Erosion I in three blocks for five seconds. |

The freeze recovery slow, Space Bind, and the Fire + Ice slow use the strongest modifier. The higher applicable vulnerability wins when both summon and spell vulnerability apply to one hit. For damage dealt to summons, the stronger applicable outgoing damage reduction wins. Flame Spirit, Frost Hunt, and Spirit Moon get their hit or healing budgets from the reaction itself.

[MCMod · Spell Anomalies and Elemental Reactions](https://www.mcmod.cn/item/1040547.html)
