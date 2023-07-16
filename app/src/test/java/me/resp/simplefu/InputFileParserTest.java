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
		Stream<CopyItem> items = parser.parse(List.of(
				"!a.txt -> !b.txt",
				" ",
				" ## ",
				" # hello world",
				" a->b#ccc"));
		Assertions.assertThat(items).hasSize(1);
		CopyItem ci = items.findFirst().get();
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("copyFrom", "a");
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("copyTo", "b");
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("fromZipFile", null);
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("fromExactly", false);

		Stream<CopyItem> items1 = parser.parse(List.of(
				"a.zip!a->b#ccc"));
		Assertions.assertThat(items1).hasSize(1);
		ci = items1.findFirst().get();
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("copyFrom", "a");
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("copyTo", "b");
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("fromZipFile", Path.of("a.zip"));
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("fromExactly", true);

		Stream<CopyItem> items2 = parser.parse(List.of(
				"a.zip!~a->b#ccc"));
		Assertions.assertThat(items2).hasSize(1);
		ci = items2.findFirst().get();
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("copyFrom", "a");
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("copyTo", "b");
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("fromZipFile", Path.of("a.zip"));
		Assertions.assertThat(ci).hasFieldOrPropertyWithValue("fromExactly", false);
	}
}
