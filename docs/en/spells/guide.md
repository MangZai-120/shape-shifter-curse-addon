# Spellcasting and Rune Research

This guide covers the default rules for SSCA 8.0.0 on Fabric 1.20.1. Server data packs can adjust costs and balance.

## Equip a book and cast

1. Hold a [Moon Dust Spellbook](https://www.mcmod.cn/item/999131.html), then **sneak and right-click** to open it and insert spell scrolls.
2. Equip the book in the spellbook accessory slot provided by Trinkets, Curios, or Accessories, depending on your installation.
3. Hold the spell-selection key and scroll to select a spell. Press the cast key to use it. Usable scrolls can also be cast directly. See Controls for key bindings.

> **8.0.1 development builds** add separate Previous Spell and Next Spell bindings for keyboard or virtual buttons.

| Book level | Spell slots | Formation slots | Book XP for next upgrade |
| --- | ---: | ---: | ---: |
| I | 3 | 1 | 600 |
| II | 5 | 3 | 900 |
| III | 7 | 6 | Maximum level |

Upgrade at the **Spell Preparation Table** ([Infusion Altar entry on MCMod](https://www.mcmod.cn/item/999132.html)) with one Shaping Core and one Moondust Pure Crystal. Upgrading resets the book's XP and preserves your player casting XP. The table also equips and removes enhancement formations. Casting adds to your [disintegration](collapse.md).

## From found scroll to research entry

1. Put one unanalyzed scroll and one [blank formation paper](https://www.mcmod.cn/item/999880.html) in the [Spell Research Table](https://www.mcmod.cn/item/999133.html). Clear the output. Analysis takes 10 seconds by default, spends the paper, and retains the scroll. Researchable spells also produce a reference diagram.
2. Identify runes in the Research Notes. Once you reach Rank I, keep a matching diagram from this world in your inventory and fill the foundation slots by hand. Projecting a known spell from the catalog displays a reference overlay.
3. Run a free test before completing the formation. Successful completion spends paper, ink for the runes used, and raw moondust, then records the spell in the catalog. An empty outer ring yields a normal scroll. A valid enhancement ring yields a rune scheme.
4. Complete a tier-I formation, then use valid foundations one tier above your current rank for free advancement training up to Rank V. The spell determines its maximum scroll tier. Rune rules, diagrams, and schemes are tied to the world.

[Watch the 18-part Spell Research Table tutorial](../../tutorials/spell-research/index.html)

## Scroll upgrades and maintenance

| Action | Where | Requirements and result |
| --- | --- | --- |
| Upgrade | Research Table · Rune Upgrade | Build the next-tier foundation with an empty outer ring and insert an analyzed scroll of the same spell one tier below. Default ink cost equals the target tier. Pure crystal cost is the target tier minus two, with a minimum of zero. Charges, cooldown, imprint, and pocket binding carry over. The selected casting tier resets. |
| Imprint | Preparation Table · Scroll Preparation | Pair a scheme from this world with an analyzed scroll of the same spell and tier. The scheme is consumed. Charges, cooldown, selected casting tier, and pocket binding carry over. |
| Repair | Preparation Table · Scroll Preparation | By default, one matching ink and two raw moondust restore all charges on an eligible scroll. |
| Dismantle | Preparation Table · Scroll Preparation | Consumes the scroll and returns raw moondust equal to half its tier, rounded up by default. A bound pocket space remains in the world. |

## Enhancement formations

Analyze a found formation and hold use to consume it and record its school, variant, and tier. Learn it at the research table with raw moondust, normally twice its tier. You can then scribe copies using one blank paper and matching ink equal to its tier. Universal formations use normal ink and have four separately learned variants. Equip the resulting formation in a book slot. Rune schemes imprint individual scrolls.

[MCMod · Spell items and formation inks](https://www.mcmod.cn/item/list/24327-1.html)
