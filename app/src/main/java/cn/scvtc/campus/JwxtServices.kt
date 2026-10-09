package cn.scvtc.campus

import cn.scvtc.campus.core.*
import kotlinx.serialization.json.*

/** JSON contracts observed on the logged-in school's own student pages. */
object JwxtServices {
    val endpoints=mapOf(
        "grades" to JwxtApi.BASE+"score/scorequerymanage/studentQuery",
        "exams" to JwxtApi.BASE+"exam/studentExamSchedule/queryPositiveExamSchedule",
        "levelExamResults" to JwxtApi.BASE+"score/gradeExamScore/studentQuery",
        "credits" to JwxtApi.BASE+"scheme/majorSchemaCustomize/queryStudentGraduationCredit",
    )
    fun pageBody(module:String,page:Int):String=buildJsonObject {
        put("pageNo",page);put("pageSize",100)
        if(module!="levelExamResults"){
            put("total",0)
            put("param",buildJsonObject {
                if(module=="grades")put("scoreInvalid","0")
                if(module=="exams")put("semesterId","")
            })
        }
    }.toString()
    fun record(module:String,row:JsonObject,account:String):NativeRecord {
        for(key in listOf("studentCode","studentId"))row[key]?.jsonPrimitive?.contentOrNull?.takeIf{it.isNotBlank()}?.let {
            check(it==account){"ACCOUNT_MISMATCH：服务返回了其他学生的数据"}
        }
        fun value(key:String)=row[key]?.let{(it as? JsonPrimitive)?.contentOrNull}.orEmpty()
        val fields=linkedMapOf<String,String>()
        val names=when(module){
            "grades"->mapOf("courseName" to "课程名称","courseCode" to "课程代码","semesterId" to "学期",
                "studentScoreFinalValue" to "成绩","courseCredit" to "学分","acquireCourseCredit" to "获得学分",
                "gradePoint" to "绩点","studyTypeName" to "修读类型","natureName" to "课程性质")
            "exams"->mapOf("courseName" to "课程名称","courseCode" to "课程代码","semester" to "学期",
                "classroomName" to "考试地点","positiveExamPaperDate" to "考试日期","positiveExamTime" to "考试时段",
                "studentSetNumber" to "座位号","roundName" to "考试名称")
            else->row.keys.filterNot{Regex("(?i)student|password|pwd|token|cookie|session|authorization").containsMatchIn(it)}.associateWith{it}
        }
        names.forEach{(key,label)->value(key).takeIf(String::isNotBlank)?.let{fields[label]=it}}
        if(module=="grades"&&fields["成绩"].isNullOrBlank())value("scoreValue").takeIf(String::isNotBlank)?.let{fields["成绩"]=it}
        if(module=="exams"){
            fields["考试时间"]=listOf(value("positiveExamPaperDate"),value("positiveExamTime")).filter(String::isNotBlank).joinToString(" ")
        }
        val title=value("courseName").ifBlank{fields.values.firstOrNull().orEmpty()}
        check(title.isNotBlank()&&fields.isNotEmpty()){"PAGE_CHANGED：服务记录缺少已核验的字段"}
        if(module=="grades")check(!fields["成绩"].isNullOrBlank()){"PAGE_CHANGED：成绩字段缺失"}
        return NativeRecord(title,fields)
    }
}
