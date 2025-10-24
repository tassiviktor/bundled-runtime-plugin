NOTE: This is a WIP project. Do not use in production yet
# Bundled Runtime Gradle Plugin

A Gradle plugin that builds a **fully runnable folder** for Java / Spring Boot applications.
It produces a self-contained directory including:

* a minimized **custom JRE** (via `jlink`)
* your **application JAR**
* **launcher scripts** (`bin/run` or `run.bat`)
* (optionally) a **ZIP archive** ready for Docker / distribution

---

## Features

✅ Automatic detection of Spring Boot (`bootJar`) vs plain Java project
✅ Optional module auto-detection via `jdeps` (minimal runtime)
✅ Platform launchers (Linux + Windows)
✅ Adds `-XX:+ExitOnOutOfMemoryError` for safe fail-fast containers
✅ Produces a ready-to-copy bundle for Docker images
✅ Gradle 7 / 8 compatible (property API)

---

## Applying the Plugin

Add to your **Gradle build (Groovy DSL)**:

```groovy
plugins {
  id 'java'
  id 'org.springframework.boot' version '3.3.4' apply false
  id 'hu.tassiviktor.gradle.plugins.bundledruntime' version '0.1.0'
}

apply plugin: 'org.springframework.boot' // if this is a Spring Boot app

group = 'com.example'
version = '1.0.0'

repositories { mavenCentral() }

java {
  toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

dependencies {
  implementation 'org.springframework.boot:spring-boot-starter-web'
  testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

---

## Configuration Block

```groovy
bundledRuntime {
  // Name of the launcher script (without extension)
  launcherName = 'run'

  // Modules to include in the jlink image (ignored if autoDetectModules = true)
  modules = [
      'java.base','java.sql','java.xml',
      'java.logging','java.naming','java.management','jdk.unsupported'
  ]

  // Extra jlink options
  jlinkOptions = ['--strip-debug','--no-header-files','--no-man-pages','--compress','2']

  // Automatically use bootJar when Spring Boot plugin is applied
  detectSpringBoot = true

  // Add -XX:+ExitOnOutOfMemoryError to launcher
  exitOnOome = true

  // Produce build/distributions/<name>-bundled-<ver>.zip
  zipOutput = true
}
```

If your project is **not Spring Boot**, make sure your `jar` manifest defines a main class:

```groovy
jar {
  manifest {
    attributes 'Main-Class': 'com.example.Main'
  }
}
```

---

## Building the Bundle

```bash
./gradlew clean buildBundled
```

**Output structure:**

```
build/
 ├─ bundled/
 │   ├─ runtime/          # jlink-generated JRE
 │   ├─ app/app.jar       # your application
 │   ├─ bin/run           # launcher (run.bat on Windows)
 │   └─ ...               
 └─ distributions/
     └─ <name>-bundled-<version>.zip
```

---

## Minimal Docker Example

```dockerfile
FROM debian:stable-slim
WORKDIR /opt/app
COPY build/bundled /opt/app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
CMD ["./bin/run"]
```

This image contains only your app + a small JRE — no system JDK needed.

---

## Runtime Detection & Behavior

| Feature                   | Description                                                                                                                            |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| **Spring Boot projects**  | Uses `bootJar` (fat JAR).                                                                                                              |
| **Plain Java projects**   | Uses `jar` + runtime classpath libraries under `/app/lib`.                                                                             |
| **Auto module detection** | Optional (enabled by default). Runs `jdeps --print-module-deps` on the built artifacts to generate the minimal module set for `jlink`. |
| **ExitOnOome**            | Adds `-XX:+ExitOnOutOfMemoryError` to launcher for container restarts.                                                                 |
| **Zip output**            | Controlled by `zipOutput = true`.                                                                                                      |

---

## Important Notes

* If you disable `bootJar` (for `bootWar` projects), the plugin falls back to the plain `jar` artifact — you must provide a valid `Main-Class`.
* The generated runtime image depends on the modules your app uses. You can force specific modules by setting `autoDetectModules = false` and listing them manually.
* For HTTPS/TLS, the plugin automatically includes `jdk.crypto.ec`.
* The launcher scripts are platform-agnostic and can be used directly on any system with basic shell / cmd support.
* Gradle daemon must run on JDK 17+ (since `jlink` requires a full JDK).

---

## Available Tasks

| Task                   | Description                                                         |
| ---------------------- | ------------------------------------------------------------------- |
| `prepareBundledApp`    | Builds the application JAR(s) into `build/bundled/app`.             |
| `makeBundledRuntime`   | Runs `jdeps` (when enabled) and `jlink` to create a custom runtime. |
| `writeBundledLauncher` | Generates platform launch scripts.                                  |
| `zipBundled`           | Packages everything into a ZIP under `build/distributions`.         |
| `buildBundled`         | Lifecycle task that runs all of the above.                          |

---

## Troubleshooting Tips

| Issue                                         | Cause / Solution                                                                                  |
| --------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| `jdeps not found`                             | Ensure your Gradle toolchain points to a **JDK** (not a JRE).                                     |
| `Module not found` in jlink                   | Add it manually to `bundledRuntime.modules`.                                                      |

---

## License and Author

**Author:** [Viktor Tassi](https://github.com/tassiviktor)
**Group ID:** `hu.tassiviktor.gradle.plugins.bundledruntime`
**Version:** `0.1.0`
**License:** Public Domain

---

## Feedback & Contributions

Pull requests, bug reports, and feature ideas are welcome!
This plugin is intentionally lightweight — no external dependencies beyond Gradle itself.
