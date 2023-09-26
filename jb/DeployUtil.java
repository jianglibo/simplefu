///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.6.3
//DEPS io.projectreactor:reactor-core:3.5.10
//DEPS com.fasterxml.jackson.core:jackson-databind:2.15.2
//DEPS com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2
//DEPS org.slf4j:slf4j-api:2.0.7
//DEPS org.slf4j:slf4j-simple:2.0.9

import java.io.Closeable;
import java.io.File;
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
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
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
            PureHttpClient.downloadDeploymentDownloadsFromAzure(null, denv.getServerRootUri(),
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
            PureHttpClient.uploadToAzure(backuped, denv.getServerRootUri(),
                    denv.getShortTimePassword(), 10, "azureblob");
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

// Generated by delombok at Tue Sep 26 10:37:39 CST 2023

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
            return downloadFromAzure(workingDir1.resolve(ss[1].trim()), baseUri, assetId,
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

// Generated by delombok at Tue Sep 26 10:37:39 CST 2023


public static class Util {
    public static Integer errorTolerance = 0;
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

    public static String printCopyFromAndTo(Path copyFrom, Path copyTo, String... extraMessages) {
        String message = String.format("Copying %s(%s) --> %s(%s)", copyFrom, copyFrom.getFileSystem().provider().getScheme(), copyTo, copyTo.getFileSystem().provider().getScheme());
        System.out.println(message);
        for (String extraMessage : extraMessages) {
            System.out.println(extraMessage);
        }
        return message;
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
        file = file == null ? Path.of("deployment.env.properties") : file;
        if (!Files.exists(file)) {
            return null;
        }
        Properties properties = new Properties();
        properties.load(Files.newInputStream(file));
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

// Generated by delombok at Tue Sep 26 10:37:39 CST 2023


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

// Generated by delombok at Tue Sep 26 10:37:39 CST 2023

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

// Generated by delombok at Tue Sep 26 10:37:39 CST 2023


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



public static class CopyTask {

	private Stream<CopyItem> items;
	private boolean copyAlways;

	public CopyTask(Stream<CopyItem> items, boolean copyAlways) {
		this.items = items;
		this.copyAlways = copyAlways;
	}

	public CopyTask(Path inpuPath, boolean copyAlways) throws IOException {
		this.items = InputFileParser.copyParser(inpuPath.toString()).parse();
		this.copyAlways = copyAlways;
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
		if (!copyAlways && Files.exists(Path.of(item.getCopyTo()))) {
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
}

// Generated by delombok at Tue Sep 26 10:37:39 CST 2023


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
