# Available Commands

## /ssc_addon

The main command prefix of this mod.

### Usage

```
/ssc_addon <subcommand> [args]
```

## Player commands

| Command | Use |
| --- | --- |
| `/ssc_addon my_whitelist` | Open your whitelist manager. |
| `/ssc_addon debug form / mana / anim` | Inspect your current form, energy, or animation diagnostics. |
| `/ssc_addon palette export` | Export your current form colors as a share code. |
| `/ssc_addon palette apply <code>` | Apply a form color share code to yourself. |

## Operator commands

These commands require permission level 2.

| Command | Use |
| --- | --- |
| `/ssc_addon set_mana <player> <nonnegative integer>` | Set existing form resources such as SSC mana, Frost, Allay magic, Mist Blood, and Anubis soul energy, capped per resource. |
| `/ssc_addon mark_owner <entity>` | Mark the executor as the owner of the selected entity for effects that need attack attribution. |
| `/ssc_addon evolution unlock_all / reset [player]` | Unlock or reset SSCA evolution talents. |
| `/ssc_addon get_book <book ID> [language]` | Give a story book. Use `zh_cn` or `en_us` for language. |
| `/ssc_addon reload` | Reload the SSCA configuration. |

[MCMod command reference](https://www.mcmod.cn/item/922952.html)

---

## Friend Marker

### Add Friend Marker

Right-click another player while holding the [Friend Marker](../items/tools.md#friend-marker) to add them to your whitelist.

### Clear Friend Marker

Right-click another player while holding the [Clear Friend Marker](../items/tools.md#clear-friend-marker) to remove them from the whitelist.

---

## Item Acquisition

You can use the vanilla `/give` command to obtain mod items:

```
/give @p ssc_addon:sp_upgrade_thing 1      # Moon Essence Cross
/give @p ssc_addon:evolution_stone 1        # Evolution Stone
```
