package me.resp.simplefu.model;

import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeployDefinition {

	public static final String FIELD_NAME_TEMPLATE_CUSTOMIZE_ID = "template_customize_id";
	private Long id;

	private String name;

	/**
	 * the combination of the template_id and template_customize_id is the unique
	 * product id. which
	 * has many prices.
	 */

	@JsonProperty("template_id")
	private Long templateId;

	@JsonProperty("minimal_requires_checked")
	private boolean minimalRequiresChecked;

	@JsonProperty(FIELD_NAME_TEMPLATE_CUSTOMIZE_ID)
	private Long templateCustomizeId;

	@JsonProperty("template_deploy_history_id")
	private Long templateDeployHistoryId;

	/**
	 * Has some special keys:
	 * 
	 * <pre>
	 * {
	 * "entrypoint_params": [],
	 * "all_possible_actions": [],
	 * }
	 * </pre>
	 */
	@Builder.Default
	private String settings = "{}";

	private Boolean pause;

	// private String cron;

	private Long next;

	private String secret;

	private String region;

	// started, ready, unused
	private String status;

	public boolean started() {
		return "started".equals(status);
	}

	public boolean ready() {
		return "ready".equals(status);
	}

	public void beReady() {
		this.status = "ready";
	}

	public String getRegion() {
		if ("outside".equals(region)) {
			return null;
		} else {
			return region;
		}
	}

	@JsonProperty("deploy_to")
	private String deployTo;

	@JsonProperty("user_id")
	private Long userId;

	@JsonProperty("keep_working_env")
	private boolean keepWorkingEnv;

	@JsonProperty("created_at")
	private OffsetDateTime createdAt;

	@JsonProperty("updated_at")
	private OffsetDateTime updatedAt;

	private Boolean main;

	@JsonProperty("main_definition_id")
	private Long mainDefinitionId;

	@JsonProperty("description_url")
	private String descriptionUrl;

	private String description;

	public Map<String, Object> getScope(ObjectMapper objectMapper)
			throws JsonMappingException, JsonProcessingException {
		return objectMapper.readValue(settings, new TypeReference<Map<String, Object>>() {
		});
	}

	public Map<String, Object> getScope(ObjectMapper objectMapper, String subkey)
			throws JsonMappingException, JsonProcessingException {
		return (Map<String, Object>) objectMapper.readValue(settings, new TypeReference<Map<String, Object>>() {
		}).get(subkey);
	}

	/**
	 * It's need to clone id and userId too because the authentifcation is involved.
	 * 
	 * @return
	 */
	public DeployDefinition cloneSettingsOnly() {
		return DeployDefinition.builder().id(id).userId(userId).settings(settings).build();
	}

	public static DeployDefinition withOnlyIdField(Long id) {
		return DeployDefinition.builder().id(id).build();
	}

	public static class DeployRegion {
		public static String china = "china";
		public static String outside = "outside";
	}

}
