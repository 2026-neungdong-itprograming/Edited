plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}

group = "io.github.2026-neungdong-itprograming"

dependencies {
    // Source: https://mvnrepository.com/artifact/it.unimi.dsi/fastutil
    implementation("it.unimi.dsi:fastutil:8.5.19")
    // Source: https://mvnrepository.com/artifact/org.jctools/jctools-core
    implementation("org.jctools:jctools-core:4.0.7")
    // Source: https://mvnrepository.com/artifact/net.java.dev.jna/jna
    implementation("net.java.dev.jna:jna:5.19.1")
    // Kiwi (Korean morphological analyzer) is not published to Maven Central; its JNI binding jar
    // (which bundles the native KiwiJava library) comes from https://github.com/bab2min/Kiwi/releases
    // and is checked in under libs/. The language model is too large to commit - see README.md.
    implementation(fileTree("libs") { include("*.jar") })
    // Source: https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-serialization-json
    implementation(libs.kotlinxSerialization)
    // Source: https://mvnrepository.com/artifact/org.eclipse.jgit/org.eclipse.jgit
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.7.1.202607240634-r")
    testImplementation(kotlin("test"))
}

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `com.example.app.AppKt`.)
    mainClass = "io.github.nd2026.edited.AppKt"
}

// Input-latency / frame-time benchmark on 1M and 10M character manuscripts (see docs/EDITOR_PERFORMANCE.md).
// Usage: ./gradlew :app:editorBenchmark            (both sizes)
//        ./gradlew :app:editorBenchmark -Pchars=1000000
tasks.register<JavaExec>("editorBenchmark") {
    group = "verification"
    description = "Measures editor input latency and frame time on large synthetic manuscripts."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "io.github.nd2026.edited.perf.EditorPerfKt"
    jvmArgs("-Xmx2g", "-Djava.awt.headless=true")
    (findProperty("chars") as String?)?.let { args(it.split(",")) }
}
