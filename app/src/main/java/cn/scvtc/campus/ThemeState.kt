package cn.scvtc.campus
import kotlinx.serialization.Serializable
enum class UiSystem {
  MIUIX,
  MATERIAL,
}

enum class VisualStyle { CLASSIC, ZHENGFANG, SLEEPDOWN }

enum class ThemeMode {
  SYSTEM,
  LIGHT,
  DARK,
}

@Serializable
data class ThemeState(
  val ui: UiSystem = UiSystem.MIUIX,
  val mode: ThemeMode = ThemeMode.SYSTEM,
  val blur: Float = 4f,
  val opacity: Float = .6f,
  val darkOpacity: Float = 2f / 3f,
  val tint: Float = 0f,
  val refraction: Float = 24f,
  val dispersion: Boolean = true,
  val highlight: Float = .75f,
  val radius: Float = 32f,
  val height: Float = 64f,
  val margin: Float = 24f,
  val border: Float = 1f,
  val shadow: Float = 10f,
  val spring: Float = 1f,
  val haptic: Boolean = true,
  val reduceMotion: Boolean = false,
  val style: VisualStyle = VisualStyle.ZHENGFANG,
  val collapseDock: Boolean = true,
  val courseCardBlur:Float=6f,
  val courseCardOpacity:Float=0.78f,
  val courseMaterial:Int=0,
  val courseRadius:Float=12f,
  val courseHighlight:Float=.35f,
  val courseMotion:Float=1f,
) {
  fun effectiveDark(system: Boolean) =
    when (mode) {
      ThemeMode.SYSTEM -> system
      ThemeMode.DARK -> true
      ThemeMode.LIGHT -> false
    }
}
