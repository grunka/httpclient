package se.grunka.httpclient;

class HttpClientException extends Exception {

	private final HttpResponse response;

	public HttpClientException(HttpResponse response) {
		this.response = response;
	}

	public HttpResponse getResponse() {
		return response;
	}
}
