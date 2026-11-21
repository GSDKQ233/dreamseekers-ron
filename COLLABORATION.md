# 寻梦者协作板

这份板用于交接工作和记录可验证结果。仓库是开发中源码，不是稳定 API 或已发布的安装包。
技术依据见 [repair3.1 验收说明](docs/dreamseekers/REPAIR3.1.zh-CN.md)；玩法背景见 [README](README.md)。

## 当前基准

- 开发候选：API65 repair3.1。核心 `1.5.0b-ore-mines.2-dreamseekers-api.65-repair3.1`，附属 `0.65.0-repair3.1`，两端必须成对同版。环境：Minecraft 1.20.1、Forge 47.4.0、Java 17。前置按 [依赖锁](addons/dreamseekers/dependencies.lock.json) 的四个精确版本及哈希取得。
- 来源：以 API65 repair3 源码 ZIP 为修复基准（SHA-256 `226856fca5f3e9eb7da58f1e190e8863dbba77036480d59a4149c9bf71ea5ef2`）。Git 以既有公开分支为父提交；不是用旧分支覆盖本候选源码。
- 已隔离生产队列包的客户端处理。波斯语保留，不强制补译或按词条数验收。继续验资源解析、简中和默认英文回退。
- 不在此仓库提交第三方依赖 JAR、私有日志、账号令牌或玩家存档。

## 验收状态

| 工作项 | 状态 | 证据与边界 |
| --- | --- | --- |
| Java 17 构建与配对 | 通过 | 核心和附属两套 Forge 测试源码编译；实际 JAR 均通过版本与内容检查。 |
| JUnit | 通过 | 368 通过，1 个既有音频测试跳过，零失败。 |
| 选定 Forge 回归 | 通过 | 19 次独立进程，63 个必需测试或阶段通过。原生英雄夹具改用真实建造路径的区块票据，修正在测试中，生产 JAR 不变。 |
| 普通专服 | 通过 | 新世界与 repair2 合成副本干净停服及重启；非空开放名单持久化。没有真实玩家加入，不能以此证明重启后新玩家可入席。 |
| 图形与语言 | 部分通过 | 两个开发客户端分别渲染主界面，验证简中和缺译英文回退。离线预演补看了局内渲染、简中 HUD、波斯语下的英文回退和 8 种头颅方块，见下节；未逐键核对。 |
| 文字描述与本地化核查 | 已改，待审 | 中英文各 690 条、键一致；带参数文本占位符一致；技能、兵种、建筑、世界树、科技数值与源码对照无出入。仅统一用词与名称（英文名按中文对齐）、修 1 处笔误、补晶台与守望塔说明，未增删键、未改代码。只做了脚本校验，未进游戏实测显示。 |
| 正式配对 JAR 双客户端联机 | 延后，未验 | 公开源码不代表此项通过。需两个独立、可联机的客户端并保留正常认证。离线预演见下两节（含同一对重混淆 JAR 的专服加两个正式版客户端），因使用离线用户名、没有正常认证，不计作本项通过。 |
| 后续玩法审阅 | 两项已修，一项待定 | 见下方“后续玩法审阅结果”。在途经验与波点预算已修并有测试；同 UUID 改名确认为缺陷，本候选不改。 |
| 正式发行与真实存档 | 未验 | 不发布 JAR，不使用任何正式存档做验证。 |
| 构建占位值与旧附属测试开关清理 | 通过 | 提交 `e676559`。核心与配对构建、JUnit 复跑通过，见下方“构建清理记录”。不改玩法，未重跑 19 次选定 Forge 回归。 |
| 界面与 HUD 核查（Devin 界面会话） | 已修复，待审 | 修复 11 处显示/操作缺陷：多处提示框不换行超出屏幕、建造提示“人口 0”、技能秒数多余小数、冷却角标无单位、建造菜单缺快捷键、多选建筑显示语言键等。JUnit 380 通过、1 个既有跳过、零失败。已在开发客户端（1920×1080）实机核对换行、快捷键、人口行、秒数和多选名称。 |
| 兵种实现核查（Devin） | 修复待审，未合并 | 基于 `30133d7` 逐项核对 23 个兵种的注册、属性、阵营、生产、技能、存档与渲染器，无空壳兵种。修复：甲虫与蜗牛补 `getHudIcon()`；甲虫、蜗牛、萤火虫、旧小石精禁止拴绳；前置实体校验补 12 个 ID；新增 `UnitHudIconLeashGameTests`。配对构建、JAR 校验、JUnit（368 通过、1 跳过）通过；开发客户端已验图标与拴绳；全量 GameTest 的 8 个失败在 `main` 上同样失败；甲虫、蜗牛、旧小石精的碰撞箱与模型不贴合，但尺寸由 `BuildingAssetGameTests`（通道契约）和 `StoneThrowerNativeGameTests` 固定，未改，待作者决定。 |
| 开放 PR 整合分支（Devin 整合会话） | 已整合，待审，未合并 | 把 #1、#3、#4、#5、#6、#10、#7、#8、#9、#12 合到一条分支，不含 #11（建筑重设计，已搁置）。配对构建与 JAR 校验通过；JUnit 400 项，399 通过、1 个既有音频测试跳过、零失败；全量 Forge 回归 567 项中 8 项失败，与 `main`（565 项中同样 8 项）逐名一致，没有新增失败。见下方“开放 PR 整合记录”。 |

## 离线双端预演（不是正式验收）

- 负责：Devin 会话（受 GSDKQ233 委托）。候选提交 `30133d7`，2026-11-20。
- 入口：`runDreamseekersServer` 的开发源集专服（`online-mode=false`，新建平坦世界）加两个开发图形客户端（离线用户名，简中和英文，软件渲染）。没有使用重混淆配对 JAR，没有正常认证。
- 同一提交在另一台机器复跑：`-Pdreamseekers test compileGametestJava compileDreamseekersGametestJava` 与 `build dreamseekersJar verifyDreamseekersJar verifyDreamseekersCoreJar` 均通过；JUnit 369 项，368 通过、1 项跳过、零失败。该次构建的 JAR 摘要与文末记录不同（清单含构建时间），未比对两者内容。

| 检查 | 结果 | 实际观察与边界 |
| --- | --- | --- |
| 两人入席 | 通过 | 两端各占一个寻梦者席位并互相可见。 |
| 准备与开局 | 通过 | 简中显示 `1 / 2 已准备`，两人准备后倒计时并开局。 |
| 重连 | 通过 | 一端断开重进后基地、5 个工人和资源不变，另一端不受影响。 |
| 定向邀请 | 通过（仅两人） | 发起、对方可见、撤回该请求；再次发起后对方重连，邀请和发起方的出站状态都在。没有第三人，未验其他人的邀请状态不被冲掉。 |
| 开放大厅重启后新玩家入席 | 通过，另有一项观察 | 干净停服重启后，首次加入的玩家实际入席。重启前只在大厅入席（未准备、未开局）的玩家，占用没有恢复，需重新入席；这符合设计：大厅入席是预留，`StartPosServerEvents.onPlayerLeave` 在玩家离开时清除，`StartPosSaveData` 只保存席位坐标、颜色和启用状态。已开局对局的席位由 `RTSPlayerSaveData` 保存，对局中重启服务器后的恢复本次未验。 |
| 同配方撤单 | 通过 | 两个同配方寻梦萤火虫排队，撤第二个只退一次 50 食物，第一个继续；再点不重复退。 |
| 科研停复 | 部分 | 用管理员命令拆除并补回前置的世界树主城：石灵攻城研究显示红色暂停提示，补回后从 32% 继续到完成。暂停时界面不显示百分比，未直接读到冻结数值；只看到英文暂停提示。 |
| 技能取消 | 通过 | 破风突击进入选点后，右键、Esc、再按技能键都能退出，随后可立即再次进入，未触发冷却。破风骑士由管理员召出。 |
| 局内渲染 | 抽查通过 | 寻梦者阵营、世界树主城、工人、若干建筑、破风骑士、甲虫、玄武蜗牛守卫、石灵工人无缺失贴图或模型。 |
| 简中 | 部分 | 大厅、准备、HUD、建筑标签与提示、倒计时、结盟聊天无裸键；本候选新增的键未逐一核对。 |
| 缺译回退 | 部分 | 语言切到波斯语后，破风突击提示回退英文而非裸键；未复现 `1 / 2 ready`。 |
| 头颅方块 | 通过 | 用数据包摆放 4 种地面和 4 种墙面变体，模型正常，朝北墙面朝向正确；其余朝向未看。 |

本节未覆盖、由下节补做的：重混淆配对 JAR 的专服和客户端、对局中重启服务器、三人邀请。仍未覆盖：正常认证。

已转给内容审计、未在此修改的图形观察：石灵工人模型明显超出碰撞箱；玄武蜗牛守卫碰撞箱明显大于模型；甲虫腿部伸出碰撞箱。

## 重混淆配对 JAR 离线预演（不是正式验收）

- 负责：Devin 会话（受 GSDKQ233 委托），2026-11-20。成品由 `30133d7` 源码构建：核心 `b290d77da41a1c84ee01f637d90e112040c4b9419d82829b10fe024463e36abb`，附属 `53f69c067ac6b443fc64b38c8c581b979c9a9d506ab33daceffeab538ef74433`（与文末记录的摘要不同，清单含构建时间）。
- 入口：官方 Forge 1.20.1-47.4.0 专服（`online-mode=false`，新世界）加两到三个正式版 Forge 客户端，三端 `mods/` 为同一组 6 个 JAR（上述两个加四项前置），不使用开发源集。离线用户名，没有正常认证；资源、建筑和部分单位由管理员命令布置。

| 检查 | 结果 | 实际观察与边界 |
| --- | --- | --- |
| 专服与客户端加载 | 通过，另有日志错误 | 握手并加入成功。日志中类缺失、端侧非法加载、Mixin 应用和 refmap 错误均为零。每个客户端另有 3 条 ERROR：离线认证、`Unable to parse animation: 3`、`reignofnether:item/scroll_of_recall` 贴图读取失败（该文件实为 WebP，修复见 PR #12）。专服有平坦世界生成层和 PasterDream 可选标签的 ERROR。 |
| 配对拒绝 | 通过 | 不装附属的客户端被拒绝，提示模组通道列表不匹配并写明所需寻梦者版本。改名或旧配对未验。 |
| 入席、准备、开局、定向邀请、重连、同配方撤单、技能取消 | 通过 | 与上节相同的流程在本组成品上重做，结果一致。 |
| 对局中重启服务器 | 通过 | 开局后正常停服再启动，两人重进仍在对局中：阵营、颜色、建筑（含一座未建完）、单位、人口、资源、已确认的结盟、已完成的科研都在；后进的一方未被淘汰。 |
| 停服时的在途项 | 按设计 | `BuildingServerEvents.onServerStopping` 撤销所有未完成的生产和科研并全额退款，各退一次（破风骑士 140/0/100、石灵攻城研究 0/150/150）。重启后界面无残留队列，被退的科研可重新开始并只完成一次，同配方撤单仍只退一次。在途进度不跨重启保留。未接受的结盟邀请只在内存中，重启后消失。 |
| 三人定向邀请 | 部分 | 三个客户端同时在线：撤回 A→B 后 A→C 仍在；A→C 待定时 B→A 独立发起；C 重连后各端视图未被冲掉。未对每个视图逐项留证。 |
| 科研停复 | 部分 | 简中下由所有者拆除前置主城、工人重建，石灵攻城研究显示暂停并在重建后完成。暂停前后的精确百分比和停顿时长未测到。 |
| 缺译回退 | 部分 | 波斯语下进入大厅并看到英文回退；`1 / 2 ready` 没有在波斯语客户端上留下确凿截图。 |
| 头颅方块 | 通过（布置方式有限） | 东、南、西朝向的 4 种墙面变体模型和朝向正常；仍由数据包摆放，不是玩家放置。 |

静态核对（不经图形界面）：本候选新增的语言键中英齐全（英文 11、简中 50）；附属 690 个键中英一一对应；核心源码里 1855 处字面翻译键只有陵墓的 `buildings.reignofnether.mausoleum.tooltip3` 在英文里缺失（修复见 PR #12）。

其他观察，未在此修改：核心约 30 个自定义状态效果没有 `textures/mob_effect/` 图标，在原版物品栏里显示为缺失贴图；美术 PR #4（`a2a1e4b`）上九个技能均可施放，但悲泣回响和甲群呼唤没有看到独立的施放动作，其余无法判定。

未覆盖：正常认证的正式双端、强杀进程后的恢复、玩家手工放置头颅、逐条触发信标／预检／室内出生提示。

## 下一位协作者

1. **双端验收**（未认领；离线预演已做，见上两节，只差正常认证）：在独立世界用相同配对 JAR 和四项前置。验证两人入席、准备、重连及定向邀请；补非空开放大厅重启后新玩家实际入席。核对两项同配方仅撤目标且只退一次、科研停复与界面进度、简中 HUD 与技能取消。
2. **后续玩法审阅**（已审，不是本次发布门槛）：结果见下方“后续玩法审阅结果”。剩余待认领：同 UUID 改名后的状态迁移（单独一项改动，见该节的取舍）；晚波怪物数量恢复到配置人口后的真实负载基线。
3. **更新本板**：先标工作项、负责角色和依赖，完成后写清候选提交或哈希、测试入口、实际结果与未覆盖项。互相影响的发现同步给对应负责人。不要把历史或诊断候选通过记到当前构建，也不要提交个人资料。

## 构建清理记录

- 工作项：替换 `build.gradle` 的 Forge 模板占位值；清理 `gradle/gametest-plan.gradle` 的旧附属测试开关。负责角色：构建清理（Devin 会话）。依赖：无，不涉及双端验收和后续玩法审阅。
- 候选提交：`e676559`，父提交 `30133d7`。
- 改动：JAR 清单的 `Specification-Vendor`、`Implementation-Vendor` 由 `examplemodsareus` 改为 `SoLegendary`。112 个旧附属开关中，22 个的命名空间已有 `-PresourceTechSuite` 套件，从选择列表和附属分支移除，再传入会报错并提示对应套件。其余 90 个是各自命名空间的唯一入口，保留不动，其中 `dreamseekersShellFunctionsFocused` 虽有同名套件，因在 repair3 回归清单里使用也保留。
- 测试入口：`bash ./gradlew build`；`bash ./gradlew -Pdreamseekers -PdreamseekersPython=python3.11 build dreamseekersJar verifyDreamseekersJar verifyDreamseekersCoreJar`；`bash ./gradlew -Pdreamseekers -PresourceTechSuite=wind_protocol -PresourceTechRun=cleanup1 runGameTestServer`。
- 实际结果：核心构建通过，JUnit 245 通过、1 跳过、0 失败。配对构建通过，JUnit 368 通过、1 个既有音频测试跳过、0 失败；两个 JAR 的版本与内容检查通过，核心 JAR 清单厂商为 `SoLegendary`。`wind_protocol` 套件实际启用命名空间 `dreamseekers_wind_protocol`，1 项必需测试通过。抽查 3 个已移除开关均按预期报错；抽查 3 个保留开关，解析出的目录与命名空间和原分支一致。
- 未覆盖：其余 21 个被替代的套件没有实际启动，只核对了命名空间和阶段键与原分支一致。90 个保留开关没有逐个运行。19 次选定 Forge 回归、专服、图形与语言、双客户端联机都没有重跑。套件入口的世界目录是 `run-resource-tech-<套件>-addon-<后缀>`，不沿用旧开关的目录。`src/main/resources/META-INF/mods.toml` 的模板注释和 `logoFile="examplemod.png"` 未改。下方 JAR 摘要早于本项；本项改了核心 JAR 清单，未重新登记摘要。

## 后续玩法审阅结果

- 角色：玩法审阅（Devin 会话，受仓库所有者委托）。基准提交 `30133d7`；修复提交 `49dba3e`（在途经验）、`1287ecd`（波点预算）。
- 测试入口：`bash ./gradlew -Pdreamseekers test compileGametestJava compileDreamseekersGametestJava`；`bash ./gradlew -PresourceTechSuite=api65_followup_hero_xp -PresourceTechRun=<后缀> runGameTestServer`（核心，不带 `-Pdreamseekers`）。
- 实际结果：JUnit 375 项，374 通过，1 个既有音频测试跳过，零失败；核心和附属的主源码与 Forge 测试源码编译通过；Forge 回归 `api65_followup_hero_xp` 3/3 通过。同一组回归在未修复的经验球上 3/3 失败。

| 审阅项 | 结论 | 依据 |
| --- | --- | --- |
| 原生英雄在途经验的重启（C05） | 缺陷，已修 | `HeroExperienceOrb` 只持有英雄的实体引用，不写入存档。区块卸载或正常停服后重新读入的经验球没有目标，第一刻即被丢弃，携带的经验丢失。现在保存英雄 UUID、中立标记和已飞行时间，读入后按 UUID 重新绑定；英雄尚未加载时等待，到原有 60 秒寿命为止。修复前的存档里的经验球没有 UUID，仍按原样丢弃。 |
| 晚波部分成功时的生成预算（C09） | 缺陷，已修 | `WaveSpawner.getValidSpawnPoints` 的尝试计数整次调用共用且不清零：一次调用最多采样 111 次、返回至多 110 个点，最后 10 个点跳过距离检查。怪物波每点一只，默认人口折算下第六档平均约 190 人口，而第 16–30 波每名玩家需要 115–610；差额只发一条聊天消息，波次照常推进，不补发。现在每个点各有 100 次尝试，累计 11 个点用尽后停止；猪灵传送门在搜索已用尽时不再重复搜索。 |
| 同 UUID 改名后的状态连续性（G04） | 缺陷，本候选不改 | 席位按 UUID 绑定，但席位名、单位、建筑、资源、科研、同盟和服务端指令鉴权都按名字。改名后同一 UUID 进服会被认作老玩家，却控制不了任何单位或建筑，也不能再开新席位（`alreadyHasSlot` 按 UUID 判定）；之后使用旧名字的账号反而能通过按名字的鉴权。处理方案见下。 |

- 改名一项的取舍（按“有推荐项就按推荐项处理”执行，推荐项是本候选保持现状）：
  - 推荐的后续做法：同一 UUID 以新名字进服时，在服务端把该席位的名字和所有按名字记账的状态原地改到新名字，再向在线客户端重新同步。它只在“UUID 相同而名字不同”时触发，不影响没有改名的玩家。
  - 本候选不做的原因：归属变化在核心和附属里是有含义的身份事件。资源账户带 `accountEpoch`（50 个源文件用到），建筑换主走占领修订 `captureRevision` / `beforeOwnerCapture`（35 个源文件用到），附属里读取归属名的源文件有 150 个，并有多份按归属名落盘的账本（合同、羁绊、世界树收入、施工付款等）。改名必须让这些全部原地跟随而不被当成占领或新账户，漏掉任何一份都会让改名玩家丢失已付费的状态。这需要单独一项改动，并且要用真实改名账号在双客户端上验证，这里做不到。
  - 否决的简化做法：拒绝“名字与某席位相同但 UUID 不同”的账号进服。服务器在正版验证和离线模式之间切换时所有玩家的 UUID 都会变，这样会把所有人挡在自己的对局外。
- 行为变化：波点预算修复后，晚期怪物波会按配置人口足量生成，数量明显多于此前。采样成本的可重复基线在 `SpawnPointSearchTest`（固定种子，每点约 1/有效率 次采样，全无效地形上限 1100 次）；实体数量增加后的真实服务器负载未测。该修复单独成一个提交，可单独撤回。
- 与测试开关清理的交叉：本项在 `gradle/gametest-plan.gradle` 的 `api65Counts` 后加了一行 `api65_followup_hero_xp`，没有动其他开关。
- 未覆盖：真实两进程停服重启（回归用的是同一份实体 NBT 的存取，不是整服重启）；真实地形和真实波次的 Forge 回归（波点预算只有纯逻辑测试）；双客户端与附属阵营；改名场景没有运行期测试，结论来自源码。
- 双客户端演练补充项（重启后开局选位未恢复）：不是缺陷，不改。开局选位是大厅里的临时预订，玩家离开时 `StartPosServerEvents.onPlayerLeave` 就会清掉（`LobbySnapshotGameTests` 有覆盖），重启会断开所有人，所以 `StartPosSaveData` 只存位置、颜色和启用状态与此一致。对局开始后的席位是 `RTSPlayerSaveData` 里的 `RTSPlayer`（名字加 UUID），由 `PlayerServerEvents.onServerStarted` 恢复。此结论来自源码，没有重跑该演练。
- 另记未处理：`UnitServerEvents.spawnMobs` 在实体被其他模组拒绝加入时仍返回该实体，怪物波和村民波会照常扣预算；非中立经验球的数值按一半取整，1 点的球携带 0 经验。

实际测试配对 JAR 摘要（仅供核对，不随仓库分发）：

- 核心：`31921285330f40f04f438bd2997546c278d2311c2472168b402b4d5648163c12`
- 附属：`736d436969720fe2cd81ad51ff03ec90528c6b1b0aea1ac9e91c9d68cad5ae4f`

## 建筑与兵种美术表现（已认领并提交 PR，待审阅）

负责角色：美术核查会话。范围只含模型、贴图、动画、图标与结构资源；兵种实现、建筑实现、文字和 UI/HUD 由各自会话负责。核查为静态核对加单元测试，没有启动图形客户端。

| 工作项 | 状态 | 证据与边界 |
| --- | --- | --- |
| 渲染器与原生资源解析 | 通过 | 33 个实体类型都有渲染器；所引用的 PasterDream beta0.4.2 geo、动画、实体贴图全部存在。 |
| 技能施放动画 | 已改，画面未验 | 9 个技能结算时经 `SkillCastAnimation` 调用原生 `setAnimation`；单元测试确认动画名存在于已安装模型。客户端实际播放效果需图形验收。 |
| 占位与复用图标 | 已改，画面未验 | 新增疯狂恐怖尖喙、亚伦柯斯左右手头像和 6 个技能图标，萤火虫工人重绘为 64×64。 |
| 结构清单与镜像 | 已改 | 清单新增 `runtime_template_assets`（5 个运行时模板）；3 个 v3/v4 世界树补入 `assets/`。11 个已登记建筑条目不变。 |

测试入口：`bash ./gradlew -Pdreamseekers build dreamseekersJar verifyDreamseekersJar verifyDreamseekersCoreJar`，JUnit 375 项中 374 通过、1 个既有音频测试跳过、零失败，其中 `ArtResourceIntegrityTest` 为新增 6 项。`DISTRIBUTION-MANIFEST.json` 描述的是 repair3.1 源码包，本次没有重新生成。

未覆盖并留给后续：

1. **图形验收**：新图标缩放后的清晰度、技能动画播放、融梦冠晶侧面贴图是否拉伸（双端演练里看着略有拉伸，未定论）。
2. **结构观感**（未认领）：礼堂、研究所、甲虫圣所的深板岩屋顶和旧结构的石砖地基与粉白色图标不一致。建议截图确认后只做同形状的方块替换，不改几何、出入口和驻军点。
3. **投射物外观**（未认领）：石块、灰烬落点、压制音波仍用原版物品模型；三种音波弹没有使用 PasterDream 原生渲染器。
4. **图标批次**（未认领）：甲虫、蜗牛、阵营、旧工人仍用 PasterDream 16×16 物品贴图；10 个研究项复用单位或建筑图标；`shadow_energy.png` 为 16×16。
5. **交给对应负责人确认**：甲虫、玄武岩蜗牛、石灵工人的碰撞箱与原生模型不同；`DreamBuildingSpec.iconItem` 没有被读取，`portraitBlock` 仍是原版方块但建筑都返回了自定义图标，所以界面上不会画出来；建筑能力、共鸣、生态、契约等按钮使用原版或 PasterDream 物品贴图。

## 建筑实现核查（建筑会话）

认领项：11 种建筑是否真实实现（注册、模板、造价、生命周期、产出、训练/研究队列、建筑技能、归属与前置、存档还原）。基准提交 `30133d7`。兵种、美术、文案、UI 不在本项内。

- **结论**：11 种建筑都有可运行的实现，没有只挂名的空壳。11 个基础模板在 `data/` 与 `assets/` 字节一致，清单 SHA-256 全部吻合；提示里的间隔、上限、人口、范围、容量与代码常量一致。
- **已修（范围修复 PR）**：范围效果原先以 `originPos`（建筑一角）为圆心。共鸣晶台 +10% 光环改为按双方占地间距 ≤12、基座高差 ≤8 判定；根冠庇护、庭园休养、术式整备、晶台庇护的目标判定改为按建筑占地计算。新范围是旧范围的超集，费用、冷却、目标上限不变。
- **有意不改**：守望塔照影标记仍用原点球形判定，因为客户端镜像 `TowerMarkClient` 用同一个球校验。
- **已修（契约与共鸣范围 PR，叠在范围修复 PR 之上）**：中立契约覆盖（32）、共鸣脉冲、共鸣网络连线（24）同样改为按建筑占地计算，仍是旧范围的超集。可连上的节点变多后，超过节点上限（4 或 6）的玩家被选中的节点组合可能与以前不同。验证：`resonance` 15/15、`resonance_fog` 1/1、`neutral_contract` 19/19、`neutral_contract_fog` 1/1、`contract_ready` 1/1、`ecology` 8/8、`luminous_research` 6/6、`building_boundary` 9/9，夹具无需改动；契约与共鸣的重启类套件未重跑。
- **已核对、无需改**：`DreamConstructionPayment.legacyCost` 只是无付款记录的旧存档取消建造时的退款依据，新建筑按实付记录退款。旧的 `dream_anchor` / `dream_anchor_ascended` 模板仍被 `WorldTreeModel.LEGACY_*` 用于还原旧存档，不能删。`DreamBuildingSpec.iconItem` 是无人读取的死字段。
- **另开 PR**：八种建筑共用的占位生命值 200 / 建造系数 1.0 改为按角色区分的数值（用户已选定建议值）。

测试入口与实际结果（Java 17，四项前置按依赖锁校验哈希，范围修复分支）：

| 入口 | 结果 |
| --- | --- |
| `./gradlew -Pdreamseekers test` | 374 项，1 项既有跳过，零失败（含新增 `DreamFootprintRangeTest` 5 项） |
| `-PdreamseekersShellFunctionsFocused runGameTestServer` | 11/11 通过（修复前基线同为 11/11） |
| `-PresourceTechSuite=building_abilities` | 31/31 通过 |
| `-PresourceTechSuite=building_boundary` | 9/9 通过 |
| `-PresourceTechSuite=building_observation` | 12/12 通过 |
| `-PresourceTechSuite=resonance` | 15/15 通过 |
| `-PresourceTechSuite=luminous_research` | 6/6 通过 |
| `-PresourceTechSuite=building_abilities_restart`（prepare、verify） | 各 1/1 通过 |
| `-PresourceTechSuite=luminous_restart`（prepare、verify） | 各 1/1 通过 |

两处夹具按新规则更新：`DreamShellFunctionGameTests` 的光环边界用例改为按占地间距断言，并新增远离原点一侧的贴边与 13 格排除；`BuildingAbilityEffectsGameTests` 的礼堂对照单位从原点外 14 格移到占地外 13 格。

未覆盖：没有在真实客户端里实机摆放验证；未跑默认全量 `dreamseekers` 命名空间；`verifyDreamseekersCoreJar`（需 Python 3.11）与正式配对 JAR 未在本次重做。

## 开放 PR 整合记录

- 工作项：把当前未合并的开放 PR 合到一条分支，供一次性审阅。负责角色：整合（Devin 会话）。分支 `devin/1795218057-integrate-open-prs`，基于 `main` 的 `bde6b83`。没有合入 `main`，各来源 PR 保持开放、未改动。
- 包含（按合并顺序）：#1 构建清理、#3 界面与 HUD、#4 图标／施法动画／结构清单、#5 后续玩法、#6 建筑范围、#10 契约与共鸣范围（叠在 #6 上）、#7 协作板离线预演记录、#8 兵种修复、#9 建筑血量与建造时间、#12 核心图标与提示键。不包含：#11 建筑重设计（所有者已搁置）。
- 冲突处理：只有本板和 `DISTRIBUTION-MANIFEST.json` 出现文本冲突。本板保留各 PR 的全部记录；清单在全部合并后按最终文件重新计算。`DreamseekersMod.java`、`UnitSkillAbility.java`、`gradle/gametest-plan.gradle` 被多个 PR 修改但自动合并无冲突，已逐个 PR 核对新增行都在；`DreamBuildingSpec.java` 在所含 PR 中只有 #9 修改。其余 74 个文件各只被一个 PR 修改，内容与该 PR 分支一致。
- 测试入口：`bash ./gradlew -Pdreamseekers -PdreamseekersPython=python3.11 build dreamseekersJar verifyDreamseekersJar verifyDreamseekersCoreJar`；`bash ./gradlew -Pdreamseekers -PdreamseekersRosterCompleteTest runGameTestServer`（`main` 和本分支各跑一次）；`-PresourceTechSuite=api65_followup_hero_xp` 与 `-PresourceTechSuite=wind_protocol`。
- 实际结果：配对构建通过，两个 JAR 的版本与内容检查通过。JUnit 400 项，399 通过、1 个既有音频测试跳过、零失败。全量 Forge 回归（命名空间 `reignofnether,dreamseekers`）：本分支 567 项、8 项失败；`main` 565 项、8 项失败；两边失败名单完全相同，多出的 2 项是 #8 和 #9 新增的测试，均通过。`api65_followup_hero_xp` 3/3 通过，`wind_protocol` 1/1 通过。
- 两边都失败的 8 项（既有问题，本分支未处理）：`originalpayerrefundsbeforetickandmutablecostscannotchangereceipts`、`savedrefundsnapshotusesoriginalpayerandimmutablepayment`、`alleightregisteredfactionfactoriespaidrotationsandcancel`、`eightactualworkerscompletethensurvivecodecandtakedamage`、`gardenworkerharvestsreplantsanddeliversfood`、`queuedfogconstructionusescollisionflightandstopclearsit`、`thresholdautomaticallyreturnsthenresumesfarmwithoutduplicatecredit`、`realreadiedmatchstart`。
- 未覆盖：没有进游戏实测，各 PR 的界面、图标、动画和建筑数值叠加后的实际显示未看；专服、双客户端联机、19 次选定 Forge 回归的独立进程入口没有重跑；除上面两个套件外，其余 `-PresourceTechSuite` 套件和保留的旧附属开关没有逐个运行；全量回归每边只跑了一次，且两次同时在一台机器上运行。
