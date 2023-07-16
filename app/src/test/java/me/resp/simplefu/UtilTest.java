package me.resp.simplefu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

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

	}

	@Test
	void testExceptionHandlerGeneric() {
		Util.exceptionHandler(() -> {
			return "yes";
		}, "", 1, "will throw exception");

		Stream<CopyItem> items = Stream.of(CopyItem.builder().copyFrom("a").copyTo("b").build(),
				CopyItem.builder().copyFrom("c").copyTo("d").build());
		Stream<CopyItem> sc = Util.exceptionHandler(() -> {
			return items;
		}, Stream.empty(), 1, "will throw exception");

		Util.exceptionHandler(() -> {
		}, 1, "will throw exception");

	}

	@Test
	void testExceptionHandlerCatch() {
		Assertions.assertThatThrownBy(() -> {
			Util.exceptionHandler(() -> {
				throw new RuntimeException("test");
			}, "", 1, "will throw exception");
		}).hasMessageContaining("will throw exception");
	}

	@Test
	void tCopyException() {
		Assertions.assertThatThrownBy(() -> {
			Files.copy(Path.of("I don't exist"), Path.of(""));
		}).hasMessageContaining("I don't exist");
	}
}
