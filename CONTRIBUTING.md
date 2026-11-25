# 参与开发

## 环境

- JDK 17
- Android SDK（compileSdk 37）
- Windows 下用 `gradlew.bat`，其余平台用 `./gradlew`

```powershell
# 调试包
.\gradlew.bat assembleDebug

# 正式包（需要 keystore，见下）
powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1
```

## 代码风格

- Kotlin + Jetpack Compose，跟随现有文件结构，不引入新框架除非必要
- UI 必须遵守 `设计规范.md`：玻璃体系用 `GlassOverlay`，禁止手写 `background + border` 仿玻璃、禁止 `AlertDialog` 式独立窗口
- 注释写“为什么”，不写“做了什么”
- 不改包名、不改签名配置、不动版本号（版本号只在发版时改）

## 提交

```
feat: 新增 …
fix: 修复 …
docs: 文档
chore: 构建/依赖/清理
release: 1.0.1
```

一次提交只做一件事；涉及行为变化请在正文说明影响面与自测方式。

## Pull Request

- 描述改动、影响面、自测结果
- 涉及 UI 请附截图；涉及性能请附前后数据
- 用 `.github/pull_request_template.md` 的清单逐项确认

## 安全红线（必读）

**绝对不要提交或粘贴：**

- `keystore/`、`*.jks`、`keystore.properties`、任何口令
- `local.properties`、含本机绝对路径的脚本
- 真实书籍/漫画、含版权内容的截图或录屏
- `*.db`、备份 JSON、崩溃日志（可能含书名与阅读记录）
- WebDAV 账号/密码、GitHub token、`gh` 凭据

**提交前自检：**

```powershell
git status --short
git ls-files | Select-String 'keystore|\.jks|local\.properties|\.db$|test_samples|^dist/|^logs/'
git grep -n -i -E 'storePassword|keyPassword|BEGIN (RSA|PRIVATE) KEY|ghp_|github_pat_'
```

完整规则见 `docs/发版规范.md` 第 5 节。

## 安全漏洞

不要开公开 Issue。请通过 GitHub 的私密漏洞报告（Security → Report a vulnerability）提交，说明影响版本、复现方式与影响面。
