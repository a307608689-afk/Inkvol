# M0.5 技术验证报告

> 日期：2026-11-13 ｜ 环境：Windows 11 + JDK 17 + NDK 26.3.11579264 + 模拟器 dsh_test（Android 14 / API 34，x86_64）
> 结论：四项验证全部通过，M1 可以立项。三项关键结论已回写方案。

---

## 1. RAR5：unRAR 7.1.6 + NDK + 16KB page

### 做法

1. 从 rarlab 官方下载 `unrarsrc-7.1.6.tar.gz`（`curl` HTTP 200，可用）；
2. 按官方 `make lib` 口径编译：`-DRARDLL -DRAR_SMP -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE`；
3. NDK clang 交叉编译 arm64：`--target=aarch64-linux-android26 -fPIC -O2`；
4. 链接加 `-Wl,-soname,libunrar.so -Wl,-z,max-page-size=16384`；
5. JNI 桥 `rar5_jni.cpp` 只暴露 `Java_com_inkvol_app_archive_Rar5_nativeDllVersion`，链接 `-L. -lunrar`。

### 结果

| 项 | 结果 |
|---|---|
| `libunrar.so` | 484,464 B，4 个 LOAD 段 **align 全部 0x4000** |
| `libinkvolrar.so`（JNI 桥） | 5,416 B，导出 `Java_com_inkvol_app_archive_Rar5_nativeDllVersion`，LOAD align 0x4000 |
| NEEDED | `libunrar.so`、`libc++_shared.so`、`libm.so`、`libdl.so`、`libc.so` |

### 三个真实坑（已解决，写入 M1 约束）

1. **必须设 SONAME**：不设时 `DT_NEEDED` 会记录构建机绝对路径（`d:\...\libunrar.so`），装到设备必炸；
2. **`dll.hpp` 消费方要定义 `_UNIX`**：否则 `HANDLE/PASCAL/UINT/LPARAM` 全是未知类型（Windows 分支未启用）；
3. **`libc++_shared.so` 要随包分发**，或改用 `c++_static`；M1 用静态 STL 可少一个 1MB 级依赖。

### M1 剩余工作

- 真实 JNI API：`RAROpenArchiveEx`（打开）、`RARReadHeaderEx` 循环（列页）、`RARProcessFile(RAR_EXTRACT)`（按页解压）；
- RAR5 随机访问策略：unRAR 库本身顺序读取，按页解压需缓存已解出的页文件（L2 抽取缓存）；
- Kotlin `Rar5` 对象 + `System.loadLibrary("inkvolrar")`；
- 打包 `jniLibs/arm64-v8a/` 与 `x86_64`（模拟器联调用）。

---

## 2. SAF：批量 query vs DocumentFile（1000 文件）

### 方法

- debug 变体内置普通 `ContentProvider`（伪装文档树，1000 个虚拟 JPG）；
- 路径 A：`DocumentsContract.buildChildDocumentsUriUsingTree` + 单次 `ContentResolver.query` + 遍历 Cursor 取 5 列；
- 路径 B：`DocumentFile.fromTreeUri().listFiles()` 后逐项读 `name/length/lastModified`；
- 预热各一次后测量：A 跑 3 次取均值，B 跑 1 次（含 1000 次元数据访问）。

### 结果

| 路径 | 耗时 |
|---|---|
| A 批量 query（1000 条 × 5 列） | **168 ms** |
| B DocumentFile（listFiles + 3 次属性/项） | **1596 ms** |
| 比值 | **9.5×** |

> 说明：测试 provider 与被测代码同进程，Binder 成本被低估；真机跨进程时差距只会更大。
> 该结果直接支持方案 §5.4 的硬约束：**扫描器禁止 DocumentFile 递归，必须批量 query**。

---

## 3. 归档页解码（3000×4000 JPEG）

### 方法

- 生成 3000×4000 测试图（文字 + 渐变），JPEG q92 落盘（745,852 B）；
- `BitmapFactory.decodeFile`，`inSampleSize` 分别 1 / 2 / 4，各跑 3 次取均值。

### 结果

| 采样 | 平均耗时 | 说明 |
|---|---|---|
| full（3000×4000） | **97.0 ms** | 全尺寸 ARGB 约 48MB |
| sample=2（1500×2000） | **60.0 ms** | 约 12MB |
| sample=4（750×1000） | **38.0 ms** | 约 3MB |

### 结论

- 任何一档解码都 **远大于一帧 16ms**，翻页不可能等解码；
- M3 的三级缓存（Bitmap LRU + 预取 ±2）是硬需求，不是优化项；
- 按屏幕分辨率降采样（sample=2）比全尺寸快 38%，配合预取可做到"翻页时位图已在内存"。

---

## 4. PDF：PdfRenderer

### 结果

- 生成 30 页 A4 PDF（29,922 B）；
- 渲染 5 页到 1080×1500：**平均 11.2 ms/页**；
- API 行为正常，PdfRenderer 可满足按需渲染 + 位图缓存。

---

## 5. 对方案的修订

| 项 | 修订 |
|---|---|
| RAR5 打包 | M1 必须设 SONAME、定义 `_UNIX`、决定 STL 静态/动态、补齐 x86_64 |
| SAF 扫描 | 批量 query 为唯一实现；DocumentFile 仅作兜底且限小目录（≤200 项） |
| 解码 | L1/L2 缓存与预取为 M3 必做项；解码档位按屏幕尺寸 |
| PDF | 按需渲染 + LRU 页缓存；不需要预转图片 |
| 性能 SLO | 解码 38~97ms（模拟器）作为基线，真机目标 ≤ 1/2 |

---

## 6. 产物索引

| 文件 | 说明 |
|---|---|
| `tools/spike/build-unrar.ps1` | unRAR arm64 编译脚本（16KB + SONAME） |
| `tools/spike/build-rar5-jni.ps1` | JNI 桥编译/链接与符号检查 |
| `tools/spike/rar5_jni.cpp` | 最小 JNI 桥 |
| `tools/spike/build-arm64/libunrar.so` | 484,464 B，16KB 对齐 |
| `tools/spike/build-arm64/libinkvolrar.so` | 5,416 B，JNI 导出 |
| `app/src/androidTest/.../M05SpikesTest.kt` | SAF/解码/PDF 基准 |
| `app/src/debug/.../BenchDocumentsProvider.kt` | 1000 文件测试 provider |
