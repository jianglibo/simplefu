package me.resp.simplefu;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * versionlize the files in the destination.
 */
public class VersionTask {

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
