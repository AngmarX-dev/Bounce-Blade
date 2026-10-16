plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.angmarx.bounceblade"
    compileSdk = 35
    defaultConfig { applicationId = "com.angmarx.bounceblade"; minSdk = 23; targetSdk = 35; versionCode = 1; versionName = "1.0.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", "../Images", "../SoundTrack")
        }
    }
    androidResources { noCompress += listOf("wav") }
}

val generatedDesignRes = layout.buildDirectory.dir("generated/designRes")
android.sourceSets.getByName("main").res.srcDir(generatedDesignRes)
val prepareDesignLauncherIcon by tasks.registering(Copy::class) {
    from("../Images/App Icon & Logo/App Icon.png")
    into(generatedDesignRes.map { it.dir("mipmap-nodpi") })
    rename { "ic_launcher.png" }
}
tasks.named("preBuild").configure { dependsOn(prepareDesignLauncherIcon) }

dependencies {
    testImplementation("junit:junit:4.13.2")
}
