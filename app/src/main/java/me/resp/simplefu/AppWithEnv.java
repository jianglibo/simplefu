package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.Getter;
import me.resp.simplefu.model.Asset;
import me.resp.simplefu.model.DataList;
import me.resp.simplefu.model.DataObject;
import me.resp.simplefu.model.DeployDefinition;
import me.resp.simplefu.model.Deployment;
import me.resp.simplefu.model.DeploymentEnv;

public class AppWithEnv {

	public static final String DEPLOYMENT_ENV_FILENAME = "deployment.env.properties";
	@Getter
	private final DeploymentEnv deploymentEnv;
	@Getter
	private final ObjectMapper objectMapper;
	@Getter
	private Deployment deployment;
	@Getter
	private DeployDefinition deployDefinition;

	/**
	 * @param deploymentEnv
	 * @throws IOException
	 * @throws DatabindException
	 * @throws StreamReadException
	 */
	public AppWithEnv(DeploymentEnv deploymentEnv) throws StreamReadException, DatabindException, IOException {
		this.deploymentEnv = deploymentEnv;
		this.objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
		loadValue();
	}

	private void loadValue() throws StreamReadException, DatabindException, IOException {
		this.deployDefinition = objectMapper.readValue(Path.of("definition.json").toFile(), DeployDefinition.class);
		this.deployment = objectMapper.readValue(Path.of("deployment.json").toFile(), Deployment.class);
	}

	public AppWithEnv(ObjectMapper objectMapper) throws IOException {
		this.deploymentEnv = Util.loadDeploymentEnv(Path.of(DEPLOYMENT_ENV_FILENAME));
		this.objectMapper = objectMapper;
		loadValue();
	}

	public AppWithEnv(DeploymentEnv deploymentEnv, ObjectMapper objectMapper) throws StreamReadException, DatabindException, IOException {
		this.deploymentEnv = deploymentEnv;
		this.objectMapper = objectMapper;
		loadValue();
	}

	/**
	 * upload a file to azure and attched to current deployment
	 * 
	 * @param file
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public DataObject<Asset> uploadToAzure(Path file) throws IOException, InterruptedException {

		String response = PureHttpClient.uploadToAzure(file, deploymentEnv.getServerRootUri(),
				deploymentEnv.getShortTimePassword(), "azureblob", deploymentEnv.getThisDeploymentId());
		return objectMapper.readValue(response, new TypeReference<DataObject<Asset>>() {
		});
	}

	/**
	 * download all assets from azure with it's id.
	 * 
	 * @param saveTo
	 * @param assetId
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public Path downloadOneAssetFromAzure(Path saveTo, Long assetId) throws IOException, InterruptedException {
		return PureHttpClient.downloadOneAssetFromAzure(saveTo, deploymentEnv.getServerRootUri(), assetId,
				deploymentEnv.getShortTimePassword());
	}

	/**
	 * Download all the assets attach to the dependency deployments.
	 * 
	 * @param workingDir
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public List<Path> downloadDependenciesDownloadsFromAzure(Path workingDir) throws IOException, InterruptedException {
		return PureHttpClient.downloadDependenciesDownloadsFromAzure(workingDir, deploymentEnv.getServerRootUri(),
				deploymentEnv.getShortTimePassword());
	}

	/**
	 * get the deployment history of current deploy definition.
	 * 
	 * @param deployDefinitionId
	 * @param count
	 * @param entrypointParams
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public DataList<Deployment> deploymentHistories(int count, String entrypointParams)
			throws IOException, InterruptedException {
		String response = PureHttpClient.deploymentHistories(deploymentEnv.getThisDeployDefinitionId(), count,
				entrypointParams,
				deploymentEnv.getServerRootUri(),
				deploymentEnv.getShortTimePassword());
		return objectMapper.readValue(response, new TypeReference<DataList<Deployment>>() {
		});
	}

	public DataList<Asset> deploymentAssets(Long deploymentId) throws IOException, InterruptedException {
		String respone = PureHttpClient.deploymentAssets(deploymentId, deploymentEnv.getServerRootUri(),
				deploymentEnv.getShortTimePassword());
		return objectMapper.readValue(respone, new TypeReference<DataList<Asset>>() {
		});
	}

}
