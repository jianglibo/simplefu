///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.6.3
//DEPS io.projectreactor:reactor-core:3.5.10
//DEPS com.fasterxml.jackson.core:jackson-databind:2.15.2
//DEPS com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2
//DEPS com.fasterxml.jackson.core:jackson-annotations:2.15.2
//DEPS org.slf4j:slf4j-api:2.0.7
//DEPS org.slf4j:slf4j-simple:2.0.9

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
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
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParseResult;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Command(name = "DeployUtil", mixinStandardHelpOptions = true, version = "DeployUtil 0.1", description = "DeployUtil made with jbang")
class DeployUtil implements Callable<Integer> {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeployUtil.class);

	@Override
	public Integer call() throws Exception {
		// System.out.println("going to do " + action);
		// for test purpose.
		switch (action.toLowerCase()) {
			case "deploy":
				log.info("not implemented yet.");
				// return deploy().block();
			default:
				System.out.println("unknown action " + action);
				return 1;
		}
	}

	// ------------------- your code stop here ---------------------------------

	@Option(names = "--deployment-env-file", defaultValue = "deployment.env.properties", description = "the path of deployment.env.properties file.")
	Path deploymentEnvFile;

	@Parameters(index = "0", description = "The action to do", defaultValue = "deploy")
	private String action;

	@Option(names = "--ignore-missing-source", description = "ignore file not exists error.")
	boolean ignoreMissingSource;

	@Option(names = {
			"--error-tolerance" }, description = "the level of tolerance before aborting the task. default: ${DEFAULT-VALUE}, means ZERO tolerance.")
	Integer errorTolerance = 0;

	@Option(names = { "--proxy" }, description = "use proxy for http request")
	// @Option(names = { "--proxy" }, description = "use proxy for http request",
	// defaultValue = "http://127.0.0.1:7890")
	private String proxy;

	private static HttpClient httpClient;

	private static DeploymentEnv denv;
	private static ObjectMapper mapper;

	private static AppWithEnv appWithEnv;

	public static void main(String... args) {
		try {
			DeployUtil app = new DeployUtil();
			int exitCode = new CommandLine(app).setExecutionStrategy(app::executionStrategy).execute(args);
			System.exit(exitCode);
		} finally {
			ZipTask.clearCache();
		}
	}

	@Command(mixinStandardHelpOptions = true)
	void copydir(@Parameters(index = "0", paramLabel = "<src>", description = "source dir") String src,
			@Parameters(index = "1", paramLabel = "<dst>", description = "destination dir") String dst) {
		copyDir(src, dst);
	}

	@Command(mixinStandardHelpOptions = true)
	Integer copy(
			@Parameters(index = "0", paramLabel = "<filelist>", description = "the file contains the list.") String filelist,
			@Option(names = "--backup") boolean backup, @Option(names = "--override") boolean override)
			throws IOException {
		if (backup) {
			InputFileParser inputFileParser = InputFileParser.copyParser(filelist);
			VersionTask versionTask = new VersionTask(inputFileParser.parse());
			versionTask.startBackup();
		}
		InputFileParser inputFileParser = InputFileParser.copyParser(filelist);
		CopyTask task = new CopyTask(inputFileParser.parse(), override);
		task.start();
		return 0;
	}

	/**
	 * 
	 * @param deployment_downloads.txt
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Command(name = "download", mixinStandardHelpOptions = true, description = "download all files listed in the 'deployment_downloads.txt'")
	public Integer download(
			@Parameters(index = "0", arity = "0..1", description = "files list the files to process.") Path inputFile)
			throws IOException, InterruptedException {
		if (inputFile == null) {
			PureHttpClient.downloadDependenciesDownloadsFromAzure(null, denv.getServerRootUri(),
					denv.getShortTimePassword());
		} else {
			PureHttpClient.downloadDeploymentDownloadsFromAzureOneFile(null, inputFile,
					denv.getServerRootUri(),
					denv.getShortTimePassword()).collect(Collectors.toList());
		}
		return 0;
	}

	/**
	 * No default value. It's not a good idea to have a default value for this.
	 * 
	 * @throws InterruptedException
	 */
	@Command(name = "backup", mixinStandardHelpOptions = true, description = "backup files")
	public Integer backup(@Option(names = {
			"--backup-to" }, required = true, paramLabel = "<filename.zip>", description = "the zip file to store the backup.") String backupTo,
			@Option(names = {
					"--upload-to-azure" }, description = "upload the zip file to azure and associate with this deployment.") boolean uploadToAzure,
			@Parameters(index = "0", arity = "1..*", paramLabel = "<filelistfiles>", description = "files which list the files to process.") List<String> inputFiles)
			throws IOException, InterruptedException {
		inputFiles = inputFiles == null ? new ArrayList<>() : inputFiles;
		if (inputFiles.isEmpty()) {
			System.out.println("input files don't exist, nothing to do");
			return 0;
		}

		BackupRestoreTask backupRestoreTask = new BackupRestoreTask(
				inputFiles.stream()
						.map(InputFileParser::restoreParser)
						.flatMap(ip -> {
							return Util.exceptionHandler(() -> ip.parse(), Stream.empty(), 1, "parse filelistfile");
						}),
				backupTo == null ? null : Path.of(backupTo));
		Path backuped = backupRestoreTask.backup();
		if (uploadToAzure) {
			appWithEnv.uploadToAzure(backuped);
		}
		return 0;
	}

	/**
	 * No default value. It's not a good idea to have a default value for this.
	 * 
	 * @param backupFile
	 * @param inputFiles
	 * @return
	 * @throws IOException
	 */
	@Command(name = "restore", mixinStandardHelpOptions = true, description = "restore files changed by this app's copy command.")
	public Integer restore(@Option(names = {
			"--restore-from" }, required = true, paramLabel = "<filename.zip>", description = "the zip file to restore from.") String backupFile,
			@Parameters(index = "0", arity = "1..*", paramLabel = "<filelistfiles>", description = "files list the files to process.") List<String> inputFiles)
			throws IOException {
		inputFiles = inputFiles == null ? new ArrayList<>() : inputFiles;
		if (inputFiles.isEmpty()) {
			System.out.println("input files don't exist, nothing to do");
			return 0;
		}
		BackupRestoreTask backupRestoreTask = new BackupRestoreTask(
				inputFiles.stream()
						.map(InputFileParser::restoreParser)
						.flatMap(ip -> {
							return Util.exceptionHandler(() -> ip.parse(), Stream.empty(), 1, "parse filelistfile");
						}),
				Path.of(backupFile));
		backupRestoreTask.restore();
		return 0;
	}

	@Command(name = "unzip", mixinStandardHelpOptions = true, description = "do unzip")
	public Integer unzip(@Option(names = {
			"--to-dir" }, description = "extracted files go this directory.") Path toDir,
			@Parameters(index = "0", arity = "1", description = "files list the files to process.") Path zipFile)
			throws IOException {
		Util.unzipTo(zipFile, toDir);
		return 0;
	}

	@Command(name = "rollback", mixinStandardHelpOptions = true, description = "rollback the destination files. a.1.txt -> a.txt")
	public Integer rollback(
			@Parameters(index = "0", arity = "1..*", paramLabel = "<filelistfiles>", description = "files list the files to process.") List<String> inputFiles)
			throws IOException {
		inputFiles = inputFiles == null ? new ArrayList<>() : inputFiles;
		if (inputFiles.isEmpty()) {
			System.out.println("input files don't exist, nothing to do");
			return 0;
		}
		VersionTask versionTask = new VersionTask(inputFiles.stream()
				.map(InputFileParser::restoreParser)
				.flatMap(ip -> {
					return Util.exceptionHandler(() -> ip.parse(), Stream.empty(), 1, "parse filelistfile");
				}));
		versionTask.startRollback();
		return 0;
	}

	private int executionStrategy(ParseResult parseResult) {
		Util.errorTolerance = errorTolerance;
		Util.setIgnoreMissingSource(ignoreMissingSource);
		log.info("main class's call being called.");
		buildHttpClient();
		try {
			if (Files.exists(deploymentEnvFile)) {
				denv = Util.loadDeploymentEnv(deploymentEnvFile);
			} else {
				log.warn("deployment env file doesn't exist. {}", deploymentEnvFile);
			}
		} catch (IOException e) {
			log.error("load deployment evn failed.", e);
		}
		mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		appWithEnv = new AppWithEnv(denv, mapper);
		// System.out.printf("errorTolerance: %d, ignoreMissingSource: %s%n",
		// errorTolerance, ignoreMissingSource);
		return new CommandLine.RunLast().execute(parseResult); // default execution strategy
	}

	/**
	 * src and dest are relative path to the current directory, copy all the files
	 * and directories under the src to the dest keep the same tree structure.
	 * 
	 * @param src
	 * @param dest
	 */
	private void copyDir(String src, String dest) {
		Path srcPath = Path.of(src);
		Path destPath = Path.of(dest);
		try {
			Files.walk(srcPath).forEach(p -> {
				Path destP = destPath.resolve(srcPath.relativize(p));
				try {
					if (Files.isDirectory(p)) {
						Files.createDirectories(destP);
					} else {
						Files.copy(p, destP);
					}
				} catch (Exception e) {
					throw new RuntimeException("copy file failed", e);
				}
			});
		} catch (Exception e) {
			throw new RuntimeException("copy dir failed", e);
		}
	}

	// private Mono<DeployDefinition> getDeployDefinition() {
	// return Mono.fromCallable(() -> {
	// return mapper.readValue((deployDir.resolve("definition.json")).toFile(),
	// DeployDefinition.class);
	// });
	// }

	// private Mono<Integer> deploy() {
	// return getDeployDefinition().flatMap(dp -> {
	// System.out.println(dp);
	// return Mono.just(0);
	// });
	// // return reqGet("https://google.com").map(r -> {
	// // System.out.println(r.body());
	// // return 0;
	// // });
	// }

	private Mono<HttpResponse<String>> reqGet(String uri) {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(uri))
				.timeout(Duration.ofMinutes(2))
				// .header("Content-Type", "application/json")
				.GET()
				// .POST(BodyPublishers.ofFile(Paths.get("file.json")))
				.build();
		return Mono.fromCallable(() -> {
			return httpClient.send(request, BodyHandlers.ofString());
		}).subscribeOn(Schedulers.boundedElastic());
	}

	private void buildHttpClient() {
		HttpClient.Builder builder = HttpClient.newBuilder();
		builder
				.version(Version.HTTP_1_1)
				.followRedirects(Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(20));
		if (proxy != null && proxy.length() > 1) {
			String[] ss = proxy.split("//", 2);
			String host;
			String port;
			if (ss.length > 1) {
				String[] ss2 = ss[1].split(":", 2);
				host = ss2[0];
				port = ss2[1];
			} else {
				String[] ss2 = ss[0].split(":", 2);
				host = ss2[0];
				port = ss2[1];
			}
			builder.proxy(ProxySelector.of(new InetSocketAddress(host, Integer.parseInt(port))));
		}
		// .authenticator(Authenticator.getDefault())
		httpClient = builder.build();
	}

	// ___insert_start___

public static class FatalException extends AbstractRtException {

	public FatalException(String message) {
		super(message, SubcommandNames.__placeholder, ErrorReason.OTHER, Integer.MAX_VALUE);
	}

}

// Generated by delombok at Wed Sep 27 15:23:54 CST 2023

public static abstract class AbstractRtException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	private Integer errorLevel;
	private final SubcommandNames subcommandName;
	private final ErrorReason reason;

	public AbstractRtException(String message, SubcommandNames subcommandName, ErrorReason reason) {
		super(message);
		this.subcommandName = subcommandName;
		this.reason = reason;
	}

	public AbstractRtException(String message, SubcommandNames subcommandName, ErrorReason reason, Integer errorLevel) {
		super(message);
		this.errorLevel = errorLevel;
		this.subcommandName = subcommandName;
		this.reason = reason;
	}

	public Integer getErrorLevel() {
		if (errorLevel != null) {
			return errorLevel;
		} else {
			return calculateLevel();
		}
	}

	private Integer calculateLevel() {
		switch (subcommandName) {
		case copy: 
			return calculateCopy();
		case backup: 
			return calulateBackup();
		case restore: 
			return calulateRestore();
		default: 
			throw new FatalException("unknown subcommand " + subcommandName);
		}
	}

	private Integer calulateRestore() {
		return 1;
	}

	private Integer calulateBackup() {
		return 1;
	}

	private Integer calculateCopy() {
		return 1;
	}


	public static enum ErrorReason {
		SOURCE_MISSING, COPY_ITEM_FROM_IS_DIRECTORY, OTHER;
	}


	public static enum SubcommandNames {
		copy, backup, restore, __placeholder;
	}

	@java.lang.SuppressWarnings("all")
	public SubcommandNames getSubcommandName() {
		return this.subcommandName;
	}

	@java.lang.SuppressWarnings("all")
	public ErrorReason getReason() {
		return this.reason;
	}
}


public static class CopySourceMissingException extends AbstractRtException {

	public CopySourceMissingException(String message, SubcommandNames subcommandName, Integer errorLevel) {
		super(message, subcommandName, ErrorReason.SOURCE_MISSING, errorLevel);
	}
	
}



public static class PureHttpClient {
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




public static class LinuxCmds {

	public static ProcessResult execOneLine(String cmd) throws IOException, InterruptedException {
		return exec(Path.of("."), "bash", "-c", cmd);
	}

	public static ProcessResult exec(String... cmds) throws IOException, InterruptedException {
		return exec(Path.of("."), cmds);
	}

	public static ProcessResult exec(Path workingDir, String... cmds) throws IOException, InterruptedException {
		workingDir = workingDir == null ? Path.of(".") : workingDir;
		ProcessBuilder pb = new ProcessBuilder(cmds);
		Map<String, String> env = pb.environment();
		pb.directory(workingDir.toFile());
		pb.redirectErrorStream(true);
		Process p = pb.start();
		int exitCode = p.waitFor();
		ProcessResult processResult = new ProcessResult();
		processResult.setExitCode(exitCode);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
			List<String> lines = new ArrayList<>();
			String line;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
			processResult.setLines(lines);
		}
		return processResult;
	}
}

// Generated by delombok at Wed Sep 27 15:23:54 CST 2023


public static class Util {
    public static Integer errorTolerance = 0;
    public static final String DEPLOYMENT_ENV_PROPERTIES = "deployment.env.properties";
    private static boolean ignoreMissingSource = false;

    public static void setIgnoreMissingSource(boolean ignoreMissingSource) {
        Util.ignoreMissingSource = ignoreMissingSource;
    }

    public static FileSystem createZipFileSystem(Path zipFile, boolean create) throws IOException {
        // convert the filename to a URI
        final URI uri = URI.create("jar:file:" + zipFile.toUri().getPath());
        final Map<String, String> env = new HashMap<>();
        if (create) {
            env.put("create", create ? "true" : "false");
        }
        try {
            // try to get the file system
            return FileSystems.getFileSystem(uri);
        } catch (FileSystemNotFoundException e) {
            // file system does not exist, create a new one
            return FileSystems.newFileSystem(uri, env);
        }
    }

    public static Path relativeFromRoot(Path maybeAbsolutePath) {
        if (maybeAbsolutePath.isAbsolute()) {
            return maybeAbsolutePath.getRoot().relativize(maybeAbsolutePath);
        }
        return maybeAbsolutePath;
    }

    public static String printFromAndTo(String prefix, Path copyFrom, Path copyTo, String... extraMessages) {
        String message = String.format("%s %s(%s) --> %s(%s)", prefix, copyFrom, copyFrom.getFileSystem().provider().getScheme(), copyTo, copyTo.getFileSystem().provider().getScheme());
        System.out.println(message);
        for (String extraMessage : extraMessages) {
            System.out.println(extraMessage);
        }
        return message;
    }

    public static String printCopyFromAndTo(Path copyFrom, Path copyTo, String... extraMessages) {
        return printFromAndTo("Copying", copyFrom, copyTo, extraMessages);
    }

    public static String printSkipFromAndTo(Path copyFrom, Path copyTo, String... extraMessages) {
        return printFromAndTo("Skipping", copyFrom, copyTo, extraMessages);
    }

    public static Path copyFile(Path copyFrom, Path copyTo) throws IOException {
        if (Files.isDirectory(copyFrom)) {
            throw new IOException(printCopyFromAndTo(copyFrom, copyTo, "source file is a directory."));
        }
        if (!Files.exists(copyFrom)) {
            if (ignoreMissingSource) {
                System.out.println("source file does not exist, ignored.");
                return null;
            } else {
                throw new IOException(printCopyFromAndTo(copyFrom, copyTo, "source file does not exist."));
            }
        }
        printCopyFromAndTo(copyFrom, copyTo);
        if (copyTo.getParent() != null && !Files.exists(copyTo.getParent())) {
            Files.createDirectories(copyTo.getParent());
        }
        return Files.copy(copyFrom, copyTo, StandardCopyOption.REPLACE_EXISTING);
    }


    public interface MaybeThrowSomething<T> {
        T call() throws Throwable;
    }


    public interface MaybeThrowSomethingNoReturn {
        void call() throws Throwable;
    }

    public static <T> T exceptionHandler(MaybeThrowSomething<T> maybeThrowSomething, T fallback, int errorLevel, String message) {
        try {
            return maybeThrowSomething.call();
        } catch (Throwable e) {
            if (errorLevel > errorTolerance) {
                throw new RuntimeException(message, e);
            } else {
                System.out.println("ignored the error: " + e.getMessage());
            }
            return fallback;
        }
    }

    public static void exceptionHandler(MaybeThrowSomethingNoReturn maybeThrowSomethingNoReturn, int errorLevel, String message) {
        try {
            maybeThrowSomethingNoReturn.call();
        } catch (Throwable e) {
            if (errorLevel > errorTolerance) {
                throw new RuntimeException(message, e);
            } else {
                System.out.println("ignored the error: " + e.getMessage());
            }
        }
    }

    /**
     * CopyFrom has priority to determine the copyTo path. If copyFrom is a file,
     * copyTo is a file. If copyFrom is a directory, copyTo is a directory.
     * 
     * @param copyFromPath
     * @return
     * @throws IOException
     */
    public static Stream<CopyItem> walkCopyFrom(Path copyFromPath, String copyTo, Path zipFileIfFromZip, boolean exactly) {
        if (!Files.exists(copyFromPath) && !ignoreMissingSource) {
            throw new RuntimeException("File not found: " + copyFromPath.toString());
        }
        if (Files.isDirectory(copyFromPath)) {
            return Util.exceptionHandler(() -> 
            // if (relativePath.isBlank()) { // skip himself.
            // return null;
            // }
            // return CopyItem.builder()
            // .copyFrom(p.toString())
            // .copyTo(copyTo + "/" + relativePath)
            // .fromZipFile(zipFileIfFromZip)
            // .fromExactly(true)
            // .build();
            Files.walk(copyFromPath).map(p -> {
                if (Files.isDirectory(p)) {
                    return null;
                } else {
                    String relativePath = copyFromPath.relativize(p).toString();
                    return CopyItem.builder().copyFrom(p.toString()).copyTo(copyTo + "/" + relativePath).fromZipFile(zipFileIfFromZip).fromExactly(exactly).build();
                }
            }).filter(Objects::nonNull), Stream.empty(), 1, "Failed to walk directory: " + copyFromPath.toString());
        } else {
            return Stream.of(CopyItem.builder().copyFrom(copyFromPath.toString()).copyTo(copyTo).fromZipFile(zipFileIfFromZip).fromExactly(exactly).build());
        }
    }

    public static void deleteDirectory(String dirToDelete) {
        deleteDirectory(Paths.get(dirToDelete));
    }

    public static void deleteDirectory(Path dirToDelete) {
        try (Stream<Path> dirStream = Files.walk(dirToDelete)) {
            dirStream.map(Path::toFile).sorted(Comparator.reverseOrder()).forEach(File::delete);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Stream<CopyItem> walkBackupItem(Path toBackup) {
        if (!Files.exists(toBackup) && !ignoreMissingSource) {
            throw new RuntimeException("File not found: " + toBackup.toString());
        }
        if (Files.isDirectory(toBackup)) {
            return Util.exceptionHandler(() ->  // skip himself.
            Files.walk(toBackup).map(p -> {
                String relativePath = toBackup.relativize(p).toString();
                if (relativePath.isBlank()) {
                    return null;
                }
                return CopyItem.builder().copyFrom("doesn\'t matter.").copyTo(p.toAbsolutePath().normalize().toString()).fromZipFile(null).fromExactly(true).build();
            }).filter(Objects::nonNull), Stream.empty(), 1, "Failed to walk directory: " + toBackup.toString());
        } else {
            return Stream.of(CopyItem.builder().copyFrom("doesn\'t matter.").copyTo(toBackup.toAbsolutePath().normalize().toString()).fromZipFile(null).fromExactly(true).build());
        }
    }

    public static <T> Stream<T> getStreamFromIterator(Iterator<T> iterator) {
        // Convert the iterator to Spliterator
        Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator, 0);
        // Get a Sequential Stream from spliterator
        return StreamSupport.stream(spliterator, false);
    }

    public static List<Path> unzipTo(Path zipFile, Path toDir) throws IOException {
        FileSystem fs = createZipFileSystem(zipFile, false);
        toDir = toDir == null ? zipFile.getParent() : toDir;
        Path toDir1 = toDir;
        List<Path> paths = getStreamFromIterator(fs.getRootDirectories().iterator()).flatMap(root -> {
            try {
                return Files.walk(root).map(p -> {
                    try {
                        if (Files.isDirectory(p)) {
                            return null;
                        }
                        Path relativePath = root.relativize(p);
                        Path copyTo = toDir1.resolve(relativePath.toString());
                        return copyFile(p, copyTo);
                    } catch (IOException e) {
                        return null;
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                return Stream.empty();
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
        fs.close();
        return paths;
    }

    public static List<Path> zipAtSameDirectory(Path createdZip, Path directoryToZip) throws IOException {
        FileSystem fs = createZipFileSystem(createdZip, true);
        Path root = fs.getPath("/");
        List<Path> paths = 
        // Files.copy(p, copyTo);
        Files.walk(directoryToZip).map(p -> {
            try {
                if (Files.isDirectory(p)) {
                    return null;
                }
                Path relativePath = directoryToZip.relativize(p);
                Path copyTo = root.resolve(relativePath.toString());
                return copyFile(p, copyTo);
            } catch (IOException e) {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
        fs.close();
        return paths;
    }

    private static Long parseLongId(String v) {
        if (v == null || v.isBlank()) {
            return null;
        } else {
            return Long.parseLong(v);
        }
    }

    /**
     * deployment.env.properties content:
     * shortTimePassword=
     * serverRootUri=
     * thisDeploymentId=
     * thisDeployDefinitionId=
     *
     * @param file
     * @return
     * @throws IOException
     */
    public static DeploymentEnv loadDeploymentEnv(Path file) throws IOException {
        file = file == null ? Path.of(DEPLOYMENT_ENV_PROPERTIES) : file;
        if (!Files.exists(file)) {
            return null;
        }
        return loadDeploymentEnv(Files.readString(file));
    }

    public static DeploymentEnv loadDeploymentEnv(String content) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(content));
        DeploymentEnv deploymentEnv = new DeploymentEnv();
        deploymentEnv.setServerRootUri(properties.getProperty("serverRootUri"));
        deploymentEnv.setThisDeployDefinitionSecret(properties.getProperty("thisDeployDefinitionSecret"));
        deploymentEnv.setMyUserId(parseLongId(properties.getProperty("myUserId")));
        deploymentEnv.setThisDeployDefinitionId(parseLongId(properties.getProperty("thisDeployDefinitionId")));
        deploymentEnv.setThisTemplateDeployHistory(parseLongId(properties.getProperty("thisTemplateDeployHistory")));
        deploymentEnv.setThisTemplateId(parseLongId(properties.getProperty("thisTemplateId")));
        deploymentEnv.setShortTimePassword(properties.getProperty("shortTimePassword"));
        deploymentEnv.setThisDeploymentId(parseLongId(properties.getProperty("thisDeploymentId")));
        return deploymentEnv;
    }

    @java.lang.SuppressWarnings("all")
    public static boolean isIgnoreMissingSource() {
        return Util.ignoreMissingSource;
    }
}

// Generated by delombok at Wed Sep 27 15:23:54 CST 2023


public static class ZipTask implements Closeable {
	private final ZipNameType zipNameType;
	private final Path zipfile;
	private FileSystem zipFileSystem;

	public ZipTask(Path zipfile) {
		this.zipfile = zipfile;
		this.zipNameType = ZipNameType.ABSOLUTE;
	}

	public ZipTask(Path zipfile, ZipNameType zipNameType) {
		this.zipNameType = zipNameType;
		this.zipfile = zipfile;
	}

	private void start(boolean readonly) throws IOException {
		this.zipFileSystem = Util.createZipFileSystem(zipfile, !readonly);
	}

	private Path getZipPath(Path copyFrom, String copyTo) {
		if (copyTo == null) {
			switch (zipNameType) {
			case ABSOLUTE: 
				return zipFileSystem.getPath(copyFrom.toAbsolutePath().normalize().toString());
			case FLATTEN: 
				return zipFileSystem.getPath(copyFrom.getFileName().toString());
			default: 
				throw new IllegalArgumentException("Unknown ZipNameType: " + zipNameType);
			}
		} else {
			return zipFileSystem.getPath(copyTo);
		}
	}

	public void push(Path copyFrom, String copyTo) throws IOException {
		Path to = getZipPath(copyFrom, copyTo);
		Path parent = to.getParent();
		if (parent != null && !Files.exists(parent)) {
			Files.createDirectories(parent);
		}
		Util.copyFile(copyFrom, to);
	}

	public void push(Path copyFrom) throws IOException {
		push(copyFrom, null);
	}

	public Optional<Path> findExactly(String path) throws IOException {
		Path p = zipFileSystem.getPath(path);
		return Files.exists(p) ? Optional.of(p) : Optional.empty();
	}

	public Optional<Path> findEndsWith(String path) throws IOException {
		return Files.walk(zipFileSystem.getPath("/"), Integer.MAX_VALUE, FileVisitOption.FOLLOW_LINKS).filter(p -> p.toString().endsWith(path)).findFirst();
	}

	private void cp(Path zp, Path copyTo) {
		if (copyTo == null) {
			copyTo = Path.of(zp.toString());
		} else if (Files.isDirectory(copyTo)) {
			copyTo = copyTo.resolve(zp.getFileName().toString());
		}
		Path copyToFinal = copyTo;
		Util.exceptionHandler(() -> Util.copyFile(zp, copyToFinal), 1, "Failed to copy file: " + zp);
	}

	public void pullExactly(String pullFrom, Path copyTo) throws IOException {
		findExactly(pullFrom).ifPresent(zp -> cp(zp, copyTo));
	}

	public void pullEndsWith(String pullFrom, Path copyTo) throws IOException {
		findEndsWith(pullFrom).ifPresent(zp -> cp(zp, copyTo));
	}

	public Stream<Path> allEntryPath() throws IOException {
		return Files.walk(zipFileSystem.getPath("/"), Integer.MAX_VALUE);
	}

	@Override
	public void close() throws IOException {
		zipFileSystem.close();
	}

	private static Map<String, ZipTask> cache = new HashMap<>();

	// public static synchronized ZipTask get(Path zipFile, ZipNameType zipNameType,
	// boolean readonly) {
	// String key = String.format("%s-%s", zipFile, zipNameType);
	// if (!cache.containsKey(key)) {
	// ZipTask zipTask = new ZipTask(zipFile, zipNameType);
	// Util.exceptionHandler(() -> zipTask.start(readonly), 1, "Failed to open zip
	// file: " + zipFile);
	// cache.put(key, zipTask);
	// }
	// return cache.get(key);
	// }
	public static ZipTask get(Path zipFile, ZipNameType zipNameType, boolean readonly) {
		ZipTask zipTask = new ZipTask(zipFile, zipNameType);
		Util.exceptionHandler(() -> zipTask.start(readonly), 1, "Failed to open zip file: " + zipFile);
		return zipTask;
	}

	public static synchronized void clearCache() {
		for (ZipTask task : cache.values()) {
			try {
				task.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		cache.clear();
	}

	@java.lang.SuppressWarnings("all")
	public ZipNameType getZipNameType() {
		return this.zipNameType;
	}

	@java.lang.SuppressWarnings("all")
	public Path getZipfile() {
		return this.zipfile;
	}

	@java.lang.SuppressWarnings("all")
	public FileSystem getZipFileSystem() {
		return this.zipFileSystem;
	}
}


public static class CustomHttpHeaders {
	public static final String X_HASURA_ADMIN_SECRET = "X-Hasura-Admin-Secret";
	public static final String X_MS_CLIENT_PRINCIPAL_ID = "X-MS-CLIENT-PRINCIPAL-ID";
	public static final String X_MS_CLIENT_PRINCIPAL_IDP = "X-MS-CLIENT-PRINCIPAL-IDP";
	public static final String X_MS_CLIENT_PRINCIPAL = "X-MS-CLIENT-PRINCIPAL";
	public static final String X_TOBE_CLIENT_SECRET = "X-TOBE-CLIENT-SECRET";
	public static final String X_MS_CLIENT_PRINCIPAL_NAME = "X-MS-CLIENT-PRINCIPAL-NAME";
	public static final String X_API_KEY = "X-API-KEY";

}

// Generated by delombok at Wed Sep 27 15:23:54 CST 2023


@JsonIgnoreProperties(ignoreUnknown = true)
public static class Asset {
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
    return Asset.builder().createdAt(createdAt).fileSize(fileSize).name(name).savedAt(savedAt).sha256sum(sha256sum).shareable(false).status(status).textContent(textContent).typee(typee).codeLanguage(codeLanguage).descriptionUrl(descriptionUrl).build();
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
    azureblob, inline, local;
  }


  public static enum AssetStatus {
    processing, ready;
  }


  @java.lang.SuppressWarnings("all")
  public static class AssetBuilder {
    @java.lang.SuppressWarnings("all")
    private Long id;
    @java.lang.SuppressWarnings("all")
    private String typee;
    @java.lang.SuppressWarnings("all")
    private String name;
    @java.lang.SuppressWarnings("all")
    private Long fileSize;
    @java.lang.SuppressWarnings("all")
    private String sha256sum;
    @java.lang.SuppressWarnings("all")
    private OffsetDateTime createdAt;
    @java.lang.SuppressWarnings("all")
    private OffsetDateTime updatedAt;
    @java.lang.SuppressWarnings("all")
    private SavedAt savedAt;
    @java.lang.SuppressWarnings("all")
    private AssetStatus status;
    @java.lang.SuppressWarnings("all")
    private Boolean shareable;
    @java.lang.SuppressWarnings("all")
    private String textContent;
    @java.lang.SuppressWarnings("all")
    private String descriptionUrl;
    @java.lang.SuppressWarnings("all")
    private String codeLanguage;
    @java.lang.SuppressWarnings("all")
    private Long userId;

    @java.lang.SuppressWarnings("all")
    AssetBuilder() {
    }

    /**
     * @return {@code this}.
     */
    @java.lang.SuppressWarnings("all")
    public Asset.AssetBuilder id(final Long id) {
      this.id = id;
      return this;
    }

    /**
     * @return {@code this}.
     */
    @java.lang.SuppressWarnings("all")
    public Asset.AssetBuilder typee(final String typee) {
      this.typee = typee;
      return this;
    }

    /**
     * @return {@code this}.
     */
    @java.lang.SuppressWarnings("all")
    public Asset.AssetBuilder name(final String name) {
      this.name = name;
      return this;
    }

    /**
     * @return {@code this}.
     */
    @JsonProperty("file_size")
    @java.lang.SuppressWarnings("all")
    public Asset.AssetBuilder fileSize(final Long fileSize) {
      this.fileSize = fileSize;
      return this;
    }

    /**
     * @return {@code this}.
     */
    @java.lang.SuppressWarnings("all")
    public Asset.AssetBuilder sha256sum(final String sha256sum) {
      this.sha256sum = sha256sum;
      return this;
    }

    /**
     * @return {@code this}.
     */
    @JsonProperty("created_at")
    @java.lang.SuppressWarnings("all")
    public Asset.AssetBuilder createdAt(final OffsetDateTime createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    /**
     * @return {@code this}.
     */
    @JsonProperty("updated_at")
    @java.lang.SuppressWarnings("all")
    public Asset.AssetBuilder updatedAt(final OffsetDateTime updatedAt) {
      this.updatedAt = updatedAt;
      return this;
    }

    /**
     * @return {@code this}.
     */
    @JsonProperty("saved_at")
    @java.lang.SuppressWarnings("all")
    public Asset.AssetBuilder savedAt(final SavedAt savedAt) {
      this.savedAt = savedAt;
      return this;
    }

    /**
     * @return {@code this}.
     */
    @java.lang.SuppressWarnings("all")
    public Asset.AssetBuilder status(final AssetStatus status) {
      this.status = status;
      return this;
    }

    /**
     * @return {@code this}.
     */
    @java.lang.SuppressWarnings("all")
    public Asset.AssetBuilder shareable(final Boolean shareable) {
      this.shareable = shareable;
      return this;
    }

    /**
     * @return {@code this}.
     */
    @JsonProperty("text_content")
    @java.lang.SuppressWarnings("all")
    public Asset.AssetBuilder textContent(final String textContent) {
      this.textContent = textContent;
      return this;
    }

    /**
     * @return {@code this}.
     */
    @JsonProperty("description_url")
    @java.lang.SuppressWarnings("all")
    public Asset.AssetBuilder descriptionUrl(final String descriptionUrl) {
      this.descriptionUrl = descriptionUrl;
      return this;
    }

    /**
     * @return {@code this}.
     */
    @JsonProperty("code_language")
    @java.lang.SuppressWarnings("all")
    public Asset.AssetBuilder codeLanguage(final String codeLanguage) {
      this.codeLanguage = codeLanguage;
      return this;
    }

    /**
     * @return {@code this}.
     */
    @JsonProperty("user_id")
    @java.lang.SuppressWarnings("all")
    public Asset.AssetBuilder userId(final Long userId) {
      this.userId = userId;
      return this;
    }

    @java.lang.SuppressWarnings("all")
    public Asset build() {
      return new Asset(this.id, this.typee, this.name, this.fileSize, this.sha256sum, this.createdAt, this.updatedAt, this.savedAt, this.status, this.shareable, this.textContent, this.descriptionUrl, this.codeLanguage, this.userId);
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
      return "Asset.AssetBuilder(id=" + this.id + ", typee=" + this.typee + ", name=" + this.name + ", fileSize=" + this.fileSize + ", sha256sum=" + this.sha256sum + ", createdAt=" + this.createdAt + ", updatedAt=" + this.updatedAt + ", savedAt=" + this.savedAt + ", status=" + this.status + ", shareable=" + this.shareable + ", textContent=" + this.textContent + ", descriptionUrl=" + this.descriptionUrl + ", codeLanguage=" + this.codeLanguage + ", userId=" + this.userId + ")";
    }
  }

  @java.lang.SuppressWarnings("all")
  public static Asset.AssetBuilder builder() {
    return new Asset.AssetBuilder();
  }

  @java.lang.SuppressWarnings("all")
  public Long getId() {
    return this.id;
  }

  @java.lang.SuppressWarnings("all")
  public String getTypee() {
    return this.typee;
  }

  @java.lang.SuppressWarnings("all")
  public String getName() {
    return this.name;
  }

  @java.lang.SuppressWarnings("all")
  public Long getFileSize() {
    return this.fileSize;
  }

  @java.lang.SuppressWarnings("all")
  public String getSha256sum() {
    return this.sha256sum;
  }

  @java.lang.SuppressWarnings("all")
  public OffsetDateTime getCreatedAt() {
    return this.createdAt;
  }

  @java.lang.SuppressWarnings("all")
  public OffsetDateTime getUpdatedAt() {
    return this.updatedAt;
  }

  @java.lang.SuppressWarnings("all")
  public SavedAt getSavedAt() {
    return this.savedAt;
  }

  @java.lang.SuppressWarnings("all")
  public AssetStatus getStatus() {
    return this.status;
  }

  @java.lang.SuppressWarnings("all")
  public Boolean getShareable() {
    return this.shareable;
  }

  @java.lang.SuppressWarnings("all")
  public String getTextContent() {
    return this.textContent;
  }

  @java.lang.SuppressWarnings("all")
  public String getDescriptionUrl() {
    return this.descriptionUrl;
  }

  @java.lang.SuppressWarnings("all")
  public String getCodeLanguage() {
    return this.codeLanguage;
  }

  @java.lang.SuppressWarnings("all")
  public Long getUserId() {
    return this.userId;
  }

  @java.lang.SuppressWarnings("all")
  public void setId(final Long id) {
    this.id = id;
  }

  @java.lang.SuppressWarnings("all")
  public void setTypee(final String typee) {
    this.typee = typee;
  }

  @java.lang.SuppressWarnings("all")
  public void setName(final String name) {
    this.name = name;
  }

  @JsonProperty("file_size")
  @java.lang.SuppressWarnings("all")
  public void setFileSize(final Long fileSize) {
    this.fileSize = fileSize;
  }

  @java.lang.SuppressWarnings("all")
  public void setSha256sum(final String sha256sum) {
    this.sha256sum = sha256sum;
  }

  @JsonProperty("created_at")
  @java.lang.SuppressWarnings("all")
  public void setCreatedAt(final OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  @JsonProperty("updated_at")
  @java.lang.SuppressWarnings("all")
  public void setUpdatedAt(final OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  @JsonProperty("saved_at")
  @java.lang.SuppressWarnings("all")
  public void setSavedAt(final SavedAt savedAt) {
    this.savedAt = savedAt;
  }

  @java.lang.SuppressWarnings("all")
  public void setStatus(final AssetStatus status) {
    this.status = status;
  }

  @java.lang.SuppressWarnings("all")
  public void setShareable(final Boolean shareable) {
    this.shareable = shareable;
  }

  @JsonProperty("text_content")
  @java.lang.SuppressWarnings("all")
  public void setTextContent(final String textContent) {
    this.textContent = textContent;
  }

  @JsonProperty("description_url")
  @java.lang.SuppressWarnings("all")
  public void setDescriptionUrl(final String descriptionUrl) {
    this.descriptionUrl = descriptionUrl;
  }

  @JsonProperty("code_language")
  @java.lang.SuppressWarnings("all")
  public void setCodeLanguage(final String codeLanguage) {
    this.codeLanguage = codeLanguage;
  }

  @JsonProperty("user_id")
  @java.lang.SuppressWarnings("all")
  public void setUserId(final Long userId) {
    this.userId = userId;
  }

  @java.lang.Override
  @java.lang.SuppressWarnings("all")
  public boolean equals(final java.lang.Object o) {
    if (o == this) return true;
    if (!(o instanceof Asset)) return false;
    final Asset other = (Asset) o;
    if (!other.canEqual((java.lang.Object) this)) return false;
    final java.lang.Object this$id = this.getId();
    final java.lang.Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
    final java.lang.Object this$fileSize = this.getFileSize();
    final java.lang.Object other$fileSize = other.getFileSize();
    if (this$fileSize == null ? other$fileSize != null : !this$fileSize.equals(other$fileSize)) return false;
    final java.lang.Object this$shareable = this.getShareable();
    final java.lang.Object other$shareable = other.getShareable();
    if (this$shareable == null ? other$shareable != null : !this$shareable.equals(other$shareable)) return false;
    final java.lang.Object this$userId = this.getUserId();
    final java.lang.Object other$userId = other.getUserId();
    if (this$userId == null ? other$userId != null : !this$userId.equals(other$userId)) return false;
    final java.lang.Object this$typee = this.getTypee();
    final java.lang.Object other$typee = other.getTypee();
    if (this$typee == null ? other$typee != null : !this$typee.equals(other$typee)) return false;
    final java.lang.Object this$name = this.getName();
    final java.lang.Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
    final java.lang.Object this$sha256sum = this.getSha256sum();
    final java.lang.Object other$sha256sum = other.getSha256sum();
    if (this$sha256sum == null ? other$sha256sum != null : !this$sha256sum.equals(other$sha256sum)) return false;
    final java.lang.Object this$createdAt = this.getCreatedAt();
    final java.lang.Object other$createdAt = other.getCreatedAt();
    if (this$createdAt == null ? other$createdAt != null : !this$createdAt.equals(other$createdAt)) return false;
    final java.lang.Object this$updatedAt = this.getUpdatedAt();
    final java.lang.Object other$updatedAt = other.getUpdatedAt();
    if (this$updatedAt == null ? other$updatedAt != null : !this$updatedAt.equals(other$updatedAt)) return false;
    final java.lang.Object this$savedAt = this.getSavedAt();
    final java.lang.Object other$savedAt = other.getSavedAt();
    if (this$savedAt == null ? other$savedAt != null : !this$savedAt.equals(other$savedAt)) return false;
    final java.lang.Object this$status = this.getStatus();
    final java.lang.Object other$status = other.getStatus();
    if (this$status == null ? other$status != null : !this$status.equals(other$status)) return false;
    final java.lang.Object this$textContent = this.getTextContent();
    final java.lang.Object other$textContent = other.getTextContent();
    if (this$textContent == null ? other$textContent != null : !this$textContent.equals(other$textContent)) return false;
    final java.lang.Object this$descriptionUrl = this.getDescriptionUrl();
    final java.lang.Object other$descriptionUrl = other.getDescriptionUrl();
    if (this$descriptionUrl == null ? other$descriptionUrl != null : !this$descriptionUrl.equals(other$descriptionUrl)) return false;
    final java.lang.Object this$codeLanguage = this.getCodeLanguage();
    final java.lang.Object other$codeLanguage = other.getCodeLanguage();
    if (this$codeLanguage == null ? other$codeLanguage != null : !this$codeLanguage.equals(other$codeLanguage)) return false;
    return true;
  }

  @java.lang.SuppressWarnings("all")
  protected boolean canEqual(final java.lang.Object other) {
    return other instanceof Asset;
  }

  @java.lang.Override
  @java.lang.SuppressWarnings("all")
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final java.lang.Object $id = this.getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final java.lang.Object $fileSize = this.getFileSize();
    result = result * PRIME + ($fileSize == null ? 43 : $fileSize.hashCode());
    final java.lang.Object $shareable = this.getShareable();
    result = result * PRIME + ($shareable == null ? 43 : $shareable.hashCode());
    final java.lang.Object $userId = this.getUserId();
    result = result * PRIME + ($userId == null ? 43 : $userId.hashCode());
    final java.lang.Object $typee = this.getTypee();
    result = result * PRIME + ($typee == null ? 43 : $typee.hashCode());
    final java.lang.Object $name = this.getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final java.lang.Object $sha256sum = this.getSha256sum();
    result = result * PRIME + ($sha256sum == null ? 43 : $sha256sum.hashCode());
    final java.lang.Object $createdAt = this.getCreatedAt();
    result = result * PRIME + ($createdAt == null ? 43 : $createdAt.hashCode());
    final java.lang.Object $updatedAt = this.getUpdatedAt();
    result = result * PRIME + ($updatedAt == null ? 43 : $updatedAt.hashCode());
    final java.lang.Object $savedAt = this.getSavedAt();
    result = result * PRIME + ($savedAt == null ? 43 : $savedAt.hashCode());
    final java.lang.Object $status = this.getStatus();
    result = result * PRIME + ($status == null ? 43 : $status.hashCode());
    final java.lang.Object $textContent = this.getTextContent();
    result = result * PRIME + ($textContent == null ? 43 : $textContent.hashCode());
    final java.lang.Object $descriptionUrl = this.getDescriptionUrl();
    result = result * PRIME + ($descriptionUrl == null ? 43 : $descriptionUrl.hashCode());
    final java.lang.Object $codeLanguage = this.getCodeLanguage();
    result = result * PRIME + ($codeLanguage == null ? 43 : $codeLanguage.hashCode());
    return result;
  }

  @java.lang.SuppressWarnings("all")
  public Asset(final Long id, final String typee, final String name, final Long fileSize, final String sha256sum, final OffsetDateTime createdAt, final OffsetDateTime updatedAt, final SavedAt savedAt, final AssetStatus status, final Boolean shareable, final String textContent, final String descriptionUrl, final String codeLanguage, final Long userId) {
    this.id = id;
    this.typee = typee;
    this.name = name;
    this.fileSize = fileSize;
    this.sha256sum = sha256sum;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.savedAt = savedAt;
    this.status = status;
    this.shareable = shareable;
    this.textContent = textContent;
    this.descriptionUrl = descriptionUrl;
    this.codeLanguage = codeLanguage;
    this.userId = userId;
  }

  @java.lang.SuppressWarnings("all")
  public Asset() {
  }

  @java.lang.Override
  @java.lang.SuppressWarnings("all")
  public java.lang.String toString() {
    return "Asset(id=" + this.getId() + ", typee=" + this.getTypee() + ", name=" + this.getName() + ", fileSize=" + this.getFileSize() + ", sha256sum=" + this.getSha256sum() + ", createdAt=" + this.getCreatedAt() + ", updatedAt=" + this.getUpdatedAt() + ", savedAt=" + this.getSavedAt() + ", status=" + this.getStatus() + ", shareable=" + this.getShareable() + ", textContent=" + this.getTextContent() + ", descriptionUrl=" + this.getDescriptionUrl() + ", codeLanguage=" + this.getCodeLanguage() + ", userId=" + this.getUserId() + ")";
  }
}

// Generated by delombok at Wed Sep 27 15:23:54 CST 2023


@JsonIgnoreProperties(ignoreUnknown = true)
public static class Deployment {
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
		started, failed, succeeded;
	}


	@java.lang.SuppressWarnings("all")
	public static class DeploymentBuilder {
		@java.lang.SuppressWarnings("all")
		private Long id;
		@java.lang.SuppressWarnings("all")
		private String output;
		@java.lang.SuppressWarnings("all")
		private Long did;
		@java.lang.SuppressWarnings("all")
		private DeployStatus status;
		@java.lang.SuppressWarnings("all")
		private Long prev;
		@java.lang.SuppressWarnings("all")
		private Long next;
		@java.lang.SuppressWarnings("all")
		private String extra;
		@java.lang.SuppressWarnings("all")
		private String entrypointParams;
		@java.lang.SuppressWarnings("all")
		private OffsetDateTime createdAt;
		@java.lang.SuppressWarnings("all")
		private OffsetDateTime updatedAt;

		@java.lang.SuppressWarnings("all")
		DeploymentBuilder() {
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public Deployment.DeploymentBuilder id(final Long id) {
			this.id = id;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public Deployment.DeploymentBuilder output(final String output) {
			this.output = output;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public Deployment.DeploymentBuilder did(final Long did) {
			this.did = did;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public Deployment.DeploymentBuilder status(final DeployStatus status) {
			this.status = status;
			return this;
		}

		/**
		 * What's the meaning of the prev?
		 * use to collect the information for the chained tasks.
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public Deployment.DeploymentBuilder prev(final Long prev) {
			this.prev = prev;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public Deployment.DeploymentBuilder next(final Long next) {
			this.next = next;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public Deployment.DeploymentBuilder extra(final String extra) {
			this.extra = extra;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty("entrypoint_params")
		@java.lang.SuppressWarnings("all")
		public Deployment.DeploymentBuilder entrypointParams(final String entrypointParams) {
			this.entrypointParams = entrypointParams;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty("created_at")
		@java.lang.SuppressWarnings("all")
		public Deployment.DeploymentBuilder createdAt(final OffsetDateTime createdAt) {
			this.createdAt = createdAt;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty("updated_at")
		@java.lang.SuppressWarnings("all")
		public Deployment.DeploymentBuilder updatedAt(final OffsetDateTime updatedAt) {
			this.updatedAt = updatedAt;
			return this;
		}

		@java.lang.SuppressWarnings("all")
		public Deployment build() {
			return new Deployment(this.id, this.output, this.did, this.status, this.prev, this.next, this.extra, this.entrypointParams, this.createdAt, this.updatedAt);
		}

		@java.lang.Override
		@java.lang.SuppressWarnings("all")
		public java.lang.String toString() {
			return "Deployment.DeploymentBuilder(id=" + this.id + ", output=" + this.output + ", did=" + this.did + ", status=" + this.status + ", prev=" + this.prev + ", next=" + this.next + ", extra=" + this.extra + ", entrypointParams=" + this.entrypointParams + ", createdAt=" + this.createdAt + ", updatedAt=" + this.updatedAt + ")";
		}
	}

	@java.lang.SuppressWarnings("all")
	public static Deployment.DeploymentBuilder builder() {
		return new Deployment.DeploymentBuilder();
	}

	@java.lang.SuppressWarnings("all")
	public Long getId() {
		return this.id;
	}

	@java.lang.SuppressWarnings("all")
	public String getOutput() {
		return this.output;
	}

	@java.lang.SuppressWarnings("all")
	public Long getDid() {
		return this.did;
	}

	@java.lang.SuppressWarnings("all")
	public DeployStatus getStatus() {
		return this.status;
	}

	/**
	 * What's the meaning of the prev?
	 * use to collect the information for the chained tasks.
	 */
	@java.lang.SuppressWarnings("all")
	public Long getPrev() {
		return this.prev;
	}

	@java.lang.SuppressWarnings("all")
	public Long getNext() {
		return this.next;
	}

	@java.lang.SuppressWarnings("all")
	public String getExtra() {
		return this.extra;
	}

	@java.lang.SuppressWarnings("all")
	public String getEntrypointParams() {
		return this.entrypointParams;
	}

	@java.lang.SuppressWarnings("all")
	public OffsetDateTime getCreatedAt() {
		return this.createdAt;
	}

	@java.lang.SuppressWarnings("all")
	public OffsetDateTime getUpdatedAt() {
		return this.updatedAt;
	}

	@java.lang.SuppressWarnings("all")
	public void setId(final Long id) {
		this.id = id;
	}

	@java.lang.SuppressWarnings("all")
	public void setOutput(final String output) {
		this.output = output;
	}

	@java.lang.SuppressWarnings("all")
	public void setDid(final Long did) {
		this.did = did;
	}

	@java.lang.SuppressWarnings("all")
	public void setStatus(final DeployStatus status) {
		this.status = status;
	}

	/**
	 * What's the meaning of the prev?
	 * use to collect the information for the chained tasks.
	 */
	@java.lang.SuppressWarnings("all")
	public void setPrev(final Long prev) {
		this.prev = prev;
	}

	@java.lang.SuppressWarnings("all")
	public void setNext(final Long next) {
		this.next = next;
	}

	@java.lang.SuppressWarnings("all")
	public void setExtra(final String extra) {
		this.extra = extra;
	}

	@JsonProperty("entrypoint_params")
	@java.lang.SuppressWarnings("all")
	public void setEntrypointParams(final String entrypointParams) {
		this.entrypointParams = entrypointParams;
	}

	@JsonProperty("created_at")
	@java.lang.SuppressWarnings("all")
	public void setCreatedAt(final OffsetDateTime createdAt) {
		this.createdAt = createdAt;
	}

	@JsonProperty("updated_at")
	@java.lang.SuppressWarnings("all")
	public void setUpdatedAt(final OffsetDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof Deployment)) return false;
		final Deployment other = (Deployment) o;
		if (!other.canEqual((java.lang.Object) this)) return false;
		final java.lang.Object this$id = this.getId();
		final java.lang.Object other$id = other.getId();
		if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
		final java.lang.Object this$did = this.getDid();
		final java.lang.Object other$did = other.getDid();
		if (this$did == null ? other$did != null : !this$did.equals(other$did)) return false;
		final java.lang.Object this$prev = this.getPrev();
		final java.lang.Object other$prev = other.getPrev();
		if (this$prev == null ? other$prev != null : !this$prev.equals(other$prev)) return false;
		final java.lang.Object this$next = this.getNext();
		final java.lang.Object other$next = other.getNext();
		if (this$next == null ? other$next != null : !this$next.equals(other$next)) return false;
		final java.lang.Object this$output = this.getOutput();
		final java.lang.Object other$output = other.getOutput();
		if (this$output == null ? other$output != null : !this$output.equals(other$output)) return false;
		final java.lang.Object this$status = this.getStatus();
		final java.lang.Object other$status = other.getStatus();
		if (this$status == null ? other$status != null : !this$status.equals(other$status)) return false;
		final java.lang.Object this$extra = this.getExtra();
		final java.lang.Object other$extra = other.getExtra();
		if (this$extra == null ? other$extra != null : !this$extra.equals(other$extra)) return false;
		final java.lang.Object this$entrypointParams = this.getEntrypointParams();
		final java.lang.Object other$entrypointParams = other.getEntrypointParams();
		if (this$entrypointParams == null ? other$entrypointParams != null : !this$entrypointParams.equals(other$entrypointParams)) return false;
		final java.lang.Object this$createdAt = this.getCreatedAt();
		final java.lang.Object other$createdAt = other.getCreatedAt();
		if (this$createdAt == null ? other$createdAt != null : !this$createdAt.equals(other$createdAt)) return false;
		final java.lang.Object this$updatedAt = this.getUpdatedAt();
		final java.lang.Object other$updatedAt = other.getUpdatedAt();
		if (this$updatedAt == null ? other$updatedAt != null : !this$updatedAt.equals(other$updatedAt)) return false;
		return true;
	}

	@java.lang.SuppressWarnings("all")
	protected boolean canEqual(final java.lang.Object other) {
		return other instanceof Deployment;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final java.lang.Object $id = this.getId();
		result = result * PRIME + ($id == null ? 43 : $id.hashCode());
		final java.lang.Object $did = this.getDid();
		result = result * PRIME + ($did == null ? 43 : $did.hashCode());
		final java.lang.Object $prev = this.getPrev();
		result = result * PRIME + ($prev == null ? 43 : $prev.hashCode());
		final java.lang.Object $next = this.getNext();
		result = result * PRIME + ($next == null ? 43 : $next.hashCode());
		final java.lang.Object $output = this.getOutput();
		result = result * PRIME + ($output == null ? 43 : $output.hashCode());
		final java.lang.Object $status = this.getStatus();
		result = result * PRIME + ($status == null ? 43 : $status.hashCode());
		final java.lang.Object $extra = this.getExtra();
		result = result * PRIME + ($extra == null ? 43 : $extra.hashCode());
		final java.lang.Object $entrypointParams = this.getEntrypointParams();
		result = result * PRIME + ($entrypointParams == null ? 43 : $entrypointParams.hashCode());
		final java.lang.Object $createdAt = this.getCreatedAt();
		result = result * PRIME + ($createdAt == null ? 43 : $createdAt.hashCode());
		final java.lang.Object $updatedAt = this.getUpdatedAt();
		result = result * PRIME + ($updatedAt == null ? 43 : $updatedAt.hashCode());
		return result;
	}

	@java.lang.SuppressWarnings("all")
	public Deployment(final Long id, final String output, final Long did, final DeployStatus status, final Long prev, final Long next, final String extra, final String entrypointParams, final OffsetDateTime createdAt, final OffsetDateTime updatedAt) {
		this.id = id;
		this.output = output;
		this.did = did;
		this.status = status;
		this.prev = prev;
		this.next = next;
		this.extra = extra;
		this.entrypointParams = entrypointParams;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	@java.lang.SuppressWarnings("all")
	public Deployment() {
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "Deployment(id=" + this.getId() + ", output=" + this.getOutput() + ", did=" + this.getDid() + ", status=" + this.getStatus() + ", prev=" + this.getPrev() + ", next=" + this.getNext() + ", extra=" + this.getExtra() + ", entrypointParams=" + this.getEntrypointParams() + ", createdAt=" + this.getCreatedAt() + ", updatedAt=" + this.getUpdatedAt() + ")";
	}
}

// Generated by delombok at Wed Sep 27 15:23:54 CST 2023


@JsonIgnoreProperties(ignoreUnknown = true)
public static class DataList<T> {
	private List<T> data;

	@java.lang.SuppressWarnings("all")
	public DataList() {
	}

	@java.lang.SuppressWarnings("all")
	public List<T> getData() {
		return this.data;
	}

	@java.lang.SuppressWarnings("all")
	public void setData(final List<T> data) {
		this.data = data;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof DataList)) return false;
		final DataList<?> other = (DataList<?>) o;
		if (!other.canEqual((java.lang.Object) this)) return false;
		final java.lang.Object this$data = this.getData();
		final java.lang.Object other$data = other.getData();
		if (this$data == null ? other$data != null : !this$data.equals(other$data)) return false;
		return true;
	}

	@java.lang.SuppressWarnings("all")
	protected boolean canEqual(final java.lang.Object other) {
		return other instanceof DataList;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final java.lang.Object $data = this.getData();
		result = result * PRIME + ($data == null ? 43 : $data.hashCode());
		return result;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "DataList(data=" + this.getData() + ")";
	}
}

// Generated by delombok at Wed Sep 27 15:23:54 CST 2023


@JsonIgnoreProperties(ignoreUnknown = true)
public static class DataObject<T> {
	private T data;

	@java.lang.SuppressWarnings("all")
	public DataObject() {
	}

	@java.lang.SuppressWarnings("all")
	public T getData() {
		return this.data;
	}

	@java.lang.SuppressWarnings("all")
	public void setData(final T data) {
		this.data = data;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof DataObject)) return false;
		final DataObject<?> other = (DataObject<?>) o;
		if (!other.canEqual((java.lang.Object) this)) return false;
		final java.lang.Object this$data = this.getData();
		final java.lang.Object other$data = other.getData();
		if (this$data == null ? other$data != null : !this$data.equals(other$data)) return false;
		return true;
	}

	@java.lang.SuppressWarnings("all")
	protected boolean canEqual(final java.lang.Object other) {
		return other instanceof DataObject;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		final java.lang.Object $data = this.getData();
		result = result * PRIME + ($data == null ? 43 : $data.hashCode());
		return result;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "DataObject(data=" + this.getData() + ")";
	}
}

// Generated by delombok at Wed Sep 27 15:23:54 CST 2023

/**
 * shortTimePassword=
 * serverRootUri=
 * thisDeploymentId=
 * thisDeployDefinitionId=
 */
public static class DeploymentEnv {
        private String shortTimePassword;
        private String serverRootUri;
        private Long thisDeploymentId;
        private String thisDeployDefinitionSecret;
        private Long myUserId;
        private Long thisDeployDefinitionId;
        private Long thisTemplateDeployHistory;
        private Long thisTemplateId;

        @java.lang.SuppressWarnings("all")
        public DeploymentEnv() {
        }

        @java.lang.SuppressWarnings("all")
        public String getShortTimePassword() {
                return this.shortTimePassword;
        }

        @java.lang.SuppressWarnings("all")
        public String getServerRootUri() {
                return this.serverRootUri;
        }

        @java.lang.SuppressWarnings("all")
        public Long getThisDeploymentId() {
                return this.thisDeploymentId;
        }

        @java.lang.SuppressWarnings("all")
        public String getThisDeployDefinitionSecret() {
                return this.thisDeployDefinitionSecret;
        }

        @java.lang.SuppressWarnings("all")
        public Long getMyUserId() {
                return this.myUserId;
        }

        @java.lang.SuppressWarnings("all")
        public Long getThisDeployDefinitionId() {
                return this.thisDeployDefinitionId;
        }

        @java.lang.SuppressWarnings("all")
        public Long getThisTemplateDeployHistory() {
                return this.thisTemplateDeployHistory;
        }

        @java.lang.SuppressWarnings("all")
        public Long getThisTemplateId() {
                return this.thisTemplateId;
        }

        @java.lang.SuppressWarnings("all")
        public void setShortTimePassword(final String shortTimePassword) {
                this.shortTimePassword = shortTimePassword;
        }

        @java.lang.SuppressWarnings("all")
        public void setServerRootUri(final String serverRootUri) {
                this.serverRootUri = serverRootUri;
        }

        @java.lang.SuppressWarnings("all")
        public void setThisDeploymentId(final Long thisDeploymentId) {
                this.thisDeploymentId = thisDeploymentId;
        }

        @java.lang.SuppressWarnings("all")
        public void setThisDeployDefinitionSecret(final String thisDeployDefinitionSecret) {
                this.thisDeployDefinitionSecret = thisDeployDefinitionSecret;
        }

        @java.lang.SuppressWarnings("all")
        public void setMyUserId(final Long myUserId) {
                this.myUserId = myUserId;
        }

        @java.lang.SuppressWarnings("all")
        public void setThisDeployDefinitionId(final Long thisDeployDefinitionId) {
                this.thisDeployDefinitionId = thisDeployDefinitionId;
        }

        @java.lang.SuppressWarnings("all")
        public void setThisTemplateDeployHistory(final Long thisTemplateDeployHistory) {
                this.thisTemplateDeployHistory = thisTemplateDeployHistory;
        }

        @java.lang.SuppressWarnings("all")
        public void setThisTemplateId(final Long thisTemplateId) {
                this.thisTemplateId = thisTemplateId;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public boolean equals(final java.lang.Object o) {
                if (o == this) return true;
                if (!(o instanceof DeploymentEnv)) return false;
                final DeploymentEnv other = (DeploymentEnv) o;
                if (!other.canEqual((java.lang.Object) this)) return false;
                final java.lang.Object this$thisDeploymentId = this.getThisDeploymentId();
                final java.lang.Object other$thisDeploymentId = other.getThisDeploymentId();
                if (this$thisDeploymentId == null ? other$thisDeploymentId != null : !this$thisDeploymentId.equals(other$thisDeploymentId)) return false;
                final java.lang.Object this$myUserId = this.getMyUserId();
                final java.lang.Object other$myUserId = other.getMyUserId();
                if (this$myUserId == null ? other$myUserId != null : !this$myUserId.equals(other$myUserId)) return false;
                final java.lang.Object this$thisDeployDefinitionId = this.getThisDeployDefinitionId();
                final java.lang.Object other$thisDeployDefinitionId = other.getThisDeployDefinitionId();
                if (this$thisDeployDefinitionId == null ? other$thisDeployDefinitionId != null : !this$thisDeployDefinitionId.equals(other$thisDeployDefinitionId)) return false;
                final java.lang.Object this$thisTemplateDeployHistory = this.getThisTemplateDeployHistory();
                final java.lang.Object other$thisTemplateDeployHistory = other.getThisTemplateDeployHistory();
                if (this$thisTemplateDeployHistory == null ? other$thisTemplateDeployHistory != null : !this$thisTemplateDeployHistory.equals(other$thisTemplateDeployHistory)) return false;
                final java.lang.Object this$thisTemplateId = this.getThisTemplateId();
                final java.lang.Object other$thisTemplateId = other.getThisTemplateId();
                if (this$thisTemplateId == null ? other$thisTemplateId != null : !this$thisTemplateId.equals(other$thisTemplateId)) return false;
                final java.lang.Object this$shortTimePassword = this.getShortTimePassword();
                final java.lang.Object other$shortTimePassword = other.getShortTimePassword();
                if (this$shortTimePassword == null ? other$shortTimePassword != null : !this$shortTimePassword.equals(other$shortTimePassword)) return false;
                final java.lang.Object this$serverRootUri = this.getServerRootUri();
                final java.lang.Object other$serverRootUri = other.getServerRootUri();
                if (this$serverRootUri == null ? other$serverRootUri != null : !this$serverRootUri.equals(other$serverRootUri)) return false;
                final java.lang.Object this$thisDeployDefinitionSecret = this.getThisDeployDefinitionSecret();
                final java.lang.Object other$thisDeployDefinitionSecret = other.getThisDeployDefinitionSecret();
                if (this$thisDeployDefinitionSecret == null ? other$thisDeployDefinitionSecret != null : !this$thisDeployDefinitionSecret.equals(other$thisDeployDefinitionSecret)) return false;
                return true;
        }

        @java.lang.SuppressWarnings("all")
        protected boolean canEqual(final java.lang.Object other) {
                return other instanceof DeploymentEnv;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public int hashCode() {
                final int PRIME = 59;
                int result = 1;
                final java.lang.Object $thisDeploymentId = this.getThisDeploymentId();
                result = result * PRIME + ($thisDeploymentId == null ? 43 : $thisDeploymentId.hashCode());
                final java.lang.Object $myUserId = this.getMyUserId();
                result = result * PRIME + ($myUserId == null ? 43 : $myUserId.hashCode());
                final java.lang.Object $thisDeployDefinitionId = this.getThisDeployDefinitionId();
                result = result * PRIME + ($thisDeployDefinitionId == null ? 43 : $thisDeployDefinitionId.hashCode());
                final java.lang.Object $thisTemplateDeployHistory = this.getThisTemplateDeployHistory();
                result = result * PRIME + ($thisTemplateDeployHistory == null ? 43 : $thisTemplateDeployHistory.hashCode());
                final java.lang.Object $thisTemplateId = this.getThisTemplateId();
                result = result * PRIME + ($thisTemplateId == null ? 43 : $thisTemplateId.hashCode());
                final java.lang.Object $shortTimePassword = this.getShortTimePassword();
                result = result * PRIME + ($shortTimePassword == null ? 43 : $shortTimePassword.hashCode());
                final java.lang.Object $serverRootUri = this.getServerRootUri();
                result = result * PRIME + ($serverRootUri == null ? 43 : $serverRootUri.hashCode());
                final java.lang.Object $thisDeployDefinitionSecret = this.getThisDeployDefinitionSecret();
                result = result * PRIME + ($thisDeployDefinitionSecret == null ? 43 : $thisDeployDefinitionSecret.hashCode());
                return result;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
                return "DeploymentEnv(shortTimePassword=" + this.getShortTimePassword() + ", serverRootUri=" + this.getServerRootUri() + ", thisDeploymentId=" + this.getThisDeploymentId() + ", thisDeployDefinitionSecret=" + this.getThisDeployDefinitionSecret() + ", myUserId=" + this.getMyUserId() + ", thisDeployDefinitionId=" + this.getThisDeployDefinitionId() + ", thisTemplateDeployHistory=" + this.getThisTemplateDeployHistory() + ", thisTemplateId=" + this.getThisTemplateId() + ")";
        }
}

// Generated by delombok at Wed Sep 27 15:23:54 CST 2023


public static class ProcessResult {
	private int exitCode;
	private List<String> lines;

	@java.lang.SuppressWarnings("all")
	public ProcessResult() {
	}

	@java.lang.SuppressWarnings("all")
	public int getExitCode() {
		return this.exitCode;
	}

	@java.lang.SuppressWarnings("all")
	public List<String> getLines() {
		return this.lines;
	}

	@java.lang.SuppressWarnings("all")
	public void setExitCode(final int exitCode) {
		this.exitCode = exitCode;
	}

	@java.lang.SuppressWarnings("all")
	public void setLines(final List<String> lines) {
		this.lines = lines;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof ProcessResult)) return false;
		final ProcessResult other = (ProcessResult) o;
		if (!other.canEqual((java.lang.Object) this)) return false;
		if (this.getExitCode() != other.getExitCode()) return false;
		final java.lang.Object this$lines = this.getLines();
		final java.lang.Object other$lines = other.getLines();
		if (this$lines == null ? other$lines != null : !this$lines.equals(other$lines)) return false;
		return true;
	}

	@java.lang.SuppressWarnings("all")
	protected boolean canEqual(final java.lang.Object other) {
		return other instanceof ProcessResult;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		result = result * PRIME + this.getExitCode();
		final java.lang.Object $lines = this.getLines();
		result = result * PRIME + ($lines == null ? 43 : $lines.hashCode());
		return result;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "ProcessResult(exitCode=" + this.getExitCode() + ", lines=" + this.getLines() + ")";
	}
}

// Generated by delombok at Wed Sep 27 15:23:54 CST 2023


@JsonIgnoreProperties(ignoreUnknown = true)
public static class DeployDefinition {
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
	private String settings;
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

	@java.lang.SuppressWarnings("all")
	private static String $default$settings() {
		return "{}";
	}


	@java.lang.SuppressWarnings("all")
	public static class DeployDefinitionBuilder {
		@java.lang.SuppressWarnings("all")
		private Long id;
		@java.lang.SuppressWarnings("all")
		private String name;
		@java.lang.SuppressWarnings("all")
		private Long templateId;
		@java.lang.SuppressWarnings("all")
		private boolean minimalRequiresChecked;
		@java.lang.SuppressWarnings("all")
		private Long templateCustomizeId;
		@java.lang.SuppressWarnings("all")
		private Long templateDeployHistoryId;
		@java.lang.SuppressWarnings("all")
		private boolean settings$set;
		@java.lang.SuppressWarnings("all")
		private String settings$value;
		@java.lang.SuppressWarnings("all")
		private Boolean pause;
		@java.lang.SuppressWarnings("all")
		private Long next;
		@java.lang.SuppressWarnings("all")
		private String secret;
		@java.lang.SuppressWarnings("all")
		private String region;
		@java.lang.SuppressWarnings("all")
		private String status;
		@java.lang.SuppressWarnings("all")
		private String deployTo;
		@java.lang.SuppressWarnings("all")
		private Long userId;
		@java.lang.SuppressWarnings("all")
		private boolean keepWorkingEnv;
		@java.lang.SuppressWarnings("all")
		private OffsetDateTime createdAt;
		@java.lang.SuppressWarnings("all")
		private OffsetDateTime updatedAt;
		@java.lang.SuppressWarnings("all")
		private Boolean main;
		@java.lang.SuppressWarnings("all")
		private Long mainDefinitionId;
		@java.lang.SuppressWarnings("all")
		private String descriptionUrl;
		@java.lang.SuppressWarnings("all")
		private String description;

		@java.lang.SuppressWarnings("all")
		DeployDefinitionBuilder() {
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder id(final Long id) {
			this.id = id;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder name(final String name) {
			this.name = name;
			return this;
		}

		/**
		 * the combination of the template_id and template_customize_id is the unique
		 * product id. which
		 * has many prices.
		 * @return {@code this}.
		 */
		@JsonProperty("template_id")
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder templateId(final Long templateId) {
			this.templateId = templateId;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty("minimal_requires_checked")
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder minimalRequiresChecked(final boolean minimalRequiresChecked) {
			this.minimalRequiresChecked = minimalRequiresChecked;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty(FIELD_NAME_TEMPLATE_CUSTOMIZE_ID)
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder templateCustomizeId(final Long templateCustomizeId) {
			this.templateCustomizeId = templateCustomizeId;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty("template_deploy_history_id")
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder templateDeployHistoryId(final Long templateDeployHistoryId) {
			this.templateDeployHistoryId = templateDeployHistoryId;
			return this;
		}

		/**
		 * Has some special keys:
		 * 
		 * <pre>
		 * {
		 * "entrypoint_params": [],
		 * "all_possible_actions": [],
		 * }
		 * </pre>
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder settings(final String settings) {
			this.settings$value = settings;
			settings$set = true;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder pause(final Boolean pause) {
			this.pause = pause;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder next(final Long next) {
			this.next = next;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder secret(final String secret) {
			this.secret = secret;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder region(final String region) {
			this.region = region;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder status(final String status) {
			this.status = status;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty("deploy_to")
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder deployTo(final String deployTo) {
			this.deployTo = deployTo;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty("user_id")
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder userId(final Long userId) {
			this.userId = userId;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty("keep_working_env")
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder keepWorkingEnv(final boolean keepWorkingEnv) {
			this.keepWorkingEnv = keepWorkingEnv;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty("created_at")
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder createdAt(final OffsetDateTime createdAt) {
			this.createdAt = createdAt;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty("updated_at")
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder updatedAt(final OffsetDateTime updatedAt) {
			this.updatedAt = updatedAt;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder main(final Boolean main) {
			this.main = main;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty("main_definition_id")
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder mainDefinitionId(final Long mainDefinitionId) {
			this.mainDefinitionId = mainDefinitionId;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@JsonProperty("description_url")
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder descriptionUrl(final String descriptionUrl) {
			this.descriptionUrl = descriptionUrl;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public DeployDefinition.DeployDefinitionBuilder description(final String description) {
			this.description = description;
			return this;
		}

		@java.lang.SuppressWarnings("all")
		public DeployDefinition build() {
			String settings$value = this.settings$value;
			if (!this.settings$set) settings$value = DeployDefinition.$default$settings();
			return new DeployDefinition(this.id, this.name, this.templateId, this.minimalRequiresChecked, this.templateCustomizeId, this.templateDeployHistoryId, settings$value, this.pause, this.next, this.secret, this.region, this.status, this.deployTo, this.userId, this.keepWorkingEnv, this.createdAt, this.updatedAt, this.main, this.mainDefinitionId, this.descriptionUrl, this.description);
		}

		@java.lang.Override
		@java.lang.SuppressWarnings("all")
		public java.lang.String toString() {
			return "DeployDefinition.DeployDefinitionBuilder(id=" + this.id + ", name=" + this.name + ", templateId=" + this.templateId + ", minimalRequiresChecked=" + this.minimalRequiresChecked + ", templateCustomizeId=" + this.templateCustomizeId + ", templateDeployHistoryId=" + this.templateDeployHistoryId + ", settings$value=" + this.settings$value + ", pause=" + this.pause + ", next=" + this.next + ", secret=" + this.secret + ", region=" + this.region + ", status=" + this.status + ", deployTo=" + this.deployTo + ", userId=" + this.userId + ", keepWorkingEnv=" + this.keepWorkingEnv + ", createdAt=" + this.createdAt + ", updatedAt=" + this.updatedAt + ", main=" + this.main + ", mainDefinitionId=" + this.mainDefinitionId + ", descriptionUrl=" + this.descriptionUrl + ", description=" + this.description + ")";
		}
	}

	@java.lang.SuppressWarnings("all")
	public static DeployDefinition.DeployDefinitionBuilder builder() {
		return new DeployDefinition.DeployDefinitionBuilder();
	}

	@java.lang.SuppressWarnings("all")
	public Long getId() {
		return this.id;
	}

	@java.lang.SuppressWarnings("all")
	public String getName() {
		return this.name;
	}

	/**
	 * the combination of the template_id and template_customize_id is the unique
	 * product id. which
	 * has many prices.
	 */
	@java.lang.SuppressWarnings("all")
	public Long getTemplateId() {
		return this.templateId;
	}

	@java.lang.SuppressWarnings("all")
	public boolean isMinimalRequiresChecked() {
		return this.minimalRequiresChecked;
	}

	@java.lang.SuppressWarnings("all")
	public Long getTemplateCustomizeId() {
		return this.templateCustomizeId;
	}

	@java.lang.SuppressWarnings("all")
	public Long getTemplateDeployHistoryId() {
		return this.templateDeployHistoryId;
	}

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
	@java.lang.SuppressWarnings("all")
	public String getSettings() {
		return this.settings;
	}

	@java.lang.SuppressWarnings("all")
	public Boolean getPause() {
		return this.pause;
	}

	@java.lang.SuppressWarnings("all")
	public Long getNext() {
		return this.next;
	}

	@java.lang.SuppressWarnings("all")
	public String getSecret() {
		return this.secret;
	}

	@java.lang.SuppressWarnings("all")
	public String getStatus() {
		return this.status;
	}

	@java.lang.SuppressWarnings("all")
	public String getDeployTo() {
		return this.deployTo;
	}

	@java.lang.SuppressWarnings("all")
	public Long getUserId() {
		return this.userId;
	}

	@java.lang.SuppressWarnings("all")
	public boolean isKeepWorkingEnv() {
		return this.keepWorkingEnv;
	}

	@java.lang.SuppressWarnings("all")
	public OffsetDateTime getCreatedAt() {
		return this.createdAt;
	}

	@java.lang.SuppressWarnings("all")
	public OffsetDateTime getUpdatedAt() {
		return this.updatedAt;
	}

	@java.lang.SuppressWarnings("all")
	public Boolean getMain() {
		return this.main;
	}

	@java.lang.SuppressWarnings("all")
	public Long getMainDefinitionId() {
		return this.mainDefinitionId;
	}

	@java.lang.SuppressWarnings("all")
	public String getDescriptionUrl() {
		return this.descriptionUrl;
	}

	@java.lang.SuppressWarnings("all")
	public String getDescription() {
		return this.description;
	}

	@java.lang.SuppressWarnings("all")
	public void setId(final Long id) {
		this.id = id;
	}

	@java.lang.SuppressWarnings("all")
	public void setName(final String name) {
		this.name = name;
	}

	/**
	 * the combination of the template_id and template_customize_id is the unique
	 * product id. which
	 * has many prices.
	 */
	@JsonProperty("template_id")
	@java.lang.SuppressWarnings("all")
	public void setTemplateId(final Long templateId) {
		this.templateId = templateId;
	}

	@JsonProperty("minimal_requires_checked")
	@java.lang.SuppressWarnings("all")
	public void setMinimalRequiresChecked(final boolean minimalRequiresChecked) {
		this.minimalRequiresChecked = minimalRequiresChecked;
	}

	@JsonProperty(FIELD_NAME_TEMPLATE_CUSTOMIZE_ID)
	@java.lang.SuppressWarnings("all")
	public void setTemplateCustomizeId(final Long templateCustomizeId) {
		this.templateCustomizeId = templateCustomizeId;
	}

	@JsonProperty("template_deploy_history_id")
	@java.lang.SuppressWarnings("all")
	public void setTemplateDeployHistoryId(final Long templateDeployHistoryId) {
		this.templateDeployHistoryId = templateDeployHistoryId;
	}

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
	@java.lang.SuppressWarnings("all")
	public void setSettings(final String settings) {
		this.settings = settings;
	}

	@java.lang.SuppressWarnings("all")
	public void setPause(final Boolean pause) {
		this.pause = pause;
	}

	@java.lang.SuppressWarnings("all")
	public void setNext(final Long next) {
		this.next = next;
	}

	@java.lang.SuppressWarnings("all")
	public void setSecret(final String secret) {
		this.secret = secret;
	}

	@java.lang.SuppressWarnings("all")
	public void setRegion(final String region) {
		this.region = region;
	}

	@java.lang.SuppressWarnings("all")
	public void setStatus(final String status) {
		this.status = status;
	}

	@JsonProperty("deploy_to")
	@java.lang.SuppressWarnings("all")
	public void setDeployTo(final String deployTo) {
		this.deployTo = deployTo;
	}

	@JsonProperty("user_id")
	@java.lang.SuppressWarnings("all")
	public void setUserId(final Long userId) {
		this.userId = userId;
	}

	@JsonProperty("keep_working_env")
	@java.lang.SuppressWarnings("all")
	public void setKeepWorkingEnv(final boolean keepWorkingEnv) {
		this.keepWorkingEnv = keepWorkingEnv;
	}

	@JsonProperty("created_at")
	@java.lang.SuppressWarnings("all")
	public void setCreatedAt(final OffsetDateTime createdAt) {
		this.createdAt = createdAt;
	}

	@JsonProperty("updated_at")
	@java.lang.SuppressWarnings("all")
	public void setUpdatedAt(final OffsetDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	@java.lang.SuppressWarnings("all")
	public void setMain(final Boolean main) {
		this.main = main;
	}

	@JsonProperty("main_definition_id")
	@java.lang.SuppressWarnings("all")
	public void setMainDefinitionId(final Long mainDefinitionId) {
		this.mainDefinitionId = mainDefinitionId;
	}

	@JsonProperty("description_url")
	@java.lang.SuppressWarnings("all")
	public void setDescriptionUrl(final String descriptionUrl) {
		this.descriptionUrl = descriptionUrl;
	}

	@java.lang.SuppressWarnings("all")
	public void setDescription(final String description) {
		this.description = description;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof DeployDefinition)) return false;
		final DeployDefinition other = (DeployDefinition) o;
		if (!other.canEqual((java.lang.Object) this)) return false;
		if (this.isMinimalRequiresChecked() != other.isMinimalRequiresChecked()) return false;
		if (this.isKeepWorkingEnv() != other.isKeepWorkingEnv()) return false;
		final java.lang.Object this$id = this.getId();
		final java.lang.Object other$id = other.getId();
		if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
		final java.lang.Object this$templateId = this.getTemplateId();
		final java.lang.Object other$templateId = other.getTemplateId();
		if (this$templateId == null ? other$templateId != null : !this$templateId.equals(other$templateId)) return false;
		final java.lang.Object this$templateCustomizeId = this.getTemplateCustomizeId();
		final java.lang.Object other$templateCustomizeId = other.getTemplateCustomizeId();
		if (this$templateCustomizeId == null ? other$templateCustomizeId != null : !this$templateCustomizeId.equals(other$templateCustomizeId)) return false;
		final java.lang.Object this$templateDeployHistoryId = this.getTemplateDeployHistoryId();
		final java.lang.Object other$templateDeployHistoryId = other.getTemplateDeployHistoryId();
		if (this$templateDeployHistoryId == null ? other$templateDeployHistoryId != null : !this$templateDeployHistoryId.equals(other$templateDeployHistoryId)) return false;
		final java.lang.Object this$pause = this.getPause();
		final java.lang.Object other$pause = other.getPause();
		if (this$pause == null ? other$pause != null : !this$pause.equals(other$pause)) return false;
		final java.lang.Object this$next = this.getNext();
		final java.lang.Object other$next = other.getNext();
		if (this$next == null ? other$next != null : !this$next.equals(other$next)) return false;
		final java.lang.Object this$userId = this.getUserId();
		final java.lang.Object other$userId = other.getUserId();
		if (this$userId == null ? other$userId != null : !this$userId.equals(other$userId)) return false;
		final java.lang.Object this$main = this.getMain();
		final java.lang.Object other$main = other.getMain();
		if (this$main == null ? other$main != null : !this$main.equals(other$main)) return false;
		final java.lang.Object this$mainDefinitionId = this.getMainDefinitionId();
		final java.lang.Object other$mainDefinitionId = other.getMainDefinitionId();
		if (this$mainDefinitionId == null ? other$mainDefinitionId != null : !this$mainDefinitionId.equals(other$mainDefinitionId)) return false;
		final java.lang.Object this$name = this.getName();
		final java.lang.Object other$name = other.getName();
		if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
		final java.lang.Object this$settings = this.getSettings();
		final java.lang.Object other$settings = other.getSettings();
		if (this$settings == null ? other$settings != null : !this$settings.equals(other$settings)) return false;
		final java.lang.Object this$secret = this.getSecret();
		final java.lang.Object other$secret = other.getSecret();
		if (this$secret == null ? other$secret != null : !this$secret.equals(other$secret)) return false;
		final java.lang.Object this$region = this.getRegion();
		final java.lang.Object other$region = other.getRegion();
		if (this$region == null ? other$region != null : !this$region.equals(other$region)) return false;
		final java.lang.Object this$status = this.getStatus();
		final java.lang.Object other$status = other.getStatus();
		if (this$status == null ? other$status != null : !this$status.equals(other$status)) return false;
		final java.lang.Object this$deployTo = this.getDeployTo();
		final java.lang.Object other$deployTo = other.getDeployTo();
		if (this$deployTo == null ? other$deployTo != null : !this$deployTo.equals(other$deployTo)) return false;
		final java.lang.Object this$createdAt = this.getCreatedAt();
		final java.lang.Object other$createdAt = other.getCreatedAt();
		if (this$createdAt == null ? other$createdAt != null : !this$createdAt.equals(other$createdAt)) return false;
		final java.lang.Object this$updatedAt = this.getUpdatedAt();
		final java.lang.Object other$updatedAt = other.getUpdatedAt();
		if (this$updatedAt == null ? other$updatedAt != null : !this$updatedAt.equals(other$updatedAt)) return false;
		final java.lang.Object this$descriptionUrl = this.getDescriptionUrl();
		final java.lang.Object other$descriptionUrl = other.getDescriptionUrl();
		if (this$descriptionUrl == null ? other$descriptionUrl != null : !this$descriptionUrl.equals(other$descriptionUrl)) return false;
		final java.lang.Object this$description = this.getDescription();
		final java.lang.Object other$description = other.getDescription();
		if (this$description == null ? other$description != null : !this$description.equals(other$description)) return false;
		return true;
	}

	@java.lang.SuppressWarnings("all")
	protected boolean canEqual(final java.lang.Object other) {
		return other instanceof DeployDefinition;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		result = result * PRIME + (this.isMinimalRequiresChecked() ? 79 : 97);
		result = result * PRIME + (this.isKeepWorkingEnv() ? 79 : 97);
		final java.lang.Object $id = this.getId();
		result = result * PRIME + ($id == null ? 43 : $id.hashCode());
		final java.lang.Object $templateId = this.getTemplateId();
		result = result * PRIME + ($templateId == null ? 43 : $templateId.hashCode());
		final java.lang.Object $templateCustomizeId = this.getTemplateCustomizeId();
		result = result * PRIME + ($templateCustomizeId == null ? 43 : $templateCustomizeId.hashCode());
		final java.lang.Object $templateDeployHistoryId = this.getTemplateDeployHistoryId();
		result = result * PRIME + ($templateDeployHistoryId == null ? 43 : $templateDeployHistoryId.hashCode());
		final java.lang.Object $pause = this.getPause();
		result = result * PRIME + ($pause == null ? 43 : $pause.hashCode());
		final java.lang.Object $next = this.getNext();
		result = result * PRIME + ($next == null ? 43 : $next.hashCode());
		final java.lang.Object $userId = this.getUserId();
		result = result * PRIME + ($userId == null ? 43 : $userId.hashCode());
		final java.lang.Object $main = this.getMain();
		result = result * PRIME + ($main == null ? 43 : $main.hashCode());
		final java.lang.Object $mainDefinitionId = this.getMainDefinitionId();
		result = result * PRIME + ($mainDefinitionId == null ? 43 : $mainDefinitionId.hashCode());
		final java.lang.Object $name = this.getName();
		result = result * PRIME + ($name == null ? 43 : $name.hashCode());
		final java.lang.Object $settings = this.getSettings();
		result = result * PRIME + ($settings == null ? 43 : $settings.hashCode());
		final java.lang.Object $secret = this.getSecret();
		result = result * PRIME + ($secret == null ? 43 : $secret.hashCode());
		final java.lang.Object $region = this.getRegion();
		result = result * PRIME + ($region == null ? 43 : $region.hashCode());
		final java.lang.Object $status = this.getStatus();
		result = result * PRIME + ($status == null ? 43 : $status.hashCode());
		final java.lang.Object $deployTo = this.getDeployTo();
		result = result * PRIME + ($deployTo == null ? 43 : $deployTo.hashCode());
		final java.lang.Object $createdAt = this.getCreatedAt();
		result = result * PRIME + ($createdAt == null ? 43 : $createdAt.hashCode());
		final java.lang.Object $updatedAt = this.getUpdatedAt();
		result = result * PRIME + ($updatedAt == null ? 43 : $updatedAt.hashCode());
		final java.lang.Object $descriptionUrl = this.getDescriptionUrl();
		result = result * PRIME + ($descriptionUrl == null ? 43 : $descriptionUrl.hashCode());
		final java.lang.Object $description = this.getDescription();
		result = result * PRIME + ($description == null ? 43 : $description.hashCode());
		return result;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "DeployDefinition(id=" + this.getId() + ", name=" + this.getName() + ", templateId=" + this.getTemplateId() + ", minimalRequiresChecked=" + this.isMinimalRequiresChecked() + ", templateCustomizeId=" + this.getTemplateCustomizeId() + ", templateDeployHistoryId=" + this.getTemplateDeployHistoryId() + ", settings=" + this.getSettings() + ", pause=" + this.getPause() + ", next=" + this.getNext() + ", secret=" + this.getSecret() + ", region=" + this.getRegion() + ", status=" + this.getStatus() + ", deployTo=" + this.getDeployTo() + ", userId=" + this.getUserId() + ", keepWorkingEnv=" + this.isKeepWorkingEnv() + ", createdAt=" + this.getCreatedAt() + ", updatedAt=" + this.getUpdatedAt() + ", main=" + this.getMain() + ", mainDefinitionId=" + this.getMainDefinitionId() + ", descriptionUrl=" + this.getDescriptionUrl() + ", description=" + this.getDescription() + ")";
	}

	@java.lang.SuppressWarnings("all")
	public DeployDefinition(final Long id, final String name, final Long templateId, final boolean minimalRequiresChecked, final Long templateCustomizeId, final Long templateDeployHistoryId, final String settings, final Boolean pause, final Long next, final String secret, final String region, final String status, final String deployTo, final Long userId, final boolean keepWorkingEnv, final OffsetDateTime createdAt, final OffsetDateTime updatedAt, final Boolean main, final Long mainDefinitionId, final String descriptionUrl, final String description) {
		this.id = id;
		this.name = name;
		this.templateId = templateId;
		this.minimalRequiresChecked = minimalRequiresChecked;
		this.templateCustomizeId = templateCustomizeId;
		this.templateDeployHistoryId = templateDeployHistoryId;
		this.settings = settings;
		this.pause = pause;
		this.next = next;
		this.secret = secret;
		this.region = region;
		this.status = status;
		this.deployTo = deployTo;
		this.userId = userId;
		this.keepWorkingEnv = keepWorkingEnv;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.main = main;
		this.mainDefinitionId = mainDefinitionId;
		this.descriptionUrl = descriptionUrl;
		this.description = description;
	}

	@java.lang.SuppressWarnings("all")
	public DeployDefinition() {
		this.settings = DeployDefinition.$default$settings();
	}
}





public static class AppWithEnv {

	private final DeploymentEnv deploymentEnv;
	private final ObjectMapper objectMapper;

	/**
	 * @param deploymentEnv
	 */
	public AppWithEnv(DeploymentEnv deploymentEnv) {
		this.deploymentEnv = deploymentEnv;
		this.objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
	}

	public AppWithEnv(DeploymentEnv deploymentEnv, ObjectMapper objectMapper) {
		this.deploymentEnv = deploymentEnv;
		this.objectMapper = objectMapper;
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

// Generated by delombok at Wed Sep 27 15:23:54 CST 2023


public static class BackupRestoreTask {
	@java.lang.SuppressWarnings("all")
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BackupRestoreTask.class);
	private Stream<CopyItem> items;
	private Path backupFile;

	public BackupRestoreTask(Stream<CopyItem> items) {
		this.items = items;
	}

	public BackupRestoreTask(Stream<CopyItem> items, Path backupFile) {
		this.items = items;
		this.backupFile = backupFile;
	}

	public BackupRestoreTask(Path inpuPath, Path backFile) throws IOException {
		this.items = InputFileParser.restoreParser(inpuPath.toString()).parse();
		this.backupFile = backFile;
	}

	public Path backup() throws IOException {
		if (backupFile == null) {
			backupFile = Files.createTempDirectory("backup").resolve("backup.zip");
		}
		try (ZipTask zipTask = ZipTask.get(backupFile, ZipNameType.ABSOLUTE, false)) {
			items.forEach(item -> {
				Path copyToPath = Path.of(item.getCopyTo());
				if (Files.isDirectory(copyToPath)) {
					copyToPath = copyToPath.resolve(Path.of(item.getCopyFrom()).getFileName());
				}
				Path copyToPathFinal = copyToPath;
				if (Files.notExists(copyToPathFinal)) {
					log.warn("File to backup not exists: {}", copyToPathFinal);
				} else {
					Util.exceptionHandler(() -> zipTask.push(copyToPathFinal), 1, "zipTask push");
				}
			});
		}
		return backupFile;
	}

	public long restore() throws IOException {
		if (backupFile == null) {
			return -1;
		}
		try (ZipTask zipTask = ZipTask.get(backupFile, ZipNameType.ABSOLUTE, true)) {
			return zipTask.allEntryPath().filter(Files::isRegularFile).map(p -> {
				String dstStr = p.toString();
				log.info("get item in zip: {}", dstStr);
				if (dstStr.startsWith("/") && dstStr.matches("/[a-zA-Z]:.*")) {
					dstStr = dstStr.substring(1);
				}
				Path dst = Path.of(dstStr);
				Path parent = dst.getParent();
				if (parent != null && Files.notExists(parent)) {
					Util.exceptionHandler(() -> Files.createDirectories(parent), 1, "createDirectories");
				}
				return Util.exceptionHandler(() -> Util.copyFile(p, dst), null, 1, "copyFile");
			}).filter(Objects::nonNull).count();
		}
	}
}

// Generated by delombok at Wed Sep 27 15:23:54 CST 2023


public static class CopyTask {
	private Stream<CopyItem> items;
	private boolean override;
	private int skipCount = 0;

	public CopyTask(Stream<CopyItem> items, boolean override) {
		this.items = items;
		this.override = override;
	}

	public CopyTask(Path inpuPath, boolean copyAlways) throws IOException {
		this.items = InputFileParser.copyParser(inpuPath.toString()).parse();
		this.override = copyAlways;
	}

	public void start() throws IOException {
		this.items.forEach(item -> {
			Util.exceptionHandler(() -> copyOne(item), 1, "copyOne");
		});
	}

	/**
	 * if copyFrom is a directory, copy all files in it to copyTo
	 * if the name of the copyFrom is like
	 * /some/path/to/some.zip!some/path/to/file.txt then is in a zip file.
	 * if the format is like /some/path/to/some.zip!~file.txt then just match the
	 * name part of the zipentry, the first match will take precedence.
	 * 
	 * @param copyFrom
	 * @param copyTo
	 * @throws IOException
	 */
	private void copyOne(CopyItem item) throws IOException {
		if (!override && Files.exists(Path.of(item.getCopyTo()))) {
			Util.printSkipFromAndTo(Path.of(item.getCopyFrom()), Path.of(item.getCopyTo()));
			skipCount++;
			return;
		}
		if (item.getFromZipFile() != null) {
			if (item.isFromExactly()) {
				ZipTask zipTask = ZipTask.get(item.getFromZipFile(), ZipNameType.ABSOLUTE, true);
				zipTask.pullExactly(item.getCopyFrom(), Path.of(item.getCopyTo()));
			} else {
				ZipTask zipTask = ZipTask.get(item.getFromZipFile(), ZipNameType.ABSOLUTE, true);
				zipTask.pullEndsWith(item.getCopyFrom(), Path.of(item.getCopyTo()));
			}
		} else {
			Path to = Path.of(item.getCopyTo());
			if (Files.isDirectory(to)) {
				to = to.resolve(Path.of(item.getCopyFrom()).getFileName());
			}
			Util.copyFile(Path.of(item.getCopyFrom()), to);
		}
	}

	@java.lang.SuppressWarnings("all")
	public int getSkipCount() {
		return this.skipCount;
	}
}

// Generated by delombok at Wed Sep 27 15:23:54 CST 2023


public static class CopyItem {
	private String copyFrom;
	private String copyTo;
	private Path fromZipFile;
	private boolean fromExactly;

	@java.lang.SuppressWarnings("all")
	CopyItem(final String copyFrom, final String copyTo, final Path fromZipFile, final boolean fromExactly) {
		this.copyFrom = copyFrom;
		this.copyTo = copyTo;
		this.fromZipFile = fromZipFile;
		this.fromExactly = fromExactly;
	}


	@java.lang.SuppressWarnings("all")
	public static class CopyItemBuilder {
		@java.lang.SuppressWarnings("all")
		private String copyFrom;
		@java.lang.SuppressWarnings("all")
		private String copyTo;
		@java.lang.SuppressWarnings("all")
		private Path fromZipFile;
		@java.lang.SuppressWarnings("all")
		private boolean fromExactly;

		@java.lang.SuppressWarnings("all")
		CopyItemBuilder() {
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public CopyItem.CopyItemBuilder copyFrom(final String copyFrom) {
			this.copyFrom = copyFrom;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public CopyItem.CopyItemBuilder copyTo(final String copyTo) {
			this.copyTo = copyTo;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public CopyItem.CopyItemBuilder fromZipFile(final Path fromZipFile) {
			this.fromZipFile = fromZipFile;
			return this;
		}

		/**
		 * @return {@code this}.
		 */
		@java.lang.SuppressWarnings("all")
		public CopyItem.CopyItemBuilder fromExactly(final boolean fromExactly) {
			this.fromExactly = fromExactly;
			return this;
		}

		@java.lang.SuppressWarnings("all")
		public CopyItem build() {
			return new CopyItem(this.copyFrom, this.copyTo, this.fromZipFile, this.fromExactly);
		}

		@java.lang.Override
		@java.lang.SuppressWarnings("all")
		public java.lang.String toString() {
			return "CopyItem.CopyItemBuilder(copyFrom=" + this.copyFrom + ", copyTo=" + this.copyTo + ", fromZipFile=" + this.fromZipFile + ", fromExactly=" + this.fromExactly + ")";
		}
	}

	@java.lang.SuppressWarnings("all")
	public static CopyItem.CopyItemBuilder builder() {
		return new CopyItem.CopyItemBuilder();
	}

	@java.lang.SuppressWarnings("all")
	public String getCopyFrom() {
		return this.copyFrom;
	}

	@java.lang.SuppressWarnings("all")
	public String getCopyTo() {
		return this.copyTo;
	}

	@java.lang.SuppressWarnings("all")
	public Path getFromZipFile() {
		return this.fromZipFile;
	}

	@java.lang.SuppressWarnings("all")
	public boolean isFromExactly() {
		return this.fromExactly;
	}

	@java.lang.SuppressWarnings("all")
	public void setCopyFrom(final String copyFrom) {
		this.copyFrom = copyFrom;
	}

	@java.lang.SuppressWarnings("all")
	public void setCopyTo(final String copyTo) {
		this.copyTo = copyTo;
	}

	@java.lang.SuppressWarnings("all")
	public void setFromZipFile(final Path fromZipFile) {
		this.fromZipFile = fromZipFile;
	}

	@java.lang.SuppressWarnings("all")
	public void setFromExactly(final boolean fromExactly) {
		this.fromExactly = fromExactly;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public boolean equals(final java.lang.Object o) {
		if (o == this) return true;
		if (!(o instanceof CopyItem)) return false;
		final CopyItem other = (CopyItem) o;
		if (!other.canEqual((java.lang.Object) this)) return false;
		if (this.isFromExactly() != other.isFromExactly()) return false;
		final java.lang.Object this$copyFrom = this.getCopyFrom();
		final java.lang.Object other$copyFrom = other.getCopyFrom();
		if (this$copyFrom == null ? other$copyFrom != null : !this$copyFrom.equals(other$copyFrom)) return false;
		final java.lang.Object this$copyTo = this.getCopyTo();
		final java.lang.Object other$copyTo = other.getCopyTo();
		if (this$copyTo == null ? other$copyTo != null : !this$copyTo.equals(other$copyTo)) return false;
		final java.lang.Object this$fromZipFile = this.getFromZipFile();
		final java.lang.Object other$fromZipFile = other.getFromZipFile();
		if (this$fromZipFile == null ? other$fromZipFile != null : !this$fromZipFile.equals(other$fromZipFile)) return false;
		return true;
	}

	@java.lang.SuppressWarnings("all")
	protected boolean canEqual(final java.lang.Object other) {
		return other instanceof CopyItem;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		result = result * PRIME + (this.isFromExactly() ? 79 : 97);
		final java.lang.Object $copyFrom = this.getCopyFrom();
		result = result * PRIME + ($copyFrom == null ? 43 : $copyFrom.hashCode());
		final java.lang.Object $copyTo = this.getCopyTo();
		result = result * PRIME + ($copyTo == null ? 43 : $copyTo.hashCode());
		final java.lang.Object $fromZipFile = this.getFromZipFile();
		result = result * PRIME + ($fromZipFile == null ? 43 : $fromZipFile.hashCode());
		return result;
	}

	@java.lang.Override
	@java.lang.SuppressWarnings("all")
	public java.lang.String toString() {
		return "CopyItem(copyFrom=" + this.getCopyFrom() + ", copyTo=" + this.getCopyTo() + ", fromZipFile=" + this.getFromZipFile() + ", fromExactly=" + this.isFromExactly() + ")";
	}
}



/**
 * when copying the source file maybe from a zip file, when backuping the file
 * to backup will not come back to the origin zip file,
 * so the copyFrom property of the Copyitem is unrelated.
 */
public static class InputFileParser {
	private static final Pattern ptn = Pattern.compile("\\s*([^#]*)#.*");

	private Path inputFilePath;

	private boolean copying;

	private InputFileParser() {
	}

	public static InputFileParser copyParser(String inputFilePath) {
		InputFileParser inputFileParser = new InputFileParser();
		inputFileParser.copying = true;
		inputFileParser.inputFilePath = Path.of(inputFilePath);
		return inputFileParser;
	}

	public static InputFileParser restoreParser(String inputFilePath) {
		InputFileParser inputFileParser = new InputFileParser();
		inputFileParser.copying = false;
		inputFileParser.inputFilePath = Path.of(inputFilePath);
		return inputFileParser;
	}

	public Stream<CopyItem> parse() throws IOException {
		List<String> lines = Files.readAllLines(inputFilePath);
		return parse(lines);
	}

	protected Stream<CopyItem> parse(List<String> lines) {
		return lines.stream()
				.map(line -> {
					Matcher matcher = ptn.matcher(line);
					if (matcher.matches()) {
						return matcher.group(1);
					} else {
						return line;
					}
				})
				.map(line -> line.trim())
				.filter(line -> !line.startsWith("#"))
				.filter(line -> !line.isBlank())
				.flatMap(line -> {
					String[] parts = line.split("->");
					if (parts.length != 2) {
						return null;
					}
					String copyFrom = parts[0].trim();
					String copyTo = parts[1].trim();
					if (copyFrom.isBlank() || copyTo.isBlank() || copyFrom.startsWith("!")) {
						return null;
					}

					if (!copying) {
						// it's a backup, so copyfrom is actually the copyto field in the filelistfile.
						// the line in filelist file a.txt -> ../notingit/a.txt, we just care about the
						// ../notingit/a.txt
						return Util.walkBackupItem(Path.of(copyTo));
					}
					Path zipFile = null;
					String entryName = null;
					boolean exactly = false;
					boolean inZip = false;
					String[] splittedZipItem = copyFrom.split("!~", 2);
					if (splittedZipItem.length == 2) {
						zipFile = Path.of(splittedZipItem[0]);
						entryName = splittedZipItem[1];
						exactly = false;
						inZip = true;
					} else {
						splittedZipItem = copyFrom.split("!", 2);
						if (splittedZipItem.length == 2) {
							zipFile = Path.of(splittedZipItem[0]);
							entryName = splittedZipItem[1];
							exactly = true;
							inZip = true;
						}
					}
					if (inZip) {
						boolean zipExists = Files.exists(zipFile) && Files.isReadable(zipFile);
						if (!zipExists && !Util.isIgnoreMissingSource()) {
							throw new RuntimeException("Zip file not found: " + zipFile);
						}
						if (zipExists) {
							ZipTask zipTask = ZipTask.get(zipFile, ZipNameType.ABSOLUTE, true);
							return Util.walkCopyFrom(zipTask.getZipFileSystem().getPath(entryName), copyTo, zipFile,
									exactly);
						} else {
							return null;
						}
					} else {
						return Util.walkCopyFrom(Path.of(copyFrom), copyTo, null, true);
					}
				}).filter(Objects::nonNull);
	}

}



/**
 * versionlize the files in the destination.
 */
public static class VersionTask {

	private Stream<CopyItem> items;

	public VersionTask(Stream<CopyItem> items) {
		this.items = items;
	}

	public VersionTask(Path inpuPath) throws IOException {
		this.items = InputFileParser.copyParser(inpuPath.toString()).parse();
	}

	public void startBackup() throws IOException {
		this.items.forEach(item -> {
			Util.exceptionHandler(() -> backupOne(item), 1, "copyOne");
		});
	}

	public void startRollback() throws IOException {
		this.items.forEach(item -> {
			Util.exceptionHandler(() -> rollbackOne(item), 1, "copyOne");
		});
	}

	/**
	 * 
	 * @throws IOException
	 */
	private void backupOne(CopyItem item) throws IOException {
		Path to = Path.of(item.getCopyTo());
		if (Files.isDirectory(to)) {
			to = to.resolve(Path.of(item.getCopyFrom()).getFileName());
		}
		if (Files.exists(to)) {
			backupFileWithIncreasingSuffix(to);
		}
	}

	private void rollbackOne(CopyItem item) throws IOException {
		Path to = Path.of(item.getCopyTo());
		if (Files.isDirectory(to)) {
			to = to.resolve(Path.of(item.getCopyFrom()).getFileName());
		}
		restoreBackup(to);
	}

	public static void backupFileWithIncreasingSuffix(Path filepath) throws IOException {
		File originalFile = filepath.toFile();
		int suffix = 1;

		// Check if the original file already exists
		if (!originalFile.exists()) {
			System.out.println("Original file does not exist.");
			return;
		}

		// Generate the backup filename with an increasing suffix
		String backupFilename;
		do {
			backupFilename = generateBackupFilename(filepath, suffix);
			suffix++;
		} while (new File(backupFilename).exists());

		// Perform the backup by renaming the original file
		File backupFile = new File(backupFilename);
		Files.copy(originalFile.toPath(), backupFile.toPath());
		System.out.println("Backup successful. File saved as: " + backupFilename);
	}

	public static String generateBackupFilename(Path filepath, int suffix) {
		File file = filepath.toFile();
		String parentDir = file.getParent();
		String name = file.getName();

		// Separate the base name and extension (if present)
		String baseName;
		String extension = "";
		int lastDotIndex = name.lastIndexOf('.');
		if (lastDotIndex != -1) {
			baseName = name.substring(0, lastDotIndex);
			extension = name.substring(lastDotIndex);
		} else {
			baseName = name;
		}

		// Append the suffix to the base name and reconstruct the full path
		String backupBaseName = baseName + "." + suffix;
		String backupFilename = parentDir == null ? backupBaseName : parentDir + File.separator + backupBaseName;
		return backupFilename + extension;
	}

	public static Path findLargestBackup(Path originFilepath) {
		File file = originFilepath.toFile();
		String baseName;
		String extension = "";
		String filename = file.getName();

		// Separate the base name and extension (if present)
		int lastDotIndex = filename.lastIndexOf('.');
		if (lastDotIndex != -1) {
			baseName = filename.substring(0, lastDotIndex);
			extension = filename.substring(lastDotIndex);
		} else {
			baseName = filename;
		}

		// Find the largest backup file with the given base name and extension
		String largestBackup = null;
		int largestSuffix = 0;
		File parentDir = file.getParentFile();
		for (File backupFile : parentDir.listFiles()) {
			String name = backupFile.getName();
			if (name.startsWith(baseName) && name.endsWith(extension)
					&& name.length() > baseName.length() + extension.length()) {
				try {
					int suffix = Integer
							.parseInt(name.substring(baseName.length() + 1, name.length() - extension.length()));
					if (suffix > largestSuffix) {
						largestSuffix = suffix;
						largestBackup = backupFile.getAbsolutePath();
					}
				} catch (NumberFormatException e) {
					// Ignore files that do not match the backup naming convention
				}
			}
		}
		return largestBackup == null ? null : Path.of(largestBackup);
	}

	public static void restoreBackup(Path originFilePath) throws IOException {
		Path backuPath = findLargestBackup(originFilePath);
		if (backuPath == null) {
			System.out.println("No backup file found.");
			return;
		}
		if (originFilePath.equals(backuPath)) {
			System.out.println("Backup file is the same as the original file.");
			return;
		}
		Files.copy(backuPath, originFilePath, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("restoring the file " + originFilePath + " from " + backuPath + " successfully.");
		Files.delete(backuPath);
		System.out.println("deleting the backup file " + backuPath + " successfully.");
	}

}


/**
 * Maybe FLATTEN and ABSOLUTE have clear sense.
 */
public enum ZipNameType {
	FLATTEN, ABSOLUTE
}



public static class Uploader {

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

	// ___insert_end___
}
