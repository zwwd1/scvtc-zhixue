package cn.scvtc.campus.core

import java.math.BigDecimal

data class CreditSummary(val confirmed:BigDecimal,val courses:Int,val uncounted:Int) {
    val display:String get()=confirmed.stripTrailingZeros().toPlainString()
    companion object {
        /** Official acquired credits, with retakes merged by course code. */
        fun from(records:List<NativeRecord>):CreditSummary {
            val usable=records.mapNotNull { record ->
                val code=record.fields["课程代码"]?.takeIf(String::isNotBlank)?:return@mapNotNull null
                val value=record.fields["获得学分"]?.toBigDecimalOrNull()?.takeIf{it.signum()>=0}?:return@mapNotNull null
                code to value
            }
            val courses=usable.groupBy({it.first},{it.second}).mapValues{(_,attempts)->attempts.max()}
            return CreditSummary(courses.values.fold(BigDecimal.ZERO,BigDecimal::add),courses.size,records.size-usable.size)
        }
    }
}
