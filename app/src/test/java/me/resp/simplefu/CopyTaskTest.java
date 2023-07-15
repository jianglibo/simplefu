package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CopyTaskTest {
	@Test
	void testStart(@TempDir Path tmpDir) throws IOException {
		Path azip = tmpDir.resolve("a.zip");
		Path dst = tmpDir.resolve("dst");
		Files.createDirectory(dst);
		Path afile = ZipUtilTest.createAfile(tmpDir.resolve("a.txt"), "a");
		Path bfile = ZipUtilTest.createAfile(tmpDir.resolve("b.txt"), "b");
		try (ZipTask zipTask = new ZipTask(azip)) {
			zipTask.start(false);
			zipTask.push(afile, "a/a.txt");
			zipTask.push(bfile, "/b/b.txt");
			Assertions.assertThat(zipTask.findExactly("a/a.txt")).isPresent();
			Assertions.assertThat(zipTask.findExactly("/b/b.txt")).isPresent();
		}

		String zipFileName = azip.toString();
		String dstFilename = dst.toString();
		Path inpuPath = ZipUtilTest.createAfile(tmpDir.resolve("copy-always.txt"), String.join(System.lineSeparator(),
				zipFileName + "!a/a.txt -> " + dstFilename,
				zipFileName + "!/b/b.txt -> " + dstFilename));
		CopyTask copyTask = new CopyTask(inpuPath, true);
		copyTask.start();
		Assertions.assertThat(dst.resolve("a.txt")).exists();
		Assertions.assertThat(dst.resolve("a.txt")).hasContent("a");
		Assertions.assertThat(dst.resolve("b.txt")).hasContent("b");
	}
}
