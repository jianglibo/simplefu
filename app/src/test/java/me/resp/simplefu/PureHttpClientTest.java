package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lombok.extern.slf4j.Slf4j;
import me.resp.simplefu.model.DeploymentEnv;

@Slf4j
public class PureHttpClientTest {

	@Test
	void testUploadToAzure(@TempDir Path tempdir) throws IOException, InterruptedException {
		DeploymentEnv denv = UtilForT.getDeploymentEnv();

		Path file = UtilForT.createARandomfile(tempdir);
		log.debug("using env: {}, to upload file: {}", denv, file);
		String result = PureHttpClient.uploadToAzure(file, denv.getServerRootUri(), denv.getShortTimePassword(),
				"azureblob", denv.getThisDeploymentId());
		log.debug("upload result: {}", result);
	}

	@Test
	void testDownloadDependenciesDownloadsFromAzure() throws IOException, InterruptedException {
		Path inTestResources = Path.of("src", "test", "resources", "395").toAbsolutePath();
		Assertions.assertThat(inTestResources).exists();
		DeploymentEnv denv = UtilForT.getDeploymentEnv();

		List<Path> paths = PureHttpClient.downloadDependenciesDownloadsFromAzure(inTestResources, denv.getServerRootUri(),
				denv.getShortTimePassword());
		log.debug("downloaded: {}", paths);
	}
}
