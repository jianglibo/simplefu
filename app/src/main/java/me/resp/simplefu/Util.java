package me.resp.simplefu;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class Util {
    public static Integer errorTolerance = 0;

    public static FileSystem createZipFileSystem(Path zipFile, boolean create) throws IOException {
        // convert the filename to a URI
        final URI uri = URI.create("jar:file:" + zipFile.toUri().getPath());

        final Map<String, String> env = new HashMap<>();
        if (create) {
            env.put("create", create ? "true" : "false");
        }
        return FileSystems.newFileSystem(uri, env);
    }

    public static Path relativeFromRoot(Path maybeAbsolutePath) {
        if (maybeAbsolutePath.isAbsolute()) {
            return maybeAbsolutePath.getRoot().relativize(maybeAbsolutePath);
        }
        return maybeAbsolutePath;
    }

    public static String printCopyFromAndTo(Path copyFrom, Path copyTo, String... extraMessages) {
        String message = String.format("Copying %s(%s) --> %s(%s)", copyFrom,
                copyFrom.getFileSystem().provider().getScheme(), copyTo,
                copyTo.getFileSystem().provider().getScheme());
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
            e.printStackTrace();
            if (errorLevel >= errorTolerance)
                throw new RuntimeException(message, e);
            return fallback;
        }
    }

    public static void exceptionHandler(MaybeThrowSomethingNoReturn maybeThrowSomethingNoReturn, int errorLevel,
            String message) {
        try {
            maybeThrowSomethingNoReturn.call();
        } catch (Throwable e) {
            if (errorLevel >= errorTolerance)
                throw new RuntimeException(message, e);
            e.printStackTrace();
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
    public static Stream<CopyItem> walkCopyFrom(Path copyFromPath, String copyTo, Path zipFile, boolean exactly) {
        if (!Files.exists(copyFromPath)) {
            throw new RuntimeException("File not found: " + copyFromPath.toString());
        }
        if (Files.isDirectory(copyFromPath)) {
            return Util.exceptionHandler(() -> Files.walk(copyFromPath).map(p -> {
                String relativePath = copyFromPath.relativize(p).toString();
                if (relativePath.isBlank()) { // skip himself.
                    return null;
                }
                return CopyItem.builder()
                        .copyFrom(p.toString())
                        .copyTo(copyTo + "/" + relativePath)
                        .fromZipFile(zipFile)
                        .fromExactly(true)
                        .build();
            }).filter(Objects::nonNull), Stream.empty(), 1, "Failed to walk directory: " + copyFromPath.toString());
        } else {
            return Stream.of(CopyItem.builder()
                    .copyFrom(copyFromPath.toString())
                    .copyTo(copyTo)
                    .fromZipFile(zipFile)
                    .fromExactly(exactly)
                    .build());
        }
    }

}
