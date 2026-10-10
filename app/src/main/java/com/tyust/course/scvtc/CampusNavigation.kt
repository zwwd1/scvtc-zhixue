package com.tyust.course.scvtc
import kotlinx.coroutines.flow.MutableStateFlow
object CampusNavigation {
 val request=MutableStateFlow<String?>(null)
 val gradeTerm=MutableStateFlow<String?>(null)
}
