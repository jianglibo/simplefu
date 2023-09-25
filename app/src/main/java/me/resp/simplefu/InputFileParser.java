package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * when copying the source file maybe from a zip file, when backuping the file
 * to backup will not come back to the origin zip file,
 * so the copyFrom property of the Copyitem is unrelated.
 */
public class InputFileParser {
	private static final Pattern ptn = Pattern.compile("\\s*([^#]*)#.*");

	private Path inputFilePath;

	private boolean copying;

	private InputFileParser() {
	}

	public static InputFileParser copyParser(String inputFilePath) {
		InputFileParser inputFileParser = new InputFileParser();
		inputFileParser.copying = true;
		inputFileParser.inputFilePath = Path.of(inputFilePath);
		return inputFileParser;
	}

	public static InputFileParser restoreParser(String inputFilePath) {
		InputFileParser inputFileParser = new InputFileParser();
		inputFileParser.copying = false;
		inputFileParser.inputFilePath = Path.of(inputFilePath);
		return inputFileParser;
	}

	public Stream<CopyItem> parse() throws IOException {
		List<String> lines = Files.readAllLines(inputFilePath);
		return parse(lines);
	}

	protected Stream<CopyItem> parse(List<String> lines) {
		return lines.stream()
				.map(line -> {
					Matcher matcher = ptn.matcher(line);
					if (matcher.matches()) {
						return matcher.group(1);
					} else {
						return line;
					}
				})
				.map(line -> line.trim())
				.filter(line -> !line.startsWith("#"))
				.filter(line -> !line.isBlank())
				.flatMap(line -> {
					String[] parts = line.split("->");
					if (parts.length != 2) {
						return null;
					}
					String copyFrom = parts[0].trim();
					String copyTo = parts[1].trim();
					if (copyFrom.isBlank() || copyTo.isBlank() || copyFrom.startsWith("!")) {
						return null;
					}

					if (!copying) {
						// it's a backup, so copyfrom is actually the copyto field in the filelistfile.
						// the line in filelist file a.txt -> ../notingit/a.txt, we just care about the
						// ../notingit/a.txt
						return Util.walkBackupItem(Path.of(copyTo));
					}
					Path zipFile = null;
					String entryName = null;
					boolean exactly = false;
					boolean inZip = false;
					String[] splittedZipItem = copyFrom.split("!~", 2);
					if (splittedZipItem.length == 2) {
						zipFile = Path.of(splittedZipItem[0]);
						entryName = splittedZipItem[1];
						exactly = false;
						inZip = true;
					} else {
						splittedZipItem = copyFrom.split("!", 2);
						if (splittedZipItem.length == 2) {
							zipFile = Path.of(splittedZipItem[0]);
							entryName = splittedZipItem[1];
							exactly = true;
							inZip = true;
						}
					}
					if (inZip) {
						boolean zipExists = Files.exists(zipFile) && Files.isReadable(zipFile);
						if (!zipExists && !Util.isIgnoreMissingSource()) {
							throw new RuntimeException("Zip file not found: " + zipFile);
						}
						if (zipExists) {
							ZipTask zipTask = ZipTask.get(zipFile, ZipNameType.ABSOLUTE, true);
							return Util.walkCopyFrom(zipTask.getZipFileSystem().getPath(entryName), copyTo, zipFile,
									exactly);
						} else {
							return null;
						}
					} else {
						return Util.walkCopyFrom(Path.of(copyFrom), copyTo, null, true);
					}
				}).filter(Objects::nonNull);
	}

}
