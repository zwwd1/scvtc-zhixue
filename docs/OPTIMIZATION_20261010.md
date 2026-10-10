# 2026-10-10 · 1.2.0 维护记录

本表对应本次消息中的 IMG-01～IMG-17；IMG-18 是 IMG-04 的重复图。上一轮同号截图属于另一份资料，参见 OPTIMIZATION_20261009.md。本次不连接手机、不运行功能或性能测试；源码检查和产物验证分别记录，不能用编译通过代替实际体验。

| 图片 | 修改 | 主要文件 | 验证与待验收 |
| --- | --- | --- | --- |
| IMG-01 | 旧版优先读取自己的 GitHub 静态 Release 清单，保留 ETag 缓存；仅在清单不存在时访问 Release API；清理 Gitee 文案 | feature/update/AppUpdate.kt、app/ui/ScheduleAppUi.kt、releases/update.json | 源码检查；发布后核对清单和下载文件。手机检查更新及下载待用户测试 |
| IMG-02、IMG-03、IMG-14 | 新版右上角使用统一的 Miuix 圆角按钮，菜单、刷新、主题与返回按同一组件规则绘制；保留刷新动画和触摸目标 | ScheduleHeader.kt、TopBarActionRail.kt、CampusQuickActions.kt、SystemUi.kt | 构建和安全区检查；不同字体、屏幕和主题的实际外观待用户测试 |
| IMG-04、IMG-06、IMG-18 | 设置和子页使用同一壁纸与外观状态；移除两套外观和三种材质的冲突。全应用只保留液态开关，关闭时走背景采样和高斯模糊，关闭透镜与色散 | NextAppearance.kt、SettingsScreen.kt、GlassSubpage.kt、GlassCompat.kt、GlassLens.kt、PhysicalLensSurface.kt、LiquidSelectionComponents.kt、CapsuleNavigationBar.kt、ScheduleScreen.kt | 代码路径统一；真实视觉效果和流畅度待用户测试。没有降低原版采样质量 |
| IMG-05、IMG-08、IMG-15、IMG-16、IMG-17 | 同步页改用原生完整顶栏，列表使用实际顶栏高度和系统底部安全区；取消叠加的返回行和整段重复外观；外观参数集中到设置 | MainActivity.kt、CampusPages.kt、CampusAppearanceSettings.kt | 布局和构建检查；长列表、横屏、大字体待用户测试 |
| IMG-07 | 成绩保持真实原生卡片与汇总；进入成绩时底栏沿用来源入口，禁止负选中位置；统一为一套原生底栏 | MainActivity.kt、CapsuleNavigationBar.kt、GradesRoute.kt（保留） | 来源页与索引检查；进入、退出和滚动动画待用户测试 |
| IMG-09 | 首页移除原生课程、选课与抢课、插件中心，保留成绩与考试卡片、课表、同步和 AI | CampusPages.kt | 入口检查；学校接口沿用已有实现，本轮没有重新读取学生数据 |
| IMG-10、IMG-11 | 旧版内置壁纸及玻璃预览使用原创川职月光；新版增加同系列内置背景；保留自选壁纸与裁剪参数 | drawable-nodpi/default_wallpaper_*.png、campus_glass_wallpaper.png、SettingsRowsUi.kt、AppearanceSettingsManager.kt、WallpaperImageStore.kt | 素材与资源检查；保留用户自定义图片，不自动覆盖 |
| IMG-12 | 学校账号头像由用户选择，原图和账号记录保留 | 头像与账号存储不修改 | 没有将用户头像替换成项目素材 |
| IMG-13 | 移除插件、问卷、统一登录适配的日常入口和提醒；快捷反馈指向 zwwd1/scvtc-zhixue/issues | SettingsScreen.kt、SettingsRoute.kt、LoginScreen.kt、MainActivity.kt、LoginActivity.kt、PluginFeedback.kt | 可见入口检查；保留既有插件存储与迁移兼容，不清用户数据 |
| 两端原创角色 | 小澄五个原创姿态：待机、眨眼、侧目、思考、打招呼；后台暂停待机循环，减少动态效果继续可选 | CampusCompanion.kt（原调度保留）、campus_companion_*.png | 图像和资源检查；角色状态及交互待用户测试 |
| 新版 AI | 加入真实 HTTPS Chat Completions 客户端；API Key 与对话用 Keystore/AES-GCM 保存，历史按账号隔离，学校课表需单独授权，学校凭据不发送 | CampusAiActivity.kt、AssistantStore.kt、AndroidManifest.xml、CampusPages.kt、SettingsScreen.kt | 编译和加密存储代码检查；实际 AI 服务需用户配置，尚未联网功能验收 |
| 自有发布与渠道 | 1.2.0 使用原包名、递增版本码和原签名；GitHub Release 包、源码、摘要对应；法定许可证与致谢保留 | app/build.gradle.kts 或 app/version.properties、scripts/publish_signed_release.py、.github/workflows/publish-release.yml、BUILD_PROVENANCE.json | 最终产物以 BUILD_PROVENANCE.json 和发布页记录为准；不把历史 APK 当成本轮成品 |

云同步和匿名统计继续暂停。Room 课表、账号、历史查询、手动编辑和自选壁纸不清除。没有新增数据库版本或破坏性迁移。参数不按机型自动降级；外观页面显示当前机型，调整由用户决定。

原生底栏的可调参数集中到同一外观页面，直接影响实际轨道、透镜、高光、阴影与回弹，不再只影响已经移除的替代底栏。新字段采用原版默认值，原版静止透镜不折射的行为继续保留；恢复按钮清除用户模糊覆盖并恢复原配方。

## 验证范围

必须核对包名、版本码、非调试标志、原签名、ZIP 完整性、16 KiB 对齐、资源与编译输入、密码明文扫描和 R8 mapping。只记录实际执行结果，详见 BUILD_PROVENANCE.json。没有安装手机、执行自动化功能测试、测帧率或宣称自然过期恢复已重新实测。

额外全量 lintRelease 的分析停留在 Kotlin/UAST 类型匹配工具中，本轮结束了该次分析，没有将它写成通过。最终仍执行 Release 打包所需的编译、R8、资源处理、签名和产物检查；构建日志保留在仓库外。

旧版构建检查发现的已有问题同时修复：Android 15 勿扰分支声明 API 要求，Condition 的 source/flags 顺序按官方签名纠正；不使用约束的两个容器改为普通 Box；玻璃拓扑检查在已提交的组合效果内执行。资源别名使用其相同的真实布局。保留 AndroidX Browser 官方顶部圆角常量，仅注明该版本错误 IntDef 所引起的诊断。

新版保留发布一致性检查；App 的可选 manifest 声明与线上 SDK 3.2.9 对齐，四份契约资源逐字节核验，网站下载、Wiki 与审核规则同步检查通过。没有改变宿主能力、增加公开插件入口、部署上游服务或上传真实开发者样本。
