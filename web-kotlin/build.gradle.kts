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

tasks.register<Copy>("publishSite") {
    dependsOn("jsBrowserDistribution")
    from(layout.projectDirectory.dir("site-template"))
    into(layout.projectDirectory.dir("site"))
    from(layout.buildDirectory.dir("distributions")) {
        include("bounce-blade.js")
    }
    into(layout.projectDirectory.dir("site/kotlin"))
}
