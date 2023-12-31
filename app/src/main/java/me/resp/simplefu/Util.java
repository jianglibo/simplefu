package me.resp.simplefu;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import lombok.Getter;
import me.resp.simplefu.model.DeploymentEnv;

public class Util {
    public static Integer errorTolerance = 0;

    public static final String DEPLOYMENT_ENV_PROPERTIES = "deployment.env.properties";

    @Getter
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
        String message = String.format("%s %s(%s) --> %s(%s)", prefix, copyFrom,
                copyFrom.getFileSystem().provider().getScheme(), copyTo,
                copyTo.getFileSystem().provider().getScheme());
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

    public static <T> T exceptionHandler(MaybeThrowSomething<T> maybeThrowSomething, T fallback, int errorLevel,
            String message) {
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

    public static void exceptionHandler(MaybeThrowSomethingNoReturn maybeThrowSomethingNoReturn, int errorLevel,
            String message) {
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
    public static Stream<CopyItem> walkCopyFrom(Path copyFromPath, String copyTo, Path zipFileIfFromZip,
            boolean exactly) {
        if (!Files.exists(copyFromPath) && !ignoreMissingSource) {
            throw new RuntimeException("File not found: " + copyFromPath.toString());
        }
        if (Files.isDirectory(copyFromPath)) {
            return Util.exceptionHandler(() -> Files.walk(copyFromPath).map(p -> {
                // if (relativePath.isBlank()) { // skip himself.
                // return null;
                // }
                if (Files.isDirectory(p)) {
                    return null;
                } else {
                    String relativePath = copyFromPath.relativize(p).toString();
                    return CopyItem.builder()
                            .copyFrom(p.toString())
                            .copyTo(copyTo + "/" + relativePath)
                            .fromZipFile(zipFileIfFromZip)
                            .fromExactly(exactly)
                            .build();
                }
                // return CopyItem.builder()
                // .copyFrom(p.toString())
                // .copyTo(copyTo + "/" + relativePath)
                // .fromZipFile(zipFileIfFromZip)
                // .fromExactly(true)
                // .build();
            }).filter(Objects::nonNull), Stream.empty(), 1, "Failed to walk directory: " + copyFromPath.toString());
        } else {
            return Stream.of(CopyItem.builder()
                    .copyFrom(copyFromPath.toString())
                    .copyTo(copyTo)
                    .fromZipFile(zipFileIfFromZip)
                    .fromExactly(exactly)
                    .build());
        }
    }

    public static void deleteDirectory(String dirToDelete) {
        deleteDirectory(Paths.get(dirToDelete));
    }

    public static void deleteDirectory(Path dirToDelete) {
        try (Stream<Path> dirStream = Files.walk(dirToDelete)) {
            dirStream
                    .map(Path::toFile)
                    .sorted(Comparator.reverseOrder())
                    .forEach(File::delete);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Stream<CopyItem> walkBackupItem(Path toBackup) {
        if (!Files.exists(toBackup) && !ignoreMissingSource) {
            throw new RuntimeException("File not found: " + toBackup.toString());
        }
        if (Files.isDirectory(toBackup)) {
            return Util.exceptionHandler(() -> Files.walk(toBackup).map(p -> {
                String relativePath = toBackup.relativize(p).toString();
                if (relativePath.isBlank()) { // skip himself.
                    return null;
                }
                return CopyItem.builder()
                        .copyFrom("doesn't matter.")
                        .copyTo(p.toAbsolutePath().normalize().toString())
                        .fromZipFile(null)
                        .fromExactly(true)
                        .build();
            }).filter(Objects::nonNull), Stream.empty(), 1, "Failed to walk directory: " + toBackup.toString());
        } else {
            return Stream.of(CopyItem.builder()
                    .copyFrom("doesn't matter.")
                    .copyTo(toBackup.toAbsolutePath().normalize().toString())
                    .fromZipFile(null)
                    .fromExactly(true)
                    .build());
        }
    }

    public static <T> Stream<T> getStreamFromIterator(Iterator<T> iterator) {
        // Convert the iterator to Spliterator
        Spliterator<T> spliterator = Spliterators
                .spliteratorUnknownSize(iterator, 0);
        // Get a Sequential Stream from spliterator
        return StreamSupport.stream(spliterator, false);
    }

    public static List<Path> unzipTo(Path zipFile, Path toDir) throws IOException {
        FileSystem fs = createZipFileSystem(zipFile, false);
        toDir = toDir == null ? zipFile.getParent() : toDir;
        Path toDir1 = toDir;
        List<Path> paths = getStreamFromIterator(fs.getRootDirectories().iterator())
                .flatMap(root -> {
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
                })
                .filter(Objects::nonNull).collect(Collectors.toList());
        fs.close();
        return paths;
    }

    public static List<Path> zipAtSameDirectory(Path createdZip, Path directoryToZip) throws IOException {
        FileSystem fs = createZipFileSystem(createdZip, true);
        Path root = fs.getPath("/");
        List<Path> paths = Files.walk(directoryToZip).map(p -> {
            try {
                if (Files.isDirectory(p)) {
                    return null;
                }
                Path relativePath = directoryToZip.relativize(p);
                Path copyTo = root.resolve(relativePath.toString());
                return copyFile(p, copyTo);
                // Files.copy(p, copyTo);
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

}
