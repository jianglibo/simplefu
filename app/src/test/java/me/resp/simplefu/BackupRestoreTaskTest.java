package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BackupRestoreTaskTest {

	@Test
	void testBackup(@TempDir Path tmpDir) throws IOException {
		// when do backup task, the source file does need to exist.
		// what we backup is from the destination files.
		// Util.ignoreMissingSource = true;
		Path afile = UtilTest.createAfile(tmpDir.resolve("a.txt"), "a");
		Path bfile = UtilTest.createAfile(tmpDir.resolve("b.txt"), "b");

		Path inpuPath = UtilTest.createAfile(tmpDir.resolve("copy-always.txt"),
				String.join(System.lineSeparator(),
						"a/a.txt -> " + afile.toString(),
						"/b/b.txt -> " + bfile.toString()));
		BackupRestoreTask backupRestoreTask = new BackupRestoreTask(inpuPath, null);
		Path backuped = backupRestoreTask.backup();

		log.info("backuped: {}, length: {}", backuped, Files.size(backuped));

		Assertions.assertThat(backuped).exists();
		Assertions.assertThat(backuped).hasFileName("backup.zip");
		ZipTask zipTask = ZipTask.get(backuped, ZipNameType.ABSOLUTE, true);
		zipTask.allEntryPath().map(Path::toString).forEach(log::info);
		Assertions.assertThat(zipTask.findExactly("a/a.txt")).isEmpty();
		Assertions.assertThat(zipTask.findExactly("/b/b.txt")).isEmpty();
		Assertions.assertThat(zipTask.findEndsWith("/a.txt")).isPresent();
		Assertions.assertThat(zipTask.findEndsWith("/b.txt")).isPresent();

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

	@Test
	void testBackupWithDirectories(@TempDir Path tmpDir) throws IOException {

		// create a zip file for test.
		Path azip = tmpDir.resolve("a.zip");
		Path dst = tmpDir.resolve("dst");
		Files.createDirectory(dst);
		Path afile = UtilTest.createAfile(tmpDir.resolve("a.txt"), "a");
		Path bfile = UtilTest.createAfile(tmpDir.resolve("b.txt"), "b");
		ZipTask zipTask = ZipTask.get(azip, ZipNameType.ABSOLUTE, false);
		// here we got a directory a which contains 2 files.
		zipTask.push(afile, "a/a.txt");
		zipTask.push(bfile, "a/b.txt");
		// when do backup, the copyTo is meaning which to backup and the meaning of the
		// copyTo will vary depending on the copyFrom.
		// We should copy the files to the dst first or else the backup will skip the
		// missing items.
		BackupRestoreTask backupRestoreTask = new BackupRestoreTask(InputFileParser.restoreParser("")
				.parse(List.of(azip.toString() + "!a -> " + dst)), null);
		Path backuped = backupRestoreTask.backup();

		ZipTask backupedZipTask = ZipTask.get(backuped, ZipNameType.ABSOLUTE, true);

		Assertions.assertThat(backuped).exists();
		Assertions.assertThat(backuped).hasFileName("backup.zip");
		Assertions.assertThat(backupedZipTask.findEndsWith("/a.txt"))
				.describedAs("a.txt should not be backuped because it is not in the copyTo directory.")
				.isEmpty();
		Assertions.assertThat(backupedZipTask.findEndsWith("/b.txt"))
				.describedAs("b.txt should not be backuped because it is not in the copyTo directory.")
				.isEmpty();

		// Copy the files into dst.
		Files.copy(afile, dst.resolve("a.txt"));
		Files.copy(bfile, dst.resolve("b.txt"));
		ZipTask.clearCache();

		backupRestoreTask = new BackupRestoreTask(InputFileParser.restoreParser("")
				.parse(List.of(azip.toString() + "!a -> " + dst)), null);
		backuped = backupRestoreTask.backup();

		backupedZipTask = ZipTask.get(backuped, ZipNameType.ABSOLUTE, true);
		Assertions.assertThat(backupedZipTask.findEndsWith("/a.txt"))
				.describedAs("a.txt should be backuped because it is in the copyTo directory.")
				.isPresent();
		Assertions.assertThat(backupedZipTask.findEndsWith("/b.txt"))
				.describedAs("b.txt should be backuped because it is in the copyTo directory.")
				.isPresent();
		ZipTask.clearCache();

		// clear the dst folder.
		Files.walk(dst).map(p -> {
			try {
				Files.delete(p);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return 0;
		});

		BackupRestoreTask restoreTask = new BackupRestoreTask(
				InputFileParser.restoreParser("")
						.parse(List.of(azip.toString() + "!a -> " + dst)),
				backuped);
		long count = restoreTask.restore();
		Assertions.assertThat(count).isEqualTo(2L);

		// Restore will write the file to the final destination after parsing the
		// listfilelist. So it maybe not the same as the original copyping from.

		// Files.copy(afile, dst.resolve("a.txt"));
		// Files.copy(bfile, dst.resolve("b.txt"));

		Assertions.assertThat(dst.resolve("a.txt")).exists();
		Assertions.assertThat(dst.resolve("b.txt")).exists();

	}
}
