package me.resp.simplefu;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import me.resp.simplefu.model.DeploymentEnv;

public class UtilForT {

	public static Path createAfileWithContent(Path file, String content) {
		try {
			Files.write(file, content.getBytes());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return file;
	}

	public static String randomString(int len) {
		String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
		StringBuilder sb = new StringBuilder(len);
		for (int i = 0; i < len; i++) {
			sb.append(chars.charAt((int) (Math.random() * chars.length())));
		}
		return sb.toString();
	}

	public static Path createARandomfile(Path file) {
		try {
			Path f = file.resolve(randomString(8) + ".txt");
			String content = randomString(100);
			Files.write(f, content.getBytes());
			return f;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * create notingit in running current directory.
	 * 
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

	public static String getContentInResources(String name) throws IOException {
		ClassLoader classLoader = UtilForT.class.getClassLoader();
		InputStream inputStream = classLoader.getResourceAsStream(name);
		return new String(inputStream.readAllBytes());
	}

	public static DeploymentEnv getDeploymentEnv() {
		try {
			return Util.loadDeploymentEnv(getContentInResources("deployment.env.test.properties"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
