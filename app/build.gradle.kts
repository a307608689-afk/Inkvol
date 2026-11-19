plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.inkvol.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.inkvol.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 临时用 debug 密钥签名，便于覆盖安装做性能对比；正式发布时替换为正式密钥
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

// Compose 编译器指标：构建时生成报告，列出每个可组合函数是否可跳过重组及原因
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_reports")
    metricsDestination = layout.buildDirectory.dir("compose_metrics")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.androidx.work.runtime.ktx)

    // 归档格式：7z/CB7（含 LZMA2）与 RAR4/RAR5（junrar 8.x 原生支持 RAR5）
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.junrar)
    runtimeOnly(libs.slf4j.nop)

    // 液态玻璃原语（Compose Multiplatform 库，Android 产物为 backdrop-android）
    implementation(libs.backdrop)
    // 连续曲率形状（Capsule / RoundedRectangle），backdrop 只以 implementation 引入，需显式声明
    implementation(libs.kyant.shapes)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation("androidx.test:runner:1.7.0")
    // 仅基准测试用：对比 DocumentFile 逐项访问与 DocumentsContract 批量 query
    androidTestImplementation("androidx.documentfile:documentfile:1.1.0")
}
