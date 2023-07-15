package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class BackupRestoreTask {

	private List<CopyItem> items;

	private Path backupFile;

	public BackupRestoreTask(List<CopyItem> items) {
		this.items = items;
	}

	public BackupRestoreTask(List<CopyItem> items, Path backupFile) {
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
		try (ZipTask zipTask = new ZipTask(backupFile, ZipNameType.ABSOLUTE);) {
			zipTask.start(false);
			for (CopyItem item : items) {
				zipTask.push(Path.of(item.getCopyTo()));
			}
		}
		return backupFile;
	}

	public long restore() {
		if (backupFile == null) {
			return -1;
		}
		try (ZipTask zipTask = new ZipTask(backupFile, ZipNameType.ABSOLUTE);) {
			zipTask.start(true);
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
					return ZipUtil.copyFile(p, dst);
				} catch (IOException e) {
					e.printStackTrace();
					return null;
				}
			}).filter(Objects::nonNull).count();
		} catch (IOException e) {
			e.printStackTrace();
			return -1;
		}
	}
}
