package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BackupRestoreTask {

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
		this.items = new InputFileParser(inpuPath).parse();
		this.backupFile = backFile;
	}

	public Path backup() throws IOException {
		if (backupFile == null) {
			backupFile = Files.createTempDirectory("backup").resolve("backup.zip");
		}
		ZipTask zipTask = ZipTask.get(backupFile, ZipNameType.ABSOLUTE, false);
		items.forEach(item -> {
			Path copyToPath = Path.of(item.getCopyTo());
			if (Files.isDirectory(copyToPath)) {
				copyToPath = copyToPath.resolve(Path.of(item.getCopyFrom()).getFileName());
			}
			try {
				if (Files.notExists(copyToPath)) {
					log.warn("File to backup not exists: {}", copyToPath);
				} else {
					zipTask.push(copyToPath);
				}
			} catch (IOException e) {
				System.out.println("zipTask push error: " + copyToPath);
				throw new RuntimeException(e);
			}
		});
		return backupFile;
	}

	public long restore() throws IOException {
		if (backupFile == null) {
			return -1;
		}
		ZipTask zipTask = ZipTask.get(backupFile, ZipNameType.ABSOLUTE, true);
		return zipTask.allEntryPath().filter(Files::isRegularFile).map(p -> {
			try {
				String dstStr = p.toString();
				if (dstStr.startsWith("/") && dstStr.matches("/[a-zA-Z]:.*")) {
					dstStr = dstStr.substring(1);
				}
				Path dst = Path.of(dstStr);
				Path parent = dst.getParent();
				if (parent != null && Files.notExists(parent)) {
					Files.createDirectories(parent);
				}
				return Util.copyFile(p, dst);
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}).filter(Objects::nonNull).count();
		// } catch (IOException e) {
		// e.printStackTrace();
		// return -1;
		// }
	}
}
