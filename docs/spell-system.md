# 法术系统

法术系统与附属其余功能共用标准源码、测试目录，按职责归入 `net.jackcooper.shapeShifterCurseAddon` 的现有包。

- [核心源码](../src/main/java/net/jackcooper/shapeShifterCurseAddon/spell)
- [网络同步](../src/main/java/net/jackcooper/shapeShifterCurseAddon/network)
- [客户端](../src/main/java/net/jackcooper/shapeShifterCurseAddon/client)
- [默认规则](../src/main/resources/data/ssc_addon/rules/defaults.json)
- [测试与探针](../src/test/java/net/jackcooper/shapeShifterCurseAddon)
- [资源检查脚本](../tools/check_resources.py)

自 8.0.1-dev.99 起，附属自有资源、注册 ID 和网络通道统一使用 `ssc_addon`，联机双方需使用兼容新版。首次打开旧世界时会读取旧形态、能力和法术记录。自定义资源包和数据包可用 `python tools/migrate_namespace_pack.py 旧包.zip 新包.zip` 生成新版副本，再启用新包。包内结构 NBT 和自定义脚本中写死的旧 ID 需手动更新。

转换器会同步更新包描述中的翻译键、字体和精确匹配旧形态模型文件名的过滤规则。如果旧规则只屏蔽即将合并的某个命名空间，或使用了难以自动改写的旧模型文件名正则，工具会指出对应的 `pack.mcmeta` 规则，供整合包作者调整后重新转换。

从 8.0.1-dev.101 起，启用转换好的数据包后再打开旧世界，玩家在自定义 Origin 层中的选择、能力来源，以及已经生成的 Apoli 装备上的能力都会按当前注册表读取。新旧 ID 同时有定义时保留原 ID，只有新 ID 有定义时使用新 ID。

当前检出版本未包含完整机制、UI 设计和原书加成映射的历史设计文档；当前行为以以上源码与实际验证为准。

在仓库根目录使用 Java 17 执行 `gradlew.bat build`。资源和文档可分别执行 `python tools/check_resources.py resources`、`python tools/check_resources.py docs`，或使用 `all` 同时检查。

资源检查默认使用 `GRADLE_USER_HOME` 下的 Minecraft 客户端缓存；未设置时使用用户目录下的 `.gradle`。使用其它缓存或 Gradle `-g` 参数时，可通过 `--minecraft-client PATH` 显式指定客户端 JAR。

构建后可执行 `python tools/check_resources.py all --addon-jar <附属JAR路径>`，逐项核对正式包中 `assets/` 和 `data/` 的文件路径与内容。CI 在 Gradle 构建完成后执行同一检查。
