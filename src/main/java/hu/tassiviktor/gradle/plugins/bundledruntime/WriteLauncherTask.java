package hu.tassiviktor.gradle.plugins.bundledruntime;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Writes platform-specific launchers:
 *   <bundled>/bin/run (unix) or run.bat (windows)
 * They execute: <runtime>/bin/java -jar <app>/app.jar [args...]
 */
public abstract class WriteLauncherTask extends DefaultTask {

    @Input
    public abstract Property<String> getLauncherName();

    @Input
    public abstract Property<Boolean> getExitOnOome();

    @OutputDirectory
    public abstract DirectoryProperty getBundledRoot();

    @TaskAction
    public void run() {
        File root = getBundledRoot().getAsFile().get();
        File bin = new File(root, "bin");
        if (!bin.mkdirs() && !bin.exists()) {
            throw new RuntimeException("Cannot create bin dir: " + bin);
        }

        String jvmFlags = getExitOnOome().get() ? "-XX:+ExitOnOutOfMemoryError" : "";

        if (Utils.isWindows()) {
            File bat = new File(bin, getLauncherName().get() + ".bat");
            String content = "@echo off\r\n" +
                    "set DIR=%~dp0\r\n" +
                    "set RUNTIME=%DIR%..\\runtime\r\n" +
                    "set APP=%DIR%..\\app\r\n" +
                    "set FLAGS=" + jvmFlags + "\r\n" +
                    "\"%RUNTIME%\\bin\\java.exe\" %FLAGS% -jar \"%APP%\\app.jar\" %*\r\n";
            writeFile(bat, content);
        } else {
            File sh = new File(bin, getLauncherName().get());
            String content = "#!/usr/bin/env bash\n" +
                    "set -euo pipefail\n" +
                    "DIR=\"$( cd \"$( dirname \"${BASH_SOURCE[0]}\" )\" && pwd )\"\n" +
                    "RUNTIME=\"$DIR/../runtime\"\n" +
                    "APP=\"$DIR/../app\"\n" +
                    "FLAGS=\"" + jvmFlags + "\"\n" +
                    "exec \"$RUNTIME/bin/java\" $FLAGS -jar \"$APP/app.jar\" \"$@\"\n";
            writeFile(sh, content);
            // make executable
            if (!sh.setExecutable(true)) {
                getLogger().warn("Could not mark launcher as executable: {}", sh);
            }
        }

        getLogger().lifecycle("[writeBundledLauncher] done -> {}", bin);
    }

    private static void writeFile(File f, String text) {
        try (FileWriter w = new FileWriter(f)) {
            w.write(text);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + f, e);
        }
    }
}
