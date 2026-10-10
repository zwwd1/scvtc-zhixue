package com.tyust.course.academic.plugin

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tyust.course.BuildConfig

object PluginFeedback {
    fun url(pkg: PluginPackage? = null, errorCode: String? = null): Uri = Uri.parse("https://github.com/zwwd1/scvtc-zhixue/issues/new").buildUpon()
        .appendQueryParameter("title", "[${BuildConfig.VERSION_NAME}] 使用反馈")
        .appendQueryParameter("body", "应用版本：${BuildConfig.VERSION_NAME}\nAndroid：${android.os.Build.VERSION.SDK_INT}\n机型：${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n${errorCode?.let { "错误编号：$it\n" }.orEmpty()}\n问题与复现步骤：\n\n请勿提交账号、密码、Cookie 或未脱敏的学校数据。")
        .build()
    fun open(context: Context, pkg: PluginPackage? = null, errorCode: String? = null) {
        context.startActivity(Intent(Intent.ACTION_VIEW, url(pkg, errorCode)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
