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
| 图形与语言 | 部分通过 | 两个开发客户端分别渲染主界面，验证简中和缺译英文回退。 |
| 正式配对 JAR 双客户端联机 | 延后，未验 | 公开源码不代表此项通过。需两个独立、可联机的客户端并保留正常认证。 |
| 正式发行与真实存档 | 未验 | 不发布 JAR，不使用任何正式存档做验证。 |

## 下一位协作者

1. **双端验收**（未认领）：在独立世界用相同配对 JAR 和四项前置。验证两人入席、准备、重连及定向邀请；补非空开放大厅重启后新玩家实际入席。核对两项同配方仅撤目标且只退一次、科研停复与界面进度、简中 HUD 与技能取消。
2. **后续玩法审阅**（待定，不是本次发布门槛）：同 UUID 改名后的状态连续性、原生英雄在途经验的正常重启、晚波部分成功时的生成预算。性能变更先建立可重复的基线。
3. **更新本板**：先标工作项、负责角色和依赖，完成后写清候选提交或哈希、测试入口、实际结果与未覆盖项。互相影响的发现同步给对应负责人。不要把历史或诊断候选通过记到当前构建，也不要提交个人资料。

实际测试配对 JAR 摘要（仅供核对，不随仓库分发）：

- 核心：`31921285330f40f04f438bd2997546c278d2311c2472168b402b4d5648163c12`
- 附属：`736d436969720fe2cd81ad51ff03ec90528c6b1b0aea1ac9e91c9d68cad5ae4f`

## 建筑实现核查（建筑会话）

认领项：11 种建筑是否真实实现（注册、模板、造价、生命周期、产出、训练/研究队列、建筑技能、归属与前置、存档还原）。基准提交 `30133d7`。兵种、美术、文案、UI 不在本项内。

- **结论**：11 种建筑都有可运行的实现，没有只挂名的空壳。11 个基础模板在 `data/` 与 `assets/` 字节一致，清单 SHA-256 全部吻合；提示里的间隔、上限、人口、范围、容量与代码常量一致。
- **已修（范围修复 PR）**：范围效果原先以 `originPos`（建筑一角）为圆心。共鸣晶台 +10% 光环改为按双方占地间距 ≤12、基座高差 ≤8 判定；根冠庇护、庭园休养、术式整备、晶台庇护的目标判定改为按建筑占地计算。新范围是旧范围的超集，费用、冷却、目标上限不变。
- **有意不改**：守望塔照影标记仍用原点球形判定，因为客户端镜像 `TowerMarkClient` 用同一个球校验。中立契约覆盖、共鸣脉冲、共鸣网络连线仍以原点为圆心，留作后续。
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
