package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.resp.simplefu.model.DeploymentEnv;

public class UtilTest {

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

	@Test
	void zipAtSameDirectory(@TempDir Path tmpDir) throws IOException {
		Path d = tmpDir.resolve("d");
		Files.createDirectories(d);

		Path a = UtilForT.createAfileWithContent(d.resolve("a"), "a");
		Path b = UtilForT.createAfileWithContent(d.resolve("b"), "b");

		Path zip = tmpDir.resolve("zip.zip");

		List<Path> paths = Util.zipAtSameDirectory(zip, d);
		Assertions.assertThat(paths).hasSize(2);

		Path unzip = tmpDir.resolve("unzip");
		Files.createDirectories(unzip);
		Files.copy(zip, unzip.resolve(zip.getFileName()));
		Path toUnzip = unzip.resolve(zip.getFileName());
		Assertions.assertThat(toUnzip).exists();
		List<Path> paths2 = Util.unzipTo(toUnzip, null);
		Assertions.assertThat(paths2).hasSize(2);

		Path d2 = tmpDir.resolve("d2");
		Files.createDirectories(d2);

		paths2 = Util.unzipTo(toUnzip, d2);
		Assertions.assertThat(paths2).hasSize(2);

		Assertions.assertThat(Files.readAllLines(d2.resolve("a"))).containsExactly("a");
		Assertions.assertThat(Files.readAllLines(d2.resolve("b"))).containsExactly("b");
	}

	@Test
	void loadDeploymentEnv() throws IOException {
		Path p = Path.of("src/test/resources/deployment.env.properties");
		DeploymentEnv denv = Util.loadDeploymentEnv(p);
		/*
		 * conent of deployment.env.properties
		 * shortTimePassword=apassword
		 * serverRootUri=https://resp.me
		 * thisDeploymentId=4
		 * thisDeployDefinitionId=6
		 */
		Assertions.assertThat(denv.getShortTimePassword()).isEqualTo("apassword");
		Assertions.assertThat(denv.getServerRootUri()).isEqualTo("https://resp.me");
		Assertions.assertThat(denv.getThisDeploymentId()).isEqualTo("4");
		Assertions.assertThat(denv.getThisDeployDefinitionId()).isEqualTo("6");
	}
}
