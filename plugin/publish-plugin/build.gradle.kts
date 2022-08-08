buildscript {
  repositories {
    google()
    mavenCentral()
  }
}

plugins {
  `java-gradle-plugin`
  `kotlin-dsl`
}

dependencies {
  implementation("com.android.tools.build:gradle:7.1.3")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.21")
}

gradlePlugin {
  plugins {
    create("mvi-core-publish-android") {
      id = "mvi-core-publish-android"
      implementationClass = "AndroidMviCorePublishPlugin"
    }
    create("mvi-core-publish-java") {
      id = "mvi-core-publish-java"
      implementationClass = "JavaMviCorePublishPlugin"
    }
  }
}
