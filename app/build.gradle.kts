plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.angmarx.bounceblade"
    compileSdk = 35
    defaultConfig { applicationId = "com.angmarx.bounceblade"; minSdk = 23; targetSdk = 35; versionCode = 1; versionName = "1.0.0" }
    buildFeatures { compose = true }
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
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
