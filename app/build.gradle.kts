import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("dagger.hilt.android.plugin")
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
//    id("hypersdk.plugin") version "2.0.6"

}

// 2026-05-22 v22 — production signing for on-device payment testing.
// PhonePe and Cashfree merchant accounts whitelist by APK signature.
// Without signing the release APK with key0.jks, side-loaded APKs have a
// different SHA-256 fingerprint than the Play Store APK and payments fail.
// To activate: create app/keystore.properties (NOT committed) containing:
//   storeFile=C:/Users/Yuvanesh/Downloads/key0.jks
//   storePassword=YOUR_KEYSTORE_PASSWORD
//   keyAlias=key0
//   keyPassword=YOUR_KEY_PASSWORD     (often same as storePassword)
val keystorePropertiesFile = rootProject.file("app/keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

//hyperSdkPlugin {
//    clientId = "hdfcmaster"
//    sdkVersion = "2.1.20"
//}



android {
    namespace = "com.gmwapp.hima"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gmwapp.hima"
        // 2026-05-25 v1076 — minSdk back to 24 (was 26) so existing prod users on
        // Android 7.x can upgrade. Play Console blocked v1075 rollout otherwise.
        // 2026-05-25 v1077 — merged origin/final_bug_fixes (20 conflicts resolved).
        // 2026-05-25 v1078 — merged 5 more Perumal commits from innovfix123 fork
        //   (native SDK autopay, DND call gate, trial-offer video, female missed-call).
        // 2026-05-25 v1079 — merged Perumal c572d5e6 autopay checkout UI redesign
        //   (4 new drawables, hero ring + benefits card + sticky CTA, retry CTA).
        // 2026-05-25 v1080 — Bug #1 fix: persist peer id on outbound calls so
        //   switchToVideo/switchToAudio FCMs reach the receiver
        //   (MaleAudio + FemaleAudio now call saveSenderId(receiverId) in onCreate).
        // 2026-05-25 v1081 — Bug #4 partial: female now captures callStartMillis
        //   monotonic snapshot in onUserJoined to align with male side.
        //   Bug #5A: countdown timer correctly parses HH:MM:SS server format
        //   in all 4 call activities (was treating "00:02:00" as 2 sec instead
        //   of 120 sec, ending low-balance calls prematurely).
        //   Bug #7: two_min_duration_completed analytics now writes its dedupe
        //   flag after firing, so it stops re-firing on every app open.
        // 2026-05-25 v1082 — Bug #3: ReconnectWatchdog timeout 30s→15s so the
        //   OTHER side doesn't sit stuck on "in call" for 30s when peer's
        //   network drops. Bug #5B: leaveChannel now ALWAYS attempts Agora
        //   teardown regardless of isJoined flag, so low-balance early ends
        //   (and any path that flips isJoined=false before leaveChannel runs)
        //   don't leave the Agora channel live for free talk time.
        minSdk = 24
        targetSdk = 35
        versionCode = 1105
        versionName = "1105"


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Auto-sign with key0.jks when keystore.properties is present.
            // Falls back to debug signing if it isn't (e.g. on a fresh checkout).
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Lint vital errors block release assembly but the actual APK builds fine.
    // We're side-loading for payment testing, not shipping a Play Store update.
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    flavorDimensions += "hima"
    productFlavors {
        create("development") {
            dimension = "hima"
            applicationIdSuffix = ".dev"
            // Demo backend + demo OneSignal project. The demo OneSignal dashboard
            // (5cd4154a-...) needs its Android FCM config (Firebase service account
            // JSON + sender id 1061192153396) completed before pushes actually
            // deliver on this flavor — until then expect empty subscription
            // records (token="", enabled=false). In-app behaviour is unaffected.
            // demohima hosts the demo_autopay backend branch (Cashfree subscription
            // endpoints, plan IDs, webhook secret). demolivedb does not — pointing
            // dev there breaks /autopay_initiate and the trial sheet config endpoints.
            buildConfigField("String", "BASE_URL", "\"https://demohima.himaapp.in/api/auth/\"")
            buildConfigField("String", "ONESIGNAL_APP_ID", "\"5cd4154a-1ece-4c3b-b6af-e88bafee64cd\"")
        }
        create("production") {
            dimension = "hima"
            isDefault = true
            // productionDebug/Release: demolivedb backend (FCM verified working).
            // demohima has a revoked FCM server key — pushes don't deliver from there.
            // For Play Store / himaapp.in, use a separate release flavor.
            buildConfigField("String", "BASE_URL", "\"https://demolivedb.himaapp.in/api/auth/\"")
            buildConfigField("String", "ONESIGNAL_APP_ID", "\"5cd4154a-1ece-4c3b-b6af-e88bafee64cd\"")
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
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.core.animation)
    implementation(libs.firebase.analytics)
    
    // Firebase BOM and Firestore for chat
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    
    val lifecycleVersion = "2.6.2"
    val glideVersion = "4.11.0"

    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
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
    ksp("com.google.dagger:hilt-android-compiler:2.48")
    ksp("androidx.hilt:hilt-compiler:1.1.0")

    //retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // Retrofit
    implementation("com.squareup.okhttp3:logging-interceptor:4.5.0")
    
    // Socket.IO
    implementation("io.socket:socket.io-client:2.1.0")

    //glide
    implementation("com.github.bumptech.glide:glide:$glideVersion")
    ksp("com.github.bumptech.glide:compiler:$glideVersion")

    implementation ("com.google.android.flexbox:flexbox:3.0.0")
    implementation ("com.skyfishjy.ripplebackground:library:1.0.1")
    implementation ("androidx.fragment:fragment-ktx:1.6.1")
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0")
    implementation("com.google.firebase:firebase-crashlytics:18.4.1")
    implementation ("com.google.gms:google-services:4.3.14")
    implementation ("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")


  //  implementation ("com.github.ZEGOCLOUD:zego_uikit_prebuilt_call_android:3.9.1-beta2")

    implementation ("com.airbnb.android:lottie:3.4.0")


    implementation("com.intuit.sdp:sdp-android:1.1.0")
    
    // Google Play In-App Review
    implementation("com.google.android.play:review:2.0.1")
    implementation("com.google.android.play:review-ktx:2.0.1")

    //circleimageview
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("org.greenrobot:eventbus:3.3.1")

    implementation(libs.androidx.hilt.common)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime)
    implementation ("androidx.work:work-runtime-ktx:2.10.0")
    //implementation ("com.onesignal:OneSignal:[5.0.0, 5.99.99]")
    implementation("com.onesignal:OneSignal:5.1.34")
    implementation("com.google.androidbrowserhelper:androidbrowserhelper:2.5.0")

    implementation ("com.github.NorthernCaptain:TAndroidLame:1.1")



    //agora
    implementation("io.agora.rtc:full-sdk:4.5.0")
//    implementation("io.agora:agora-rtm:2.2.2")
    implementation("commons-codec:commons-codec:1.9")
    implementation ("com.android.billingclient:billing:7.1.1")


    //inAppUpdate
    implementation ("com.google.android.play:app-update:2.1.0")
    implementation ("com.google.android.play:app-update-ktx:2.1.0")

    // Meta Pixel
    implementation ("com.facebook.android:facebook-android-sdk:18.0.3")

    //Phonepe
    implementation ("phonepe.intentsdk.android.release:IntentSDK:5.1.0")

   // implementation ("com.google.mlkit:face-detection:16.1.7")

    implementation ("com.google.android.gms:play-services-mlkit-face-detection:17.1.0") // Unbundled model
    val camerax_version = "1.4.2"
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-video:${camerax_version}")

    implementation("androidx.camera:camera-view:${camerax_version}")
    implementation("androidx.camera:camera-extensions:${camerax_version}")

    implementation ("com.google.guava:guava:31.1-android")



//    implementation ("com.alphacephei:vosk-android:0.3.47")

    implementation ("io.jsonwebtoken:jjwt:0.9.1")

    //Truecaller
    implementation ("com.truecaller.android.sdk:truecaller-sdk:3.0.0")

    //Zoho chat
    implementation ("com.zoho.salesiq:mobilisten:8.2.2")

    //Appflyer
    implementation ("com.appsflyer:af-android-sdk:6.15.0")

    //Install referer
    implementation ("com.android.installreferrer:installreferrer:2.2")

    //Snapchat SDK - Install Tracking (App Ads Kit)
    implementation("com.snap.appadskit:appadskit:2.1.0")


    //Cashfree
    // 2.3.2 adds CFSubscriptionPayment / doSubscriptionPayment / CFSubscriptionResponseCallback
    // needed for native in-app UPI mandate checkout (replaces our Custom Tabs handoff).
    implementation ("com.cashfree.pg:api:2.3.2")

    implementation ("com.google.android.gms:play-services-auth-api-phone:18.2.0")



    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
