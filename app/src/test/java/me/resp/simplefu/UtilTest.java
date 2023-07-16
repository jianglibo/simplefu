package me.resp.simplefu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class UtilTest {

	public static Path createAfile(Path file, String content) {
		try {
			Files.write(file, content.getBytes());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return file;
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

	@Test
	void testExceptionHandler() {
		Util.exceptionHandler(() -> {
			return "yes";
		}, "", "will throw exception");
	}
}
