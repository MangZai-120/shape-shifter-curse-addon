# 新法术系统

新法术系统已与附属其余功能统一到主 `src`，Gradle 使用标准源码目录。

- [核心源码](../../src/main/java/net/ssca/neomagic)
- [法术与形态衔接](../../src/main/java/net/jackcooper/shapeShifterCurseAddon/spell)
- [默认规则](../../src/main/resources/data/ssca_neomagic/rules/defaults.json)
- [测试与探针](../../src/test/java/net/ssca/neomagic)
- [资源检查脚本](../../tools/check_resources.py)

设计资料保留在此目录：[完整机制](SSCA新法术系统完整设计V2.md)、[UI 设计](UI设计说明V2.md)、[原书加成映射](旧法术书接管与加成映射.md)。其中历史版本号和测试记录代表文档撰写时的状态，当前行为以源码与实际验证为准。

在仓库根目录使用 Java 17 执行 `gradlew.bat build`。资源和文档可分别执行 `python tools/check_resources.py resources`、`python tools/check_resources.py docs`，或使用 `all` 同时检查。
