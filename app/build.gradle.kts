import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// URL base del backend. Nunca se versiona: se toma de la propiedad de Gradle
// `sigeh.api.baseUrl` (-P o ~/.gradle/gradle.properties) o de `local.properties`.
// Vacía por defecto: en M0 la app no se conecta a ningún servidor.
val localProperties = Properties().apply {
    val archivo = rootProject.file("local.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}
val sigehApiBaseUrl: String = (
    providers.gradleProperty("sigeh.api.baseUrl").orNull
        ?: localProperties.getProperty("sigeh.api.baseUrl")
        ?: ""
    ).replace("\"", "")

// Interruptor de IAM real (M1): `sigeh.api.iam=real` usa POST /auth/login, /refresh, /me y /logout
// del backend SIGE-H-WEB. Cualquier otro valor (o sin URL base) mantiene el modo Fake de M0.
val sigehIamReal: Boolean = (
    providers.gradleProperty("sigeh.api.iam").orNull
        ?: localProperties.getProperty("sigeh.api.iam")
        ?: "fake"
    ).trim().equals("real", ignoreCase = true)

// Interruptor de asistencia real (M2): `sigeh.api.asistencia=real` usa GET /pases-lista y
// PUT /programaciones/{id}/asistencia. Solo surte efecto con IAM real (ver NetworkConfig), así se
// puede probar IAM real con asistencia Fake si el backend tiene ASISTENCIA_ENABLED=false.
val sigehAsistenciaReal: Boolean = (
    providers.gradleProperty("sigeh.api.asistencia").orNull
        ?: localProperties.getProperty("sigeh.api.asistencia")
        ?: "fake"
    ).trim().equals("real", ignoreCase = true)

// Funciones sin backend todavía (M2): `sigeh.features.futuras=true` muestra incidencias,
// movimientos, recepción de eventual, resumen/envío del pase, sincronización y conflictos.
val sigehFuncionesFuturas: Boolean = (
    providers.gradleProperty("sigeh.features.futuras").orNull
        ?: localProperties.getProperty("sigeh.features.futuras")
        ?: "false"
    ).trim().equals("true", ignoreCase = true)

android {
    namespace = "mx.sigeh.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "mx.sigeh.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-m0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"$sigehApiBaseUrl\"")
        buildConfigField("boolean", "IAM_REAL", sigehIamReal.toString())
        buildConfigField("boolean", "ASISTENCIA_REAL", sigehAsistenciaReal.toString())
        buildConfigField("boolean", "FUNCIONES_FUTURAS", sigehFuncionesFuturas.toString())
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

room {
    // Esquema exportado para revisión y futuras migraciones (sin migraciones destructivas).
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    // Declarada explícitamente: la app usa corrutinas directamente y así su versión coincide con
    // kotlinx-coroutines-test (sin esto llegaba 1.9.0 transitiva y fallaban las pruebas instrumentadas).
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    // El registro HTTP solo existe en debug (ver src/debug).
    debugImplementation(libs.okhttp.logging.interceptor)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Servidor HTTP simulado para probar el cliente IAM contra el contrato real sin backend.
    testImplementation(libs.okhttp.mockwebserver3)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
