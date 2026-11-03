# 测试

当前可自动运行的检查是 `app` 模块单元测试：

```bash
./gradlew :app:testDebugUnitTest
```

`AnnotationOptionsTest` 覆盖标注开关的独立切换、旧偏好迁移，以及锁屏时隐藏悬浮层。`RowShapeTest` 覆盖重复行结构签名、字体差异保留和间距几何去重。

`tests/test_*.sh` 是针对旧 APK 的 smali 检查（固定寄存器、源行号和已删除的设计稿入口）。它们不是当前界面的验收入口，也不计入通过数。仓库里没有 `verify_current.sh` 或 `tests/*.py`。

自动测试不替代真机验收。仍需在已安装对应版本、解锁且无障碍服务已绑定的设备上检查点击、滚动、锁屏恢复、截图和相册文件。截图相关能力需要 Android 11 及以上。
