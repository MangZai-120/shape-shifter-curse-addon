# 新法术系统

新法术系统已与附属其余功能统一到主 `src`，Gradle 使用标准源码目录。

- [核心源码](../../src/main/java/net/ssca/neomagic)
- [法术与形态衔接](../../src/main/java/net/jackcooper/shapeShifterCurseAddon/spell)
- [默认规则](../../src/main/resources/data/ssca_neomagic/rules/defaults.json)
- [测试与探针](../../src/test/java/net/ssca/neomagic)
- [资源检查脚本](../../tools/check_resources.py)

当前检出版本未包含完整机制、UI 设计和原书加成映射的历史设计文档；当前行为以以上源码与实际验证为准。

在仓库根目录使用 Java 17 执行 `gradlew.bat build`。资源和文档可分别执行 `python tools/check_resources.py resources`、`python tools/check_resources.py docs`，或使用 `all` 同时检查。

资源检查默认使用 `GRADLE_USER_HOME` 下的 Minecraft 客户端缓存；未设置时使用用户目录下的 `.gradle`。使用其它缓存或 Gradle `-g` 参数时，可通过 `--minecraft-client PATH` 显式指定客户端 JAR。
