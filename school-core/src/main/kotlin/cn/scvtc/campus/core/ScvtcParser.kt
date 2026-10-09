package cn.scvtc.campus.core

import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object ScvtcParser {
  private val weekPattern =
    Regex("(\\d+(?:\\s*[-－—～~,，、;；]\\s*\\d+)*\\s*(?:[（(]?[单双][）)]?)?\\s*周(?:[（(]?[单双][）)]?)?)")
  private val nodePattern = Regex("第?\\s*(\\d+(?:\\s*[-－—～~,，、]\\s*\\d+)*)\\s*节")
  private val semesterPattern = Regex("20\\d{2}[-—]20\\d{2}[-—][12]")

  fun html(text: String, source: String, module: String): Extraction {
    require(School.trusted(source)) { "来源不是已确认的学校站点" }
    val doc = Jsoup.parse(text, source)
    val isLogin = doc.select("input[type=password]").isNotEmpty()
    doc.select("script,style,input[type=password]").remove()
    val semester =
      doc
        .select("option[selected]")
        .map(Element::text)
        .firstOrNull { semesterPattern.containsMatchIn(it) }
        ?.let { semesterPattern.find(it)?.value }
        .orEmpty()
        .ifBlank { semesterPattern.find(doc.text())?.value.orEmpty() }
    val account =
      Regex("(?:学号|学生编号)[：:\\s]*([0-9]{6,20})").find(doc.text())?.groupValues?.get(1).orEmpty()
    val links =
      doc
        .select("a[href]")
        .mapNotNull {
          val label = it.text().trim()
          val url = it.absUrl("href")
          if (
            label.isNotBlank() &&
              School.trusted(url) &&
              it.parents().none{parent->parent.tagName() in setOf("nav","aside")||parent.hasClass("el-menu")} &&
              !url.contains(Regex("(?i)(ticket|token|password|session)="))
          )
            ServiceLink(label.take(60), url)
          else null
        }
        .distinctBy { it.label to it.url }
    val meetings = mutableListOf<Meeting>()
    val warnings = mutableListOf<String>()
    if (module == "schedule" || module == "classSchedule" || module == "venue") {
      for (table in doc.select("table")) {
        val heads = (table.select("thead tr").lastOrNull() ?: table.select("tr").firstOrNull())?.children()?.map { it.text() }.orEmpty()
        val days = heads.map { day(it) }
        val occupied = mutableMapOf<Int, Int>()
        for (row in table.select("tbody > tr").ifEmpty { table.select("tr") }) {
          var column = 0
          val rowNodes =
            runCatching { WeekRules.nodes(row.children().firstOrNull()?.text().orEmpty()) }
              .getOrDefault(emptyList())
          for (cell in row.children().filter { it.tagName() == "td" }) {
            while ((occupied[column] ?: 0) > 0) column++
            val span = cell.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
            val rowSpan = cell.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
            val weekday =
              days.getOrNull(column)?.takeIf { it > 0 } ?: column.takeIf { it in 1..7 } ?: 0
            val candidates =
              cell
                .select("[class*=courseBox],[data-course-name]")
                .filter { it.select("[class*=courseBox],[data-course-name]").size == 1 }
                .ifEmpty {
                  cell
                    .children()
                    .filter { weekPattern.containsMatchIn(it.text()) }
                    .filter {
                      it.children().none { ch ->
                        weekPattern.containsMatchIn(ch.text()) &&
                          ch.select("[class*=courseBox]").isNotEmpty()
                      }
                    }
                }
            if (weekday > 0)
              for (block in candidates) {
                runCatching { parseBlock(block, weekday, rowNodes) }
                  .onSuccess { meetings += it }
                  .onFailure { warnings += it.message ?: "某个课程块无法解析" }
              }
            if (rowSpan > 1) for (c in column until column + span) occupied[c] = rowSpan
            column += span
          }
          occupied.keys.toList().forEach { occupied[it] = (occupied[it] ?: 0) - 1 }
        }
      }
    }
    val records = if (module == "schedule") emptyList() else if(module=="credits"){
      val completed=Regex("已完成毕业学分\\s*(\\d+(?:\\.\\d+)?)\\s*分").findAll(doc.text()).map{it.groupValues[1]}.distinct().toList()
      val remaining=Regex("未完成毕业学分\\s*(\\d+(?:\\.\\d+)?)\\s*分").findAll(doc.text()).map{it.groupValues[1]}.distinct().toList()
      if(completed.size==1&&remaining.size==1)listOf(NativeRecord("毕业学分",mapOf("已完成毕业学分" to completed.single(),"未完成毕业学分" to remaining.single())))else records(doc,source)
    }else records(doc, source)
    val attachments=if(module=="calendar")doc.select("img[src]").mapNotNull{img->val url=img.absUrl("src");if(School.trusted(url)&&!url.contains(Regex("(?i)(ticket|token|password|session)=")))ServiceLink(img.attr("alt").ifBlank{"官方校历图片"},url)else null}else emptyList()
    if (module == "schedule" && meetings.isEmpty())
      warnings +=
        if (isLogin || doc.text().contains("账号登录")) "当前仍在登录页"
        else "页面中未找到可解析课程；请进入学生个人课表并等待加载，或尝试响应捕获"
    return Extraction(
      module,
      account,
      semester,
      source,
      meetings.distinctBy { it.stableId() },
      records,
      (links+attachments).distinct(),
      warnings.distinct(),
    )
  }

  private fun parseBlock(block: Element, day: Int, rowNodes: List<Int>): List<Meeting> {
    val fields = block.children().map { it.text().trim() }.filter(String::isNotBlank)
    val lines = block.text()
    val name = block.attr("data-course-name").ifBlank { fields.firstOrNull().orEmpty() }.trim()
    require(name.isNotBlank()) { "课程名为空" }
    val wm = weekPattern.find(lines) ?: error("课程 $name 缺少周次")
    val weeks = WeekRules.parse(wm.value)
    val nodeText = nodePattern.find(lines)?.groupValues?.get(1)
    val nodes = if (nodeText != null) WeekRules.nodes(nodeText) else rowNodes
    require(nodes.isNotEmpty()) { "课程 $name 缺少节次" }
    val teacher =
      field(block, listOf("教师", "任课教师", "teacher")).ifBlank {
        fields
          .lastOrNull()
          ?.takeIf {
            !weekPattern.containsMatchIn(it) && !nodePattern.containsMatchIn(it) && it != name
          }
          .orEmpty()
      }
    val room =
      field(block, listOf("教室", "地点", "classroom", "room")).ifBlank {
        fields
          .firstOrNull { Regex("(楼|馆|教室|实验室|实训室|[A-Z]\\d{2,4})").containsMatchIn(it) }
          ?.substringBefore('(')
          ?.substringBefore('（')
          .orEmpty()
      }
    return WeekRules.runs(nodes).map {
      Meeting(
        name = name,
        teacher = teacher,
        room = room,
        day = day,
        startNode = it.first,
        endNode = it.last,
        weeks = weeks,
      )
    }
  }

  private fun field(block: Element, names: List<String>): String {
    for (n in names) {
      block
        .select("[data-label],[title],[aria-label],[class]")
        .firstOrNull { e ->
          listOf(e.attr("data-label"), e.attr("title"), e.attr("aria-label"), e.className()).any {
            it.contains(n, true)
          }
        }
        ?.text()
        ?.takeIf(String::isNotBlank)
        ?.let {
          return it.substringAfter('：').substringAfter(':').trim()
        }
      Regex(n + "[：:]\\s*([^\\n]+)").find(block.wholeText())?.groupValues?.get(1)?.let {
        return it.trim()
      }
    }
    return ""
  }

  private fun day(s: String) =
    when {
      s.contains("一") -> 1
      s.contains("二") -> 2
      s.contains("三") -> 3
      s.contains("四") -> 4
      s.contains("五") -> 5
      s.contains("六") -> 6
      s.contains("日") || s.contains("天") -> 7
      else -> 0
    }

  private fun records(doc: Element, source: String): List<NativeRecord> {
    val result = mutableListOf<NativeRecord>()
    for (table in doc.select("table")) {
      val headers =
        table.select("thead th").map(Element::text).ifEmpty {
          table.parents().firstOrNull{it.hasClass("el-table")}?.select(".el-table__header-wrapper th")?.map(Element::text).orEmpty()
            .ifEmpty { table.parents().firstOrNull{it.hasClass("ant-table")}?.selectFirst(".ant-table-header table")?.select("thead th")?.map(Element::text).orEmpty() }
            .ifEmpty { table.select("tr").firstOrNull()?.select("th")?.map(Element::text).orEmpty() }
        }
      if (headers.isEmpty()) continue
      for (row in table.select("tbody tr")) {
        val cells = row.children().filter { it.tagName() == "td" }.map { it.text().trim() }
        if (cells.isEmpty() || cells.all(String::isBlank) || row.hasClass("ant-table-placeholder") || row.select(".ant-empty").isNotEmpty()) continue
        val map =
          cells
            .mapIndexed { i, s ->
              (headers.getOrNull(i)?.ifBlank { "字段${i+1}" } ?: "字段${i+1}") to s
            }
            .toMap()
        val link=row.select("a[href]").map{it.absUrl("href")}.firstOrNull{School.trusted(it)&&!it.contains(Regex("(?i)(ticket|token|password|session)="))}.orEmpty()
        result += NativeRecord(cells.firstOrNull(String::isNotBlank).orEmpty(), map,link)
      }
    }
    if (result.isEmpty()) {
      val rows = doc.select("[class*=infoItem],[class*=detailItem],dl")
      for (row in rows) {
        val t = row.text().trim()
        if (t.isNotBlank()) result += NativeRecord(t.take(100), mapOf("内容" to t))
      }
    }
    return result.distinct()
  }

  fun api(
    body: String,
    source: String,
    module: String,
    account: String = "",
    semester: String = "",
    coverage: List<Int> = emptyList(),
  ): Extraction {
    require(School.trusted(source))
    val root = Json.parseToJsonElement(body)
    val obj = root as? JsonObject ?: error("响应不是对象")
    val code = obj["code"]?.jsonPrimitive?.contentOrNull
    require(code == null || code in setOf("200", "0")) { "官方接口未返回成功状态" }
    require(obj["success"]?.jsonPrimitive?.booleanOrNull != false) { "官方接口未返回成功状态" }
    val data = obj["data"] ?: root
    val list = mutableListOf<Meeting>()
    var knownCells = 0
    var rawCourses = 0
    var namedCourses = 0
    var validCourses = 0
    fun walk(e: JsonElement, inheritedWeek: JsonObject? = null, inheritedTime: JsonObject? = null) {
      when (e) {
        is JsonArray -> e.forEach { walk(it, inheritedWeek, inheritedTime) }
        is JsonObject -> {
          if (e["week"] is JsonObject && e["time"] is JsonObject) {
            val courses = (e["courseList"] ?: e["courses"]) as? JsonArray
            if (courses != null) { knownCells++; rawCourses += courses.size }
          }
          val name = e.string("courseName")
          if (name.isNotBlank()) namedCourses++
          if (name.isNotBlank() && e["weeks"] != null) {
            val wd = e["week"] as? JsonObject ?: inheritedWeek
            val time = e["time"] as? JsonObject ?: inheritedTime
            val weekText =
              when (val w = e["weeks"]) {
                is JsonArray -> w.joinToString(",") { it.jsonPrimitive.content }
                else -> w?.jsonPrimitive?.content.orEmpty()
              }
            val weeks = WeekRules.parse(weekText)
            val day =
              wd?.string("weekName")?.let(::day)?.takeIf { it > 0 }
                ?: e.string("weekName").let(::day).takeIf { it > 0 }
                ?: 0
            val timeName = time?.string("timeName").orEmpty().ifBlank { e.string("timeName") }
            val nodes = runCatching { WeekRules.nodes(timeName) }.getOrDefault(emptyList())
            if (day in 1..7 && nodes.isNotEmpty() && weeks.isNotEmpty()) {
              validCourses++
              for (r in WeekRules.runs(nodes)) list +=
                Meeting(
                  name = name,
                  teacher = e.string("teacherName"),
                  room = e.string("classroomName"),
                  day = day,
                  startNode = r.first,
                  endNode = r.last,
                  weeks = weeks,
                  courseCode = e.string("courseCode"),
                  className = e.string("teachingClassName"),
                )
            }
          } else
            e.values.forEach {
              walk(
                it,
                e["week"] as? JsonObject ?: inheritedWeek,
                e["time"] as? JsonObject ?: inheritedTime,
              )
            }
        }
        else -> Unit
      }
    }
    walk(data)
    val explicitSuccess = code in setOf("200", "0") || obj["success"]?.jsonPrimitive?.booleanOrNull == true
    val confirmedEmpty = module == "schedule" && explicitSuccess && list.isEmpty() && namedCourses == 0 &&
      ((data is JsonArray && data.isEmpty()) || (knownCells > 0 && rawCourses == 0))
    val records = if (module == "schedule") emptyList() else jsonRecords(data)
    return Extraction(
      module,
      account,
      semester,
      source,
      list.distinctBy { it.stableId() },
      records,
      coverageWeeks = coverage,
      full = false,
      emptyConfirmed = confirmedEmpty,
      warnings =
        when {
          module == "schedule" && namedCourses > validCourses -> listOf("部分课程缺少星期、节次或周次，原课表保留")
          module == "schedule" && list.isEmpty() && !confirmedEmpty -> listOf("JSON 中未找到已支持的课程结构或成功证据")
          else -> emptyList()
        },
    )
  }

  private fun JsonObject.string(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull.orEmpty()

  private fun jsonRecords(data: JsonElement): List<NativeRecord> {
    val array =
      when (data) {
        is JsonArray -> data
        is JsonObject ->
          data.values.filterIsInstance<JsonArray>().firstOrNull() ?: JsonArray(listOf(data))
        else -> JsonArray(emptyList())
      }
    return array.mapNotNull { item ->
      val o = item as? JsonObject ?: return@mapNotNull null
      val fields =
        o.filterValues { it is JsonPrimitive }.mapValues { it.value.jsonPrimitive.content }
      if (fields.isEmpty()) null else NativeRecord(fields.values.first(), fields)
    }
  }
}
