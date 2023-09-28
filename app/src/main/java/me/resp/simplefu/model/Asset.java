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
public class Asset {

  private Long id;
  private String typee;

  private String name;

  @JsonProperty("file_size")
  private Long fileSize;
  private String sha256sum;
  @JsonProperty("created_at")
  private OffsetDateTime createdAt;

  @JsonProperty("updated_at")
  private OffsetDateTime updatedAt;

  @JsonProperty("saved_at")
  private SavedAt savedAt;

  private AssetStatus status;

  private Boolean shareable;

  @JsonProperty("text_content")
  private String textContent;

  @JsonProperty("description_url")
  private String descriptionUrl;

  @JsonProperty("code_language")
  private String codeLanguage;

  @JsonProperty("user_id")
  private Long userId;

  public Asset clone() {
    return Asset.builder()
        .createdAt(createdAt)
        .fileSize(fileSize)
        .name(name)
        .savedAt(savedAt)
        .sha256sum(sha256sum)
        .shareable(false)
        .status(status)
        .textContent(textContent)
        .typee(typee)
        .codeLanguage(codeLanguage)
        .descriptionUrl(descriptionUrl)
        .build();

  }

  public String getFileNameOnly() {
    String[] parts = name.split("[\\\\/]");
    return parts[parts.length - 1];
  }

  /**
   * When want to combine remote and local files in to a flux or stream, we mark the local file as
   * type local.
   */
  public static enum SavedAt {
    azureblob, inline, local
  }

  public static enum AssetStatus {
    processing, ready
  }
}
