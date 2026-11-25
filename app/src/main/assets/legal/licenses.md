# Inkvol 开源许可

Inkvol 以 **GNU General Public License v3.0（GPL-3.0）** 发布。

- 源码地址：https://github.com/a307608689-afk/Inkvol
- 完整许可证文本：应用内「设置 → 关于与诊断 → GPL-3.0 全文」，或仓库根目录 `LICENSE`

## 附加许可（GPL §7 · junrar 例外）

RAR / CBR 解压能力作为可选组件依赖 junrar 库，该库受 **UnRAR License** 约束。依据 GPL-3.0 第 7 条，本项目在 `LICENSE` 末尾授予附加许可：仅 junrar 及其解压组件受 UnRAR License 约束，项目其余部分仍完整适用 GPL-3.0。

## 第三方依赖

| 依赖 | 许可证 | 用途 |
|---|---|---|
| AndroidX / Jetpack Compose 全家桶 | Apache-2.0 | UI 框架、生命周期、导航、DataStore、WorkManager |
| Kotlin Coroutines / Serialization | Apache-2.0 | 并发与 JSON |
| Coil 3 | Apache-2.0 | 图片加载 |
| Apache Commons Compress | Apache-2.0 | ZIP / 7z 解压 |
| XZ for Java | Public Domain | LZMA2 |
| junrar | UnRAR License | RAR4 / RAR5 解压（见上方附加许可） |
| SLF4J NOP | MIT | 日志空实现 |
| io.github.kyant0:backdrop | Apache-2.0 | 液态玻璃原语 |
| io.github.kyant0:shapes | Apache-2.0 | 连续曲率形状 |
| pagecurl（源码内嵌，oleksandrbalan/pagecurl） | Apache-2.0 | 小说仿真翻页动画（见 `app/src/main/java/com/inkvol/pagecurl/NOTICE.md`） |

各依赖的完整许可证文本随其发布物提供；如需特定组件的许可全文，可通过项目 Issues 索取。

## 免责

本应用仅供个人学习、交流与非商业使用。应用内展示的全部书籍内容来自你自行导入的文件，开发者不提供、不传播任何书籍内容，也不对其合法性负责。请仅处理你拥有合法权益的文件。
