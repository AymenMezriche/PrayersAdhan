plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    kotlin("plugin.serialization")
}

android {
    namespace = "com.better.alarm"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // ✅ Adds your Application ID as a BuildConfig field
        buildConfigField("String", "LIBRARY_ACTION_PREFIX", "\"com.best.deskclock\"")

        // Leave this empty for libraries, will be set by the app using the library , in app's build.gradle
        manifestPlaceholders["libApplicationId"] = ""
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

//dependencies {
//    val coroutinesVersion = "1.7.3"
//    val serializationVersion = "1.6.2"
//    implementation("ch.acra:acra-mail:5.12.0")
//    //we removed this because it uses old support libraries
////    implementation("com.melnykov:floatingactionbutton:1.3.0")
//    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
//    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-rx2:1.7.3")
//    implementation("io.insert-koin:koin-android:3.5.3")
//    implementation("androidx.fragment:fragment:1.6.2")
//    // TODO remove this when we don't use it anymore
//    implementation("androidx.preference:preference:1.2.1")
//    // resolves duplicate class caused by androidx.preference:preference:1.2.0
//    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
//    // implementation("androidx.lifecycle:lifecycle-viewmodel:2.5.1")
//
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-rx2:$coroutinesVersion")
//    implementation("com.google.android.material:material:1.8.0")
//    implementation("org.slf4j:slf4j-api:1.7.36")
//    implementation("com.github.tony19:logback-android:2.0.1")
//    implementation("androidx.multidex:multidex:2.0.1")
//    implementation("androidx.datastore:datastore:1.0.0")
//    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$serializationVersion")
//    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
//
//    testImplementation("net.wuerl.kotlin:assertj-core-kotlin:0.2.1")
//    testImplementation("junit:junit:4.13.2")
//    testImplementation("io.mockk:mockk:1.13.17")
//    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
//    testImplementation("org.slf4j:slf4j-simple:2.0.17")
//
//    val androidxTest = "1.6.1"
//    androidTestImplementation("com.squareup.assertj:assertj-android:1.2.0")
//    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
//    androidTestImplementation("androidx.test:runner:$androidxTest")
//    androidTestImplementation("androidx.test:rules:$androidxTest")
//    androidTestImplementation("androidx.test.ext:junit:1.2.1")
//}

dependencies {
    val coroutinesVersion = "1.7.3"
    val serializationVersion = "1.6.2"

    // Core dependencies needed by your library at runtime
    api("io.insert-koin:koin-android:3.5.3")
    api("io.reactivex.rxjava2:rxjava:2.2.21")
    api("io.reactivex.rxjava2:rxandroid:2.1.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-rx2:$coroutinesVersion")
    api("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$serializationVersion")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    api("androidx.fragment:fragment:1.6.2")
    api("androidx.preference:preference:1.2.1")
    api("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    api("androidx.multidex:multidex:2.0.1")
    api("androidx.datastore:datastore:1.0.0")
    api("com.google.android.material:material:1.8.0")

    // Logging / utility libs
    api("org.slf4j:slf4j-api:1.7.36")
    api("com.github.tony19:logback-android:2.0.1")

    // Optional or internal-only dependencies
    implementation("ch.acra:acra-mail:5.12.0")

    // Test
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.17")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("org.slf4j:slf4j-simple:2.0.17")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
