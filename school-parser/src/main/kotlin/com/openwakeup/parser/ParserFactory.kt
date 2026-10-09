package com.openwakeup.parser

import com.openwakeup.parser.web.YlParser

object ParserFactory {
  internal fun create(type: String): Parser =
    when (type) {
      "yl",
      "scvtc" -> YlParser
      else -> throw UnsupportedParserTypeException(type)
    }

  fun parse(input: ParserInput): List<CoursePreview> = create(input.type).parse(input)
}
