# Spec

中文 | [English](#spec-english)

Android 设计验收工具：在真机上叠加标注，检查尺寸、间距、字体、颜色和圆角。

悬浮球 · 元素检查 · 全屏标注

当前版本：**2.0**（versionCode 26）。已发布安装包见 [GitHub Releases](https://github.com/ZhaoLegs/DesignQA/releases)。

---

## 功能

- **悬浮球**：随时开启 / 关闭检查与标注；长按可退出当前模式并清空标注
- **元素检查**：点选控件查看尺寸、间距、字号、字重、行高、字间距、颜色、圆角、描边等
- **字体识别**：按系统字体回退链读取真实字体文件名；中文 / 英文 / 数字可分开显示
- **字重**：被测应用以 debug 依赖接入 `spec-inspector` 时显示精确字重（如 `500 Medium`），信息卡副标题为「精确」。未接入时按截图笔画宽度估算，并明确标成 `≈` 或「(估)」，不会把估算值当成精确值
- **信息卡片**：核心属性网格展示，自动避开选中元素与标注区域
- **显示开关**：尺寸、间距、字体等开关在各模式下均可操作（检查模式下标注开关暂停，卡片仍展示完整信息）

## 环境要求

- Android 9 及以上（API 28+）可安装并做结构检查、尺寸与间距
- **截图取色、描边、圆角和保存图片需要 Android 11（API 30）及以上**
- 需授予**无障碍**权限（用于读取界面节点并截屏）
- 悬浮窗权限（用于悬浮球与叠加层）
- 可选：电池优化豁免，降低系统冻结无障碍服务的概率

## 快速开始

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

仓库不包含签名密钥，也没有 release `signingConfig`。上面的 debug 包使用 debug 签名，可直接安装。安装后打开 Spec → 打开无障碍服务 → 允许悬浮窗 → 用悬浮球开始检查。

## 工程结构

| 路径 | 说明 |
|------|------|
| `app/` | Spec 主应用（`com.legs.designqa`） |
| `spec-inspector/` | 可选调试库，为目标 App 提供精确字重 |
| `spec-sample/` | 示例 App，方便自测检查能力 |
| `tests/` | 历史设备脚本；当前可自动运行的是 `app` 模块单元测试 |

## 给被测 App 接入字重（可选）

把 `spec-inspector` 以 debug 依赖接入被测应用后，Spec 才能读到精确字重。未接入时信息卡仍会给出带 `≈` 的估算字重。只用 `debugImplementation`，正式包里不要带上这个库。详见 [`spec-inspector/README.md`](spec-inspector/README.md)。

## 隐私与权限

Spec 没有 `INTERNET` 权限，不会上传界面、截图或字体。无障碍说明亦写明：只在本机读取当前界面的控件结构与截图，用于测量与取色。

| 权限 | 用途 |
|------|------|
| 无障碍服务 | 读取其他应用的界面节点，并在 Android 11+ 截屏 |
| 悬浮窗 | 绘制悬浮球、标注层和信息卡 |
| `QUERY_ALL_PACKAGES` | 在本机打开被测应用 APK，解析其内置字体文件名；不上传 |
| 前台服务（special use） | 保持无障碍检查与截图可用，并显示一条低优先级通知 |
| 忽略电池优化 | 可选。由用户在系统设置里确认，避免服务被冻结 |
| 写入外部存储（仅 API 28） | 旧系统保存图片。Android 10 及以上通过 MediaStore 写入 `Pictures/Spec`，不需要该权限 |

`spec-inspector` 的 ContentProvider 仅允许 Spec（`com.legs.designqa`）或宿主应用自身查询，并且不返回文字内容。

## 开源协议

应用代码为 [MIT](LICENSE)。`spec-sample` 内的 `tibetan_fit.ttf`（Tibetan Fit Demo）是受 SIL Open Font License 1.1 约束的字体，见 [`spec-sample/OFL-TibetanFitDemo.txt`](spec-sample/OFL-TibetanFitDemo.txt)，不随 MIT 再授权。

---

<a id="spec-english"></a>

# Spec (English)

[中文](#spec) | English

Android design QA overlay: inspect sizes, spacing, fonts, colors, and corner radii on a real device.

Floating button · element inspection · full-screen annotation

Current version: **2.0** (versionCode 26). Installable builds are on [GitHub Releases](https://github.com/ZhaoLegs/DesignQA/releases).

## Features

- **Floating button**: toggle inspection / annotation; long-press exits the active mode and clears annotations
- **Element inspection**: tap a view to see size, spacing, font size, weight, line height, letter spacing, color, radius, stroke, and more
- **Font names**: resolve the real font file via the system fallback chain; Chinese / Latin / digits can be shown separately
- **Font weight**: exact weight (for example `500 Medium`) and an “精确” subtitle when the target app depends on `spec-inspector` in debug. Without that library, weight is estimated from screenshot stroke width and labeled `≈` or “(估)” — an estimate is never presented as exact
- **Info card**: core attributes in a compact grid; auto-positions so it does not cover the selection or annotations
- **Display toggles**: size, spacing, font, and the other chips stay available outside inspect mode. Inspect mode pauses those chips and still shows the full card

## Requirements

- Android 9+ (API 28) to install and inspect structure, size, and spacing
- **Screenshots, sampled color, stroke, corner radius, and saving images require Android 11+ (API 30)**
- **Accessibility** permission (to read UI nodes and capture the screen)
- Overlay / draw-over-other-apps permission (floating button and overlays)
- Optional: battery-optimization exemption so the system is less likely to freeze the service

## Quick start

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

This repo has no keystore and no release `signingConfig`. The debug APK above is debug-signed and installable. Open Spec → enable the accessibility service → allow overlay → use the floating button to inspect.

## Project layout

| Path | Purpose |
|------|---------|
| `app/` | Spec app (`com.legs.designqa`) |
| `spec-inspector/` | Optional debug library for exact font weight in target apps |
| `spec-sample/` | Sample app for trying inspection |
| `tests/` | Historical device scripts. The tests you can run today are the `app` unit tests |

## Exact font weight in target apps (optional)

Add `spec-inspector` as a debug dependency in the app under test so Spec can read exact weights. Without it, the info card still shows an estimated weight marked `≈`. Use `debugImplementation` only; do not ship the library in release builds. See [`spec-inspector/README.md`](spec-inspector/README.md).

## Privacy and permissions

Spec has no `INTERNET` permission and does not upload UI trees, screenshots, or fonts. The accessibility service description states the same: structure and screenshots stay on device for measurement and color sampling.

| Permission | Why |
|------------|-----|
| Accessibility service | Read another app’s nodes and, on Android 11+, take screenshots |
| Overlay | Draw the floating button, annotations, and info card |
| `QUERY_ALL_PACKAGES` | Open the target APK on device and parse bundled font file names. Nothing is uploaded |
| Foreground service (special use) | Keep inspection and capture available, with one low-priority notification |
| Ignore battery optimizations | Optional, confirmed by the user in system settings |
| Write external storage (API 28 only) | Save images on Android 9. Android 10+ writes `Pictures/Spec` through MediaStore without that permission |

The `spec-inspector` content provider accepts queries only from Spec (`com.legs.designqa`) or the host app itself, and it does not return text content.

## License

Application code is [MIT](LICENSE). `tibetan_fit.ttf` in `spec-sample` (Tibetan Fit Demo) is a font under the SIL Open Font License 1.1; see [`spec-sample/OFL-TibetanFitDemo.txt`](spec-sample/OFL-TibetanFitDemo.txt). It is not relicensed as MIT.
