plugins {
    id ("com.android.application")
}

android {
    namespace = "com.example.contadorkm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.contadorkm"
        minSdk = 24          // Android 7.0 (Nougat) em diante — cobre bem mais de 95% dos aparelhos ativos
        targetSdk = 34        // Android 14
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled  = false
            proguardFiles ( getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation ("androidx.appcompat:appcompat:1.7.0")
    implementation ("com.google.android.material:material:1.12.0")
    implementation ("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation ("androidx.cardview:cardview:1.0.0")
    implementation ("androidx.activity:activity:1.9.0")

    // Localização (GPS) — usada pelo LocationService
    implementation ("com.google.android.gms:play-services-location:21.3.0")

    // Vão entrar nas próximas etapas:
    implementation ("androidx.room:room-runtime:2.6.1")         // banco local
    annotationProcessor ("androidx.room:room-compiler:2.6.1")
    implementation ("androidx.work:work-runtime:2.9.1")          // tarefas agendadas / notificações
}
