package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Path;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ZipTaskTest {

	@Test
	void testAddFileAbsolute(@TempDir Path tmpDir) throws IOException {
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");

		Path azip = tmpDir.resolve("a.zip");
		Path afile = ZipUtilTest.createAfile(tmpDir.resolve("a.txt"), "a");

		ZipTask zipTask = new ZipTask(azip, ZipNameType.ABSOLUTE);
		zipTask.start(false);
		zipTask.push(afile, null);
		Assertions.assertThat(zipTask.findExactly(afile.toString())).isPresent();
		zipTask.close();
	}

	@Test
	void testAddFileFlatten(@TempDir Path tmpDir) throws IOException {
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");
		Path azip = tmpDir.resolve("a.zip");
		Path bfile = ZipUtilTest.createAfile(tmpDir.resolve("b.txt"), "b");
		ZipTask zipTask = new ZipTask(azip, ZipNameType.FLATTEN);
		zipTask.start(false);
		zipTask.push(bfile, null);
		String zentryName = bfile.getFileName().toString();
		Assertions.assertThat(zipTask.findExactly(zentryName))
				.describedAs("%s should exist in the zip.", zentryName)
				.isPresent();
		zipTask.close();
	}
}
