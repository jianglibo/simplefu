package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BackupRestoreTaskTest {

	@Test
	void testBackup(@TempDir Path tmpDir) throws IOException {

		Path afile = ZipUtilTest.createAfile(tmpDir.resolve("a.txt"), "a");
		Path bfile = ZipUtilTest.createAfile(tmpDir.resolve("b.txt"), "b");

		Path inpuPath = ZipUtilTest.createAfile(tmpDir.resolve("copy-always.txt"),
				String.join(System.lineSeparator(),
						"a/a.txt -> " + afile.toString(),
						"/b/b.txt -> " + bfile.toString()));
		BackupRestoreTask backupRestoreTask = new BackupRestoreTask(inpuPath, null);
		Path backuped = backupRestoreTask.backup();

		Assertions.assertThat(backuped).exists();
		Assertions.assertThat(backuped).hasFileName("backup.zip");
		try (ZipTask zipTask = new ZipTask(backuped)) {
			zipTask.start(false);
			zipTask.allEntryPath().map(Path::toString).forEach(log::info);
			Assertions.assertThat(zipTask.findExactly("a/a.txt")).isEmpty();
			Assertions.assertThat(zipTask.findExactly("/b/b.txt")).isEmpty();
			Assertions.assertThat(zipTask.findEndsWith("/a.txt")).isPresent();
			Assertions.assertThat(zipTask.findEndsWith("/b.txt")).isPresent();
		}

		Files.delete(afile);
		Files.delete(bfile);

		Assertions.assertThat(afile).doesNotExist();
		Assertions.assertThat(bfile).doesNotExist();

		BackupRestoreTask restoreTask = new BackupRestoreTask(inpuPath, backuped);
		long count = restoreTask.restore();
		Assertions.assertThat(count).isEqualTo(2L);

		Assertions.assertThat(afile).exists();
		Assertions.assertThat(bfile).exists();

	}
}
