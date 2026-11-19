<div align="center">

# Inkvol

**本地漫画 + 小说双引擎阅读器 · 纯离线 · 零权限 · 零采集**

Kotlin · Jetpack Compose · Material 3 · 液态玻璃设计体系

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
| 要联网、要账号、要全套存储权限，内置广告或推荐流 | **纯本地、零网络、零采集**：不申请 INTERNET 权限，不申请存储权限，主界面没有任何在线入口。书在你手里，数据不出设备 |
| 漫画装一个 App、小说装一个 App，还总有格式打不开 | **双引擎单应用**：散页文件夹 / ZIP·CBZ / 7z·CB7 / PDF / RAR4·CBR / **RAR5** + TXT / EPUB |
| 翻页卡顿、快速连点丢击、动画打断阅读节奏 | **零延迟翻页**：连点快翻自动累加落点精确；翻页动画三档（无动画 / 平移 / 覆盖）随点随切、即时生效 |
| 删书手抖、进度神秘丢失、换机迁移靠拷文件夹碰运气 | **数据永不丢**：进度节流落盘、删除进回收站、一键全量 JSON 备份恢复（书 + 进度 + 书签 + 注释 + 净化规则 + 偏好）、崩溃可恢复 |
| 小说里全是广告行、灌水句，或者全是不能跳过的冗长描写 | **净化规则**：自定义正则行过滤，从源头删掉不想看的内容；划线 / 笔记 / 书签 / 注释浏览器 / 全文搜索齐备 |
| UI 千篇一律，横屏要么挤要么闲 | **液态玻璃设计体系**（动态模糊 + 连续曲率形状）+ 一镜到底开书动画 + 横屏玻璃侧栏 + 动态取色 |

## 功能

### 书架与书库
- 多书库管理：SAF 托管文件夹（零权限）、复制入库、单文件导入，随时增删
- 系列聚合卡（默认视图）/ 平铺视图，全部 · 漫画 · 小说 类型过滤贯穿二级页
- 智能书夹：未读 / 在读 / 读完 / 收藏 / 有书签 / 标签
- 重复检测、回收站（含连文件删除与仅移除两种档位）、阅读统计
- 桌面「继续阅读」小部件：封面 / 书名 / 进度，点击直达

### 漫画阅读器
- 九宫格手势分区：点击翻页、边缘拖动亮度 / 色温、长按放大镜、音量键翻页
- 双页模式（横屏自动 / 始终 / 关）+ 跨页切割 + 宽页独占铺满
- 三档翻页动画（无动画 / 平移 / 覆盖），快捷面板就地切换
- 连点快翻累加器：快速连点每击必前进、不回跳、停手即落位
- 双击缩放、条漫模式、进度与预取全链兼容

### 小说阅读器
- TXT / EPUB 双解析，自定义净化规则（正则行过滤）
- 划线、笔记、书签、注释浏览器、跨章全文搜索
- 挖孔 / 刘海动态避让，横屏双侧补偿

### 数据安全
- 进度节流落盘，秒退不丢页
- 全量 JSON 备份 / 恢复：书、进度、书签、注释、净化规则、偏好
- 删除进回收站，误删可救
- 崩溃自动恢复阅读位置

### 设计
- 液态玻璃材质体系：`GlassSurface` 单一入口，低配设备自动降级、几何动效完全一致
- 一镜到底开书动画：书架封面 → 阅读器，旋转 / 历史入口全路径精确
- 横屏玻璃侧栏（GlassRail）、底部玻璃栏、动态取色（Material You）

## 隐私

- **零网络**：应用不持有 INTERNET 权限，无任何遥测 / 上报 / 在线服务
- **零采集**：无账号体系，无埋点，无广告 SDK
- **零存储权限**：全部文件访问走 SAF（Storage Access Framework），只读你授权的文件夹
- 当前版本不存在任何可选的联网能力

## 构建

```
JDK 17 + Android SDK（compileSdk 37）
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
```

- minSdk 26（Android 8.0）/ targetSdk 37 / 16KB page size / arm64
- 无需任何 API 密钥或本地配置即可构建

## 开源许可

本项目以 **[GPL-3.0](LICENSE)** 发布：任何基于本项目的衍生作品、二次分发均须以 GPL-3.0 开源。

LICENSE 末尾附带一段依 GPL §7 授予的**附加许可（junrar 例外）**：RAR/CBR 解压支持作为可选组件，单独受 junrar 库的 UnRar 许可约束，其余部分仍受 GPL-3.0 管辖。

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

## 项目结构

```
app/src/main/java/com/inkvol/app/
├── data/            # 模型 / 数据库 / 仓库 / 扫描 / 导入解析 / 备份
├── ui/              # 书架 / 历史 / 设置 / 漫画阅读器 / 小说阅读器 / 玻璃组件
├── util/            # 指纹（重复检测）/ 自然排序
└── widget/          # 继续阅读桌面小部件
```

更多设计与实现细节见 [`docs/`](docs/) 与 [`设计规范.md`](设计规范.md)。
