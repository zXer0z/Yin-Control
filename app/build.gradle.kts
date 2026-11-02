import java.security.KeyStore
import java.security.MessageDigest

plugins { id("com.android.application") }
fun debugCertificateSha256(): String {
    val certificateFile = File(System.getProperty("user.home"), ".android/debug.keystore")
    require(certificateFile.isFile) { "Create the debug keystore first: tools/Prepare-DebugSigning.ps1 (see README)." }
    val keys = KeyStore.getInstance(KeyStore.getDefaultType())
    certificateFile.inputStream().use { keys.load(it, "android".toCharArray()) }
    return MessageDigest.getInstance("SHA-256").digest(keys.getCertificate("androiddebugkey").encoded)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

android {
    namespace = "local.v60.player"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "27.2.12479018"
    val configuredNdk = providers.gradleProperty("ndkPath").orNull
    if (configuredNdk != null) ndkPath = configuredNdk
    else rootProject.file(".tools/android-ndk-r27c").takeIf { it.isDirectory }?.let { ndkPath = it.absolutePath }
    defaultConfig {
        applicationId = "local.v60.player"
        minSdk = 31
        targetSdk = 36
        versionCode = 144
        versionName = "3.0.0-yin"
        ndk { abiFilters += "arm64-v8a" }
        val yangCert = providers.gradleProperty("yangCertSha256").orNull ?: debugCertificateSha256()
        require(yangCert.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid yangCertSha256" }
        buildConfigField("String", "YANG_CERT_SHA256", "\"${yangCert.lowercase()}\"")
        externalNativeBuild { ndkBuild { arguments += "APP_SUPPORT_FLEXIBLE_PAGE_SIZES=true" } }
    }
    buildTypes { release {
        isMinifyEnabled = true
        signingConfig = signingConfigs.getByName("debug")
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { buildConfig = true }
    sourceSets.getByName("main").java.srcDir(rootProject.file("shared/yin-bridge/java"))
    externalNativeBuild { ndkBuild { path = file("src/main/cpp/Android.mk") } }
    packaging { jniLibs { useLegacyPackaging = true }; resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
dependencies {
    compileOnly(files("libs/xposed-api.jar"))
    implementation(files("libs/ffmpeg-kit-audio-8.1.7.aar", "libs/smart-exception-java-0.2.1.jar", "libs/smart-exception-common-0.2.1.jar"))
}
