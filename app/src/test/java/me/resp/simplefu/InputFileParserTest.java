package me.resp.simplefu;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class InputFileParserTest {

	@Test
	void testParse() {
		InputFileParser parser = new InputFileParser("");
		Util.ignoreMissingSource = true;
		List<CopyItem> items = parser.parse(List.of(
				"!a.txt -> !b.txt",
				" ",
				" ## ",
				" # hello world",
				" a->b#ccc"))
				.collect(java.util.stream.Collectors.toList());
		CopyItem ci = items.get(0);
		Assertions.assertThat(items).hasSize(1);
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("copyFrom", "a");
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("copyTo", "b");
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("fromZipFile", null);
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("fromExactly", true);

		List<CopyItem> items1 = parser.parse(List.of(
				"fixtures/a.zip!b.txt->b#ccc"))
				.collect(java.util.stream.Collectors.toList());
		ci = items1.get(0);
		Assertions.assertThat(items1).hasSize(1);
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("copyFrom", "b.txt");
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("copyTo", "b");
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("fromZipFile", Path.of("fixtures", "a.zip"));
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("fromExactly", true);

		List<CopyItem> items2 = parser.parse(List.of(
				"fixtures/b.zip!~a.txt->b#ccc"))
				.collect(java.util.stream.Collectors.toList());
		Assertions.assertThat(items2).hasSize(1);
		ci = items2.get(0);
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("copyFrom", "a.txt");
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("copyTo", "b");
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("fromZipFile", Path.of("fixtures", "b.zip"));
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("fromExactly", false);
	}
}
