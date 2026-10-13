# TECH AutoModpack 客户端定制

本分支用于 TECH Minecraft 26.2 / NeoForge 26.2 客户端，基于官方 `v5.0.0-rc.2`
（`8d51399a0c51ccb1d74140a954e7fd1eff4be4f6`）。这是非官方客户端定制，内部版本号仍为
`5.0.0-rc.2`；服务端继续使用官方组件。

仓库：<https://github.com/zedoCN/AutoModpack/tree/codex/tech-neoforge26.2>。
上游历史、README 和 LGPL-3.0 许可保留；本分支的 Java 修改与 2026-10-09 已发布源码包中的补丁一致。

## 定制行为

- 默认保留个人模组，确认页重建、取消后重试保持选择。
- `confirm-unverified-mods:false` 跳过 Modrinth/CurseForge 查询、风险复选框及正常更新倒计时。
  未配置时默认 true，设为 true 恢复原机制；不使用线路来源白名单。
- 历史管理页补齐 journal 引用的历史 policy，验证哈希并复用缓存，不下载全部历史模组。
- Windows 本地化文件占用错误交给退出助手，游戏退出后再替换；缺文件、路径碰撞和真实存储错误仍报错。
- 根据本地管理状态协商必需/兼容轨道：旧辅助文件仍受管理或状态无法可靠读取时走兼容轨，
  首次安装或已完成个人移交时走必需轨。一个 HTTP 连接固定 User-Agent，轨道选择不自动迁移个人模组。

证书 CA、HTTPS 主机名、已有 pin 和对象哈希仍校验。手动切换历史版本与必要重启保留原机制。
同 ID 去重仍可能归档旧版本或移除重复副本；“保留个人模组”不保证保留所有冲突版本。

## 构建与验证

从本分支构建，无需再次应用旧补丁。Gradle 使用 JDK 25，core 使用 Java 17 toolchain：

```sh
./gradlew oneJar :core:test spotlessJavaCheck checkNoFullyQualifiedNames -Pautomodpack.targets=26.2-neoforge --no-daemon
```

输出为 `merged/automodpack-5.0.0-rc.2.jar`。生产组件有独立的 TECH 文件名，
不能仅凭内部版本号判断是否使用了定制 JAR。
现有测试覆盖确认开关、选择保留、轨道协商、历史 policy、TLS 与更新事务。
源码/构建验证不替代玩家 PCL/HMCL 的真实启动与进服验收。

2026-10-09 已发布材料的 SHA256：

| 材料 | SHA256 |
| --- | --- |
| 官方基线源码 ZIP | `7f505f1128b6cb04ecf0154183c845250b7b1097dc09db6d80def210ea414368` |
| TECH 补丁 | `c5be37ddf31692219b07becf4c6eef2558eb7c506f456f88c91d3b8e5c6a15b6` |
| 已发布定制 JAR | `439e1ec7ab23e6a673bc0ab4fa0e9a04b210be82b75b5198853d9f95f8b86918` |
| 已发布完整源码交付 ZIP | `2e74eebf43501f55ba7d360ca536218df80cceef9ee68cc23d4102a862c7acbd` |

这些哈希用于追溯现有发布物，不保证后续工具链重建的 JAR 逐字节相同。

2026-10-13 Git 归档验证：官方源码包的 797 个文件与基线 Git 树一致，23 个定制 Java 文件
与“基线源码 + 已发布补丁”的内容逐字节一致。上述构建与检查成功；core 共 823 项测试，
819 项通过、4 项跳过、零失败/错误。本次重建 JAR 的 SHA256 与已发布定制 JAR 完全相同。

## 客户端配置与安装

关闭游戏后，确保 `mods/` 只有一个 AutoModpack 更新器，合并定制 JAR 与
`automodpack/client.conf`；保留玩家已有 pin。TECH 配置为：

```hocon
update-selected-modpack-on-launch: true
self-updater: false
sync-automodpack-version: false
sync-versions: false
play-music: false
show-modpack-settings-button: true
pinned-mod-ids: []
confirm-unverified-mods: false
```

旧 JAR 不识别新确认开关，必须同时更新组件与配置。不改玩家 Java、加载器或启动器。
不要运行中强制覆盖 JAR，不删除玩家 mods 或 AutoModpack 管理目录。
旧辅助文件从管理清单撤出可能删除玩家副本，移交应先备份、完成事务、迁回个人目录并核验。

## TECH 公网发布

客户端安装包：<https://status.xiang233.top/TECH-2026-自动更新.zip>。
对应完整源码包：<https://status.xiang233.top/TECH-2026-AutoModpack-source.zip>。
两个链接可能随以后发布更新，历史版本按上述哈希识别。

本次 Git 归档不替换已部署 JAR，不重新发布玩家包，不重启 Minecraft。
发布源与两轨维护流程在 infra-ops 的 `runbooks/minecraft-tech-automodpack.md`；
TECH 服务器本地可读 `AGENTS.md` 与 `AUTOMODPACK.md`。
