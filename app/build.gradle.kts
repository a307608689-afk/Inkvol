import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 版本号单一真源：gradle.properties
val inkvolVersionCode = providers.gradleProperty("INKVOL_VERSION_CODE").getOrElse("1").toInt()
val inkvolVersionName = providers.gradleProperty("INKVOL_VERSION_NAME").getOrElse("0.1.0")

// 正式签名口令：工程根 keystore.properties（已 gitignore，不入库）
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

android {
    namespace = "com.inkvol.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.inkvol.app"
        minSdk = 26
        targetSdk = 37
        versionCode = inkvolVersionCode
        versionName = inkvolVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 调试包与正式包同包名（com.inkvol.app）同签名：
            // debug 可直接覆盖安装到装了正式版的设备上，应用数据 / SAF 授权全部保留，
            // 装回正式版同样是覆盖安装、数据不丢。
            // 没有 keystore.properties 的机器退回默认 debug 签名（那时与正式版冲突，属预期）。
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            versionNameSuffix = "-debug"
        }

        release {
            // 正式发布签名：密钥与口令见工程根 keystore.properties（不入库）
            signingConfig = signingConfigs.getByName("release")
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

    // WebDAV 同步：只有 OkHttp 能发 PROPFIND/MKCOL（HttpURLConnection 有方法白名单）
    implementation(libs.okhttp)

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

// 发布构建缺少密钥时立刻失败：避免误产出无法上架的 unsigned / debug 签名包
val releaseTasksRequested = gradle.startParameter.taskNames.any {
    it.contains("Release", ignoreCase = true) || it == "assemble" || it == "build"
}
if (releaseTasksRequested && !keystorePropsFile.exists()) {
    throw GradleException(
        "缺少 keystore.properties：正式发布必须使用自建密钥签名。请运行 scripts/build-release.ps1。",
    )
}
