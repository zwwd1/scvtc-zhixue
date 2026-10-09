package cn.scvtc.campus.core

import java.net.URI
import kotlinx.serialization.json.*

/** School-owned evidence is separate from rendering and persistence. */
class ScvtcSchoolAdapter {
  fun renderedModule(html: String, url: String, module: String) =
    ScvtcParser.html(html, url, module)

  fun observedSchedule(
    body: String,
    url: String,
    account: String,
    semester: String,
    coverage: List<Int>,
  ) = ScvtcParser.api(body, url, "schedule", account, semester, coverage)

  fun validateReadRecipe(recipe: QueryRecipe): Boolean =
    runCatching {
        val uri = URI(recipe.url)
        School.trusted(recipe.url) &&
          uri.host == "jwxt.scvtc.edu.cn" &&
          uri.path in School.readPaths &&
          recipe.method in setOf("GET", "POST") &&
          !Regex("(?i)password").containsMatchIn(recipe.body)
      }
      .getOrDefault(false)

  fun observedCoverage(query: JsonObject?): List<Int> {
    val start = query?.get("startWeek")?.jsonPrimitive?.intOrNull ?: return emptyList()
    val stop = query["stopWeek"]?.jsonPrimitive?.intOrNull ?: return emptyList()
    return if (start >= 1 && stop in start..100) (start..stop).toList() else emptyList()
  }
}
