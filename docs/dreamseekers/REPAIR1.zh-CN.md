# 寻梦者 API65 repair1 首批修复候选

这是十轮审计的首批实施版，不代表全部 49 个分组已关闭。原始 API65 text-refresh 源码包、原发布 JAR 和玩家世界均未修改。修复基线：`API65-text-refresh-source.zip`，SHA-256 `59bec26a11801b5fb7cd3fbb49e611ac00c29806d49c0ed6ea8cbaefa5e0d148`。

## 改动范围

| 对应审计 | 本批行为 |
|---|---|
| R1.01 沙盒权限 | 沙盒工具、场景设计和自定义建筑编辑按当前连接、当前世界、UUID 席位和受控动作重新判断。普通玩家或共享单位权限不自动获得全图编辑；命令编辑仍有额外权限要求。 |
| R1.02 场景状态 | 角色从本世界的 SavedData 加载；空的新世界建立默认值，停止时解除旧世界对象。登录只收所在服务器的当前快照。 |
| R1.04 / R4.02 生产队列 | 队首撤销按该次训练的独立 ID 判定，同配方也不会混淆。旧请求不会撤掉下一单。普通队列重同步为请求者换整份快照，不再给旁人追加一份。 |
| R2.01 技能同步 | 只在支持和鸣展示的技能状态里放该标志；不放宽原校验器。 |
| R2.02 采集任务 | 保存时合并尚未加载工人的待恢复工单，优先合法活体的实际工作状态；不增加存档字段。显式取消和永久退场清理待恢复条目。 |
| R3.01 梦生花园 | 未知的合法复合标签原样回写，未知几何不参与自动判毁或被动产出；旧版、已知版继续走原迁移。通用未知建筑数据隔离（R3.02）不在本批。 |
| R3.03 中立契约 | 工人失效时，对仍属于同一有效会话的动作回送中性完成结果，从待办回收该序号；不会泄露已失去的工人视图。 |
| R4.01 资源交货 | 仅消费实际计入账户的有价货物，不清掉未计价物品；混合食、木、矿各自记入统计。 |
| R6.01 施工队列 | 首次 Shift 追加时保留已经开始的施工，真正空闲才从新队列起步。 |
| R1.08 / R9.01–03 验收入口 | 增量测试跟踪外部输入；补齐 API65 套件入口、互斥选择和有效阶段检查；标准开发专服接入附属。 |

## 协议和房主管理

核心 `1.5.0b-ore-mines.2-dreamseekers-api.65-repair1`，附属 `0.65.0-repair1`。训练项 ID 是本次运行临时状态，不写存档；核心网络标识有意变化，旧版不能与 repair1 混连。替换时服务端和客户端同时换一整对 JAR，四个前置仍必须匹配 `addons/dreamseekers/dependencies.lock.json`。

单人房主可按正常流程进入沙盒。专服由等级 4 管理员或显式获邀的玩家使用沙盒编辑；使用时仍需要当前世界的有效沙盒席位。管理员可用 `/rts-sandbox-editor grant <在线玩家>`、`/rts-sandbox-editor list`、`/rts-sandbox-editor revoke <UUID>` 管理本世界的授权。不继承普通白名单或联盟身份。升级前保留世界、mods 与 config 的关服备份。

## 本次验证

- **Gradle / JUnit：342 通过、1 跳过、0 失败**（共 343 项）。跳过项是原有的真实客户端音乐淡出测试；纯 JVM 无法初始化它所需的 Forge 客户端。
- **Forge 1.20.1 / 47.4.0，JDK 17：5 组共 38 项通过。** 核心和联动附属同时加载，使用独立测试世界和锁定的四个前置。分别为和鸣技能 10、被动与存档 3、编辑权限与世界隔离 2、交货与生产/施工队列 4、中立契约 19。
- **构建：**核心及附属的 GameTest 源码编译通过；核心与附属的发布构建均完成重混淆转换和静态包核验，检查了依赖版本、包隔离和 Mixin 引用映射。标准开发专服的 Gradle 配置和实际解析的依赖也通过校验；这是配置验收，不是对普通专服的启动验收。
- **尚未验证：**用成品 JAR 在普通专服启动、两个真实图形客户端联机、真实旧世界升级与冷重启后的长期行为。未运行其余完整 API65 套件，未改动用户存档。GameTest 中以隔离存储和模拟连接覆盖的路径不等同于这些验收。

### 重跑

```sh
./gradlew -Pdreamseekers test compileGametestJava compileDreamseekersGametestJava verifyDreamseekersDevServer verifyDreamseekersJar verifyDreamseekersCoreJar
./gradlew -Pdreamseekers -PresourceTechSuite=api65_editor_worlds -PresourceTechRun=repair1-editors runGameTestServer
./gradlew -Pdreamseekers -PresourceTechSuite=api65_repair_play -PresourceTechRun=repair1-play runGameTestServer
```

测试与构建用的前置安装步骤见 `docs/dreamseekers/INSTALL.zh-CN.txt` 和 `docs/dreamseekers/VALIDATION.zh-CN.md`。成品包不捆绑第三方前置。

