/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

class AppTest {

    // # the relative path is relative to the working directory.
    // fixtures/zip-playground/a.txt -> ../notingit
    // fixtures/zip-playground/adir/b.txt -> ../notingit

    @Test
    void testErrorTolerance() {
        App.main(new String[] { "--error-tolerance", "5" });
        Assertions.assertThat(Util.errorTolerance).isEqualTo(5);
    }

    @Test
    void backupAndRestore(@TempDir Path tmpDir) throws IOException {
        Path pwd = Path.of("").normalize().toAbsolutePath();
        Assertions.assertThat(pwd.getFileName().toString()).isEqualTo("app");

        Path backupTo = tmpDir.resolve("backup.zip");

        if (!Files.exists(pwd.resolve(App.COPY_ALWAYS_FILENAME))) {
            Files.copy(pwd.resolve("fixtures/copy-always.txt"),
                    pwd.resolve(App.COPY_ALWAYS_FILENAME));
        }

        int exitCode = new CommandLine(new App()).execute("backup", "--backup-to",
                backupTo.toString());

        Assertions.assertThat(exitCode).isEqualTo(0);
        if (Files.exists(pwd.resolve(App.COPY_ALWAYS_FILENAME))) {
            Files.delete(pwd.resolve(App.COPY_ALWAYS_FILENAME));
        }

        exitCode = new CommandLine(new App()).execute("backup", "--backup-to",
                backupTo.toString(),
                "fixtures/copy-always.txt", "fixtures/copy-if-missing.txt");
        Assertions.assertThat(exitCode).isEqualTo(0);
        // try (ZipTask zipTask = new ZipTask(backupTo)) {
        ZipTask zipTask = ZipTask.get(backupTo, ZipNameType.ABSOLUTE, true);
        Assertions.assertThat(zipTask.findExactly("/a.txt")).isEmpty();
        Assertions.assertThat(zipTask.findExactly("/adir/b.txt")).isEmpty();
        Assertions.assertThat(zipTask.findEndsWith("/a.txt")).isNotEmpty();
        Assertions.assertThat(zipTask.findEndsWith("/b.txt")).isNotEmpty();
        // }

        // Copying ..\notingit\a.txt(file) -->
        // C:/Users/jiang/simplefu/notingit/a.txt(jar)
        // Copying ..\notingit\b.txt(file) -->
        // C:/Users/jiang/simplefu/notingit/b.txt(jar)
        // Copying ..\notingit\only-if-missing.txt(file) -->
        // C:/Users/jiang/simplefu/notingit/only-if-missing.txt(jar)
        // Copying ..\notingit\b.txt(file) -->
        // C:/Users/jiang/simplefu/notingit/b.txt(jar)
        Path a = Path.of("../notingit/a.txt");
        Path b = Path.of("../notingit/b.txt");
        Path c = Path.of("../notingit/only-if-missing.txt");
        Assertions.assertThat(a).exists();
        Assertions.assertThat(b).exists();
        Assertions.assertThat(c).exists();

        Files.delete(a);
        Files.delete(b);
        Files.delete(c);
        Assertions.assertThat(a).doesNotExist();
        Assertions.assertThat(b).doesNotExist();
        Assertions.assertThat(c).doesNotExist();

        exitCode = new CommandLine(new App()).execute("restore", "--restore-from",
                backupTo.toString(),
                "fixtures/copy-always.txt", "fixtures/copy-if-missing.txt");
        Assertions.assertThat(a).exists();
        Assertions.assertThat(b).exists();
        Assertions.assertThat(c).exists();
    }
}
