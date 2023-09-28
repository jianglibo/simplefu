package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import lombok.Getter;

public class CopyTask {

	private Stream<CopyItem> items;
	private boolean override;
	@Getter
	private int skipCount = 0;

	public CopyTask(Stream<CopyItem> items, boolean override) {
		this.items = items;
		this.override = override;
	}

	public CopyTask(Path inpuPath, boolean copyAlways) throws IOException {
		this.items = InputFileParser.copyParser(inpuPath.toString()).parse();
		this.override = copyAlways;
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
		if (!override && Files.exists(Path.of(item.getCopyTo()))) {
			Util.printSkipFromAndTo(Path.of(item.getCopyFrom()), Path.of(item.getCopyTo()));
			skipCount++;
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
