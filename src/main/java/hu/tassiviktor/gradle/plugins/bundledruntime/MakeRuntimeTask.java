package hu.tassiviktor.gradle.plugins.bundledruntime;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Runs jlink to produce a minimized runtime image:
 *   <dest>/runtime
 */
// MakeRuntimeTask.java
public abstract class MakeRuntimeTask extends DefaultTask {

    @Input public abstract ListProperty<String> getModules();
    @Input public abstract ListProperty<String> getJlinkOptions();
    @Input public abstract Property<Boolean> getAutoDetectModules();

    @InputDirectory
    public abstract DirectoryProperty getAppRoot(); // points to build/bundled/app

    @OutputDirectory
    public abstract DirectoryProperty getDestinationDir();

    @TaskAction
    public void run() {
        Utils.ensureJava17Plus();

        // 1) Kimeneti könyvtár előkészítés
        File out = getDestinationDir().getAsFile().get();
        if (out.exists()) deleteRecursively(out);
        if (!out.mkdirs() && !out.exists()) {
            throw new GradleException("Cannot create runtime output dir: " + out);
        }

        // 2) Modulok eldöntése
        List<String> modulesToUse;
        if (Boolean.TRUE.equals(getAutoDetectModules().get())) {
            modulesToUse = autoDetectModules();
        } else {
            modulesToUse = getModules().get();
        }

        // 3) jlink futtatása
        File jlink = Utils.jlinkExecutable(getProject());
        List<String> args = new ArrayList<>();
        args.addAll(getJlinkOptions().get());
        args.add("--add-modules");
        args.add(String.join(",", modulesToUse));
        args.add("--output");
        args.add(out.getAbsolutePath());

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exit = getProject().exec(spec -> {
            spec.setExecutable(jlink.getAbsolutePath());
            spec.args(args);
            spec.setStandardOutput(stdout);
            spec.setErrorOutput(stderr);
            spec.setIgnoreExitValue(true);
        }).getExitValue();

        if (exit != 0) {
            throw new GradleException("jlink failed (exit=" + exit + ")\nSTDOUT:\n" +
                    stdout + "\nSTDERR:\n" + stderr);
        }

        getLogger().lifecycle("[makeBundledRuntime] done -> {}", out);
    }

    /** Runs jdeps to compute minimal module deps from app.jar (+ libs for non-Boot). */
    private List<String> autoDetectModules() {
        File appRoot = getAppRoot().getAsFile().get();
        File appJar = new File(appRoot, "app.jar");
        if (!appJar.exists()) {
            throw new GradleException("autoDetectModules: app.jar not found at " + appJar.getAbsolutePath());
        }

        // Build classpath from /app/lib (if exists)
        File libDir = new File(appRoot, "lib");
        String cp = "";
        if (libDir.exists() && libDir.isDirectory()) {
            File[] jars = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (jars != null && jars.length > 0) {
                cp = Arrays.stream(jars)
                        .map(File::getAbsolutePath)
                        .collect(Collectors.joining(File.pathSeparator));
            }
        }

        // jdeps --print-module-deps
        File jdeps = Utils.jdepsExecutable(getProject());
        List<String> args = new ArrayList<>();
        args.add("--ignore-missing-deps");
        // multi-release a toolchain fő verziójához igazítható, de 21 kompatibilis jó default
        args.add("--multi-release"); args.add("21");
        if (!cp.isEmpty()) { args.add("-cp"); args.add(cp); }
        args.add("--print-module-deps");
        args.add(appJar.getAbsolutePath());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = getProject().exec(spec -> {
            spec.setExecutable(jdeps.getAbsolutePath());
            spec.args(args);
            spec.setStandardOutput(out);
            spec.setErrorOutput(err);
            spec.setIgnoreExitValue(true);
        }).getExitValue();

        if (exit != 0) {
            throw new GradleException("jdeps failed (exit=" + exit + ")\nSTDOUT:\n" +
                    out + "\nSTDERR:\n" + err);
        }

        String line = out.toString().trim(); // comma-separated module list
        Set<String> modules = new LinkedHashSet<>();
        if (!line.isEmpty()) {
            modules.addAll(Arrays.stream(line.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .collect(Collectors.toList()));
        }

        // Safety add-ons (kis méret, nagy haszon):
        //  - TLS/HTTPS gyakran igényli az EC kriptót
        modules.add("jdk.crypto.ec");
        //  - néha szolgáltatók miatt hasznos (pl. SPI-k): --bind-services helyett inkább modul,
        //    de ha kell, beállíthatunk jlinkOptions közé "--bind-services"-t is.

        getLogger().lifecycle("[autoDetectModules] {}", modules);
        return new ArrayList<>(modules);
    }

    private static void deleteRecursively(File f) {
        if (!f.exists()) return;
        if (f.isDirectory()) {
            for (File c : f.listFiles()) deleteRecursively(c);
        }
        if (!f.delete()) {
            throw new GradleException("Failed to delete: " + f.getAbsolutePath());
        }
    }
}
