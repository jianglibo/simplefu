package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class CopyTask {

	private List<CopyItem> items;
	private boolean copyAlways;

	public CopyTask(List<CopyItem> items, boolean copyAlways) {
		this.items = items;
		this.copyAlways = copyAlways;
	}

	public CopyTask(Path inpuPath, boolean copyAlways) throws IOException {
		this.items = new InputFileParser(inpuPath).parse();
		this.copyAlways = copyAlways;
	}

	public void start() throws IOException {
		for (CopyItem item : items) {
			copyOne(item);
		}
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
				try (ZipTask zipTask = new ZipTask(item.getFromZipFile());) {
					zipTask.start(true);
					zipTask.pullExactly(item.getCopyFrom(), Path.of(item.getCopyTo()));
					zipTask.close();
				}
			} else {
				try (ZipTask zipTask = new ZipTask(item.getFromZipFile());) {
					zipTask.start(true);
					zipTask.pullEndsWith(item.getCopyFrom(), Path.of(item.getCopyTo()));
					zipTask.close();
				}
			}
		} else {
			Path to = Path.of(item.getCopyTo());
			if (Files.isDirectory(to)) {
				to = to.resolve(Path.of(item.getCopyFrom()).getFileName());
			}
			ZipUtil.copyFile(Path.of(item.getCopyFrom()), to);
		}
	}
}
