<div align="center">

# Inkvol

**本地漫画 + 小说双引擎阅读器 · 本地优先 · 零权限 · 零采集**

Kotlin · Jetpack Compose · Material 3 · 液态玻璃设计体系

中文别名：**页澜**（应用显示名仍为 Inkvol，仅用于搜索与传播）

![Version](https://img.shields.io/badge/Version-1.0.0-orange)
![License](https://img.shields.io/badge/License-GPL--3.0-blue)
![Platform](https://img.shields.io/badge/Platform-Android%2026%2B-green)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-purple)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.09-orange)

</div>

---

## 为什么是 Inkvol

市面上的阅读器，痛点大多出在同一个地方：**它想要的比你想读的多**。

| 常见痛点 | Inkvol 的答案 |
|---|---|
| 要账号、要全套存储权限，内置广告或推荐流 | **零采集、零存储权限**：不申请存储权限，无账号体系，主界面没有任何在线入口；联网能力仅存在于你主动配置的可选 WebDAV 同步。书在你手里，数据不出设备 |
| 漫画装一个 App、小说装一个 App，还总有格式打不开 | **双引擎单应用**：散页文件夹 / ZIP·CBZ / 7z·CB7 / PDF / RAR4·CBR / **RAR5** + TXT / EPUB / **MOBI** |
| 翻页卡顿、快速连点丢击、动画打断阅读节奏 | **零延迟翻页**：连点快翻自动累加落点精确；翻页动画三档（无动画 / 平移 / 覆盖）随点随切、即时生效；小说仿真翻页（page curl） |
| 删书手抖、进度神秘丢失、换机迁移靠拷文件夹碰运气 | **数据永不丢**：进度节流落盘、删除进回收站、一键全量 JSON 备份恢复（书 + 进度 + 书签 + 注释 + 净化规则 + 偏好）、崩溃可恢复；多设备可选 WebDAV 同步 |
| 小说里全是广告行、灌水句，或者全是不能跳过的冗长描写 | **净化规则**：自定义正则行过滤，从源头删掉不想看的内容；划线 / 笔记 / 书签 / 注释浏览器 / 全文搜索齐备 |
| 书一多就找不到 | **全库搜索**：书名元数据 + 小说全文，格式 / 书夹 / 标签过滤 |
| UI 千篇一律，横屏要么挤要么闲 | **液态玻璃设计体系**（动态模糊 + 连续曲率形状）+ 一镜到底开书动画 + 横屏玻璃侧栏 + 动态取色 |

## 功能

### 书架与书库
- 多书库管理：SAF 托管文件夹（零权限）、复制入库、单文件导入，随时增删
- 系列聚合卡（默认视图）/ 平铺视图，全部 · 漫画 · 小说 类型过滤贯穿二级页
- 智能书夹：未读 / 在读 / 读完 / 收藏 / 有书签 / 标签
- 全库搜索：书名与小说全文检索，格式 / 书夹 / 标签多面板过滤
- 重复检测、回收站（含连文件删除与仅移除两种档位）、阅读统计
- 桌面「继续阅读」小部件：封面 / 书名 / 进度，点击直达

### 漫画阅读器
- 九宫格手势分区：点击翻页、边缘拖动亮度 / 色温、长按放大镜、音量键翻页
- 双页模式（横屏自动 / 始终 / 关）+ 跨页切割 + 宽页独占铺满
- 三档翻页动画（无动画 / 平移 / 覆盖），快捷面板就地切换
- 连点快翻累加器：快速连点每击必前进、不回跳、停手即落位
- 双击缩放、条漫模式、进度与预取全链兼容

### 小说阅读器
- TXT / EPUB / MOBI（无 DRM 旧格式）解析，自定义净化规则（正则行过滤）
- 仿真翻页动画（page curl）：拖拽卷曲、点击翻页
- 划线、笔记、书签、注释浏览器、跨章全文搜索
- 挖孔 / 刘海动态避让，横屏双侧补偿

### 数据安全
- 进度节流落盘，秒退不丢页
- 全量 JSON 备份 / 恢复：书、进度、书签、注释、净化规则、偏好
- 删除进回收站，误删可救
- 崩溃自动恢复阅读位置
- 应用锁（可选）：离开 30 秒后需系统锁屏凭据解锁
- 空间清理：只清可重建缓存与孤儿封面，不碰书籍 / 进度 / 标注
- 崩溃日志：仅存本机，可在「关于与诊断」中导出分享

### 数据同步（可选，默认关闭）
- WebDAV 同步（坚果云 / 自建服务器均可），多设备书库与进度合并
- 墓碑机制合并策略，删书 / 删注释跨设备不复活
- 同步密码以 Android Keystore AES-256-GCM 加密落盘，明文不进日志
- 书内文件可选上传，上传 / 下载量限流保护流量

### 设计
- 液态玻璃材质体系：`GlassSurface` 单一入口，低配设备自动降级、几何动效完全一致
- 一镜到底开书动画：书架封面 → 阅读器，旋转 / 历史入口全路径精确
- 横屏玻璃侧栏（GlassRail）、底部玻璃栏、动态取色（Material You）

## 下载与反馈

- 安装包与更新说明：[GitHub Releases](https://github.com/a307608689-afk/Inkvol/releases)
- 问题反馈：[Issues](https://github.com/a307608689-afk/Inkvol/issues)。请附上机型、系统版本、复现步骤，以及崩溃日志
- 从旧测试包升级请先卸载（签名不同，无法覆盖安装）

### 怎么导出崩溃日志（反馈问题时必看）

应用崩溃后不会自动上传任何东西，日志只写在本机。导出步骤：

1. 重新打开 Inkvol
2. 进入 **设置 → 关于与诊断 → 崩溃日志**
3. 点对应时间的日志右侧 **分享**，发送到微信 / 邮件 / 任意文件应用
4. 把文件和「机型、系统版本、复现步骤」一起贴到 Issue

日志内容只有堆栈、版本号与设备信息，不包含书库、阅读内容或账号。

## 隐私

- **零采集**：无账号体系，无埋点，无广告 SDK，无遥测
- **零存储权限**：全部文件访问走 SAF（Storage Access Framework），只读写你授权的文件夹
- **默认零网络**：联网权限仅服务于可选的 WebDAV 同步——不在设置中配置服务器，应用不会发起任何网络请求
- 应用本体的全部功能（阅读、备份、搜索、统计）完全离线可用

完整政策见 [`docs/privacy.md`](docs/privacy.md)（与应用内「设置 → 关于与诊断 → 隐私政策」同文），首次启动需明确同意。

安装包声明的权限与用途：

| 权限 | 用途 |
|---|---|
| `INTERNET`、`ACCESS_NETWORK_STATE` | 仅可选的 WebDAV 同步（默认关闭） |
| `WAKE_LOCK`、`RECEIVE_BOOT_COMPLETED`、`FOREGROUND_SERVICE` | WorkManager 后台书库扫描调度，不常驻前台服务 |

补充说明：

- 系统云备份（若开启）可能包含书库索引、阅读进度与设置；封面、EPUB 提取图片、TXT 解析缓存与崩溃日志**不参与**云备份
- 崩溃日志仅写入本机私有目录，最多保留 5 份，需要用户在应用内主动导出

## 构建

```
JDK 17 + Android SDK（compileSdk 37）
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug

# 正式版（APK + AAB，需 keystore.properties 与 keystore/inkvol-release.jks，均不入库）
powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1
```

版本号单一真源在 `gradle.properties`（`INKVOL_VERSION_CODE` / `INKVOL_VERSION_NAME`）。
正式包必须使用自建密钥签名；缺少 `keystore.properties` 时发布构建会直接失败，避免误产出 debug 签名的包。

- minSdk 26（Android 8.0）/ targetSdk 37
- 无可执行原生库（RAR 由 junrar 纯 Java 实现），16KB page size 天然满足，无 ABI 限制
- 无需任何 API 密钥或本地配置即可构建（正式签名除外）

## 开源许可

本项目以 **[GPL-3.0](LICENSE)** 发布：任何基于本项目的衍生作品、二次分发均须以 GPL-3.0 开源。

LICENSE 末尾附带一段依 GPL §7 授予的**附加许可（junrar 例外）**：RAR/CBR 解压支持作为可选组件，单独受 junrar 库的 UnRar 许可约束，其余部分仍受 GPL-3.0 管辖。

仿真翻页模块（`com.inkvol.pagecurl`）源码移植自开源库 oleksandrbalan/pagecurl（Apache-2.0），来源与许可声明见 [`app/src/main/java/com/inkvol/pagecurl/NOTICE.md`](app/src/main/java/com/inkvol/pagecurl/NOTICE.md)。

### 第三方依赖

| 依赖 | 许可证 | 用途 |
|---|---|---|
| AndroidX / Jetpack Compose 全家桶 | Apache-2.0 | UI 框架 |
| Kotlin Coroutines / Serialization | Apache-2.0 | 并发与 JSON |
| Coil 3 | Apache-2.0 | 图片加载 |
| Apache Commons Compress | Apache-2.0 | ZIP / 7z |
| XZ for Java | Public Domain | LZMA2 |
| **junrar** | **UnRar License** | RAR4/RAR5 解压（见附加许可） |
| SLF4J NOP | MIT | 日志空实现 |
| io.github.kyant0:backdrop | Apache-2.0 | 液态玻璃原语 |
| io.github.kyant0:shapes | Apache-2.0 | 连续曲率形状 |
| **pagecurl**（源码内嵌，[oleksandrbalan/pagecurl](https://github.com/oleksandrbalan/pagecurl)） | Apache-2.0 | 小说仿真翻页动画（见 NOTICE） |

## 项目结构

```
app/src/main/java/com/inkvol/
├── app/
│   ├── data/        # 模型 / 数据库 / 仓库 / 扫描 / 导入解析 / 备份 / 同步
│   ├── ui/          # 书架 / 历史 / 设置 / 搜索 / 漫画阅读器 / 小说阅读器 / 玻璃组件
│   ├── util/        # 指纹（重复检测）/ 自然排序
│   └── widget/      # 继续阅读桌面小部件
└── pagecurl/        # 仿真翻页（移植自 oleksandrbalan/pagecurl，Apache-2.0）
```

更多设计与实现细节见 [`docs/`](docs/) 与 [`设计规范.md`](设计规范.md)。
