# 进化加点 · 数据驱动加形态

> 本页面向**开发者 / 整合包作者**，讲解 SSCA「永久形态进化加点系统」的数据驱动框架：如何用 JSON 设计一个形态的整套进化加点，以及加一个全新形态进化时你需要做哪些事。

## 一、它能做什么

进化加点的「**进化树**」已完全数据化：一个形态的**节点布局、解锁消耗、前置关系、分支、图标、发点等级、自动分支等级**全部写在一个 JSON 文件里，框架自动完成：

- 加载（datapack reload 扫描）
- 单机 / 多人同步
- 经验等级发放升级点
- 节点解锁 / 前置校验 / 扣点
- 进化树界面渲染
- 形态识别（变成起点形态自动进入路线）
- 继续进化门控（解锁全部分支节点才能用月髓环 / 进化石进化）

!!! note "文件位置"
    route 文件放在 `src/main/resources/data/<命名空间>/ssca_evolution/routes/<route_id>.json`。
    **文件名 = route id**。下划线 `_` 开头的文件会被跳过（可放占位 / 注释样例）。
    可参考随附的模板 `routes/example.json`。

## 二、模块化边界（务必先看）

| 层 | 是否纯 JSON | 说明 |
|---|---|---|
| 进化树定义（节点 / 消耗 / 前置 / 分支 / 发点等级） | ✅ 纯 JSON | 写 route JSON 即可 |
| 服务端逻辑（发点 / 解锁 / 形态识别 / 进化门控） | ✅ 已泛化 | 框架按 route 数据处理，无需改 Java |
| 能力门控（解锁后能力才生效） | ✅ 数据 | power JSON 挂 `ssc_addon:has_talent` 条件 |
| 多人同步 | ✅ 自动 | 框架 S2C 同步 route 定义到客户端 |
| **形态本体**（外观 / 模型 / 能力 power 注册） | ❌ 需 Java + 资源 | 与普通新增形态一样，**不属于进化框架** |
| **客户端入口 / UI 触点** | ⚠️ 当前仍绑 familiar_fox | 见下方「四、当前仍需手改的 Java 触点」 |

!!! warning "结论"
    **进化树这一层已经是纯 JSON**；但「让一个**全新形态**接入进化系统」还需要做形态本体，以及改几处客户端入口代码（目前硬编码了试点形态「进化使魔」）。

## 三、route JSON 字段

```jsonc
{
  "enabled": true,                                  // 是否开放（false 则入口不显示）
  "display_name": "evolution.my_addon.fox.name",    // 路线名 lang key（可省略，自动生成）
  "start_form": "my_addon:upgrade_familiar_fox",    // 进入该路线对应的形态 id
  "base_node": "familiar_fox_base",                 // 初始自动解锁节点（可省略=首个 auto_unlock 主线节点）
  "level_milestones": [5, 10, 15, 20, 30, 40, 45],  // 到达这些经验等级各发 1 点
  "auto_branch_level": 50,                          // 到该等级自动解锁分支节点
  "nodes": [ /* 见下 */ ],
  "branches": { /* 见下 */ }
}
```

### 节点 `nodes[]`

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `id` | ✅ | — | 节点唯一 id（= 能力门控 `has_talent` 用的 talent id） |
| `icon` | | `minecraft:barrier` | 图标物品 id（零美术，用原版 / mod 物品） |
| `cost` | | `1` | 解锁消耗点数 |
| `prereqs` | | `[]` | 前置节点 id（**全部**解锁才满足，AND） |
| `col` / `row` | | `0` | 进化树布局列 / 行 |
| `auto_unlock` | | `false` | true = 满足前置即自动解锁、不消耗点数（如初始节点 / 分支节点） |
| `branch` | | `""` | 分支标记（`""`=主线；非空=该分支专属节点） |
| `name` / `desc` | | 自动 | lang key，省略则自动用 `evolution.my_addon.<route>.node.<id>.name/.desc` |
| `grants_powers` | | `[]` | 该节点关联的 power id（元数据，便于查阅；门控仍靠 power 自身的 has_talent） |

### 分支 `branches{}`

```jsonc
"branches": {
  "spirit_lord": { "sp_form": "my_addon:familiar_fox_sp",         "requires_nodes": ["fire_ring"] },
  "mancianima":  { "sp_form": "my_addon:familiar_fox_mancianima", "requires_nodes": ["fire_ring"] }
}
```

| 字段 | 说明 |
|---|---|
| `sp_form` | 该分支融合到的 SP 形态 id |
| `requires_nodes` | 解锁该分支的前置节点 |
| `display_name` | 分支名 lang key（可省略，自动生成） |

## 四、加一个全新形态进化：完整步骤

### A. 进化树（纯 JSON）

1. 新建 `routes/<你的形态>.json`，按上面字段写好节点树与分支。
2. 给每个「可解锁能力」对应的 power JSON 挂门控条件：
   ```jsonc
   "condition": { "type": "ssc_addon:has_talent", "talent_id": "你的节点 id" }
   ```
3. 补 lang 键（节点 `name`/`desc`、路线 `display_name`），中英文件都加。

!!! danger "能力门控铁律（踩坑总结）"
    `has_talent` 顶层 `condition` 只适合「变身时即确定」的能力。
    **「解锁后才生效」的被动能力**（`apoli:action_on_entity_use` 吸附类、`mana_type_power` 等）**绝不能用顶层 condition** —— 否则变身时未解锁→power 未激活→事件 handler 不注册→以后解锁也永久失效。
    应改用 **inner / bientity 的 `actor_condition`**（power 始终激活，触发时才查解锁），或始终注册 + 客户端渲染 mixin 门控。

### B. 形态本体（需 Java + 资源，与普通新增 SP 形态一致）

这部分**不属于进化框架**，参考现有 SP 形态做法：

- `SscAddon.registerForms()` 代码注册形态 + `FormIdentifiers` 常量
- `data/<ns>/ssc_form/<id>.json`（带 `originLayerID`）
- `assets/<ns>/ssc_form_model/...`（模型 / 贴图）
- `data/<ns>/origins/form_<id>.json`（power 列表）
- `data/origins/origin_layers/origin.json` 加该 origin id
- lang `origin.<ns>.form_<id>.name/.description`

### C. 客户端入口 / UI（当前仍需手改，见下节）

## 五、当前仍需手改的 Java 触点

进化加点的客户端入口已全部泛化（按当前 route 动态判断），**加新形态进化无需再改这些**：

| 触点 | 文件 | 状态 |
|---|---|---|
| 进化加点按钮显示条件 | `client/evolution/EvolutionBookHook` | ✅ 已泛化（任意 enabled route 的起点形态都显示） |
| 开局选形态 | `client/evolution/SscaFormSelectScreen` | ✅ 已泛化（自动列出所有 enabled route 的起点形态） |
| 进化树渲染 | `client/evolution/EvolutionScreen` | ✅ 已泛化（按玩家当前 route 渲染） |

仅以下**形态特定的 HUD / 叙述**仍按试点形态硬编码，新形态如有这类需求需另配：

| 触点 | 文件 | 说明 |
|---|---|---|
| cd / mana 条门控 | `SkillCooldownBarRenderer` / mana 条 mixin | 形态自带的冷却条 / 法力条的解锁前隐藏（形态特定 HUD） |
| 书内叙述 | `SscAddonCodexStatusMixin` | 进化使魔书内 appearance 段的加点叙述（形态特定文案） |

## 六、测试与同步

- **单机**：`/reload`（或重进世界）即重新加载 routes。
- **多人**：客户端进服后框架自动经 S2C 同步 route 定义；服务端改了 route 需让客户端重连或重新触发同步。
- 管理指令：`/ssc_addon evolution unlock_all|reset [玩家]`。
