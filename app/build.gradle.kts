plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("dagger.hilt.android.plugin")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.gmwapp.hima"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gmwapp.hima"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "11.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    flavorDimensions += "hima"
    productFlavors {
        create("development") {
            dimension = "hima"
          applicationIdSuffix = ".dev"
            buildConfigField( "String", "BASE_URL",  "\"https://demo.himaapp.in/api/auth/\"")
        }
        create("production") {
            dimension = "hima"
            buildConfigField( "String", "BASE_URL",  "\"https://himaapp.in/api/auth/\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    hilt {
        enableAggregatingTask = false
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    val lifecycleVersion = "2.6.2"
    val glideVersion = "4.11.0"

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    //viewmodel
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")

    // LiveData
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion")

    //coroutine
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3" )// Use latest version
    implementation ("io.github.chaosleung:pinview:1.4.4")

    implementation("com.github.bumptech.glide:glide:4.15.0")
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-android-compiler:2.48")
    kapt("androidx.hilt:hilt-compiler:1.1.0")

    //retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // Retrofit
    implementation("com.squareup.okhttp3:logging-interceptor:4.5.0")

    //glide
    implementation("com.github.bumptech.glide:glide:$glideVersion")
    annotationProcessor("com.github.bumptech.glide:compiler:$glideVersion")

    implementation ("com.google.android.flexbox:flexbox:3.0.0")
    implementation ("com.skyfishjy.ripplebackground:library:1.0.1")
    implementation ("androidx.fragment:fragment-ktx:1.6.1")
    implementation ("com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.1")
    implementation("com.google.firebase:firebase-crashlytics:18.4.1")
    implementation ("com.google.gms:google-services:4.3.14")
    implementation ("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")


    implementation ("com.github.ZEGOCLOUD:zego_uikit_prebuilt_call_android:3.9.1-beta2")

    implementation ("com.airbnb.android:lottie:3.4.0")


    implementation("com.intuit.sdp:sdp-android:1.1.0")

    //circleimageview
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("org.greenrobot:eventbus:3.3.1")

    implementation(libs.androidx.hilt.common)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime)
    implementation ("androidx.work:work-runtime-ktx:2.10.0")
    implementation ("com.onesignal:OneSignal:[5.0.0, 5.99.99]")
    implementation("com.google.androidbrowserhelper:androidbrowserhelper:2.5.0")

    implementation ("com.github.NorthernCaptain:TAndroidLame:1.1")
    implementation ("com.android.billingclient:billing:7.1.1")




    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}