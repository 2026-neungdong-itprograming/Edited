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
    // Source: https://mvnrepository.com/artifact/org.apache.lucene/lucene-analyzers-nori
    implementation("org.apache.lucene:lucene-analyzers-nori:8.11.4")
    // Source: https://mvnrepository.com/artifact/org.eclipse.jgit/org.eclipse.jgit
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.7.1.202607240634-r")
}

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `com.example.app.AppKt`.)
    mainClass = "io.github.nd2026.edited.AppKt"
}
