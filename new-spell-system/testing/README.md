# 新体系验证材料

这些PNG由实际Minecraft客户端渲染，使用`NeoVisualProbe`准备的界面测试数据。它们验证界面布局和文字，不冒充真实多人战斗记录。HTML设计原型的截图仍保存在`ui/previews/`。

- [构式页](screenshots/01-craft.png)
- [观测页](screenshots/02-observe.png)
- [实验与统计](screenshots/03-experiment.png)
- [笔记](screenshots/04-notes.png)
- [320×240逻辑分辨率：身体承载](screenshots/05-narrow-burden.png)
- [320×240逻辑分辨率：元件与制作](screenshots/06-narrow-components.png)

代码检查入口：`neoMagicTest`、`neoMagicIntegrationTest`、`neoMagicWorldTest`、`neoMagicVisualTest`。前三者依次验证纯规则、Fabric/持久化集成，以及真实ServerWorld中的效果和服务端制作权限。最后一项启动独立客户端，截图后检查按钮范围及重叠并退出。

实际测试世界、客户端目录及日志位于`build/neomagic/`，不会打开用户的现有存档。游戏测试客户端使用离线测试身份，因此其认证／Realms警告不代表联网验收通过。

最终检查摘要见[verification.json](verification.json)。人工多人、全部第三方保护插件及长时间平衡验收仍未进行。
