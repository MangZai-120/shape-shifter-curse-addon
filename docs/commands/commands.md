# 可用指令

## /ssc_addon

本模组的主要指令前缀。

### 用法

```
/ssc_addon <子命令> [参数]
```

---

## 需要 OP 2 级权限的指令

| 指令 | 说明 |
| ---- | ---- |
| `/ssc_addon set_mana <玩家> <数值>` | 设置对应玩家的所有法力/能量资源为设定值（超过上限自动取上限），恢复法力就用这个 |
| `/ssc_addon mark_owner <玩家>` | 将执行者标记为所选目标的"所有者"，主要用于"狐火灼烧"等持续性伤害的击杀归属 |
| `/ssc_addon skill <形态> <技能> [玩家]` | 手动触发某形态的技能。形态可选 `snow_fox` / `anubis_wolf` / `allay` / `axolotl` / `wild_cat` / `familiar_fox` / `familiar_fox_red` |
| `/ssc_addon block <玩家> <形态> <技能>` | 封禁某玩家的技能（`unblock` 解封、`list_blocks` 查看封禁列表） |
| `/ssc_addon resistance get / set <值> / add <增量> [玩家]` | 契灵"共鸣抗性"的查询 / 设置 / 增减 |
| `/ssc_addon evolution unlock_all / reset [玩家]` | SSCA 进化加点全解锁 / 重置 |
| `/ssc_addon mancianima_assault reset / lock / status [玩家]` | 契灵"敲钟袭击"每日冷却的重置 / 锁定 / 查询 |
| `/ssc_addon get_book <书籍 ID> [语言]` | 给予剧情书（语言填 `zh_cn` 或 `en_us`） |
| `/ssc_addon list_books [语言]` | 列出所有剧情书 |
| `/ssc_addon reload_books` | 重载剧情书 |
| `/ssc_addon debug form / mana / anim` | 调试当前形态 / 能量条 / 动画日志 |
| `/ssc_addon reload` | 重载 SSCA 配置 |

---

## 无需 OP（普通玩家可用）的指令

| 指令 | 说明 |
| ---- | ---- |
| `/ssc_addon my_whitelist` | 打开自助白名单管理 GUI（只作用于自己） |
| `/ssc_addon palette export` | 导出当前形态配色为分享码 |
| `/ssc_addon palette apply <分享码>` | 应用配色分享码（只作用于自己） |
| `/ssc_addon nova primary / secondary` | 朔望形态的主 / 次技能（一般由技能按键内部调用） |

!!! note "旧指令已移除"
    旧版 `/ssc_addon whitelist add/remove/list/clear` 系列指令已被移除，由 `my_whitelist` 图形界面取代。

---

## 友军标记相关

### 添加友军标记

使用 [友军标记](../items/tools.md#友军标记) 物品对目标玩家右键使用，将其加入白名单。

### 移除友军标记

使用 [消除友军标记](../items/tools.md#消除友军标记) 物品对目标玩家右键使用，将其从白名单中移除。

---

## 物品获取

可使用原版的 `/give` 指令获取本模组物品：

```
/give @p ssc_addon:sp_upgrade_thing 1      # 月髓十字环
/give @p ssc_addon:evolution_stone 1        # 进化石
```
