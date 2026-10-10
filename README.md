# 川职·知学

四川职业技术学院同学的课表和教务记录工具。首页看今天的课程，课表看整周安排，成绩与学分单独查看；其他学校业务从官方教务入口办理。

项目由 [zwwd1](https://github.com/zwwd1) 维护，基于已有 Android 项目重构，保留原生页面、玻璃动效、课程编辑、提醒和小组件。它是非官方应用，教务数据以学校系统为准。

[下载最新版](https://github.com/zwwd1/scvtc-zhixue/releases/latest) · [使用方法](docs/SCVTC_USAGE.md) · [反馈问题](https://github.com/zwwd1/scvtc-zhixue/issues)

## 开始使用

1. 安装 APK。装过知学的话，直接覆盖安装，不用先卸载。
2. 点击“连接学校”，填写学校统一认证的学号和密码，选择“登录并自动同步”。
3. 应用核验学生身份后开始后台同步。离开登录页也会继续读取，首页可以查看进度和最近成功时间。
4. 点今天的课程查看详情，或进入周课表。成绩支持学期切换、搜索、筛选和导出；学分页可以直接跳到对应学期的课程。

会话过期后，会先尝试恢复学校登录，再继续刚才的数据读取。学校要求验证码或二次认证时，用“补充认证”完成即可；已填的学号和密码会保留。修改学校密码后需要更新本机保存的密码。

刷新失败会保留已有记录。没有取得数据和学校确认没有记录是两种状态，页面会分别说明。

## 界面和日常操作

首页、课表、服务中心、设置共用顶栏按钮、壁纸和外观设置。液态玻璃开关统一控制，关闭后使用高斯模糊；底栏的原有动效保留，效果参数仍可自行调整。

服务中心保留课表、成绩和学分入口。请假、学籍等其他业务进入学校官方页面，不显示没有接通的数据。AI 助手需要你自己配置模型服务，看板娘“小澄”有不同状态。

## 隐私

学校密码、会话和服务查询缓存使用 Android Keystore 与 AES-GCM 加密，并按账号隔离。密码不会放进源码、安装包资源、普通日志或导出备份。课表的 Room 数据库位于应用沙箱里，不宣称整个数据库都使用了 SQLCipher。

云服务和匿名统计已暂停。AI 的密钥和历史在本机加密保存；只有开启课表授权，助手才会向你配置的服务发送课程上下文。

[完整隐私说明](docs/SCVTC_PRIVACY.md)

## 源码与构建

`cn/scvtc/campus` 负责学校认证和真实接口，`com/tyust/course/scvtc` 负责同步与应用页面，`school-core` 保存数据规则。认证完成还要调用学生身份接口，进入某个网页地址不算登录成功。

构建使用 JDK 21、Android SDK 37.0 和 Gradle Wrapper：

```powershell
.\gradlew.bat :app:assembleRelease
```

签名配置放在仓库之外。同一应用的正式更新沿用原签名；发布前核对安装包、源码、版本和签名，并检查插件平台的一致性。

[构建原理](docs/SCVTC_ARCHITECTURE.md) · [这版改了什么](release-notes/v1.2.2.md) · [实际验证范围](VERIFICATION.md)

## 致谢

[zhengfang-apk](https://github.com/znjhahaha/zhengfang-apk) 提供应用基底，[SleepDown-Schedule](https://github.com/xiaomanjun233/SleepDown-Schedule) 提供界面和动效参考。原项目和第三方组件的许可证、必要版权声明保留在仓库中。

只想看课表，也可以使用：[川职·课间](https://github.com/zwwd1/scvtc-kejian)。
