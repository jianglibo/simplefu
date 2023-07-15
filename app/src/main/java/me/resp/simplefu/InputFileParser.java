package me.resp.simplefu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class InputFileParser {
	private static final Pattern ptn = Pattern.compile("\\s*([^#]*)#.*");

	private Path inputFilePath;

	public InputFileParser(String inputFilePath) {
		this.inputFilePath = Path.of(inputFilePath);
	}

	public InputFileParser(Path inputFilePath) {
		this.inputFilePath = inputFilePath;
	}

	public List<CopyItem> parse() throws IOException {
		try (Stream<String> stream = Files.lines(inputFilePath)) {
			return parse(stream);
		}
	}

	protected List<CopyItem> parse(Stream<String> stream) {
		return stream
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
				.map(line -> {
					String[] parts = line.split("->");
					if (parts.length != 2) {
						return null;
					}
					String copyFrom = parts[0].trim();
					String copyTo = parts[1].trim();
					if (copyFrom.isBlank() || copyTo.isBlank() || copyFrom.startsWith("!")) {
						return null;
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
						return CopyItem.builder()
								.copyFrom(entryName)
								.copyTo(copyTo)
								.fromZipFile(zipFile)
								.fromExactly(exactly)
								.build();
					} else {
						return CopyItem.builder()
								.copyFrom(copyFrom)
								.copyTo(copyTo)
								.build();
					}
				}).filter(Objects::nonNull).collect(java.util.stream.Collectors.toList());
	}

}
