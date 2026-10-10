# 插件宿主 3.6 同步记录

川职·知学的常用页面提供本校课表、成绩与学分。底层插件宿主仍需与上游 SDK 保持一致，正式发布继续执行原来的线上校验。

本次宿主依据 [zhengfang-apk 的固定提交](https://github.com/znjhahaha/zhengfang-apk/commit/3cd767ed2c2f0eb53b4e152a06a4d1f838e2ccc5) 同步真实实现，涉及原生页面参数、输入状态、独立网页、用户脚本、设备和位置服务。自己的导航、登录、反馈与学校数据接口保留。GeckoView 和地图仅在对应宿主功能使用时启动；学校 CAS 登录继续使用系统 WebView。

| 项目 | 对应版本 |
| --- | --- |
| SDK / 契约 | 3.6.0 |
| API | 3 |
| 审核规则 | 2026-10-10.2 |
| Bootstrap | 1 |

`python scripts/check_plugin_platform.py` 会校验线上平台元数据、SDK 开发包、Starter、宿主资源、文档和安全规则。它使用线上校验路径，未改成只检查本机文件，也未跳过正式发布门槛。

线上 SDK 包 SHA-256 为 `9e6f0aea432ee3fb9b1f33be77d9f3b7a8fdfc90ea8b43270bfebc57c7b2d9e9`，Starter 为 `4a25e57a4c801738c378069f179ae4df7f59ab195d290ac9faddfbd7e7530299`。

审核工具链依据[上游公开自测](https://github.com/znjhahaha/zhengfang-apk/actions/runs/38044088428)及其 `audit-toolchain` 回执核对：提交与上述固定源码相同，3.6.0 / API 3 / 2026-10-10.2 匹配，记录 90 项通过。下载回执 ZIP 的 SHA-256 为 `08e8c960b8d81ba13556e8f4ff5b5231045982aa56dd4d37c5d7628e79fb44a5`。

这是对已有上游工具链回执的核对，本轮没有在用户设备运行应用测试，也没有提交开发者插件作为测试样本。应用编译与安装包检查、学校实际认证和界面验收是不同的验证范围，结果另见根目录 `VERIFICATION.md`。平台以后再次更新时，正式发布仍须重新核对。

## 原生库打包

GitHub 提供包含现有各架构的 APK。GeckoView、MapLibre 和 ONNX 的原生库使用压缩打包，安装时由 Android 提取设备对应的库；没有删去浏览器、地图或识别功能，也没有调整界面动效。

安装包逐项检查 ZIP 对齐及 ELF LOAD 段。[Android 官方 16 KB 指引](https://developer.android.com/guide/practices/page-sizes)要求检查 `arm64-v8a` 和 `x86_64` 库的 16 KiB 对齐；保留的 32 位库按 4 KiB 检查。最初把同一阈值应用到所有架构导致了误报，已修正并重新检查。本轮没有在 16 KiB 设备运行应用，不能把静态对齐检查写成实机兼容验证。
