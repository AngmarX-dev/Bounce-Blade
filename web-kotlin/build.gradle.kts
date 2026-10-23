plugins {
    kotlin("js") version "2.1.0"
}

repositories {
    mavenCentral()
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "bounce-blade.js"
            }
        }
        binaries.executable()
    }
}

tasks.register<Copy>("copySiteShell") {
    from(layout.projectDirectory.dir("site-template"))
    into(layout.projectDirectory.dir("site"))
}

tasks.register<Copy>("copyKotlinBundle") {
    dependsOn("jsBrowserDistribution")
    from(layout.buildDirectory.dir("distributions")) {
        include("bounce-blade.js")
    }
    into(layout.projectDirectory.dir("site/kotlin"))
}

tasks.register("publishSite") {
    dependsOn("copySiteShell", "copyKotlinBundle")
}
