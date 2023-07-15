package me.resp.simplefu;

import java.nio.file.Path;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
public class CopyItem {
	private String copyFrom;
	private String copyTo;

	private Path fromZipFile;
	private boolean fromExactly;
}
