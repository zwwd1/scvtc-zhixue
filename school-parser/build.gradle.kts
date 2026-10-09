plugins { kotlin("jvm") }
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
dependencies { implementation(project(":school-core")); implementation("org.jsoup:jsoup:1.18.3");implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1");testImplementation(kotlin("test")) }
