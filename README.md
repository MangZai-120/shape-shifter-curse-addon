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

>The beta version of this mod may be developed based on the dev build of the original SSC mod, so please make sure you have installed the corresponding version of the original SSC mod before playing, otherwise you may encounter compatibility issues or be unable to play properly. Download from the Releases section on the right, and please confirm that the version you download matches the version of the original SSC mod you have installed.
>这个模组的beta版可能会使用SSC原版的测试版为基础进行开发，所以请在游玩前确保你已经安装了对应版本的SSC原版模组，否则可能会遇到兼容性问题或者无法正常游玩。下载地点在右侧Releases(发行作品)里，下载前请确认你下载的版本和你安装的SSC原版模组版本一致。

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

## Q/A
- Q:这个模组和Xu233的模组能不能共用？
- A:可以共用，因为Xu233的模组写的很标准，完全可以和这个模组共用。

- Q:这个模组的各个形态感觉没有比原版的形态强啊，而且玩法也差不多啊，感觉没什么意义了。
- A:这个附属的目标是在不破坏原模组的游玩体验下去添加一些属于我自己的想法，所以我不希望扩展形态的玩法比原ssc的形态差很多，我希望玩家们能更容易的去上手游玩，就和玩先前的形态一样。

- Q:这个模组的故事感觉有点鸡汤啊，感觉不太适合放在游戏里啊。
- A:这个模组的故事内容是我个人的创作，虽然可能有些鸡汤，但我觉得它们能为这个模组增添一些独特的氛围和情感色彩，虽然可能不适合每个人的口味，但我希望它能为喜欢这种风格的玩家提供一些有趣的故事体验，并且让喜欢剧情的玩家能更好的沉浸在这个模组的世界观里。

- Q:这个模组还会继续更新么？
- A:会的兄弟，会的。只是在我上学期间这个模组的更新频率会变得比寒暑假慢一些。

- Q:这个模组会不会收费啊？
- A:不会的兄弟，这个模组是完全免费的，我也不希望它被任何人商业使用或垄断。但或许我会开个赞助，让愿意用资金支持我的玩家有个途径来支持我，但这完全是自愿的，模组本体和所有内容都将永远免费提供给所有人。

- Q:这个模组会不会有和原版不一样的特殊形态啊？
- A:会的兄弟，但这个短期内可能做不出来，因为我的精力现在全用到了ssca和另一个饰品桥模组上了，但这个模组未来大概率会出几个专属于ssca的彩蛋形态，里面可能会有其它游戏的内容，也可能会有一些原创的内容，敬请期待吧。

- Q:这个模组和Xu233的模组有什么区别啊？
- A:这个模组和Xu233的模组是两个独立的模组，虽然这两个模组都是ssc的扩展，但是玩法和注重点完全不一样。这个模组的重点是添加一些新的形态和技能，提供更多样化的游玩体验，同时也加入了一些原创的故事内容；而Xu233的模组则更注重于优化原版的形态和技能，提升游戏的平衡性和可玩性，同时也加入了一些新的机制和功能。

- Q:我可以把它用在我的服务器内么？
- A:可以的兄弟，我很欢迎，你只要不要靠它来盈利就行。

