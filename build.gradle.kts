plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.room) apply false
}

tasks.register("calidad") {
  group = "verification"
  description = "Puerta de verificación N1 del Lector de Noticias."
  dependsOn(":app:testDebugUnitTest", ":app:assembleDebug")
}
