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

tasks.register("copyKotlinBundle") {
    dependsOn("browserProductionWebpack")
    doLast {
        val buildRoot = layout.buildDirectory.get().asFile
        val known = listOf(
            buildRoot.resolve("distributions/bounce-blade.js"),
            buildRoot.resolve("distributions/web-kotlin.js"),
            buildRoot.resolve("kotlin-webpack/js/productionExecutable/bounce-blade.js"),
            buildRoot.resolve("kotlin-webpack/js/productionExecutable/web-kotlin.js"),
            buildRoot.resolve("webpack/bin/main/productionExecutable/bounce-blade.js"),
            buildRoot.resolve("webpack/bin/main/productionExecutable/web-kotlin.js")
        )
        val bundle = known.firstOrNull { it.isFile } ?: buildRoot.walkTopDown()
            .filter { it.isFile && it.extension == "js" && !it.path.contains("node_modules") && !it.name.endsWith(".test.js") }
            .maxByOrNull { it.length() }
        if (bundle == null || !bundle.isFile || bundle.length() == 0L) {
            val found = buildRoot.walkTopDown().filter { it.isFile && it.extension == "js" }
                .take(40).joinToString("\\n") { it.relativeTo(buildRoot).path }
            throw GradleException("Kotlin/JS webpack output not found. JS files under build: $found")
        }
        val destination = layout.projectDirectory.dir("site/kotlin").asFile
        destination.mkdirs()
        bundle.copyTo(destination.resolve("bounce-blade.js"), overwrite = true)
        logger.lifecycle("Copied Kotlin/JS bundle from ${bundle.relativeTo(buildRoot).path} (${bundle.length()} bytes)")
    }
}

tasks.register("publishSite") {
    dependsOn("copySiteShell", "copyKotlinBundle")
}
