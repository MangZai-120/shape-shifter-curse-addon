# 幻形者诅咒附属模组

> **📖 玩法指南 / Gameplay Guide**  
> **THIS MOD IS FREE FOR ALL,DON'T TRUST ANYONE WHO CLAIMS TO SELL THIS MOD.**
> 
> **本模组免费提供给所有人使用，请不要相信任何声称出售此模组的人。**
>
>Please enable particle display in the game settings, otherwise the display of skills in the game will have issues.
> 请在游戏设置中开启粒子显示，否则游戏内技能的显示会有问题。
>
>For a more accurate and detailed guide, please refer to the [MC百科](https://www.mcmod.cn/class/24327.html). ps
>更准确详细的教程请在[MC百科](https://www.mcmod.cn/class/24327.html)内查看

这是一个基于 Fabric 的附属模组项目，用于为《幻形者诅咒》模组添加更多玩法。

### Wiki：[幻形者诅咒扩展包 Wiki](https://shape-shifter-curse-addon.readthedocs.io/zh-cn/latest/)

## 临时教程
为了beta版的测试和体验，以下是一些临时的粗制教程，后续会在Wiki和MC百科内进行更详细的教程编写：
>金沙岚技能介绍
>被动————侵蚀烙印：
>近战攻击自动为目标叠加侵蚀烙印，每秒最多叠1层，最高3层，持续10秒（每次叠加刷新计时），不同层数会用不同的颜色标记对方。被标记的生物会发出对应颜色的荧光（32格可视范围），方便你追踪目标。当被动叠满时，下次攻击会附加其生命值10%的额外魔法伤害（伤害上限30点），并且消除自身的所有侵蚀烙印层数。
>
>主动技能键————凋零金沙：
>蓄力1秒后释放 15格AoE 金沙风暴
>对范围内敌对生物施加3秒的沙盲效果
>为所有受影响目标 叠加1层侵蚀烙印
>蓄力期间 减速50%
>受到伤害打断蓄力 → 进入 7秒CD
>正常释放 → 26秒CD
>
>次要技能键————引爆标记：
>引爆所有处于非绿色状态的侵蚀烙印
>每层造成 5点物理伤害（3层 = 15点）
>自身每层回复 10%最大生命值（3层 = 30%）
>3层满的目标先触发被动爆发再引爆（双重伤害）
>没有可引爆目标时不进入CD
>CD：10秒

## 添加模组

- 将下载后的.jar文件放入游戏的 `mods` 文件夹，并确保你下载的是.jar格式的模组文件，而不是源代码。下载请到Releases(发行作品)里去找。

## 注意事项

- 本项目是一个独立的 Fabric 模组，构建后生成的 jar 文件应放入游戏 `mods` 文件夹，与《幻形者诅咒》主模组一起运行。
- 本模组的文本内容（如技能描述、形态介绍等）默认使用中文，英文文本可能不完整或不准确，请以中文文本为准。

## 致谢名单

-  Onixary 如果不是他，那这个模组将会永不存在，感谢他提供的源代码以及帮助。 
-  wuhenqiubai 感谢他为我的模组进行bug修复、代码优化以及帮助。
-  xu233333 感谢他为我的模组进行bug修复以及帮助。
-  以及所有为这个模组提供过帮助的人们，包括但不限于游玩、推荐、提供bug反馈、测试、建议等的玩家们，感谢你游玩我的模组。

## 许可协议 / License

- **代码部分**：采用 [MIT License](LICENSE) 进行许可。
- **故事内容**（包括 `story/` 目录、游戏内书籍、Codex 叙事文本）：采用 [CC BY-NC-ND 4.0](https://creativecommons.org/licenses/by-nc-nd/4.0/) 进行许可。
  - 可自由转发，不得商用，不得修改内容。文本须按原样提供，但允许更改字体和字号。

- **Code**: Licensed under [MIT License](LICENSE).
- **Story Content** (including `story/` directory, in-game books, and Codex narrative text): Licensed under [CC BY-NC-ND 4.0](https://creativecommons.org/licenses/by-nc-nd/4.0/).
  - Free to share, no commercial use, no modifications. Text must be provided as-is, but font and font size changes are permitted.
