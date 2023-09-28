package me.resp.simplefu.model;

import java.util.List;

import lombok.Data;

@Data
public class ProcessResult {
	private int exitCode;	
	private List<String> lines;
}
