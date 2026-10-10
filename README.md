# 川职·知学

四川职业技术学院非官方 Android 应用，由 **zwwd1** 维护。1.0.0 是本项目第一版，当前维护版为 **1.2.0**。知学保留原生课表、成绩卡片、考试、提醒和小组件，服务中心只显示课表、成绩、学分，其他业务通过学校官方教务页面办理。

## 1.2.0

合并外观入口，顶栏按钮、子页壁纸、课表与底栏统一风格。液态玻璃关闭后全应用使用高斯模糊。修复同步页遮挡和成绩底栏，精简首页，移除问卷、插件、统一登录适配入口，加入本机加密的 AI 配置与对话。使用原创川职月光和小澄五种状态。

[按本次 IMG-01～IMG-17 的修改与验收](docs/OPTIMIZATION_20261010.md) · [原创素材](docs/BRANDING_20261010.md)。本轮不连接手机、不执行功能或性能测试，编译和签名不能代替实际体验。历史验收保留在对应日期报告中。

## 下载

- [1.2.0 原签名 APK](https://github.com/zwwd1/scvtc-zhixue/releases/download/v1.2.0/scvtc-zhixue-1.2.0.apk) · [SHA-256](https://github.com/zwwd1/scvtc-zhixue/releases/download/v1.2.0/scvtc-zhixue-1.2.0.apk.sha256)
- [1.2.0 发布页](https://github.com/zwwd1/scvtc-zhixue/releases/tag/v1.2.0) · [对应源码](https://github.com/zwwd1/scvtc-zhixue/archive/refs/tags/v1.2.0.zip)
- [构建与签名对应关系](BUILD_PROVENANCE.json) · [验证范围](VERIFICATION.md)

最低 Android 13。已有安装请直接覆盖，保留应用数据；不要先卸载。自行更换签名的构建不能覆盖本项目原签名 APK。

## 使用与原理

首次在官方 CAS 完成登录，App 继续建立教务 Session，并用需要学生身份的真实 JSON API 核验。成功后加密保存认证，再次启动检查 Session；过期时恢复 SSO 或使用本机凭据重建认证，再继续同步。登录 URL 到达主页不能单独证明成功。学校密码变更或额外验证仍需本人处理，失败保留最后成功数据。

| 需要什么 | 入口 |
| --- | --- |
| 安装、登录和日常操作 | [使用方法](docs/SCVTC_USAGE.md) |
| 认证、真实接口、缓存和编译 | [构建原理](docs/SCVTC_ARCHITECTURE.md) |
| 本机加密、网络和数据删除 | [隐私说明](docs/SCVTC_PRIVACY.md) |
| 问题反馈 | [本项目 Issues](https://github.com/zwwd1/scvtc-zhixue/issues) |
| 轻量课表 | [川职·课间](https://github.com/zwwd1/scvtc-kejian) |

成绩与学分使用学校真实 JSON，保留实际学期和完整分页；毕业要求与成绩所得分开，未知字段不作为假零值。学校接口未核验的功能使用明确的官网入口，不编造通知、成绩或课表。

## 数据与构建

学校凭据由 Android Keystore 与 AES-256-GCM 保护，服务查询缓存按账号、学期和模块加密。普通课表 Room 数据依靠应用沙箱与设备存储加密，不宣称 SQLCipher 全库加密。**云同步与匿名统计暂停**。系统自动备份关闭；个人导出由用户主动操作。

学校认证与接口位于 cn/scvtc/campus，同步和 UI 位于 com/tyust/course/scvtc，school-core 负责数据规则。AI Key 与历史在 noBackupFilesDir 中 AES-GCM 加密，只有用户启用课表授权才向配置的模型服务发送课程上下文。使用 JDK 21、Android SDK 37.0 和项目 Gradle Wrapper 构建。原签名配置通过仓库外环境变量提供；私钥、密码、个人截图和原始学校响应不进入源码。

发布工作流只重组已签名的本机 APK，核对 SHA-256、包名、版本、原证书和嵌入的源码提交，不接触 Android 私钥。正式 Release 标签指向 APK 对应源码，更新清单在发布文件可用后更新。

## 致谢与许可

应用基底：[zhengfang-apk](https://github.com/znjhahaha/zhengfang-apk)；[SleepDown-Schedule](https://github.com/xiaomanjun233/SleepDown-Schedule) 提供界面与动效参考。原项目许可证、版权及第三方组件声明保留。应用中的维护、更新与反馈属于本项目，依法必要的原作者信息仅保留在致谢与许可中。本项目不代表学校或原项目官方。
