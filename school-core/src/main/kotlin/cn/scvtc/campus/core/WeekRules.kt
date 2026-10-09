package cn.scvtc.campus.core

object WeekRules {
  fun parse(input: String): List<Int> {
    val s =
      input
        .replace('，', ',')
        .replace('、', ',')
        .replace('；', ',')
        .replace(';', ',')
        .replace('－', '-')
        .replace('—', '-')
        .replace('–', '-')
        .replace('～', '-')
        .replace('~', '-')
        .replace(Regex("[\\s（）()\\[\\]周第]"), "")
    val odd = s.contains("单")
    val even = s.contains("双")
    require(!(odd && even)) { "单双周条件冲突" }
    val cleaned = s.replace("单", "").replace("双", "")
    require(cleaned.matches(Regex("\\d+(?:-\\d+)?(?:,\\d+(?:-\\d+)?)*"))) { "无法识别周次：$input" }
    return cleaned
      .split(',')
      .flatMap { token ->
        val parts = token.split('-').map(String::toInt)
        val end = parts.last()
        val start = parts.first()
        require(start > 0 && end >= start && end <= 100) { "周次范围不合法" }
        (start..end).toList()
      }
      .distinct()
      .sorted()
      .filter { (!odd || it % 2 == 1) && (!even || it % 2 == 0) }
  }

  fun nodes(input: String): List<Int> {
    val cleaned =
      input
        .replace('，', ',')
        .replace('、', ',')
        .replace('—', '-')
        .replace('－', '-')
        .replace('～', '-')
    val match = Regex("(\\d+(?:\\s*[-,]\\s*\\d+)*)").find(cleaned) ?: error("缺少节次")
    return match.value
      .replace(" ", "")
      .split(',')
      .flatMap { token ->
        val n = token.split('-').map(String::toInt)
        require(n.first() > 0 && n.last() >= n.first() && n.last() <= 30)
        (n.first()..n.last()).toList()
      }
      .distinct()
      .sorted()
  }

  fun runs(nodes: List<Int>): List<IntRange> {
    val result = mutableListOf<IntRange>()
    for (n in nodes.sorted().distinct()) {
      if (result.isNotEmpty() && result.last().last + 1 == n)
        result[result.lastIndex] = result.last().first..n
      else result += n..n
    }
    return result
  }
}
