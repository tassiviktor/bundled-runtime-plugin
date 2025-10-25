plugins {
  id 'java'
  id 'hu.tassiviktor.gradle.plugins.bundled-runtime'
}
repositories { mavenCentral() }
java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }
bundledRuntime { exitOnOome = false }
jar { manifest { attributes 'Main-Class': 'com.example.Main' } }
