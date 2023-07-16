package me.resp.simplefu.exceptions;

import lombok.Getter;

public abstract class AbstractRtException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private Integer errorLevel;

	@Getter
	private final SubcommandNames subcommandName;

	@Getter
	private final ErrorReason reason;

	public AbstractRtException(String message, SubcommandNames subcommandName, ErrorReason reason) {
		super(message);
		this.subcommandName = subcommandName;
		this.reason = reason;
	}

	public AbstractRtException(String message, SubcommandNames subcommandName, ErrorReason reason, Integer errorLevel) {
		super(message);
		this.errorLevel = errorLevel;
		this.subcommandName = subcommandName;
		this.reason = reason;
	}

	public Integer getErrorLevel() {
		if (errorLevel != null) {
			return errorLevel;
		} else {
			return calculateLevel();
		}
	}

	private Integer calculateLevel() {
		switch (subcommandName) {
			case copy:
				return calculateCopy();
			case backup:
				return calulateBackup();
			case restore:
				return calulateRestore();
			default:
				throw new FatalException("unknown subcommand " + subcommandName);
		}
	}

	private Integer calulateRestore() {
		return 1;
	}

	private Integer calulateBackup() {
		return 1;
	}

	private Integer calculateCopy() {
		return 1;
	}

	public static enum ErrorReason {
		SOURCE_MISSING, COPY_ITEM_FROM_IS_DIRECTORY, OTHER
	}

	public static enum SubcommandNames {
		copy, backup, restore, __placeholder
	}

}
