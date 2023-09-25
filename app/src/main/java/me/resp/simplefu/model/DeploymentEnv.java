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
        private Long thisDeploymentId;

        private String thisDeployDefinitionSecret;
        private Long myUserId;
        private Long thisDeployDefinitionId;
        private Long thisTemplateDeployHistory;
        private Long thisTemplateId;
}
