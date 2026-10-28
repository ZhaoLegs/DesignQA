# Spec Inspector（调试版集成库）

让 Spec 设计走查工具读到你 App 里文字/控件的**精确属性**（字重、字体族、字号、行高、字间距、颜色、圆角、描边），
而不是从截图推测。Spec 检测到该库后，检查卡片会显示绿色「精确」标记，数值不再带「≈」。

## 接入（一行）

**方式一：源码模块**（把 `spec-inspector/` 目录拷进工程）

```kotlin
// settings.gradle.kts
include(":spec-inspector")

// app/build.gradle.kts
dependencies {
    debugImplementation(project(":spec-inspector"))
}
```

**方式二：AAR**（把 `spec-inspector-release.aar` 放到 `app/libs/`）

```kotlin
dependencies {
    debugImplementation(files("libs/spec-inspector-release.aar"))
}
```

无需改任何代码：库通过 Manifest 里的 ContentProvider 自动初始化（无需在 Application 里调用）。
只用 `debugImplementation`，**release 包里完全不包含**。

要求：minSdk 21+；字重精确值需要 Android 9（API 28）+，系统字体族名在 Android 14+ 最可靠。

## 工作原理

- 记录当前 Activity 窗口（ActivityLifecycleCallbacks），并尝试通过反射读取 Dialog/PopupWindow 等其他窗口。
- 暴露 `content://<applicationId>.specinspector/dump`（全部可见 View）和 `/at?x=&y=`（某点下的 View）。
- 在主线程读取（Handler + CountDownLatch，1.5 秒超时），所有逻辑都包在 try/catch 里，不会让宿主 App 崩溃。

读取的内容：

| 类型 | 字段 |
| --- | --- |
| TextView | 屏幕坐标、字号 px、字重（Typeface.getWeight；textStyle=bold/fakeBold 时 400 → 700）、系统字体族名、res/font 资源名（尽力而为）、字间距（em 和 px）、行高 px、lineSpacingExtra/Multiplier、文字颜色、includeFontPadding、gravity、maxLines |
| 任意 View | 坐标、类名、资源 id 名、透明度、背景色、圆角、描边宽度/颜色（GradientDrawable；MaterialShapeDrawable 通过反射） |
| Compose | 尽力而为：通过反射遍历语义树，读取 TextLayoutResult 的样式（字号、字重、字间距、行高、fontFamily）；依赖 Compose 内部 API，未在真机验证，可能随版本失效 |

## 隐私与安全

- 只有包名为 `com.legs.designqa`（Spec）的 App 或宿主 App 自身可以查询，其他调用方拿到的是空结果。
- **不返回文字内容**，只返回样式和位置。
- 只在 debug 构建中存在；不要用 `implementation` 接入正式包。
