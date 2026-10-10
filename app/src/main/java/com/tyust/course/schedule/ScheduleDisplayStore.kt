package com.tyust.course.schedule

import android.content.SharedPreferences
import org.json.JSONObject

data class ScheduleDisplayPreferences(
    val dayView: Boolean = false, val showWeekend: Boolean = true, val compact: Boolean = false,
    val textScale: Float = 1f, val rowHeightScale: Float = 1f, val showPeriodTimes: Boolean = true
)
data class ScheduleViewPosition(val week: Int, val day: Int, val weekScroll: Int, val dayScroll: Int, val calendar: String)

/** Only density/weekend are lasting preferences. A saved Activity session owns date, view and scroll. */
class ScheduleDisplayStore(private val prefs: SharedPreferences, private val session: String = "") {
    fun read(account: String): ScheduleDisplayPreferences {
        migrateWeekDefault(account)
        return ScheduleDisplayPreferences(
        if (session.isNotBlank() && prefs.getString("viewSession:$account", null) == session) prefs.getBoolean("day:$account", false) else false,
        prefs.getBoolean("weekend:$account", true), prefs.getBoolean("compact:$account", false),
        prefs.getFloat("textScale:$account",1f).coerceIn(0.85f,1.3f),
        prefs.getFloat("rowHeightScale:$account",1f).coerceIn(1f,1.6f),prefs.getBoolean("periodTimes:$account",true))
    }
    @Synchronized private fun migrateWeekDefault(account:String) {
        if(account.isBlank())return
        val key="week-default-v043:$account"
        if(!prefs.getBoolean(key,false)) prefs.edit().putBoolean("day:$account",false).putBoolean(key,true).commit()
    }
    fun write(account: String, value: ScheduleDisplayPreferences) {
        if (account.isBlank()) return
        migrateWeekDefault(account)
        prefs.edit().putString("viewSession:$account", session).putBoolean("day:$account", value.dayView).putBoolean("weekend:$account", value.showWeekend)
            .putBoolean("compact:$account", value.compact)
            .putFloat("textScale:$account",value.textScale).putFloat("rowHeightScale:$account",value.rowHeightScale)
            .putBoolean("periodTimes:$account",value.showPeriodTimes).apply()
    }
    fun position(account: String, term: String): ScheduleViewPosition? = runCatching {
        val data = JSONObject(prefs.getString("position:$account|$term", null) ?: return null)
        if (session.isBlank() || data.optString("session") != session) return null
        ScheduleViewPosition(data.getInt("week"), data.getInt("day").coerceIn(1, 7),
            data.optInt("weekScroll").coerceAtLeast(0), data.optInt("dayScroll").coerceAtLeast(0), data.optString("calendar"))
    }.getOrNull()
    fun savePosition(account: String, term: String, value: ScheduleViewPosition) {
        if (account.isBlank() || term.isBlank() || session.isBlank()) return
        prefs.edit().putString("position:$account|$term", JSONObject().put("session", session).put("week", value.week).put("day", value.day)
            .put("weekScroll", value.weekScroll).put("dayScroll", value.dayScroll).put("calendar", value.calendar).toString()).apply()
    }
}
