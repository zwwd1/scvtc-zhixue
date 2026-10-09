# 川职·校园 Next 0.4.1

本分支在 zhengfang-apk 原生工程上继续实现四川职业技术学院的个人客户端。包名 `cn.scvtc.campus.next`、versionCode 3、versionName 0.4.1，支持 Android 13 及以上；保留 GPL-3.0、原生插件、课表、课程详情、编辑、提醒、小部件和玻璃功能。与旧版使用不同签名和数据空间。没有发布上游正式 release。

修复 CourseApplication 主进程中遗漏的 UsageStatsManager 初始化，位于 UserManager 初始化后、首次 Activity/Compose 前。原隔离 UID 与进程保护保留；统计偏好和使用提示仍存在，用户可以关闭。Android 17 冷启动是否修复仍须真机确认。

本轮还增加应用级 WebView 与 WorkManager 同步、有限自动认证恢复、Room 课表恢复到原生显示缓存、分页服务完整提交、账号切换隔离、学校时区与跨午夜刷新、课表风格相关间距/圆角/材质/按压弹簧差异。网络、认证、解析、未知空结果失败仍保留最后有效课程及成功时间。恢复缓存沿用原始时间，不冒充新同步。

自动认证只使用用户真实成功登录后核实的账号、HTTPS 页面和表单，凭据保存在 Android Keystore 加密的本机存储；不上传到云、不写日志、不进入源码备份。相同官方表单才能恢复，使用官网原有按钮流程；验证码/MFA/未知结构需要本人完成。尚未获得本校可用会话，不能把离线表单测试写成学校登录成功。

正式构建：`./gradlew --max-workers=1 :school-core:test :school-parser:test :app:assembleRelease`。本分支显式启用 release 单元测试任务，可运行 `:app:testReleaseUnitTest`。Windows 构建和逐项验证脚本位于交接根目录 scripts。签名使用 `tools/sign-next.py` 和原 Next 私钥，固定指纹见 tools/next-certificate.sha256。unsigned APK 仅为构建检查产物，debug/uiPreview/历史 APK 不属于修复成品。

完整验证、上游核对与缺项见交接根目录 output/报告。云协议只在 localhost 验证，云服务未部署；动效帧率、玻璃采样成本与 Xiaomi API 37 录屏仍须真机测量。源码修改和离线测试不能代替实测。
