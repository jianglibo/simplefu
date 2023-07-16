package me.resp.simplefu.exceptions;

public class CopySourceMissingException extends AbstractRtException {

	public CopySourceMissingException(String message, SubcommandNames subcommandName, Integer errorLevel) {
		super(message, subcommandName, ErrorReason.SOURCE_MISSING, errorLevel);
	}
	
}
