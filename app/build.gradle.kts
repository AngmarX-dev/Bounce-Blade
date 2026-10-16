import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

private fun trimTransparentPadding(image: BufferedImage): Pair<BufferedImage, Boolean> {
    val width = image.width
    val height = image.height
    if (width < 2 || height < 2 || !image.colorModel.hasAlpha()) return image to false

    val pixels = image.getRGB(0, 0, width, height, null, 0, width)
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    for (index in pixels.indices) {
        val alpha = (pixels[index] ushr 24) and 0xff
        if (alpha > 12) {
            val x = index % width
            val y = index / width
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
    }
    if (maxX < minX || maxY < minY) return image to false

    // Retain a small safety margin so soft glows and anti-aliased edges survive.
    val margin = maxOf(1, (maxOf(width, height) * 0.02f).toInt())
    val left = (minX - margin).coerceAtLeast(0)
    val top = (minY - margin).coerceAtLeast(0)
    val right = (maxX + margin + 1).coerceAtMost(width)
    val bottom = (maxY + margin + 1).coerceAtMost(height)
    if (left == 0 && top == 0 && right == width && bottom == height) return image to false

    // A subimage keeps the original color model (including indexed PNG palettes),
    // so trimming dimensions does not inflate compact source PNGs into RGBA files.
    val cropped = image.getSubimage(left, top, right - left, bottom - top)
    return cropped to true
}

val generatedTrimmedImageAssets = layout.buildDirectory.dir("generated/trimmedImageAssets")
val generatedDesignRes = layout.buildDirectory.dir("generated/designRes")

android {
    namespace = "com.angmarx.bounceblade"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.angmarx.bounceblade"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets {
        getByName("main") {
            // Images are copied into a generated asset tree after transparent PNG
            // padding is removed. Original repository assets remain untouched.
            assets.srcDirs("src/main/assets", generatedTrimmedImageAssets, "../SoundTrack")
        }
    }
    androidResources { noCompress += listOf("wav") }
}

android.sourceSets.getByName("main").res.srcDir(generatedDesignRes)

val trimTransparentImageAssets by tasks.registering {
    val sourceDirectory = layout.projectDirectory.dir("../Images")
    val outputDirectory = generatedTrimmedImageAssets
    inputs.dir(sourceDirectory)
    outputs.dir(outputDirectory)

    doLast {
        val sourceRoot = sourceDirectory.asFile
        val outputRoot = outputDirectory.get().asFile
        outputRoot.deleteRecursively()
        outputRoot.mkdirs()
        val trimmedPaths = mutableListOf<String>()

        sourceRoot.walkTopDown().filter { it.isFile }.forEach { sourceFile ->
            val relativePath = sourceFile.relativeTo(sourceRoot).path
                .replace(File.separatorChar, '/')
            val outputFile = File(outputRoot, relativePath)
            outputFile.parentFile.mkdirs()

            if (sourceFile.extension.equals("png", ignoreCase = true)) {
                val image = ImageIO.read(sourceFile)
                if (image != null) {
                    val (trimmedImage, wasTrimmed) = trimTransparentPadding(image)
                    if (wasTrimmed) {
                        if (!ImageIO.write(trimmedImage, "png", outputFile)) {
                            error("Could not write trimmed PNG: $relativePath")
                        }
                        trimmedPaths += relativePath
                    } else {
                        sourceFile.copyTo(outputFile, overwrite = true)
                    }
                } else {
                    sourceFile.copyTo(outputFile, overwrite = true)
                }
            } else {
                sourceFile.copyTo(outputFile, overwrite = true)
            }
        }

        File(outputRoot, "trimmed-assets.txt").writeText(
            trimmedPaths.sorted().joinToString(separator = "\n", postfix = if (trimmedPaths.isEmpty()) "" else "\n")
        )
        logger.lifecycle("Trimmed transparent padding from ${trimmedPaths.size} PNG assets.")
    }
}

val prepareDesignLauncherIcon by tasks.registering(Copy::class) {
    from("../Images/App Icon & Logo/App Icon.png")
    into(generatedDesignRes.map { it.dir("mipmap-nodpi") })
    rename { "ic_launcher.png" }
    doLast {
        val iconFile = generatedDesignRes.get().file("mipmap-nodpi/ic_launcher.png").asFile
        val icon = ImageIO.read(iconFile) ?: error("Could not read launcher icon PNG")
        val (trimmedIcon, wasTrimmed) = trimTransparentPadding(icon)
        if (wasTrimmed && !ImageIO.write(trimmedIcon, "png", iconFile)) {
            error("Could not write trimmed launcher icon")
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareDesignLauncherIcon, trimTransparentImageAssets)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
