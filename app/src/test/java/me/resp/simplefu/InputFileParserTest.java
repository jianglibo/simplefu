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
		List<CopyItem> items = parser.parse(Stream.of(
				"!a.txt -> !b.txt",
				" ",
				" ## ",
				" # hello world",
				" a->b#ccc"));
		Assertions.assertThat(items).hasSize(1);
		CopyItem ci = items.get(0);
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("copyFrom", "a");
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("copyTo", "b");
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("fromZipFile", null);
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("fromExactly", false);

		items = parser.parse(Stream.of(
				"a.zip!a->b#ccc"));
		Assertions.assertThat(items).hasSize(1);
		ci = items.get(0);
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("copyFrom", "a");
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("copyTo", "b");
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("fromZipFile", Path.of("a.zip"));
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("fromExactly", true);

		items = parser.parse(Stream.of(
				"a.zip!~a->b#ccc"));
		Assertions.assertThat(items).hasSize(1);
		ci = items.get(0);
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("copyFrom", "a");
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("copyTo", "b");
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("fromZipFile", Path.of("a.zip"));
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("fromExactly", false);
	}
}
