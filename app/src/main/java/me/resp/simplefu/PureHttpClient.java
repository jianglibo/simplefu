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
import java.util.stream.Stream;

public class PureHttpClient {
  public static String DEPLOYMENT_DOWNLOADS_FILE_PATH = "deployment_downloads.txt";

  public static HttpClient createHttpClient() {
    HttpClient client = HttpClient.newBuilder()
        .version(Version.HTTP_1_1)
        .followRedirects(Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(60))
        .build();
    return client;
  }

  public static String requestUploadUrl(String baseUri, String fileName, String shortTimePassword,
      int expireInMinutes, String savedAt) throws IOException, InterruptedException {
    String uri = String.format("%s/sapi/upload-url?fileName=%s&expireInMinutes=%d&savedAt=%s",
        baseUri, fileName, expireInMinutes, savedAt);

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

      int expireInMinutes, String savedAt) throws IOException, InterruptedException {
    return uploadToAzure(file,
        requestUploadUrl(baseUri, fileName, shortTimePassword, expireInMinutes, savedAt));
  }

  public static String uploadToAzure(Path file, String baseUri, String shortTimePassword,
      int expireInMinutes, String savedAt) throws IOException, InterruptedException {
    return uploadToAzure(file, file.getFileName().toString(), baseUri, shortTimePassword,
        expireInMinutes, savedAt);
  }

  public static String uploadToAzure(Path file, String sasUrl)
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

  public static Path downloadFromAzure(Path saveTo, String baseUri, Long assetId,
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
  public static List<Path> downloadDeploymentDownloadsFromAzure(Path workingDir, String baseUri,
      String shortTimePassword) throws IOException, InterruptedException {

    return Files.walk(workingDir)
        .filter(p -> p.getFileName().toString().equals(DEPLOYMENT_DOWNLOADS_FILE_PATH))
        .flatMap(p -> {
          try {
            return Files.readAllLines(p).stream().map(line -> {
              try {
                String[] ss = line.split(",");
                if (ss.length == 3) {
                  long assetId = Long.parseLong(ss[0].trim());
                  return downloadFromAzure(workingDir.resolve(ss[1].trim()), baseUri, assetId,
                      shortTimePassword);
                }
              } catch (IOException | InterruptedException | NumberFormatException e) {
                e.printStackTrace();
              }
              return null;
            }).filter(p1 -> p1 != null);
          } catch (IOException e) {
            e.printStackTrace();
            return Stream.empty();
          }
        }).collect(java.util.stream.Collectors.toList());
  }
}
