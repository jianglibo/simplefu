package me.resp.simplefu.exceptions;

public class FatalException extends AbstractRtException {

	public FatalException(String message) {
		super(message, SubcommandNames.__placeholder, ErrorReason.OTHER, Integer.MAX_VALUE);
	}

}
