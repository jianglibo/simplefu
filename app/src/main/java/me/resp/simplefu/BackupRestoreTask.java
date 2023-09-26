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
