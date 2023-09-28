package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lombok.extern.slf4j.Slf4j;
import me.resp.simplefu.model.Asset;
import me.resp.simplefu.model.DeploymentEnv;

@Slf4j
public class AppWithEnvTest {

	@Test
	void testUploadToAzure(@TempDir Path tempdir) throws IOException, InterruptedException {
		DeploymentEnv denv = UtilForT.getDeploymentEnv();

		AppWithEnv appWithEnv = new AppWithEnv(denv);

		Path file = UtilForT.createARandomfile(tempdir);
		log.debug("using env: {}, to upload file: {}", denv, file);
		Asset result = appWithEnv.uploadToAzure(file).getData();
		log.debug("upload result: {}", result);
	}

}
