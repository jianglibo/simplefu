package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ZipTaskTest {

	/**
	 * if zip file doesn't exist, can we still use path api in it?
	 */
	@Test
	void tNotExistZip() {
		Assertions.assertThatThrownBy(() -> {
			ZipTask zipTask = ZipTask.get(Path.of("not-exists"), ZipNameType.ABSOLUTE, true);
		}).hasMessageContaining("Failed to open zip file");
	}

	@Test
	void testAddFileAbsolute(@TempDir Path tmpDir) throws IOException {
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");

		Path azip = tmpDir.resolve("a.zip");
		Path afile = UtilForT.createAfileWithContent(tmpDir.resolve("a.txt"), "a");

		ZipTask zipTask = ZipTask.get(azip, ZipNameType.ABSOLUTE, false);
		zipTask.push(afile, null); // null mean absolute.
		Assertions.assertThat(zipTask.findExactly(afile.toString())).isPresent();
		zipTask.close();
		Files.copy(azip, Path.of("fixtures/a.zip"), StandardCopyOption.REPLACE_EXISTING);
	}

	@Test
	void testAddFileFlatten(@TempDir Path tmpDir) throws IOException {
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");
		Path azip = tmpDir.resolve("a.zip");
		Path bfile = UtilForT.createAfileWithContent(tmpDir.resolve("b.txt"), "b");
		ZipTask zipTask = ZipTask.get(azip, ZipNameType.FLATTEN, false);
		zipTask.push(bfile, null);
		String zentryName = bfile.getFileName().toString();
		Assertions.assertThat(zipTask.findExactly(zentryName))
				.describedAs("%s should exist in the zip.", zentryName)
				.isPresent();
		zipTask.close();
		Files.copy(azip, Path.of("fixtures/a.zip"), StandardCopyOption.REPLACE_EXISTING);

	}

	@Test
	void tBfile(@TempDir Path tmpDir) throws IOException {

		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");
		Path bzip = tmpDir.resolve("b.zip");
		Path afile = UtilForT.createAfileWithContent(tmpDir.resolve("a.txt"), "a");
		ZipTask zipTask1 = ZipTask.get(bzip, ZipNameType.FLATTEN, false);
		String zentryName = "xxx/a.txt";
		zipTask1.push(afile, zentryName);
		Assertions.assertThat(zipTask1.findExactly(zentryName))
				.describedAs("%s should exist in the zip.", zentryName)
				.isPresent();
		zipTask1.close();
		Files.copy(bzip, Path.of("fixtures/b.zip"), StandardCopyOption.REPLACE_EXISTING);
	}
}
