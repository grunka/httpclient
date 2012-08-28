package se.grunka.httpclient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;

public class HttpResponse {
	private final int code;
	private final String content;
	private final Exception cause;


	public HttpResponse(int code, String content) {
		this.code = code;
		this.content = content;
		this.cause = null;
	}

	public HttpResponse(Exception cause) {
		this.code = -1;
		this.content = null;
		this.cause = cause;
	}

	public int getCode() {
		return code;
	}

	public String getContent() {
		return content;
	}

	public boolean isOk() {
		return code == 200;
	}

	public boolean hasError() {
		return cause != null;
	}

	public boolean malformedUrl() {
		return cause instanceof MalformedURLException;
	}

	public boolean readError() {
		return cause instanceof IOException && !malformedUrl() && !couldNotConnect();
	}

	public boolean couldNotConnect() {
		return cause instanceof ConnectException;
	}

	public Exception getCause() {
		return cause;
	}

}
