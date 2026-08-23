plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.chronosx.labsdk"
    compileSdk = 37

    defaultConfig {
        minSdk = 27
    }
}

dependencies {
    api(project(":core"))
}
