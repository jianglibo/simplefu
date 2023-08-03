package me.resp.simplefu;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Paths;
import java.time.Duration;

public class Uploader {

	// upload_to_azure() {
	// local file_name=${2:-$1}
	// local x_tobe_client_secret=${3:-$X_TOBE_CLIENT_SECRET}
	// local expire_in_minutes=${4:-10}
	// local server_root=${5:-$SERVER_ROOT_URI}
	// local upload_url=$(curl -H "X-TOBE-CLIENT-SECRET: ${x_tobe_client_secret}"
	// "${server_root}/sapi/upload-url?fileName=${file_name}&savedAt=azureblob&expireInMinutes=${expire_in_minutes}&deploymentId=${THIS_DEPLOYMENT_ID}")
	// curl -v -X PUT -H "x-ms-blob-type: BlockBlob" --data-binary "${1}"
	// "${upload_url}"
	// local blob_id=$(echo "$upload_url" | grep -oE "[^/]+/\S+" | cut -d '?' -f1 |
	// awk -F '/' '{print $NF}')
	// inform the server that upload is finished.
	// curl -H "X-TOBE-CLIENT-SECRET: ${x_tobe_client_secret}"
	// "${server_root}/sapi/upload-url?blobId=${blob_id}"
	// }

	// https://ssdockermapping.blob.core.windows.net/assets/cfab10e9-583f-4c96-b920-a84ab9510640?sv=2022-11-02&st=2023-07-24T06%3A41%3A26Z&se=2023-07-24T07%3A41%3A26Z&sr=b&sp=r&sig=SbGxPmt05AwD4DsgZKe%2BEoxRvWCTCNVnqzTZPiDE%2B6A%3D
	public void uploadToAzure(String secret, String serverRootUri, String filepath, String filename)
			throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("https://foo.com/"))
				.timeout(Duration.ofMinutes(2))
				.header("Content-Type", "application/json")
				.POST(BodyPublishers.ofFile(Paths.get("file.json")))
				.build();

		HttpClient client = HttpClient.newBuilder()
				.version(Version.HTTP_1_1)
				.followRedirects(Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(20))
				.proxy(ProxySelector.of(new InetSocketAddress("proxy.example.com", 80)))
				.authenticator(Authenticator.getDefault())
				.build();
		HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
		System.out.println(response.statusCode());
		System.out.println(response.body());
	}
}
