package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UtilForT {

	/**
	 * create notingit in running current directory.
	 * @throws IOException
	 */
	public static Path recreateNotingitDirectory() throws IOException {
        Path pwd = Path.of("").normalize().toAbsolutePath();
		Path notingit = pwd.resolve("../notingit");
		if (Files.exists(notingit)) {
			Util.deleteDirectory(notingit);
		}
		Files.createDirectories(notingit);
		return notingit;
	}

}
