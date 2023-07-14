package me.resp.simplefu;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtil {

    public void zipFile(Path writeTo, Path... filesAndDirectories) {
        try (OutputStream fos = Files.newOutputStream(writeTo);
                ZipOutputStream zipOut = new ZipOutputStream(fos);) {

            for (Path file : filesAndDirectories) {
                if (Files.isDirectory(file)) {
                    zipDirectory(zipOut, file);
                } else {
                    addOneFile(zipOut, file);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addOneFile(ZipOutputStream zipOut, Path copyFrom) throws IOException {
        InputStream is = Files.newInputStream(copyFrom);
        ZipEntry zipEntry = new ZipEntry(copyFrom.toAbsolutePath().normalize().toString());
        zipOut.putNextEntry(zipEntry);

        byte[] bytes = new byte[1024];
        int length;
        while ((length = is.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }
    }

    public void zipDirectory(ZipOutputStream zipOut, Path copyFrom) throws IOException {
        Files.walk(copyFrom).filter(Files::isRegularFile).forEach(path -> {
            try {
                addOneFile(zipOut, path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void appendToZip() throws IOException {
        String file3 = "src/main/resources/zipTest/file3.txt";
        Map<String, String> env = new HashMap<>();
        env.put("create", "true");

        Path path = Paths.get(Paths.get(file3).getParent() + "/compressed.zip");
        URI uri = URI.create("jar:" + path.toUri());

        try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
            Path nf = fs.getPath("newFile3.txt");
            Files.write(nf, Files.readAllBytes(Paths.get(file3)), StandardOpenOption.CREATE);
        }
    }

    private Path unzip(ZipInputStream zis, Path dst) throws IOException {

        final String fileZip = "src/main/resources/unzipTest/compressed.zip";
        final File destDir = new File("src/main/resources/unzipTest");
        final byte[] buffer = new byte[1024];
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
            final File newFile = Path.of(zipEntry.getName()).toFile();
            if (zipEntry.isDirectory()) {
                if (!newFile.isDirectory() && !newFile.mkdirs()) {
                    throw new IOException("Failed to create directory " + newFile);
                }
            } else {
                File parent = newFile.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Failed to create directory " + parent);
                }

                final FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }
            zipEntry = zis.getNextEntry();
        }
        return dst;
    }

}
