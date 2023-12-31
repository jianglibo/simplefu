package me.resp.simplefu;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class PureHttpClient {
  public static String DEPLOYMENT_DOWNLOADS_FILE_PATH = "deployment_downloads.txt";

  private static HttpClient createHttpClient() {
    HttpClient client = HttpClient.newBuilder()
        .version(Version.HTTP_1_1)
        .followRedirects(Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(60))
        .build();
    return client;
  }

  public static String deploymentHistories(Long deployDefinitionId, int count, String entrypointParams, String baseUri,
      String shortTimePassword)
      throws IOException, InterruptedException {
    String uri = String.format("%s/sapi/rest/deployment?deploy_definition_id=%s&latest=%s&entrypoint_params=%s",
        baseUri,
        deployDefinitionId,
        count,
        entrypointParams);
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(uri))
        .timeout(Duration.ofMinutes(2))
        .header("Content-Type", "application/json")
        .header(CustomHttpHeaders.X_TOBE_CLIENT_SECRET, shortTimePassword)
        .GET()
        .build();

    return createHttpClient().send(request, BodyHandlers.ofString())
        .body();
  }

  public static String deploymentAssets(Long deploymentId, String baseUri, String shortTimePassword)
      throws IOException, InterruptedException {
    String uri = String.format("%s/sapi/rest/assets?deployment_id=%s", baseUri, deploymentId);
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(uri))
        .timeout(Duration.ofMinutes(2))
        .header("Content-Type", "application/json")
        .header(CustomHttpHeaders.X_TOBE_CLIENT_SECRET, shortTimePassword)
        .GET()
        .build();

    return createHttpClient().send(request, BodyHandlers.ofString())
        .body();
  }

  private static String requestUploadUrl(String baseUri, String fileName, String shortTimePassword,
      int expireInMinutes, String savedAt, Long deploymentId) throws IOException, InterruptedException {
    String uri = String.format("%s/sapi/upload-url?fileName=%s&expireInMinutes=%d&savedAt=%s&deploymentId=%s",
        baseUri, fileName, expireInMinutes, savedAt, deploymentId);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(uri))
        .timeout(Duration.ofMinutes(2))
        .header("Content-Type", "application/json")
        .header(CustomHttpHeaders.X_TOBE_CLIENT_SECRET, shortTimePassword)
        .GET()
        .build();

    return createHttpClient().send(request, BodyHandlers.ofString())
        .body();
  }

  private static String confirmUploadUrl(String baseUri, String blobId, String shortTimePassword)
      throws IOException, InterruptedException {
    String uri = String.format("%s/sapi/upload-url?blobId=%s", baseUri, blobId);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(uri))
        .timeout(Duration.ofMinutes(2))
        .header("Content-Type", "application/json")
        .header(CustomHttpHeaders.X_TOBE_CLIENT_SECRET, shortTimePassword)
        .GET()
        .build();

    return createHttpClient().send(request, BodyHandlers.ofString())
        .body();
  }

  public static String uploadToAzure(Path file, String fileName, String baseUri, String shortTimePassword,
      String savedAt, Long deploymentId) throws IOException, InterruptedException {
    // https://ssdockermapping.blob.core.windows.net/assets/cfab10e9-583f-4c96-b920-a84ab9510640?sv=2022-11-02&st=2023-07-24T06%3A41%3A26Z&se=2023-07-24T07%3A41%3A26Z&sr=b&sp=r&sig=SbGxPmt05AwD4DsgZKe%2BEoxRvWCTCNVnqzTZPiDE%2B6A%3D
    String sasUrl = requestUploadUrl(baseUri, fileName, shortTimePassword, 5, savedAt, deploymentId);
    String result = uploadToAzure(file, sasUrl);
    Pattern pattern = Pattern.compile("([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})");
    Matcher matcher = pattern.matcher(sasUrl);
    if (matcher.find()) {
      String uuid = matcher.group(1);
      return confirmUploadUrl(baseUri, uuid, shortTimePassword);
    }
    return result;
  }

  public static String uploadToAzure(Path file, String baseUri, String shortTimePassword,
      String savedAt, Long deploymentId) throws IOException, InterruptedException {
    return uploadToAzure(file, file.getFileName().toString(), baseUri, shortTimePassword, savedAt, deploymentId);
  }

  private static String uploadToAzure(Path file, String sasUrl)
      throws IOException, InterruptedException {
    // curl -v -X PUT -H "x-ms-blob-type: BlockBlob" --data-binary "${1}"
    // "${upload_url}"
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(sasUrl))
        .timeout(Duration.ofMinutes(2))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("x-ms-blob-type", "BlockBlob")
        .PUT(BodyPublishers.ofFile(file))
        .build();
    return createHttpClient().send(request, BodyHandlers.ofString())
        .body();
  }

  public static Path downloadOneAssetFromAzure(Path saveTo, String baseUri, Long assetId,
      String shortTimePassword) throws IOException, InterruptedException {

    String uri = String.format("%s/sapi/asset-download/%s", baseUri, assetId);
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(uri))
        .timeout(Duration.ofMinutes(2))
        .header(CustomHttpHeaders.X_TOBE_CLIENT_SECRET, shortTimePassword)
        .GET()
        .build();

    return createHttpClient().send(request, BodyHandlers.ofFile(saveTo))
        .body();
  }

  /**
   * Find all the files named after deployment_downloads.txt in the workingDir
   * recursively. read and
   * parse the content of the file then download them all.
   * 
   * @param workingDir
   * @param baseUri
   * @param shortTimePassword
   * @return
   * @throws IOException
   * @throws InterruptedException
   */
  public static List<Path> downloadDependenciesDownloadsFromAzure(Path workingDir, String baseUri,
      String shortTimePassword) throws IOException, InterruptedException {
    Path workingDir1 = workingDir == null ? Path.of(".") : workingDir;
    return Files.walk(workingDir1)
        .filter(p -> p.getFileName().toString().equals(DEPLOYMENT_DOWNLOADS_FILE_PATH))
        .flatMap(p -> {
          return downloadDeploymentDownloadsFromAzureOneFile(workingDir1, p, baseUri, shortTimePassword);
        }).collect(java.util.stream.Collectors.toList());
  }

  public static Stream<Path> downloadDeploymentDownloadsFromAzureOneFile(Path workingDir, Path deploymentDownloadsFile,
      String baseUri,
      String shortTimePassword) {
    Path workingDir1 = workingDir == null ? Path.of(".") : workingDir;
    try {
      return Files.readAllLines(deploymentDownloadsFile).stream().map(line -> {
        try {
          String[] ss = line.split(",");
          if (ss.length == 3) {
            long assetId = Long.parseLong(ss[0].trim());
            return downloadOneAssetFromAzure(workingDir1.resolve(ss[1].trim()), baseUri, assetId,
                shortTimePassword);
          }
        } catch (IOException | InterruptedException | NumberFormatException e) {
        }
        return null;
      }).filter(p1 -> p1 != null);
    } catch (Exception e) {
      e.printStackTrace();
      return Stream.empty();
    }
  }
}
