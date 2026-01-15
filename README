# 幻形者诅咒附属模组

这是一个基于 Fabric 的附属模组项目，用于为《幻形者诅咒》模组添加新形态。

## 目录结构说明

本项目完全遵循《幻形者诅咒》的扩展教程，所有文件均位于 `src/main/resources` 下。

### 资源包 (Resource Pack)
位于 `src/main/resources/assets/`：
- `my_addon/lang/`: 语言文件
- `my_addon/player_animation/`: 玩家动画文件 (.json)
- `orif-defaults/furs/`: 形态模型定义文件
- `orif-defaults/geo/`: 模型几何文件 (.geo.json - 请自行放置)
- `orif-defaults/textures/`: 纹理文件 (.png - 请自行放置)

### 数据包 (Data Pack)
位于 `src/main/resources/data/`：
- `my_addon/ssc_form/`: 形态定义文件
- `my_addon/origins/`: 起源能力定义
- `my_addon/powers/`: 具体能力文件
- `my_addon/origins_power_extra/`: 额外的能力挂载（用于给现有形态添加变形能力）
- `origins/origin_layers/`: 注册新起源

## 示例文件

已为您创建了一套名为 `example` (ID: `my_addon:example`) 的完整示例文件：
1. `assets/my_addon/lang/zh_cn.json`: 本地化文本
2. `assets/orif-defaults/furs/my_addon.form_example.json`: 模型引用配置
3. `data/my_addon/ssc_form/example.json`: 核心形态定义
4. `data/my_addon/origins/form_example.json`: 形态起源定义
5. `data/my_addon/powers/example_scale.json`: 缩放能力
6. `data/my_addon/powers/to_example_form.json`: 变形道具能力（使用木棍变形）
7. `data/my_addon/origins_power_extra/append_transform.json`: 将变形能力添加到初始形态

## 如何开始

1. **添加模型和纹理**：
   请将您的 Blockbench 导出文件放入：
   - 模型: `src/main/resources/assets/orif-defaults/geo/`
   - 纹理: `src/main/resources/assets/orif-defaults/textures/`
   并修改 `assets/orif-defaults/furs/my_addon.form_example.json` 指向正确的文件名。

2. **构建模组**：
   在本项目根目录下运行 `gradlew build` 即可生成的 jar 文件（位于 `build/libs`）。

## 注意事项

- 本项目是一个独立的 Fabric 模组，构建后生成的 jar 文件应放入游戏 `mods` 文件夹，与《幻形者诅咒》主模组一起运行。
- 请勿修改父目录中的任何文件。
