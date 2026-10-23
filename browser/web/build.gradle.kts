plugins {
    kotlin("multiplatform")
}
kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }
    sourceSets {
        jsMain.dependencies {
            implementation("org.jetbrains.kotlin:kotlin-dom-api-compat:2.0.0")
        }
    }
}
