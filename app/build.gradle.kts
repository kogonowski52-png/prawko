import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

val demoMode = providers.gradleProperty("prawkoDemo").map(String::toBooleanStrict).getOrElse(false)
val previewMode = providers.gradleProperty("prawkoLocalPreview").map(String::toBooleanStrict).getOrElse(false)
val catalogPath = providers.gradleProperty("prawkoCatalogDir").orNull
require(!demoMode || !previewMode) { "Use either prawkoDemo or prawkoLocalPreview" }
require(catalogPath == null || (!demoMode && !previewMode)) {
    "Bundled catalogs cannot be combined with prawkoDemo or prawkoLocalPreview"
}

android {
    namespace = "pl.prawko.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "pl.prawko.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "PRAWKO_DEMO", "false")
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    buildTypes {
        debug {
            if (demoMode) {
                applicationIdSuffix = ".demo"
                versionNameSuffix = "-DEMO"
                buildConfigField("boolean", "PRAWKO_DEMO", "true")
                resValue("string", "app_name_demo", "Prawko DEMO")
                manifestPlaceholders["appLabel"] = "@string/app_name_demo"
            }
            if (previewMode) {
                applicationIdSuffix = ".preview"
                versionNameSuffix = "-Katalog-TEST"
                resValue("string", "app_name", "Prawko Katalog TEST")
            }
        }
    }

    sourceSets {
        named("debug") {
            java.srcDir(if (previewMode) "src/catalogPreview/java" else "src/noCatalogPreview/java")
        }
        named("release") { java.srcDir("src/noCatalogPreview/java") }
        if (previewMode) {
            named("androidTest") { java.srcDir("src/catalogPreviewAndroidTest/java") }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.media3:media3-exoplayer:1.9.1")
    implementation("androidx.media3:media3-ui:1.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    ksp("androidx.room:room-compiler:2.8.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

catalogPath?.let { path ->
    val catalogDir = rootProject.file(path)
    require(catalogDir.resolve("questions.json").isFile) {
        "prawkoCatalogDir must point to an importer output directory containing questions.json"
    }

    val catalogAssets = layout.buildDirectory.dir("generated/catalog-assets")
    val stageCatalogAssets by tasks.registering(Sync::class) {
        into(catalogAssets)
        from(catalogDir) {
            include("questions.json")
            into("catalog")
        }
        includeEmptyDirs = false
    }
    android.sourceSets.named("main") {
        assets.srcDir(catalogAssets)
    }
    tasks.named("preBuild") {
        dependsOn(stageCatalogAssets)
    }
}
