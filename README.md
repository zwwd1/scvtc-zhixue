# 川职·知学

四川职业技术学院非官方 Android 应用，维护：**zwwd1**。本项目 `1.0.0` 为第一版，与原项目版本记录分别维护。

知学保留原生课表、课程编辑、提醒、小组件和插件中心，接入川职官方 CAS 与教务 JSON 接口。首次登录后，在本机加密保存已验证的凭据；之后检查 Session，必要时恢复认证并继续同步。学校要求补充验证时，仍需本人完成。

## 下载与验证

- [1.0.0 原签名 APK](https://raw.githubusercontent.com/zwwd1/scvtc-zhixue/main/dist/scvtc-campus-next-1.0.0.apk) · [SHA-256](dist/scvtc-campus-next-1.0.0.apk.sha256)
- [完整源码](https://github.com/zwwd1/scvtc-zhixue/archive/refs/heads/main.zip) · [验证记录](VERIFICATION.md) · [源码与 APK 对应关系](BUILD_PROVENANCE.json)
- 最低 Android 13；覆盖安装请保留应用数据。仓库源码不含原签名私钥，自行使用其他私钥打包不能覆盖此 APK。

## 使用入口

| 需要什么 | 说明 |
| --- | --- |
| 首次安装与登录 | [使用方法](docs/SCVTC_USAGE.md) |
| 自动登录和数据来源 | [构建原理与接口](docs/SCVTC_ARCHITECTURE.md) |
| 本机加密、联网和删除 | [隐私说明](docs/SCVTC_PRIVACY.md) |
| 编译 APK 与原签名升级 | [构建说明](docs/SCVTC_ARCHITECTURE.md#构建) |
| 问题反馈 | [本项目 Issues](https://github.com/zwwd1/scvtc-zhixue/issues) |
| 轻量课表应用 | [川职·课间](https://github.com/zwwd1/scvtc-kejian) |

## 本校功能

| 功能 | 实现与边界 |
| --- | --- |
| 学生身份、当前学期、课表 | 原生 HTTP 读取，匹配学生身份后缓存，支持离线查看 |
| 学期列表 | 使用官方返回的实际列表 |
| 成绩、正考安排 | 校验并读取完整分页，失败时保留缓存 |
| 等级考试查询 | 已确认空结果；非空字段需继续核验 |
| 毕业学分要求 | 显示官方要求，不推算已获得学分 |
| 通知、请假等 | 尚未完成本校接口核验，使用明确的官方入口 |
| 其他学校插件 | 保留原有平台；不代表经过本项目逐校测试 |

学生主页的 Hash 路由只用于页面导航，不能当成后端接口。URL 到达主页也不能证明登录成功：必须取得需要身份的学生 JSON，并匹配当前账号。

## 数据与隐私

密码、表单绑定和认证备份由 **Android Keystore + AES-256-GCM** 保护，不进入公开源码、明文日志、APK 资源或普通课表导出。课表和查询缓存存于应用私有 Room 数据库，依靠 Android 沙箱和设备存储加密保护；本项目不把普通 Room 缓存描述为全库加密。

**云同步与匿名统计暂停。** 系统自动云备份和设备迁移备份关闭。课表导出是用户主动操作，导出的文件应作为个人文件保管。插件更新、版本检查和主动使用的第三方服务有各自的网络连接，详见隐私说明。

## 源码与构建

本校认证与接口在 `app/src/main/java/cn/scvtc/campus`，原生页面和同步协调在 `app/src/main/java/com/tyust/course/scvtc`，数据校验在 `school-core`。原生课表与插件平台保留在对应基底模块。准备 JDK 21、Android SDK 37.0 与 Build Tools，设置 `JAVA_HOME`、`ANDROID_HOME` 后使用项目 Gradle Wrapper。覆盖安装要求保留 `cn.scvtc.campus.next`、原签名和递增的 `versionCode`；源码仓库不包含签名私钥。

已完成本地发布构建、原签名与对齐检查，以及最终源码的 10 项既有认证恢复 / 账号隔离检查。最终 APK 的手机登录和重启免输入尚未完成，详见 [验证记录](VERIFICATION.md)。

## 致谢项目

[zhengfang-apk · znjhahaha](https://github.com/znjhahaha/zhengfang-apk) 提供原生基底；[SleepDown-Schedule · xiaomanjun233](https://github.com/xiaomanjun233/SleepDown-Schedule) 提供界面与动效参考。原项目及组件的许可证、版权与必要致谢保留。本项目由 zwwd1 维护，不代表学校或原项目官方。
