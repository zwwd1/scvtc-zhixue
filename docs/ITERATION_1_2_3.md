# 1.2.3 修改记录

这一版集中整理登录和课表设置，并去掉按钮的第二层粗描边。原生玻璃折射、底栏透镜、按压反馈和可调参数保留。

| 截图 | 修改内容 | 主要文件 | 验证与待确认 |
| --- | --- | --- | --- |
| IMG-01、IMG-02 | 顶栏刷新、主题、更多及返回按钮复用原有玻璃组件；移除重复描边与厚色板 | `SystemUi.kt`、`GlassChipAppearance.kt`、`GlassChipSurface.kt` | 核对统一入口；实机材质和触感由用户验收 |
| IMG-03 | 登录、同步和其他操作按钮使用与选择器相同的玻璃管线；首次连接改成川职专用表单 | `SystemUi.kt`、`SystemDialogButton.kt`、`LiquidComponents.kt`、`LoginScreen.kt`、`LoginActivity.kt` | 处理安全区、键盘、表单宽度与输入法提交；学校登录及键盘显示未做实机测试 |
| IMG-04 | 当前课表详情集中到一个入口；补齐字号、节次高度、时间列和显示恢复；课前提醒按钮统一材质 | `SettingsScreen.kt`、`ScheduleSettingsScreen.kt`、`ScheduleDisplayStore.kt`、`ScheduleAgendaScreen.kt`、`ScheduleScreen.kt` | 显示偏好按账号保存，恢复显示不改变日期、课程或提醒；布局由用户验收 |
| IMG-05 | 「未设置」成为可点击的红色玻璃按钮，继续打开原生时间选择器 | `ScheduleSettingsScreen.kt` | 不编造学校作息；设置与提醒沿用所选账号和学期 |
| 二级菜单 | 预测性返回保留进度、取消恢复与提交；到达页面顶端后可下拉返回，后方页面联动模糊 | `DialogHost.kt`、`MainActivity.kt`、Manifest | 滚动内容先消费手势，弹层打开时不拖动其父页；实机手势与流畅度未测试 |
| 日夜切换 | 主题、预设壁纸光斑与玻璃前景一起过渡；自选图片切换保持即时对比度 | `AnimatedThemePalette.kt`、`Theme.kt`、`AppAppearance.kt`、`WallpaperAppearance.kt` | 尊重系统关闭动画；不更换自定义壁纸或降低底栏效果 |
| 空应用首次使用 | 读取本机账号和教务缓存后判断是否打开连接页 | `MainActivity.kt`、`ScvtcRuntime.kt` | 已有账号或记录的覆盖安装不强制登录，返回后仍能使用本地功能 |
| 认证提示 | 区分真实验证、表单未准备好、回调超时和会话失效 | `official-login-memory.js`、`CasAuthManager.kt`、`JwxtApi.kt`、`ScvtcRuntime.kt` | 沿用原有成功登录路径；验证码与二次认证按学校要求完成 |

此前服务中心精简、成绩与学分、AI、看板娘、壁纸、更新来源和隐私功能保留。README 和使用说明重写为安装、日常操作、两版区别与实现方式，不包含聊天提示词。

正式构建、签名、资源、源码对应关系与插件平台发布检查结果见根目录 `VERIFICATION.md`。本轮不连接手机，不运行功能或性能测试；登录过期恢复、主题中间帧、下拉与边缘返回的实际体验由用户验收。
