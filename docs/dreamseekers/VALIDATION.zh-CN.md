# 验证与兼容边界

封存的原版运行版本为核心 `1.5.0b-ore-mines.2-dreamseekers-api.65-text-refresh` 与附属 `0.65.0-text-refresh`。**以下是原 text-refresh 发布与文档整理的历史证据，并非 repair1 或 repair2 的验证结果；本次结果见 [repair2 交付说明](REPAIR2.zh-CN.md)。**

## 本次源码整理检查

- 以清理前正式 `API65-text-refresh-source.zip` 为基线，SHA256 为 `712cdb0c4def515fcf04bc3c2ab75b05886097320de07595e96bd83272f0e107`；基线有 3330 个文件，原逐成员清单与 ZIP CRC 校验通过。
- 完整保留 2520 个生产实现/资源成员、778 个测试代码/资源成员及 17 个构建、依赖锁定、许可证等支持文件，共 3315 个成员逐字节不变，其中含 1462 个生产 Java 文件。未发现可确认的历史源码副本；没有为了精简而删除测试、兼容类或迁移逻辑。
- 仅归并历史文档并更新当前说明；检查被移除文档的引用、当前文档链接和 JSON 资源。重新生成 `DISTRIBUTION-MANIFEST.json`，仅覆盖实际包内文件并排除清单自身；源码 ZIP 的 SHA256 在包外记录。
- 不修改配置路径、注册 ID、资源键、Mixin 接线、存档字段或网络协议。`CONFIG_VERSION` 保留 `audit-fixes` 后缀；网络标识仍为 `native-building-snapshot-2`。

## 当前运行版本的既有证据

以下摘自上述封存基线中的 `text-refresh` 发布记录及其所引用的发布证据包，**不是本轮新增通过数，也未在本轮重新执行**：

- 318 份生产 JSON/mcmeta 解析通过；中英文各 680 个资源键、格式占位符与游戏数值保持不变。
- 21 项构建任务、双 JAR 重混淆及生产内容隔离检查通过。JUnit 共 335 项：334 通过、0 失败、1 项真实音频实例验证因环境限制明确跳过。
- 精确生产配对在独立云端合成世界副本中完成两次标准 Forge 服务端启动、正常保存与重启，57 项检查通过；原配置加载与单位身份保留。该范围不代表任意旧世界迁移通过。
- 未重新执行全部历史 GameTest。早期版本的阶段性通过数、旧配对散列和客户端截图不自动作为当前版本的完整验收。

既有生产文件身份（本轮不重建）：

```text
2948f446caa06476f5130e93a64b664385b45352e5077c5e4a0e9bd0a022526c  reignofnether-1.5.0b-ore-mines.2-dreamseekers-api.65-text-refresh.jar
9b441c8d7c7f3aa8447039ddfa29dd56e7331c271e8e94d344b211bfe6f0f204  dreamseekers-0.65.0-text-refresh.jar
```

## 兼容与行为边界

- 只面向锁文件指定的原版 PasterDream `beta0.4.2`（内部版本 `0.3.5.7`），不是 Reborn，也不保证任意后续版本；环境及前置见[安装指南](INSTALL.zh-CN.txt)。
- 旧注册身份、迁移逻辑、默认值回退与未知格式保护保留。旧档缺失的冷却、统计、支付记录或已丢失的施工状态不凭空恢复；活动施法不保存，相关计时不按停服时长补进度。寻梦者未完成训练及收据型研究按现有取消/退款规则处理，不跨重启续跑；它们与已持久化的建筑物理施工队列不同。
- 区块票据仅按已知身份与生命周期处理，不批量清除无法归属的历史孤儿票据。旧数字实体 ID 无法可靠映射为 UUID 时不猜测绑定。
- 双手共契仍为各自生命值、条件化伤害分享与付费新单位替换；不恢复已隔离的旧 Boss、免费召唤或通用旧协议。
- 战争迷雾仍包含客户端视觉遮盖机制，不是完整的抗修改客户端地形保密方案。原生旧生产队列的增量重连同步等未覆盖场景不能由本次文档整理推定修复。

## 尚未完成全面验收

客户端 GUI、中文字体与悬浮提示实机画面、真实音频、真实玩家双客户端联机、村庄视觉生成、广泛旧存档迁移、异常断电一致性、多人长局与完整 PvP 平衡均不在当前完整验收声明内。更新前正常停服，并备份世界、`mods` 和 `config`。

## 修复源码的验证入口

在源码根目录操作。先按[源码 README](../../README.md)准备 JDK 17 与四份锁定依赖；所有 `run-*` 使用独立开发目录，不应指向玩家存档。

```bash
# JUnit；只变清单/源文/断言所读夹具，也会让 test 失效重跑
bash ./gradlew -Pdreamseekers test

# 仅编译 GameTest，不代表已运行任何用例
bash ./gradlew -Pdreamseekers compileGametestJava compileDreamseekersGametestJava

# 检查配对开发专服的有效模组源集、Mixin、前置 classpath；不启动世界
bash ./gradlew -Pdreamseekers verifyDreamseekersDevServer
# 普通专服 + 对应客户端，分两个进程启动；均不使用 GameTest 测试源、测试 Mixin
bash ./gradlew -Pdreamseekers runDreamseekersServer
bash ./gradlew -Pdreamseekers runDreamseekersClient
```

普通配对专服在 `run-dreamseekers-server/`，与客户端、GameTest 及未启用附属的核心 `bash ./gradlew runServer` 隔离。发布验收另行用两个实际重混淆 JAR 和四份前置启动，不能用开发源集替代。

**单一套件与阶段。** 用 `-PresourceTechSuite=<名称>`，可用白名单见 `gradle/gametest-plan.gradle`。每次只选择一个入口，不混用聚焦、旧经济/击杀或重启选择开关。带阶段的套件都用 `prepare`、`verify`；`research_restart` 和 `api65_repair2_cargo` 另需 `verify_settled`；`api65_repair2_survival` 还需按顺序执行 `verify_settled`、`verify_final`；本候选的 `api65_repair3_survival_result` 用 `prepare → verify → verify_terminal`。同组各阶段保留同一 `resourceTechRun` 后缀及核心／配对配置，每阶段用独立 JVM，在上一阶段已验证且正常退出后接续。重开整组用未使用的后缀。

```bash
bash ./gradlew -PresourceTechSuite=api65_editor_worlds runGameTestServer
bash ./gradlew -PresourceTechSuite=api65_repair_play runGameTestServer
bash ./gradlew -Pdreamseekers -PresourceTechSuite=api65_addon_restart -PresourceTechRun=trial1 -PresourceTechPhase=prepare runGameTestServer
bash ./gradlew -Pdreamseekers -PresourceTechSuite=api65_addon_restart -PresourceTechRun=trial1 -PresourceTechPhase=verify runGameTestServer
```

其中携货（附属）须使用 `-Pdreamseekers -PresourceTechSuite=api65_repair2_cargo`；塔防（核心）须使用 `-PresourceTechSuite=api65_repair2_survival`。为组内所有阶段保留各自的 `GAMETEST_VERIFIED` 日志，不将单阶段通过视作整组通过。旧附属定向入口若传 `dreamseekersTestCase`，还须指定相应的 `dreamseekersTestBatch`。源码里的历史测试类不一定在现有入口中，不能把它们计入已运行用例。

API65 入口按本次 JVM 的控制台核对**唯一实际命名空间、完成数、通过数**与白名单期望；必须非零且相同，缺证即失败。没有 `GAMETEST_VERIFIED` 不算该套件通过。其他历史套件仍须独立核对实际选择、非零完成数与退出结果。

复核 JUnit 增量输入时，请在临时源码副本中先连续运行两次同一条 `test` 命令，确认第二次 `UP-TO-DATE`；只把 `addons/dreamseekers/economy_manifest.json` 的 `mine.cost.wood` 从 `300` 改为 `299`，原命令必须重新执行并失败；恢复 `300` 后必须重新执行并转绿。不要使用 `--rerun-tasks` 或清缓存掩盖失效问题。
