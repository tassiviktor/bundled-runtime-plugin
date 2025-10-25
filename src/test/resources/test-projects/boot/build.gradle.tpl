plugins {
  id 'java'
  id 'org.springframework.boot' version '${BOOT_VERSION}'
  id 'io.spring.dependency-management' version '${DM_VERSION}'
  id 'hu.tassiviktor.gradle.plugins.bundled-runtime'
}
repositories { mavenCentral() }
java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }
dependencies {
  implementation 'org.springframework.boot:spring-boot-starter'
}
tasks.named('bootJar').configure { it.archiveFileName.set('app.jar') } // short-lived app
bundledRuntime { exitOnOome = false }
