package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CopyTaskTest {

	@Test
	void testCopyDirectory(@TempDir Path tmpDir) throws IOException {
		Path dst = tmpDir.resolve("dst");
		Files.createDirectory(dst);

		String dstFilename = dst.toString();
		Path inpuPath = UtilTest.createAfile(tmpDir.resolve("copy-always.txt"), String.join(System.lineSeparator(),
				"fixtures -> " + dstFilename));
		Stream<CopyItem> copyItems = InputFileParser.copyParser(inpuPath.toString()).parse();

		List<CopyItem> cis = copyItems.collect(Collectors.toList());

		Assertions.assertThat(cis).hasSize(8);

	}

	@Test
	void testFlatCopyMissingSource(@TempDir Path tmpDir) throws IOException {
		Path dst = tmpDir.resolve("dst");
		Files.createDirectory(dst);

		String dstFilename = dst.toString();
		Path inpuPath = UtilTest.createAfile(tmpDir.resolve("copy-always.txt"), String.join(System.lineSeparator(),
				"b/b.txt -> " + dstFilename));
		Stream<CopyItem> copyItems = InputFileParser.copyParser(inpuPath.toString()).parse();
		CopyTask copyTask = new CopyTask(copyItems, true);
		Assertions.assertThatThrownBy(() -> copyTask.start())
				.hasMessageContaining("b.txt");

		Util.setIgnoreMissingSource(true);
		copyItems = InputFileParser.copyParser(inpuPath.toString()).parse();
		CopyTask copyTask1 = new CopyTask(copyItems, true);
		copyTask1.start();
	}

	@Test
	void testFlatCopy(@TempDir Path tmpDir) throws IOException {
		// create a zip file for test.
		Path azip = tmpDir.resolve("a.zip");
		Path dst = tmpDir.resolve("dst");
		Files.createDirectory(dst);
		Path afile = UtilTest.createAfile(tmpDir.resolve("a.txt"), "a");
		Path bfile = UtilTest.createAfile(tmpDir.resolve("b.txt"), "b");

		ZipTask zipTask = ZipTask.get(azip, ZipNameType.ABSOLUTE, false);
		zipTask.push(afile, "a/a.txt");
		zipTask.push(bfile, "/b/b.txt");

		Assertions.assertThat(zipTask.findExactly("a/a.txt")).isPresent();
		Assertions.assertThat(zipTask.findExactly("/b/b.txt")).isPresent();

		Assertions.assertThat(zipTask.getZipFileSystem().getPath("a")).isDirectory().exists();

		String zipFileName = azip.toString();
		String dstFilename = dst.toString();
		Path inpuPath = UtilTest.createAfile(tmpDir.resolve("copy-always.txt"), String.join(System.lineSeparator(),
				zipFileName + "!a/a.txt -> " + dstFilename,
				zipFileName + "!/b/b.txt -> " + dstFilename));
		Stream<CopyItem> copyItems = InputFileParser.copyParser(inpuPath.toString()).parse();
		CopyTask copyTask = new CopyTask(copyItems, true);
		copyTask.start();
		Assertions.assertThat(dst.resolve("a.txt")).exists();
		Assertions.assertThat(dst.resolve("a.txt")).hasContent("a");
		Assertions.assertThat(dst.resolve("b.txt")).hasContent("b");
	}

	@Test
	void testWithDirectory(@TempDir Path tmpDir) throws IOException {
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
		Assertions.assertThat(zipTask.findExactly("a/a.txt")).isPresent();
		Assertions.assertThat(zipTask.findExactly("a/b.txt")).isPresent();

		Assertions.assertThat(zipTask.getZipFileSystem().getPath("a")).isDirectory().exists();

		String zipFileName = azip.toString();
		// the /bbb will be considered as a directory because the copyFrom is a
		// directory.
		String dstFilename = dst.toString() + "/bbb";
		// we point the copyFrom to a directory in a zip file.
		Stream<CopyItem> copyItems = InputFileParser.copyParser("")
				.parse(List.of(zipFileName + "!a -> " + dstFilename));
		CopyTask copyTask = new CopyTask(copyItems, true);
		copyTask.start();
		Assertions.assertThat(dst.resolve("bbb/a.txt")).exists();
		Assertions.assertThat(dst.resolve("bbb/a.txt")).hasContent("a");
		Assertions.assertThat(dst.resolve("bbb/b.txt")).hasContent("b");
	}
}
