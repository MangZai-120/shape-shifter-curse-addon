# 独立新法术系统

本目录属于 SSCA 的 `neomagic` 试验田，原项目 `main` 独立维护。当前版本为 `8.0.0-neomagic.13`。

游戏中的书、卷轴、导引杖统一使用崩解度 C。形态稳定度 S 表示安全阈值，法术书原容量和回蓝加成分别转换成额外 S 和崩解度恢复加成；原伤害、冷却、符文、法阵、书经验和三槽选择保留。

失败按起手 `C＋实际Q` 抽一次，抽中后在不可打断界限前随机一刻失败；偏移仍每10 tick实时检查。逐步支付和成功施法熟练度沿用已确认机制。

## 游戏界面

稳定度条使用玩家确认的玻璃外框与修改后的左侧 PNG，独立于形态蓝条和法术书定位。Mod Menu 中打开“HUD位置与显示”，点击稳定度条或其它 HUD 后，只显示该目标的锚点、X/Y与显隐设置；点击空白处收起调整框。

左上详细面板名为“崩解度debug条”，在客户端配置页单独开关。

## 保留在版本库中的内容

- `src/main/`：运行代码、数据和最终游戏素材。
- `src/test/`：有效回归测试及测试专用原图夹具。
- `docs/`：现行完整系统设计、UI说明和书加成映射。
- `tools/check_resources.py`：资源与文档链接校验。

新增 HUD 代码位于 `net.jackcooper.shapeShifterCurseAddon.client.hud`；原有新法术核心仍在 `net.ssca.neomagic`，数据命名空间保持 `ssca_neomagic`。

## 现行设计

- [完整系统设计V2](docs/SSCA新法术系统完整设计V2.md)
- [研究界面与HUD](docs/UI设计说明V2.md)
- [法术书接管与加成映射](docs/旧法术书接管与加成映射.md)

## 本地材料与构建

历史方案、美术过程文件、HTML原型、截图、测试报告和停用代码分类保存在仓库根 `.local-dev/`，目录内及根忽略规则均阻止它们进入提交。分类索引为 `.local-dev/分类索引.json`。这些材料保留在本机，不作为模组资源打包。

使用 Java 17 和仓库 wrapper（Gradle 8.11.1）：

```powershell
.\gradlew.bat --offline build
.\gradlew.bat --offline neoMagicVisualTest
.\gradlew.bat --offline neoMagicWorldTest -PneoBookCutover=true
```

客户端原生检查和临时测试世界位于 `build/neomagic/`；需保留的证据归档到 `.local-dev/verification/`。自动化、引擎夹具和真人多人验收分别记录，不混称。

Git 提交和 push 由用户手动执行。
