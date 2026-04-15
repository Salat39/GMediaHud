plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinSerialization)
    id(libs.plugins.googleServices.get().pluginId)
    id(libs.plugins.firebase.crashlytics.get().pluginId)
}

android {
    namespace = "com.salat.gmediahud"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.salat.gmediahud"
        minSdk = 26
        targetSdk = 35
        versionCode = 1420
        versionName = "1.5"

        setProperty("archivesBaseName", "$versionName[$versionCode]GMediaHud")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Float", "UI_SCALE", "1.4f")
        }
        debug {
            isDebuggable = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Float", "UI_SCALE", "1f")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }

    // Automatic signing and assembly
    // 1) Copy and rename the file "_secure.signing.gradle" to "secure.signing.gradle"
    // 2) You can copy it to any location and specify the path to it in the "gradle.properties" file
    // 3) Specify the necessary values to sign all builds of the application
    // 4) Run the command in the terminal "./gradlew prepareRelease"
    // 5) Wait and pick up all builds from "app/build/outputs/apk/" and "app/build/outputs/bundle/"
    // https://www.timroes.de/handling-signing-configs-with-gradle
    if (project.hasProperty("secure.signing") && project.file(project.property("secure.signing") as String).exists()) {
        apply(project.property("secure.signing"))
    }
}

dependencies {

    // For release build – compileOnly (used at compile time, not included in the final APK)
    add("releaseCompileOnly", project(":adaptapi"))
    add("releaseCompileOnly", project(":tencent"))
    add("releaseCompileOnly", project(":ecarx_car"))
    add("releaseCompileOnly", project(":ecarx_fw"))
    add("releaseCompileOnly", project(":com_geely"))

    // For debug build – implementation (fully included)
    add("debugImplementation", project(":adaptapi"))
    add("debugImplementation", project(":tencent"))
    add("debugImplementation", project(":ecarx_car"))
    add("debugImplementation", project(":ecarx_fw"))
    add("debugImplementation", project(":com_geely"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.media)
    implementation(libs.material)
    implementation(libs.coil.compose)
    implementation(libs.timber)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

afterEvaluate {
    tasks.matching { it.name == "kspNonMinifiedReleaseKotlin" }.all {
        dependsOn("kspReleaseKotlin")
    }
}

// -----------------------------------------------------
// Create release BP and assemble release
// -----------------------------------------------------

// Step 1: Create release profiles
tasks.register("prepareRelease") {
    // dependsOn("generateReleaseBaselineProfile")
    finalizedBy("assembleReleaseBuild")
}

// Step 2: Assemble release
tasks.register("assembleReleaseBuild").get()
    .dependsOn("assembleRelease")
