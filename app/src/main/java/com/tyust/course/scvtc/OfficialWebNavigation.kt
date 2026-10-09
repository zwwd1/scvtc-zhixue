package com.tyust.course.scvtc

import android.net.Uri
import cn.scvtc.campus.core.School

/** The browser owns navigation; school credentials stay within observed SSO origins. */
data class OfficialWebPage(
    val title: String = "学校官方教务系统",
    val url: String = "",
    val loading: Boolean = false,
    val error: String = "",
)

object OfficialWebNavigation {
    fun trusted(url: String): Boolean {
        val u = Uri.parse(url)
        if (u.scheme != "https" || u.userInfo != null) return false
        return (u.host in School.allowedHosts + "cas.scvtc.edu.cn" && u.port in setOf(-1, 443)) ||
            (u.host == "www.shulin-soft.com" && u.port == 8267 && u.path == "/casLogin.html")
    }

    fun title(url: String, observed: String = ""): String {
        val u = Uri.parse(url)
        if (u.host == "cas.scvtc.edu.cn") return "学校统一身份认证"
        if (u.host == "portal.scvtc.edu.cn") return "学校一网通办"
        if (u.host != "jwxt.scvtc.edu.cn") return "学校官方网站"
        val route = u.fragment.orEmpty().substringBefore('?')
        if (route.endsWith("/personal/studentSchedule")) return "个人课程表"
        if (route == "/jwxt/js/student/childServiceQuery/myScoreQuery") return "我的成绩"
        // Only headings observed in this document may name otherwise unknown routes.
        return observed.trim().take(60).ifBlank { "学校官方教务系统" }
    }
}
