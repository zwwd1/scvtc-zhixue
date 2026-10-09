# 川职·知学

四川职业技术学院非官方 Android 应用，维护：**zwwd1**。本项目 `1.0.0` 为第一版，与原项目版本记录分别维护。

知学保留原生课表、课程编辑、提醒、小组件和插件中心，接入川职官方 CAS 与教务 JSON 接口。首次登录后，在本机加密保存已验证的凭据；之后检查 Session，必要时恢复认证并继续同步。学校要求补充验证时，仍需本人完成。

## 1.1.0 维护版

本校服务中心只保留课表、成绩、学分三个主要入口，其他业务进入独立的学校官网浏览器。修复真实新窗口、页面标题和历史恢复，补齐实时同步、右上角液态玻璃刷新、学期筛选、学分口径、加密查询缓存、壁纸裁剪和独立卡片材质。保留原生课表与插件能力。按用户二次元参考生成新头像图标与小澄五种状态。

[按图片逐项的修改与验收](docs/OPTIMIZATION_20261009.md) · [生图素材与提示词](docs/BRANDING_20261009.md)。[1.1.0 源码分支](https://github.com/zwwd1/scvtc-zhixue/tree/codex/unified-optimization-20261009)。本轮 APK 在本机以原签名打包，含用户指定的赞赏原图；为避免公开支付资料，原图和这批 APK 不进入公开 Git。下方 1.0.0 为保留的历史第一版，不能当作本轮修复产物。

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
| 成绩 | 完整分页、实际学期筛选与账号隔离缓存；本轮真机取得 39 条真实成绩 |
| 学分 | 官方毕业要求与成绩所得分开；按课程代码合并重修，不虚构 GPA 或毕业资格 |
| 通知、请假等 | 尚未完成本校接口核验，使用明确的官方入口 |
| 其他学校插件 | 保留原有平台；不代表经过本项目逐校测试 |

学生主页的 Hash 路由只用于页面导航，不能当成后端接口。URL 到达主页也不能证明登录成功：必须取得需要身份的学生 JSON，并匹配当前账号。

## 数据与隐私

密码、表单绑定和认证备份由 **Android Keystore + AES-256-GCM** 保护，不进入公开源码、明文日志、APK 资源或普通课表导出。服务 JSON 缓存使用账号、学期和模块关联的 AES-GCM；旧明文缓存兼容读取，在下次成功同步时加密替换。普通课程 Room 数据仍由 Android 沙箱和设备加密保护，不称为 SQLCipher 全库加密。

**云同步与匿名统计暂停。** 系统自动云备份和设备迁移备份关闭。课表导出是用户主动操作，导出的文件应作为个人文件保管。插件更新、版本检查和主动使用的第三方服务有各自的网络连接，详见隐私说明。

## 源码与构建

本校认证与接口在 `app/src/main/java/cn/scvtc/campus`，原生页面和同步协调在 `app/src/main/java/com/tyust/course/scvtc`，数据校验在 `school-core`。原生课表与插件平台保留在对应基底模块。准备 JDK 21、Android SDK 37.0 与 Build Tools，设置 `JAVA_HOME`、`ANDROID_HOME` 后使用项目 Gradle Wrapper。覆盖安装要求保留 `cn.scvtc.campus.next`、原签名和递增的 `versionCode`；源码仓库不包含签名私钥。

本轮相关认证恢复、账号隔离、壁纸和学分规则共 28 项检查通过；Android 13 真机保留数据升级后，已免重新输入读取真实课表、成绩和毕业学分要求。最终视觉版结果与未验证场景见 [本轮验收](docs/OPTIMIZATION_20261009.md)，历史第一版记录保留在 [VERIFICATION.md](VERIFICATION.md)。

## 致谢项目

[zhengfang-apk · znjhahaha](https://github.com/znjhahaha/zhengfang-apk) 提供原生基底；[SleepDown-Schedule · xiaomanjun233](https://github.com/xiaomanjun233/SleepDown-Schedule) 提供界面与动效参考。原项目及组件的许可证、版权与必要致谢保留。本项目由 zwwd1 维护，不代表学校或原项目官方。
