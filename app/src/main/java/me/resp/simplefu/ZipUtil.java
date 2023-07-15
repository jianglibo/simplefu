package me.resp.simplefu;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ZipUtil {

    public void zipFile(Path writeTo, ZipNameType pt, Path... filesAndDirectories) {
        try (OutputStream fos = Files.newOutputStream(writeTo);
                ZipOutputStream zipOut = new ZipOutputStream(fos);) {
            for (Path file : filesAndDirectories) {
                if (Files.isDirectory(file)) {
                    addDirectory(zipOut, file);
                } else {
                    addFile(zipOut, file);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addFile(ZipOutputStream zipOut, Path copyFrom) throws IOException {
        try (InputStream is = Files.newInputStream(copyFrom);) {
            ZipEntry zipEntry = new ZipEntry(copyFrom.toAbsolutePath().normalize().toString());
            zipOut.putNextEntry(zipEntry);

            byte[] bytes = new byte[1024];
            int length;
            while ((length = is.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
        }
    }

    public void addDirectory(ZipOutputStream zipOut, Path copyFrom) throws IOException {
        Files.walk(copyFrom).filter(Files::isRegularFile).forEach(path -> {
            try {
                addFile(zipOut, path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    // private void appendToZip() throws IOException {
    // String file3 = "src/main/resources/zipTest/file3.txt";
    // Map<String, String> env = new HashMap<>();
    // env.put("create", "true");

    // Path path = Paths.get(Paths.get(file3).getParent() + "/compressed.zip");
    // URI uri = URI.create("jar:" + path.toUri());

    // try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
    // Path nf = fs.getPath("newFile3.txt");
    // Files.write(nf, Files.readAllBytes(Paths.get(file3)),
    // StandardOpenOption.CREATE);
    // }
    // }

    // private void createDirectoryIfNotExists(Path dir) throws IOException {
    // boolean exist = Files.exists(dir);
    // boolean isDirectory = Files.isDirectory(dir);
    // if (exist && !isDirectory) {
    // throw new IOException("Failed to create directory " + dir);
    // }
    // if (!exist) {
    // Files.createDirectories(dir);
    // }
    // }

    public static FileSystem createZipFileSystem(Path zipFile, boolean create) throws IOException {
        // convert the filename to a URI
        final URI uri = URI.create("jar:file:" + zipFile.toUri().getPath());

        final Map<String, String> env = new HashMap<>();
        if (create) {
            env.put("create", create ? "true" : "false");
        }
        return FileSystems.newFileSystem(uri, env);
    }

    public void copyOutFiles(Path zipFile, List<String[]> filesAndDirectories, ZipNameType unzipPathType)
            throws IOException {
        FileSystem zipfs = createZipFileSystem(zipFile, false);
        Files.walk(zipfs.getPath("/")).forEach(path -> {
            try {
                if (Files.isRegularFile(path)) {
                    Path dstPath = calPath(Path.of(""), path, unzipPathType);
                    copyFile(path, dstPath);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    protected static Path calPath(Path dst, Path newFile, ZipNameType unzipPathType) {
        switch (unzipPathType) {
            case FLATTEN:
                return dst.resolve(newFile.getFileName());
            default:
                throw new RuntimeException("UnzipPathType not supported: " + unzipPathType);
        }
    }

    // private Path unzip(ZipInputStream zis, Path dst, ZipNameType unzipPathType)
    // throws IOException {
    // final byte[] buffer = new byte[1024];
    // ZipEntry zipEntry = zis.getNextEntry();
    // while (zipEntry != null) {
    // final Path newFile = calPath(dst, Paths.get(zipEntry.getName()),
    // unzipPathType);
    // if (zipEntry.isDirectory()) {
    // createDirectoryIfNotExists(newFile);
    // } else {
    // createDirectoryIfNotExists(newFile.getParent());
    // try (OutputStream fos = Files.newOutputStream(dst);) {
    // int len;
    // while ((len = zis.read(buffer)) > 0) {
    // fos.write(buffer, 0, len);
    // }
    // }
    // }
    // zipEntry = zis.getNextEntry();
    // }
    // return dst;
    // }

    public static ZipEntry findEntry(Path zipFile, String entryName) throws IOException {
        return findEntry(new ZipInputStream(Files.newInputStream(zipFile)), entryName);
    }

    public static ZipEntry findEntry(ZipInputStream zis, String entryName) throws IOException {
        ZipEntry zipEntry = zis.getNextEntry();
        String normalizedEntryName = entryName.replaceAll("\\\\", "/");
        while (zipEntry != null) {
            String zentryName = zipEntry.getName();
            log.info("zentryName: {}", zentryName);
            if (zentryName.equals(normalizedEntryName)) {
                return zipEntry;
            }
            zipEntry = zis.getNextEntry();
        }
        return null;
    }

    public static Path relativeFromRoot(Path maybeAbsolutePath) {
        if (maybeAbsolutePath.isAbsolute()) {
            return maybeAbsolutePath.getRoot().relativize(maybeAbsolutePath);
        }
        return maybeAbsolutePath;
    }

    public static String printCopyFromAndTo(Path copyFrom, Path copyTo) {
        String message = String.format("Copying %s(%s) --> %s(%s)", copyFrom,
                copyFrom.getFileSystem().provider().getScheme(), copyTo,
                copyTo.getFileSystem().provider().getScheme());
        System.out.println(message);
        return message;
    }

    public static Path copyFile(Path copyFrom, Path copyTo) throws IOException {
        if (Files.isDirectory(copyFrom)) {
            throw new IOException(printCopyFromAndTo(copyFrom, copyTo));
        }
        printCopyFromAndTo(copyFrom, copyTo);
        return Files.copy(copyFrom, copyTo, StandardCopyOption.REPLACE_EXISTING);
    }

}
