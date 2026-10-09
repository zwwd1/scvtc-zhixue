package cn.scvtc.campus.core

import java.security.MessageDigest
import kotlinx.serialization.Serializable

@Serializable
data class Meeting(
  val id: String = "",
  val name: String,
  val teacher: String = "",
  val room: String = "",
  val day: Int,
  val startNode: Int,
  val endNode: Int,
  val weeks: List<Int>,
  val courseCode: String = "",
  val className: String = "",
) {
  fun stableId() =
    id.ifBlank {
      digest(
        listOf(
            courseCode,
            name,
            teacher,
            room,
            className,
            day,
            startNode,
            endNode,
            weeks.sorted().joinToString(","),
          )
          .joinToString("|")
      )
    }
}

@Serializable
data class NativeRecord(
  val title: String,
  val fields: Map<String, String> = emptyMap(),
  val link: String = "",
)

@Serializable data class ServiceLink(val label: String, val url: String)

@Serializable
data class Extraction(
  val module: String,
  val account: String = "",
  val semester: String = "",
  val source: String = "",
  val meetings: List<Meeting> = emptyList(),
  val records: List<NativeRecord> = emptyList(),
  val links: List<ServiceLink> = emptyList(),
  val warnings: List<String> = emptyList(),
  val coverageWeeks: List<Int> = emptyList(),
  val full: Boolean = false,
  val emptyConfirmed: Boolean = false,
)

@Serializable
data class QueryRecipe(
  val url: String,
  val method: String,
  val body: String,
  val headers: Map<String, String> = emptyMap(),
  val coverageWeeks: List<Int> = emptyList(),
)

data class CampusService(
  val id: String,
  val title: String,
  val group: String,
  val write: Boolean = false,
  val aliases: List<String> = emptyList(),
)

object School {
  const val ORIGIN = "https://jwxt.scvtc.edu.cn"
  const val HOME = ORIGIN + "/jwgr/#/jwxt/js/student/index"
  const val SCHEDULE = ORIGIN + "/jwgr/#/jwxt/js/student/personal/studentSchedule"
  const val PORTAL = "https://portal.scvtc.edu.cn/onlineServiceClient/#/homePage"
  const val WEBSITE = "https://www.scvtc.edu.cn/"
  val allowedHosts =
    setOf("jwxt.scvtc.edu.cn", "portal.scvtc.edu.cn", "www.scvtc.edu.cn", "scvtc.edu.cn")
  val readPaths =
    setOf(
      "/jwgr/api/student/studentInfo/querySelf",
      "/jwgr/api/baseInfo/semester/selectCurrentXnXq",
      "/jwgr/api/arrange/CourseScheduleAllQuery/studentCourseSchedule",
    )
  val services =
    listOf(
      CampusService("schedule", "个人课程表", "学习与成绩", aliases = listOf("课程表", "学生课表", "我的课表")),
      CampusService("grades", "成绩查询", "学习与成绩", aliases = listOf("成绩详情", "我的成绩")),
      CampusService("credits", "学分概况", "学习与成绩"),
      CampusService("distribution", "成绩分布", "学习与成绩"),
      CampusService("classSchedule", "班级课表", "学习与成绩"),
      CampusService("curriculum", "培养方案", "学习与成绩"),
      CampusService("courses", "开课课程", "学习与成绩"),
      CampusService("attendance", "缺勤查询", "学习与成绩"),
      CampusService("exams", "考试安排", "考试与报名", aliases = listOf("正考考试安排")),
      CampusService("levelExam", "等级考试", "考试与报名", true, listOf("等级考试报名")),
      CampusService("levelExamResults", "等级考试查询", "考试与报名"),
      CampusService("selection", "网上选课", "教务办理", true),
      CampusService("evaluation", "评教", "教务办理", true, listOf("学生评教")),
      CampusService("statusWarning", "学籍预警", "教务办理", aliases = listOf("学生学籍预警", "学生学籍、预警查询")),
      CampusService("statusChange", "学籍异动", "教务办理", true),
      CampusService("internship", "实习", "教务办理", true),
      CampusService("studentInfo", "学籍信息", "教务办理", aliases = listOf("个人信息")),
      CampusService("studentCheck", "学籍核对", "教务办理", true, listOf("学籍信息核对")),
      CampusService("retake", "重修报名", "教务办理", true),
      CampusService("transfer", "转专业报名", "教务办理", true),
      CampusService("calendar", "校历", "校园资源"),
      CampusService("rooms", "空闲教室", "校园资源"),
      CampusService("venue", "场地课表", "校园资源"),
      CampusService("messages", "留言管理", "校园资源", true),
      CampusService("notices", "通知公告", "校园资源"),
      CampusService("phone", "绑定手机", "账号服务", true),
      CampusService("password", "修改密码", "账号服务", true),
    )

  fun service(id: String) = services.firstOrNull { it.id == id }

  fun trusted(url: String): Boolean =
    runCatching {
        java.net.URI(url).let {
          it.scheme == "https" &&
            it.host in allowedHosts &&
            it.userInfo == null &&
            it.port in setOf(-1, 443)
        }
      }
      .getOrDefault(false)
}

fun digest(value: String) =
  MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).take(12).joinToString("") {
    "%02x".format(it)
  }
