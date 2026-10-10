import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin module: input engine, chord codec, text editor and typing session.
// It must never depend on Android — this is what keeps the core logic fast to test
// and impossible to entangle with UI code.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Flow/StateFlow appear in repository interfaces, so coroutines are part of the public API.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
