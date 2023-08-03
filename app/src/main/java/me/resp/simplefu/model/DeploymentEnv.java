package me.resp.simplefu.model;

import lombok.Data;

/**
 * shortTimePassword=
 * serverRootUri=
 * thisDeploymentId=
 * thisDeployDefinitionId=
 */
@Data
public class DeploymentEnv {
	private String shortTimePassword;
	private String serverRootUri;
	private String thisDeploymentId;
	private String thisDeployDefinitionId;
}
