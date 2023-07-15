package me.resp.simplefu;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.stream.Stream;

import lombok.Getter;

public class ZipTask implements Closeable {
	@Getter
	private final ZipNameType zipNameType;
	@Getter
	private final Path zipfile;

	private FileSystem zipFileSystem;

	public ZipTask(Path zipfile) {
		this.zipfile = zipfile;
		this.zipNameType = ZipNameType.ABSOLUTE;
	}

	public ZipTask(Path zipfile, ZipNameType zipNameType) {
		this.zipNameType = zipNameType;
		this.zipfile = zipfile;
	}

	public ZipTask start(boolean readonly) throws IOException {
		this.zipFileSystem = ZipUtil.createZipFileSystem(zipfile, !readonly);
		return this;
	}

	private Path getZipPath(Path copyFrom, String copyTo) {
		if (copyTo == null) {
			switch (zipNameType) {
				case ABSOLUTE:
					return zipFileSystem.getPath(copyFrom.toAbsolutePath().normalize().toString());
				case FLATTEN:
					return zipFileSystem.getPath(copyFrom.getFileName().toString());
				default:
					throw new IllegalArgumentException("Unknown ZipNameType: " + zipNameType);
			}
		} else {
			return zipFileSystem.getPath(copyTo);
		}
	}

	public void push(Path copyFrom, String copyTo) throws IOException {
		Path to = getZipPath(copyFrom, copyTo);
		Path parent = to.getParent();
		if (parent != null && !Files.exists(parent)) {
			Files.createDirectories(parent);
		}
		ZipUtil.copyFile(copyFrom, to);
	}

	public void push(Path copyFrom) throws IOException {
		push(copyFrom, null);
	}

	public Optional<Path> findExactly(String path) throws IOException {
		Path p = zipFileSystem.getPath(path);
		return Files.exists(p) ? Optional.of(p) : Optional.empty();
	}

	public Optional<Path> findEndsWith(String path) throws IOException {
		return Files.walk(zipFileSystem.getPath("/"), Integer.MAX_VALUE, FileVisitOption.FOLLOW_LINKS)
				.filter(p -> p.toString().endsWith(path))
				.findFirst();
	}

	private void cp(Path zp, Path copyTo) {
		try {
			if (copyTo == null) {
				copyTo = Path.of(zp.toString());
			} else if (Files.isDirectory(copyTo)) {
				copyTo = copyTo.resolve(zp.getFileName().toString());
			}
			ZipUtil.copyFile(zp, copyTo);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void pullExactly(String pullFrom, Path copyTo) throws IOException {
		findExactly(pullFrom).ifPresent(zp -> cp(zp, copyTo));
	}

	public void pullEndsWith(String pullFrom, Path copyTo) throws IOException {
		findEndsWith(pullFrom).ifPresent(zp -> cp(zp, copyTo));
	}

	public Stream<Path> allEntryPath() throws IOException {
		return Files.walk(zipFileSystem.getPath("/"), Integer.MAX_VALUE);
	}

	@Override
	public void close() throws IOException {
		zipFileSystem.close();
	}

}
