# Pgyer APK Uploader (Android Studio / IntelliJ Plugin)

中文 | [English](#english)

面向 **Android Studio**（及 IntelliJ IDEA）的插件：在 IDE 内选择 APK（或检测最近构建产物），上传到 [蒲公英 Pgyer](https://www.pgyer.com)，并在成功后展示安装页链接。

## 功能

- Tools 菜单 / 项目视图右键：上传 APK 到蒲公英
- 右侧 Tool Window：**Pgyer Upload**（选文件、检测 `build/outputs/apk`、填写描述与安装方式）
- 设置页保存 **API Key**（PasswordSafe / 系统钥匙串），以及默认安装方式、密码、更新说明
- 上传进度条（百分比 + 阶段文案）+ 成功/失败状态提示；成功后在 Tool Window / 弹窗展示应用名、版本、构建号、安装页与二维码

## 获取蒲公英 API Key

1. 登录 [https://www.pgyer.com](https://www.pgyer.com)
2. 打开 [API 信息](https://www.pgyer.com/account/api)
3. 复制 **API Key**
4. 在 IDE：**Settings / Preferences → Tools → Pgyer APK Uploader** 中粘贴保存

> 当前开放接口（API 2.0）上传鉴权参数为 `_api_key`。旧版 User Key **不是**本插件上传流程所必需。  
> 示例配置见 [`docs/settings.example.md`](docs/settings.example.md)。**切勿**把真实密钥提交到仓库。

## 上传参数（v1）

| 参数 | 说明 |
| --- | --- |
| APK 文件 | 必选 |
| Update description | 对应 `buildUpdateDescription` |
| App description | 对应 `buildDescription` |
| Install type | 1 公开 / 2 密码 / 3 邀请 |
| Install password | 密码安装时使用 |

实现基于蒲公英快速上传：`getCOSToken` → COS 上传 → 轮询 `buildInfo`。文档：https://www.pgyer.com/doc/view/api_upload

## 构建与在 Android Studio 中运行

### 环境要求（务必对齐）

| 组件 | 要求 |
| --- | --- |
| **运行 Gradle 的 JDK** | **17 或 21（推荐 21）** |
| Gradle Wrapper | **9.0.0**（由仓库自带，勿用本机旧 Gradle） |
| IntelliJ Platform Gradle Plugin | **2.19.0** |
| 编译 toolchain | JDK **17**（`jvmToolchain(17)`） |
| IDE | Android Studio / IntelliJ IDEA（建议 2024.3+） |

> **常见错误**：`Type T not present` / `Could not create task ':test'`  
> 原因几乎都是用 **JDK 24+** 跑 **Gradle 8.x**，或 Plugin / Gradle 版本不匹配。  
> 处理：安装 JDK 21，并让 IDE 的 **Gradle JVM** 指向它；或设置  
> `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`（macOS）后执行 `./gradlew …`。  
> 仓库已通过 `gradle/gradle-daemon-jvm.properties` 将 Daemon 固定为 **toolchain 21**。

> **上传错误**：`HTTP/1.1 header parser received no bytes`  
> 多为上传链路被对端提前关闭（旧实现用 chunked 上传 COS 时易触发）。当前版本已改为 **固定 Content-Length** 上传并自动重试；若仍失败，请检查 VPN/代理/防火墙是否放行 `pgyer.com` 与 COS 域名。

> **上传错误**：`COS upload failed with HTTP 400`  
> COS 拒绝了 multipart 表单（字段顺序/多余字段/文件名）。当前实现按官方示例只提交  
> `key → signature → x-cos-security-token → x-cos-meta-file-name → file`，并用 urlencoded 获取 token；错误信息会附带 COS 返回正文便于排查。

### 用 Gradle 构建

```bash
# 确认 Gradle JVM 为 17/21
java -version
./gradlew --version

./gradlew test buildPlugin
```

产物：`build/distributions/pgyer-apk-uploader-*.zip`

### 安装到 Android Studio

1. **Settings → Plugins → ⚙️ → Install Plugin from Disk…**
2. 选择上一步生成的 zip
3. 重启 IDE
4. 配置 API Key 后使用 **Tools → Upload APK to Pgyer** 或 Tool Window **Pgyer Upload**

### 调试（runIde）

```bash
./gradlew runIde
```

会启动一个带本插件的 IDE 沙箱。若要针对本机 Android Studio 调试，可在 `build.gradle.kts` 的 `intellijPlatform { ... }` 中改为本地安装路径（参见 [Android Studio Plugin Development](https://plugins.jetbrains.com/docs/intellij/android-studio.html)）。

### 从源码在 IDE 中打开

1. 用 IntelliJ IDEA / Android Studio 打开本仓库根目录
2. 信任并导入 Gradle 项目
3. 运行 Gradle 任务 `runIde` 或 `buildPlugin`

## 项目结构

```
src/main/kotlin/com/pgyer/ide/apkuploader/
  actions/          # Tools 菜单与右键操作
  notifications/    # 成功/失败通知
  settings/         # Configurable + PasswordSafe
  ui/               # Dialog + Tool Window
  upload/           # Pgyer API 客户端与上传任务
src/main/resources/META-INF/plugin.xml
```

## 安全说明

- API Key 与安装密码写入 **PasswordSafe**，不写入明文配置文件
- 仓库内仅提供示例说明，无真实密钥
- `.gitignore` 已忽略常见密钥文件模式

## License

MIT（示例用途；请按你的组织策略调整）

---

<a id="english"></a>

# English

IntelliJ Platform plugin for **Android Studio** / IntelliJ IDEA that uploads APK files to [Pgyer](https://www.pgyer.com).

## Features

- **Tools** menu and Project View context action to upload an APK
- Tool window **Pgyer Upload**: browse APK, detect recent `build/outputs/apk` artifacts, set description / install type / password
- Settings page for **API Key** (PasswordSafe), default install options
- Progress bar (percent + stage text) with success/failure status; success view shows app name, version, build number, install page, and QR code

## Get a Pgyer API Key

1. Sign in at [https://www.pgyer.com](https://www.pgyer.com)
2. Open [API info](https://www.pgyer.com/account/api)
3. Copy the **API Key**
4. In the IDE: **Settings / Preferences → Tools → Pgyer APK Uploader**

> API 2.0 authenticates uploads with `_api_key` only. Legacy **User Key** is not required by this plugin.  
> See [`docs/settings.example.md`](docs/settings.example.md). **Never commit real secrets.**

## Upload parameters (v1)

| Parameter | Notes |
| --- | --- |
| APK file | Required |
| Update description | `buildUpdateDescription` |
| App description | `buildDescription` |
| Install type | 1 public / 2 password / 3 invite |
| Install password | When install type is password |

Uses Pgyer fast upload: `getCOSToken` → COS upload → poll `buildInfo`. Docs: https://www.pgyer.com/doc/en/view/api_upload

## Build & run in Android Studio

### Requirements (keep these aligned)

| Component | Required |
| --- | --- |
| **JDK used to run Gradle** | **17 or 21 (21 recommended)** |
| Gradle Wrapper | **9.0.0** (use `./gradlew`, not a system Gradle) |
| IntelliJ Platform Gradle Plugin | **2.19.0** |
| Compile toolchain | JDK **17** (`jvmToolchain(17)`) |
| IDE | Android Studio / IntelliJ IDEA (2024.3+ recommended) |

> **Common failure**: `Type T not present` / `Could not create task ':test'`  
> Almost always means **JDK 24+** is running **Gradle 8.x**, or plugin/Gradle versions are mismatched.  
> Fix: install JDK 21 and point the IDE **Gradle JVM** at it (or set `JAVA_HOME` to JDK 21) before `./gradlew …`.  
> This repo pins the Gradle Daemon via `gradle/gradle-daemon-jvm.properties` (`toolchainVersion=21`).

> **Upload error**: `HTTP/1.1 header parser received no bytes`  
> Usually the peer closed the connection early (chunked COS uploads were a common trigger). Current builds use **fixed Content-Length** uploads with retries; if it persists, check VPN/proxy/firewall access to `pgyer.com` and the COS host.

> **Upload error**: `COS upload failed with HTTP 400`  
> COS rejected the multipart form (field order / extra fields / filename). The client now posts only  
> `key → signature → x-cos-security-token → x-cos-meta-file-name → file` (official order) and uses urlencoded `getCOSToken`; the error text includes the COS response body.

### Build the plugin zip

```bash
java -version
./gradlew --version

./gradlew test buildPlugin
```

Output: `build/distributions/pgyer-apk-uploader-*.zip`

### Install into Android Studio

1. **Settings → Plugins → ⚙️ → Install Plugin from Disk…**
2. Select the zip
3. Restart the IDE
4. Set API Key, then use **Tools → Upload APK to Pgyer** or the **Pgyer Upload** tool window

### Debug with runIde

```bash
./gradlew runIde
```

Launches a sandboxed IDE with the plugin. To target a local Android Studio install, configure `intellijPlatform` / local IDE path per the [Android Studio plugin docs](https://plugins.jetbrains.com/docs/intellij/android-studio.html).

### Open from source

1. Open this repository root in IntelliJ IDEA / Android Studio
2. Import the Gradle project
3. Run `runIde` or `buildPlugin`

## Security

- API Key and install password are stored via **PasswordSafe**
- Only example settings are documented in-repo — no real keys
- `.gitignore` excludes common secret file patterns
