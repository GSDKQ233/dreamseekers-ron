# Reign of Nether 与寻梦者源码说明

本项目将 Minecraft 的方块世界与即时战略玩法结合：建设基地、采集资源、训练军队，并通过俯视视角指挥单位作战。源码包含 Reign of Nether 核心及独立的寻梦者附属模组。

寻梦者为原版 PasterDream 提供 RTS 适配，加入以世界树主城为中心的阵营、兵种、建筑、影能经济和研究体系。附属在运行时使用已安装的 PasterDream，不随包提供其代码、模型、纹理、声音或第三方依赖 JAR。

## 当前版本

| 组件 | 版本 |
| --- | --- |
| Reign of Nether 核心 | `1.5.0b-ore-mines.2-dreamseekers-api.65-repair2` |
| 寻梦者附属 | `0.65.0-repair2` |
| 运行环境 | Minecraft 1.20.1 · Forge 47.4.0 · Java 17 |

`repair2` 基于已经交付的 `repair1`。核心与附属必须精确配对，客户端与服务端也必须同时更新；不要与 `repair1`、`text-refresh`、`audit-fixes` 或其他版本交叉混装。

**本包是源码，不是可安装模组。** 实际改动和测试边界见 [repair2 交付说明](docs/dreamseekers/REPAIR2.zh-CN.md)，安装和回退见[安装与更新指南](docs/dreamseekers/INSTALL.zh-CN.txt)。原 API65 发布证据只代表原版。

## 功能概览

- **RTS 核心**：提供村民、怪物、猪灵阵营，以及资源、人口、建造、生产、研究、单位指挥、联盟和战争迷雾等系统。
- **寻梦者阵营**：以寻梦萤火虫承担采集与建造，配合侦察、近战、远程和支援单位组织军队。
- **基地建设**：世界树主城、梦生花园、破风训练庭、梦术礼堂、共鸣研究院等 11 种建筑，覆盖经济、生产、研究、照护和驻军防御。
- **阵营成长**：影能资源、世界树分支升级、风之旅途科技、兵种专精与共鸣网络。
- **战术能力**：单位主动技能、建筑支援能力，以及按各自规则受资源、人口或数量上限约束的中立契约与召唤。

兵种、建筑用途和使用要点见[寻梦者功能说明](docs/dreamseekers/FEATURES.zh-CN.md)。

## 源码结构

| 路径 | 内容 |
| --- | --- |
| `src/main/java`、`src/main/resources` | Reign of Nether 核心实现与资源 |
| `addons/dreamseekers/src/main` | 寻梦者实现与资源 |
| `src/test`、`addons/dreamseekers/src/test` | 单元验证代码 |
| `src/gametest`、`addons/dreamseekers/src/gametest` | 游戏运行环境中的集成验证代码与夹具 |
| `addons/dreamseekers/dependencies.lock.json` | 附属依赖的精确版本、文件名、来源及 SHA256 |
| `docs/dreamseekers` | 当前功能、安装与验证边界说明 |
| `CHANGELOG.zh-CN.md` | 影响当前行为或兼容性的精简迭代记录 |
| `DISTRIBUTION-MANIFEST.json` | 源码包成员的字节数与 SHA256 清单，不含清单自身 |

验证代码与生产代码分开组织，不应将验证类、夹具或运行世界加入生产 JAR。源码包不包含 Git 历史、第三方依赖、缓存、存档或认证信息。

## 从源码构建

使用 JDK 17 和随包提供的 Gradle 7.6.5 Wrapper，在源码根目录执行命令。首次构建需要下载 Gradle、Forge 及构建依赖；源码包不是离线构建环境，也不承诺构建结果逐字节一致。

仅构建核心：

```bash
bash ./gradlew build
```

构建寻梦者配对前，按 `dependencies.lock.json` 从原作者发布渠道另行准备四个前置 JAR。将它们置于同一目录，再准备本地依赖仓库；将下列路径替换为实际目录：

```bash
python3 addons/dreamseekers/prepare_local_dependencies.py "/path/to/dependency-jars"
```

该脚本先核对全部文件的 SHA256，再复制到私有本地依赖仓库；不会下载或打包第三方内容。随后构建并检查两个生产 JAR：

```bash
bash ./gradlew -Pdreamseekers build dreamseekersJar verifyDreamseekersJar verifyDreamseekersCoreJar
```

产物位于 `build/libs/`。附属必须显式启用 `-Pdreamseekers`；不要把源码 ZIP、开发缓存或前置依赖仓库当作模组分发。

## 兼容性与验证范围

- 寻梦者面向原版 PasterDream `beta0.4.2`（内部版本 `0.3.5.7`），不支持以 PasterDream: Reborn 替换。其余前置按锁文件使用，不能将版本范围声明理解为任意后续版本均已兼容。
- 更新前应正常停服并备份世界、`mods` 与 `config`。旧数据缺失时不补造历史状态；活动施法不保存，相关经济与技能计时不按停服时长补进度。
- 战争迷雾包含客户端视觉遮盖机制，不是完整的抗修改客户端地形保密方案。
- GUI、真实音频、真实玩家双客户端联机、村庄视觉生成、通用旧存档迁移及断电一致性均未完成全面验收。功能介绍不能替代这些场景的验证。

当前证据来源、源码整理检查及未覆盖场景见[验证与兼容边界](docs/dreamseekers/VALIDATION.zh-CN.md)。第二批修复及本次实际验证见[repair2 交付说明](docs/dreamseekers/REPAIR2.zh-CN.md)；既有验收数字不计作 repair2 通过项。旧注册身份、迁移逻辑和配置兼容常量属于当前实现，不因名称中带有旧版本信息而删除。

## 许可与署名

许可和作者信息见根目录的 `LICENSE.txt`、`CREDITS.txt`，以及附属资源目录中的 `LICENSE-DREAMSEEKERS.txt`、`CREDITS-DREAMSEEKERS.txt`。寻梦者为独立兼容项目；第三方模组及其资源仍归各自作者所有。
