package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class VersionTaskTest {

	@Test
	void testBackupFileWithIncreasingSuffix(@TempDir Path tmpDir) throws IOException {
		Path a = tmpDir.resolve("a.txt");
		Files.writeString(a, "abc");
		VersionTask.backupFileWithIncreasingSuffix(a);

		Assertions.assertThat(tmpDir.resolve("a.1.txt")).exists();
		VersionTask.backupFileWithIncreasingSuffix(a);
		Assertions.assertThat(tmpDir.resolve("a.2.txt")).exists();
	}

	@Test
	void testBackupFileWithIncreasingSuffixNoSuffixOrigin(@TempDir Path tmpDir) throws IOException {

		Path a = tmpDir.resolve("a");
		Files.writeString(a, "abc");
		VersionTask.backupFileWithIncreasingSuffix(a);

		Assertions.assertThat(tmpDir.resolve("a.1")).exists();
		VersionTask.backupFileWithIncreasingSuffix(a);
		Assertions.assertThat(tmpDir.resolve("a.2")).exists();
		Path lf = VersionTask.findLargestBackup(a);
		Assertions.assertThat(lf).isEqualTo(a.getParent().resolve("a.2"));

		a = tmpDir.resolve("a.txt");
		Files.writeString(a, "abc");
		VersionTask.backupFileWithIncreasingSuffix(a);

		Assertions.assertThat(tmpDir.resolve("a.1.txt")).exists();
		VersionTask.backupFileWithIncreasingSuffix(a);
		Assertions.assertThat(tmpDir.resolve("a.2.txt")).exists();
		lf = VersionTask.findLargestBackup(a);
		Assertions.assertThat(lf).isEqualTo(a.getParent().resolve("a.2.txt"));
		VersionTask.restoreBackup(a);
		Assertions.assertThat(a).hasContent("abc");
		lf = VersionTask.findLargestBackup(a);
		Assertions.assertThat(lf).isEqualTo(a.getParent().resolve("a.1.txt"));
	}

	@Test
	void latestFile(@TempDir Path tmpDir) throws IOException {
		Path a = tmpDir.resolve("a.txt");
		Files.writeString(a, "abc");

		for (int i = 0; i < 13; i++) {
			VersionTask.backupFileWithIncreasingSuffix(a);
		}
		Path lf = VersionTask.findLargestBackup(a);
		Assertions.assertThat(lf).isEqualTo(a.getParent().resolve("a.13.txt"));

		for (int i = 0; i < 12; i++) {
			VersionTask.restoreBackup(a);
		}

		lf = VersionTask.findLargestBackup(a);
		Assertions.assertThat(lf).isEqualTo(a.getParent().resolve("a.1.txt"));

		for (int i = 0; i < 12; i++) {
			VersionTask.restoreBackup(a);
		}
		lf = VersionTask.findLargestBackup(a);
		Assertions.assertThat(lf).isNull();

	}
}
