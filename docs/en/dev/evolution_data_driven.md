# Evolution Talents · Data-Driven Form Trees

> This page is for **developers / modpack authors**. It explains the data-driven framework behind SSCA's "permanent-form evolution talent system": how to design a form's entire evolution tree in JSON, and what you need to do to add a brand-new form's evolution.

## 1. What it does

The **evolution tree** itself is now fully data-driven. A form's **node layout, unlock costs, prerequisites, branches, icons, point-granting levels and auto-branch level** all live in one JSON file, and the framework handles automatically:

- Loading (datapack reload scan)
- Singleplayer / multiplayer sync
- Granting talent points by XP level
- Node unlocking / prerequisite checks / point spending
- Evolution tree screen rendering
- Form recognition (entering the start form auto-joins the route)
- Continue-evolution gating (all branch nodes unlocked before Moon-Ring / Evolution Stone can evolve)

!!! note "File location"
    Route files go in `src/main/resources/data/<namespace>/ssca_evolution/routes/<route_id>.json`.
    **Filename = route id.** Files starting with `_` are skipped (use them for placeholders / commented samples).
    See the bundled template `routes/example.json`.

## 2. Modularization boundary (read first)

| Layer | Pure JSON? | Notes |
|---|---|---|
| Tree definition (nodes / costs / prereqs / branches / point levels) | ✅ Pure JSON | Just write a route JSON |
| Server logic (points / unlocking / form recognition / gating) | ✅ Generalized | Framework reads route data, no Java needed |
| Ability gating (ability only works after unlock) | ✅ Data | power JSON with `ssc_addon:has_talent` condition |
| Multiplayer sync | ✅ Automatic | Framework S2C-syncs route definitions to clients |
| **Form itself** (appearance / model / ability powers) | ❌ Needs Java + assets | Same as any new form, **not part of the evolution framework** |
| **Client entry / UI hooks** | ⚠️ Still bound to familiar_fox | See "4. Java hooks that still need manual edits" |

!!! warning "Bottom line"
    **The evolution tree layer is already pure JSON.** But making a **brand-new form** join the evolution system still requires building the form itself, plus editing a few client entry points (the pilot form "Upgrade Familiar Fox" is currently hardcoded).

## 3. Route JSON fields

```jsonc
{
  "enabled": true,                                  // open or not (false = hidden from entry)
  "display_name": "evolution.my_addon.fox.name",    // route name lang key (optional, auto-generated)
  "start_form": "my_addon:upgrade_familiar_fox",    // the form id this route enters
  "base_node": "familiar_fox_base",                 // initial auto-unlocked node (optional = first auto_unlock main node)
  "level_milestones": [5, 10, 15, 20, 30, 40, 45],  // grant 1 point at each of these XP levels
  "auto_branch_level": 50,                          // auto-unlock branch nodes at this level
  "nodes": [ /* see below */ ],
  "branches": { /* see below */ }
}
```

### Nodes `nodes[]`

| Field | Required | Default | Description |
|---|---|---|---|
| `id` | ✅ | — | Unique node id (= the talent id used by `has_talent` gating) |
| `icon` | | `minecraft:barrier` | Icon item id (zero art — use vanilla / mod items) |
| `cost` | | `1` | Unlock cost in points |
| `prereqs` | | `[]` | Prerequisite node ids (**all** must be unlocked, AND) |
| `col` / `row` | | `0` | Tree layout column / row |
| `auto_unlock` | | `false` | true = auto-unlock when prereqs met, no point cost (e.g. base / branch nodes) |
| `branch` | | `""` | Branch tag (`""` = main line; non-empty = node exclusive to that branch) |
| `name` / `desc` | | auto | lang keys; omit to auto-use `evolution.my_addon.<route>.node.<id>.name/.desc` |
| `grants_powers` | | `[]` | Associated power ids (metadata for reference; gating still via the power's own has_talent) |

### Branches `branches{}`

```jsonc
"branches": {
  "spirit_lord": { "sp_form": "my_addon:familiar_fox_sp",         "requires_nodes": ["fire_ring"] },
  "mancianima":  { "sp_form": "my_addon:familiar_fox_mancianima", "requires_nodes": ["fire_ring"] }
}
```

| Field | Description |
|---|---|
| `sp_form` | The SP form this branch evolves into |
| `requires_nodes` | Nodes required to unlock this branch |
| `display_name` | Branch name lang key (optional, auto-generated) |

## 4. Adding a brand-new form's evolution: full steps

### A. Evolution tree (pure JSON)

1. Create `routes/<your_form>.json` and write the node tree + branches using the fields above.
2. For each unlockable ability, add a gating condition to that power's JSON:
   ```jsonc
   "condition": { "type": "ssc_addon:has_talent", "talent_id": "your node id" }
   ```
3. Add lang keys (node `name`/`desc`, route `display_name`) in both zh and en files.

!!! danger "Ability gating rule (hard-won lesson)"
    A top-level `has_talent` `condition` only fits abilities that are "determined at transform time."
    **Passive abilities that "only take effect after unlock"** (`apoli:action_on_entity_use` absorb-type, `mana_type_power`, etc.) **must NOT use a top-level condition** — otherwise at transform time it's unlocked=false → power inactive → event handler not registered → it stays broken forever even after unlocking.
    Use an **inner / bientity `actor_condition`** instead (power always active, checks unlock on trigger), or always-register + a client render mixin gate.

### B. The form itself (needs Java + assets, same as any new SP form)

This is **not part of the evolution framework**. Follow existing SP forms:

- Code-register the form in `SscAddon.registerForms()` + a `FormIdentifiers` constant
- `data/<ns>/ssc_form/<id>.json` (with `originLayerID`)
- `assets/<ns>/ssc_form_model/...` (model / texture)
- `data/<ns>/origins/form_<id>.json` (power list)
- Add the origin id to `data/origins/origin_layers/origin.json`
- lang `origin.<ns>.form_<id>.name/.description`

### C. Client entry / UI (still needs manual edits — see next section)

## 5. Java hooks that still need manual edits

The evolution talent client entry points are now all generalized (decided dynamically by the current route), so **adding a new form's evolution no longer requires editing these**:

| Hook | File | Status |
|---|---|---|
| Talent button visibility | `client/evolution/EvolutionBookHook` | ✅ Generalized (shows for any enabled route's start form) |
| Starting form selection | `client/evolution/SscaFormSelectScreen` | ✅ Generalized (auto-lists all enabled routes' start forms) |
| Tree rendering | `client/evolution/EvolutionScreen` | ✅ Generalized (renders the player's current route) |

Only the following **form-specific HUD / narrative** is still hardcoded for the pilot form; a new form needs its own if applicable:

| Hook | File | Notes |
|---|---|---|
| cd / mana bar gating | `SkillCooldownBarRenderer` / mana bar mixin | Hide a form's own cooldown / mana bar before unlock (form-specific HUD) |
| Book narrative | `SscAddonCodexStatusMixin` | The Upgrade Familiar Fox's in-book appearance narrative (form-specific text) |

## 6. Testing and sync

- **Singleplayer**: `/reload` (or re-enter the world) reloads routes.
- **Multiplayer**: the framework auto-syncs route definitions via S2C when a client joins; after changing a route server-side, have clients reconnect or re-trigger sync.
- Admin commands: `/ssc_addon evolution unlock_all|reset [player]`.
