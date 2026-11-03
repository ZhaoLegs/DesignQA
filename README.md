# Spec

中文 | [English](#spec-english)

Android 设计验收工具：在真机上叠加标注，检查尺寸、间距、字体、颜色和圆角。

悬浮球 · 元素检查 · 全屏标注 · 设计稿对比

---

## 功能

- **悬浮球**：随时开启 / 关闭检查与标注；长按可退出当前模式并清空标注
- **元素检查**：点选控件查看尺寸、间距、字号、字重、行高、字间距、颜色、圆角、描边等
- **字体识别**：按系统字体回退链读取真实字体文件名；中文 / 英文 / 数字可分开显示
- **字重**：接入 `spec-inspector` 调试库时显示精确字重（如 `500 Medium`）；未接入则提示需接入调试库
- **信息卡片**：核心属性网格展示，自动避开选中元素与标注区域
- **显示开关**：尺寸、间距、字体等开关在各模式下均可操作
- **设计稿叠加**：对照设计稿做视觉比对（应用内相关入口）

## 环境要求

- Android 9 及以上（API 28+）
- 需授予**无障碍**权限（用于读取界面节点）
- 悬浮窗权限（用于悬浮球与叠加层）

## 快速开始

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

安装后打开 Spec → 打开无障碍服务 → 允许悬浮窗 → 用悬浮球开始检查。

## 工程结构

| 路径 | 说明 |
|------|------|
| `app/` | Spec 主应用（`com.legs.designqa`） |
| `spec-inspector/` | 可选调试库，为目标 App 提供精确字重 |
| `spec-sample/` | 示例 App，方便自测检查能力 |
| `tests/` | 自动化 / 回归脚本 |

## 给被测 App 接入字重（可选）

把 `spec-inspector` 以 debug 依赖接入被测应用后，Spec 才能读到精确字重；否则信息卡会提示「需接入调试库」，不会用估算值糊弄。

## 开源协议

[MIT](LICENSE)

---

<a id="spec-english"></a>

# Spec (English)

[中文](#spec) | English

Android design QA overlay: inspect sizes, spacing, fonts, colors, and corner radii on a real device.

Floating button · element inspection · full-screen annotation · design overlay

## Features

- **Floating button**: toggle inspection / annotation; long-press exits the active mode and clears annotations
- **Element inspection**: tap a view to see size, spacing, font size, weight, line height, letter spacing, color, radius, stroke, and more
- **Font names**: resolve the real font file via the system fallback chain; Chinese / Latin / digits can be shown separately
- **Font weight**: exact weight (e.g. `500 Medium`) when the target app integrates `spec-inspector`; otherwise shows a prompt to integrate the debug library — never a guessed “≈” weight
- **Info card**: core attributes in a compact grid; auto-positions so it does not cover the selection or annotations
- **Display toggles**: size, spacing, font, etc. stay usable in every mode
- **Design overlay**: compare the live UI against a design mock

## Requirements

- Android 9+ (API 28)
- **Accessibility** permission (to read UI nodes)
- Overlay / draw-over-other-apps permission (floating button & overlays)

## Quick start

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open Spec → enable the accessibility service → allow overlay → use the floating button to inspect.

## Project layout

| Path | Purpose |
|------|---------|
| `app/` | Spec app (`com.legs.designqa`) |
| `spec-inspector/` | Optional debug library for exact font weight in target apps |
| `spec-sample/` | Sample app for trying inspection |
| `tests/` | Automation / regression scripts |

## Exact font weight in target apps (optional)

Add `spec-inspector` as a debug dependency in the app under test so Spec can read exact weights. Without it, the info card shows a “integrate debug library” hint instead of inventing a weight.

## License

[MIT](LICENSE)
