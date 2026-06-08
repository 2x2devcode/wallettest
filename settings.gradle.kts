import java.io.File
import java.util.Properties

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

fun resolveAndroidSdkDir(): String? {
    val fromEnv = sequenceOf(
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT")
    ).firstOrNull { !it.isNullOrBlank() && File(it).isDirectory }

    if (fromEnv != null) return fromEnv

    val home = System.getProperty("user.home")
    val candidates = listOf(
        "$home/Android/Sdk",
        "$home/android-sdk",
        "$home/Library/Android/sdk",
        "/opt/android-sdk",
        "/usr/local/lib/android/sdk"
    )

    return candidates.firstOrNull { File(it).isDirectory }
}

fun configureAndroidSdk(rootDir: File) {
    val localPropertiesFile = File(rootDir, "local.properties")
    val localProperties = Properties()

    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }

    var sdkDir = localProperties.getProperty("sdk.dir")?.trim()?.takeIf { it.isNotEmpty() }

    if (sdkDir == null || !File(sdkDir).isDirectory) {
        sdkDir = resolveAndroidSdkDir()
        if (sdkDir != null) {
            localPropertiesFile.writeText("sdk.dir=$sdkDir\n")
        }
    }

    if (sdkDir == null || !File(sdkDir).isDirectory) {
        throw GradleException(
            """
            Android SDK não encontrado.

            Configure o SDK de uma destas formas:
            1. Defina a variável de ambiente:
               export ANDROID_HOME=/caminho/para/android-sdk
            2. Crie local.properties com o caminho correto:
               echo "sdk.dir=/caminho/para/android-sdk" > local.properties
            3. Instale automaticamente (Linux/macOS):
               ./scripts/setup-android-sdk.sh

            Caminhos verificados automaticamente:
            - ANDROID_HOME (atual: ${System.getenv("ANDROID_HOME") ?: "não definido"})
            - ANDROID_SDK_ROOT (atual: ${System.getenv("ANDROID_SDK_ROOT") ?: "não definido"})
            - ~/Android/Sdk (Android Studio)
            - ~/android-sdk
            """.trimIndent()
        )
    }
}

configureAndroidSdk(settingsDir)

rootProject.name = "2x2Wallet"
include(":app")
