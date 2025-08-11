@file:Suppress("UnstableApiUsage")

import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yourname.tapvpn.tunnel" // 🔁 Replace with your actual package
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

//    externalNativeBuild {
//        cmake {
//            path = file("tools/CMakeLists.txt")
//        }
//    }

    buildTypes {
        all {
//            externalNativeBuild {
//                cmake {
//                    targets("libwg-go.so", "libwg.so", "libwg-quick.so")
//                    arguments("-DGRADLE_USER_HOME=${project.gradle.gradleUserHomeDir}")
//                    arguments("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
//                }
//            }
        }
    }

    testOptions {
        unitTests.all {
            it.testLogging {
                events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
            }
        }
    }

    lint {
        disable += "LongLogTag"
        disable += "NewApi"
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.7.1")
    implementation("androidx.collection:collection:1.2.0")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    testImplementation("junit:junit:4.13.2")
}
