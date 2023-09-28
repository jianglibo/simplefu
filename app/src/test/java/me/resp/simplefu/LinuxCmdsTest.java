package me.resp.simplefu;

import java.io.IOException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import me.resp.simplefu.model.ProcessResult;

public class LinuxCmdsTest {

	@Test
	void testExec() throws IOException, InterruptedException {
		Assertions.assertThatThrownBy(() -> {
			LinuxCmds.exec("echo hello");
		}).hasMessageContaining("No such file or directory");

		ProcessResult pr = LinuxCmds.exec("echo", "hello");
		Assertions.assertThat(pr.getExitCode()).isEqualTo(0);
		Assertions.assertThat(pr.getLines()).containsExactly("hello");

		pr = LinuxCmds.exec("bash", "-c", "echo hello");
		Assertions.assertThat(pr.getExitCode()).isEqualTo(0);
		Assertions.assertThat(pr.getLines()).containsExactly("hello");

		pr = LinuxCmds.exec("bash", "-c", "echo hello && echo world");
		Assertions.assertThat(pr.getExitCode()).isEqualTo(0);
		Assertions.assertThat(pr.getLines()).containsExactly("hello", "world");

		pr = LinuxCmds.exec("bash", "-c", "echo hello || echo world");
		Assertions.assertThat(pr.getExitCode()).isEqualTo(0);
		Assertions.assertThat(pr.getLines()).containsExactly("hello");

		pr = LinuxCmds.execOneLine("echo hello ; echo world");
		Assertions.assertThat(pr.getExitCode()).isEqualTo(0);
		Assertions.assertThat(pr.getLines()).containsExactly("hello", "world");
	}
}
