# Bundled Runtime Gradle Plugin

A lightweight Gradle plugin that builds a **self-contained, runnable distribution** for Java or Spring Boot applications.

It creates:
- a minimized **custom JRE** (via `jlink`)
- your **application JAR**
- **launcher scripts** (`bin/run` / `run.bat`)
- optionally, a **ZIP archive** for Docker or distribution

---

## Features

- Auto-detects Spring Boot (`bootJar`) vs. plain Java (`jar`)
- Optional module detection via `jdeps` (minimal runtime)
- Generates platform launchers (Linux + Windows)
- Adds `-XX:+ExitOnOutOfMemoryError` for container safety
- Produces Docker-ready, copy-and-run bundles
- Works with Gradle 7 and 8 (Property API)

---

##  Apply the Plugin

```groovy
plugins {
  id 'java'
  id 'org.springframework.boot' version '3.3.4' apply false
  id 'org.springframework.boot.aot' apply false                      //Optional, for AOT support
  id 'hu.tassiviktor.gradle.plugins.bundled-runtime' version '1.0.1'
}

apply plugin: 'org.springframework.boot' // only if Spring Boot

group = 'com.example'
version = '1.0.0'

repositories { mavenCentral() }

java {
  toolchain { languageVersion = JavaLanguageVersion.of(21) }
}
````

---

## Configuration

```groovy
// This is an example. No configuration required when default values match the needs
bundledRuntime {
  aotJvm = false                      // enable aot support (Spring boot 3.x)
  launcherName = 'run'                // launcher name (no extension)
  autoDetectModules = true            // run jdeps to find required modules
  exitOnOome = true                   // add -XX:+ExitOnOutOfMemoryError
  modules = [                         // base module set (used if autoDetectModules = false)
    'java.base','java.sql','java.xml',
    'java.logging','java.naming','java.management','jdk.unsupported'
  ]
  jlinkOptions = [                    // extra jlink options
    '--strip-debug','--no-header-files','--no-man-pages','--compress','2'
  ]
}
```

If not a Spring Boot project, make sure your JAR declares a `Main-Class`:

```groovy
jar {
  manifest {
    attributes 'Main-Class': 'com.example.Main'
  }
}
```

---

## Build Tasks

| Task           | Description                                                     |
| -------------- | --------------------------------------------------------------- |
| `buildBundled` | Builds the app, creates jlink runtime, and writes launchers.    |
| `zipBundled`   | Packages the bundled folder into a ZIP (`build/distributions`). |

Run:

```bash
./gradlew buildBundled
# or
./gradlew zipBundled
```

**Output structure:**

```
build/
 ├─ bundled/
 │   ├─ runtime/        # jlink-generated JRE
 │   ├─ app/app.jar     # your application
 │   ├─ bin/run(.bat)   # launcher
 │   └─ ...
 └─ distributions/
     └─ <name>-bundled-<version>.zip
```

---

## Docker Example

```dockerfile
FROM debian:stable-slim
WORKDIR /opt/app
COPY build/bundled /opt/app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
CMD ["./bin/run"]
```

---

## Behavior Overview

| Feature               | Description                                          |
|-----------------------|------------------------------------------------------|
| Spring Boot           | Uses `bootJar` automatically.                        |
| Plain Java            | Uses `jar` and libraries under `/app/lib`.           |
| Auto module detection | Uses `jdeps` to find required modules (default: on). |
| ExitOnOome            | Adds JVM flag for fail-fast restart.                 |
| AOT support           | Enable AOT build.                                    |

---

## Requirements

* JDK 17+ (Gradle daemon must run on a full JDK — `jlink` required)
* Works with Java 17–21+
* Compatible with Gradle 7.0–8.x

---

## Troubleshooting

| Problem            | Solution                                      |
| ------------------ | --------------------------------------------- |
| `jdeps not found`  | Ensure Gradle uses a JDK (not a JRE).         |
| `Module not found` | Add it manually via `bundledRuntime.modules`. |

---

## Author & License

**Author:** [Viktor Tassi](https://github.com/tassiviktor)
**Group ID:** `hu.tassiviktor.gradle.plugins.bundled-runtime`
**Version:** `1.0.1`
**License:** Public Domain

---

## Contributions

Pull requests, bug reports, and feature ideas are welcome!
The goal is simplicity — zero external dependencies, only Gradle.

---

## TL;DR;

```markdown
A Gradle plugin that builds a **self-contained runnable distribution** for Java and Spring Boot apps.

It automatically:
- detects whether the project is Spring Boot or plain Java
- creates a minimized JRE via `jlink`
- copies your app JARs and dependencies
- generates platform launchers (`bin/run`, `run.bat`)
- and optionally produces a ZIP archive

✅ Works on Gradle 7–8  
✅ JDK 17+ required  
✅ Perfect for Dockerized or slim runtime images

**Main tasks:**
- `buildBundled` → builds app + jlink runtime + launchers  
- `zipBundled` → creates a ZIP of the full bundle  

**Example:**
```groovy
bundledRuntime {
  autoDetectModules = true
  launcherName = 'run'
  exitOnOome = true
}
````
