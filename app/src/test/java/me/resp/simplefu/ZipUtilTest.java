package me.resp.simplefu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ZipUtilTest {
	@Test
	void testZipDirectory() {

	}

	@Test
	void testZipFile() {

	}

	public static Path createAfile(Path file, String content) {
		try {
			Files.write(file, content.getBytes());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return file;
	}

	/**
	 * Create a zip file with the given name and the given files and directories,
	 * /a.txt with content "a"
	 * /b.txt with content "b"
	 * /d1/x.txt with content "x"
	 * /d2/y.txt with content "y"
	 * /d2/d3/z.txt with content "z"
	 * 
	 * @return
	 */
	public static Path createAzipFile(Path zipFile) {
		Path a = Paths.get("a.txt");
		Path b = Paths.get("b.txt");
		Path d1 = Paths.get("d1");
		Path d2 = Paths.get("d2");
		Path d3 = Paths.get("d3");
		Path x = Paths.get("x.txt");
		Path y = Paths.get("y.txt");
		Path z = Paths.get("z.txt");
		try {
			Files.write(a, "a".getBytes());
			Files.write(b, "b".getBytes());
			Files.createDirectories(d1);
			Files.createDirectories(d2);
			Files.createDirectories(d3);
			Files.write(x, "x".getBytes());
			Files.write(y, "y".getBytes());
			Files.write(z, "z".getBytes());
			new ZipUtil().zipFile(zipFile, ZipNameType.ABSOLUTE, a, b, d1, d2);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return zipFile;
	}

	@Test
	void testCalPath(@TempDir Path tmpDir) {

		Path p1 = tmpDir.resolve("p1").toAbsolutePath().normalize();
		Assertions.assertThat(p1.getRoot().relativize(p1).toString());
		Path root = p1.getRoot();
		Assertions.assertThat(root.relativize(p1).toString().startsWith("/")).isFalse();
		System.out.println(root.relativize(p1).normalize().toString());

		System.out.println(p1.getRoot().toString());
		Path p2 = Paths.get("p2").resolve(p1);

		Assertions.assertThat(p2).isAbsolute();
		// Assertions.assertEquals(p1, ZipUtil.calPath(p2, "p1",
		// UnzipPathType.RELATIVELIZE));

	}
}
