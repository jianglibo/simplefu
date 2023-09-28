package me.resp.simplefu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.resp.simplefu.model.ProcessResult;

public class LinuxCmds {

	public static ProcessResult execOneLine(String cmd) throws IOException, InterruptedException {
		return exec(Path.of("."), "bash", "-c", cmd);
	}

	public static ProcessResult exec(String... cmds) throws IOException, InterruptedException {
		return exec(Path.of("."), cmds);
	}

	public static ProcessResult exec(Path workingDir, String... cmds) throws IOException, InterruptedException {
		workingDir = workingDir == null ? Path.of(".") : workingDir;
		ProcessBuilder pb = new ProcessBuilder(cmds);
		Map<String, String> env = pb.environment();
		pb.directory(workingDir.toFile());
		pb.redirectErrorStream(true);
		Process p = pb.start();
		int exitCode = p.waitFor();
		ProcessResult processResult = new ProcessResult();
		processResult.setExitCode(exitCode);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
			List<String> lines = new ArrayList<>();
			String line;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
			processResult.setLines(lines);
		}
		return processResult;
	}
}
