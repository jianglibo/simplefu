package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Path;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ZipTaskTest {

	@Test
	void testAddFileAbsolute(@TempDir Path tmpDir) throws IOException {
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");

		Path azip = tmpDir.resolve("a.zip");
		Path afile = UtilTest.createAfile(tmpDir.resolve("a.txt"), "a");

		ZipTask zipTask = ZipTask.get(azip, ZipNameType.ABSOLUTE, false);
		zipTask.push(afile, null);
		Assertions.assertThat(zipTask.findExactly(afile.toString())).isPresent();
		zipTask.close();
	}

	@Test
	void testAddFileFlatten(@TempDir Path tmpDir) throws IOException {
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");
		Path azip = tmpDir.resolve("a.zip");
		Path bfile = UtilTest.createAfile(tmpDir.resolve("b.txt"), "b");
		ZipTask zipTask = ZipTask.get(azip, ZipNameType.FLATTEN, false);
		zipTask.push(bfile, null);
		String zentryName = bfile.getFileName().toString();
		Assertions.assertThat(zipTask.findExactly(zentryName))
				.describedAs("%s should exist in the zip.", zentryName)
				.isPresent();
		zipTask.close();
	}
}
