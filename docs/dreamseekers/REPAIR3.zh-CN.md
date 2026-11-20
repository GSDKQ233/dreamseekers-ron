# API65 repair3 修复候选

**仅源码，不是可安装版本。** 基准是资料库的 `API65-repair2-source.zip`，SHA-256 为
`86dd30ac22ff1ff33ed2ccfee48f994e4f17a77f56f1fa47b1b1758913d03150`。未使用 GitHub 分支作为基准，未覆盖资料库的 repair2，也未修改玩家存档。

候选核心 `1.5.0b-ore-mines.2-dreamseekers-api.65-repair3` 与附属 `0.65.0-repair3` 必须精确配对；禁止与 repair2 混装。正常构建仍使用 Minecraft 1.20.1、Forge 47.4.0、JDK 17 和同一份四项依赖锁。已有安装版仍以此前独立验收的 repair2 交付为准。

## 构建与回归入口

此包没有生产 JAR。配对 JAR 的发行验收须先按根 README 准备锁定依赖，再用同一套 Java 17：

```bash
bash ./gradlew -Pdreamseekers test compileGametestJava compileDreamseekersGametestJava
bash ./gradlew -Pdreamseekers build dreamseekersJar verifyDreamseekersJar verifyDreamseekersCoreJar
```

成品核验通过 Python 3.11+ 读取两份实际 JAR 中的 TOML，核对版本和强制核心依赖，不以源码描述符替代成品。复跑测试的世界必须独立；只有整组所有阶段成功才算重启回归通过。已有 repair2 结果不是本候选的测试结果。

## 改动与剩余风险

下表的编号对应十轮复审报告。“已改”表示候选源码已变更，不代表 Forge 中的联机、重启、GUI 或旧存档已验收。

| 审计范围 | 已改的行为 |
| --- | --- |
| A01–A04、G02 | 临时离场不淘汰玩家，并保留未加载单位证据；锁独立于名单持久化；实体起始方块只改其所属地图席位；倒计时以冻结席位批量预检，失败不发开局成功；重置保留席位位置但清报名和准备状态。 |
| A05–A07 | 撤回指定出站邀请；重连定向还原外交视图，避免冲掉其他人的本地邀请状态；同步已占用场景角色的编号。 |
| A08、G01、G03、G05 | 未知建筑存档和扩展字段保留；场景发布通过服务端按可见性同步实际建筑、单位归属；显形由持久计时与当前主城状态推导；沙盒／场景重置的保图行为在客户端和帮助中统一，不默认增加建筑删除。 |
| B01–B04、B08 | 有账可记才消费有效投箱物；平民传送门保留不接收的绿宝石，货栈继续正常入账；零价值物和物品标签不会因收取、拾取或同一实体的无关数据更新丢失；失效交货点可在本维选择替代；墓地拒绝实体生成时保留头颅库存。 |
| B05–B07、B09–B10 | 生产权限绑定发令者和目标；撤单只匹配观察到的队列项或收据；服务器暂停科研时，客户端投影同步停滞；自定义模板按配方持有；原生室内出生搜索限制高度步数。 |
| C01–C04 | 不符合联动交战规则的中立对象不再抢占合法敌军或建筑；破风突击从起手到命中按发令者核查隐身与战争迷雾；场景 AI 就近目标按同一维筛选；晚序被取消的死亡不清理持续效果。 |
| C06–C08、C10–C11 | 经典淘汰判胜与塔防分离；塔防终局持久化，信标胜利后的自由游玩不再发计分波或奖励；零实际发布不推进新波；旁观登录无队伍胜负、联盟副作用；奖励实体被否决时在比赛中保留原席待领奖励，终局不继续派发未兑现奖励。 |
| D01–D05 | 训练本地预测用于排序，不拦截可送达服务端的合法大厅请求；鼠标使用当前按下事件取样；技能状态区分等待同步、就绪和真实冷却；重复点击同一契约／遭遇键退出选点；补上原核心英语有而简中缺失的 39 个键。 |
| F01–F05 | 拒绝缺少批次的旧案例选择，补三个确实受本轮影响的历史套件入口；补携货／塔防重启阶段及候选文档；核查实际 JAR 描述符，并在构建前复核四个本地原始依赖哈希；修正 4 种头颅地面模型匹配及墙面模型链接。 |

A04 的预检防止普通拒绝造成半开局；不承诺回滚第三方在生成或建筑回调内执行的外部世界副作用。C11 以正常关服重启为验证目标，不承诺跨世界／实体与账本写盘之间的断电原子性。未知建筑与已恢复建筑在同一原点冲突时，原条目另行保留以便备份恢复，不覆盖活动建筑。

**未作为完成项：** C05（原生英雄在途经验球）、C09（波点共享预算）、E01–E05（需先对真实负载基准测量）、G04（UUID 已绑定但原生控制／显示仍受改名影响）、F01 未纳入的其余历史测试组。不新开玩法、不调阵营平衡、不自动推送或发布。

### 独立 Forge 测试建议（本次未执行）

使用新的运行目录后缀，按下列配置补验。带阶段者同组用同一后缀，必须按列出的顺序各启动一次并正常停止。

| 套件 | 所需配置／阶段 |
| --- | --- |
| `api65_repair3_scenario` | 核心；3 个直接声明的测试 |
| `api65_repair3_survival_result` | 核心；`prepare → verify → verify_terminal` |
| `api65_repair2_survival` | 核心；`prepare → verify → verify_settled → verify_final`，追加重叠波次回归 |
| `api65_repair2_lobby`、`api65_units`、`api65_auth_extended`、`api60_results_data`、`api65_repair_play`、`api63_native_production`、`api65_unit_dimensions` | 核心；单阶段 |
| `api60_results_restart` | 核心；`prepare → verify`，复测明确锁定状态跨重启 |
| `api65_repair2_cargo` | 配对；`prepare → verify → verify_settled` |
| `aaroncos_skills_fog`、`jelly_flow` | 配对；单阶段 |
| `dreamseekersShellFunctionsFocused`、`dreamseekersSlimeFocused`、`dreamseekersWindDashFocused` | 配对；旧聚焦入口，核对实际运行数量和退出结果 |

例：`bash ./gradlew -PresourceTechSuite=api65_repair3_survival_result -PresourceTechRun=repair3trial1 -PresourceTechPhase=prepare runGameTestServer`。配对入口另加 `-Pdreamseekers`。之后在两份匹配成品上复测专服启动、两个客户端开局／重连、中文反馈、技能与 HUD 点击，再决定是否发行 JAR。

## 实际验证

云端的 Gradle Wrapper 访问官方分发站时返回 `Network is unreachable`，未能进入 Gradle 或 Forge；没有重新运行全量 JUnit、真正的 Forge GameTest、成品构建、专服、双客户端 GUI 或玩家存档。以下离线 Java 检查使用 JDK 21 的编译模块和 `-source 17 -target 17`，不是带 Java 17 标准库的 `--release 17`。

- 生产／科研 7,183 条断言（多数用于验证有界出生坐标搜索）；货运 40 条；兼容玩法方法 216 条。探针使用轻量替身，不包含 Minecraft 运行时。
- 显形和重置策略的 173 条断言通过，未知建筑数据以存档替身往返核对 35 条断言，场景发布以无客户端类的服务端替身核对 14 条断言，训练大厅选择的 7 个场景通过。塔防游标／计分、席位辅助逻辑、目标点击和技能状态另外跑了隔离方法测试；整套生产 JSON／资源链接做了静态核验。
- 成品描述符核验工具以合成 JAR 执行 7 项检查，验证精确版本并拒绝错误版本、宽松依赖、重复记录及不完整文件；真实配对 JAR 仍未构建。
- 测试选择器中的声明计数不是实测通过数；完整候选源码包逐成员核对字节数、SHA-256 和 ZIP CRC。
