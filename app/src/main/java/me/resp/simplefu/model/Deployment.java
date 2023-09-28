package me.resp.simplefu.model;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class Deployment {

	public static final String ASSET_HOLDER_DOWNLOADS = "deployments/downloads";

	private Long id;
	private String output;

	private Long did;
	private DeployStatus status;

	/**
	 * What's the meaning of the prev?
	 * use to collect the information for the chained tasks.
	 */
	private Long prev;

	private Long next;

	private String extra;

	@JsonProperty("entrypoint_params")
	private String entrypointParams;

	@JsonProperty("created_at")
	private OffsetDateTime createdAt;
	@JsonProperty("updated_at")
	private OffsetDateTime updatedAt;

	public static enum DeployStatus {
		started, failed, succeeded
	}

}
