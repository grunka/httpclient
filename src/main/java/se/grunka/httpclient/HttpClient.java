package se.grunka.httpclient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

public class HttpClient {
	private final int connectTimeout;
	private final int readTimeout;
	private static final int MAX_BUFFER_SIZE = 4096;

	public HttpClient(int connectTimeout, int readTimeout) {
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
	}

	public HttpResponse get(String path) {
		HttpURLConnection connection;
		try {
			connection = openConnection(path);
		} catch (HttpClientException e) {
			return e.getResponse();
		}
		return readResponse(connection);
	}

	public HttpResponse post(String path, Map<String, String> parameters) {
		Parameters convertedParameters = new Parameters();
		for (Map.Entry<String, String> parameterEntry : parameters.entrySet()) {
			convertedParameters.add(parameterEntry.getKey(), parameterEntry.getValue());
		}
		return post(path, convertedParameters);
	}

	public HttpResponse post(String path, Parameters parameters) {
		byte[] content;
		try {
			content = parameters.toString().getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new Error("UTF-8 not supported", e);
		}
		HttpURLConnection connection;
		try {
			connection = openConnection(path);
		} catch (HttpClientException e) {
			return e.getResponse();
		}
		try {
			connection.setRequestMethod("POST");
		} catch (ProtocolException e) {
			throw new Error("POST method not supported", e);
		}
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		connection.setRequestProperty("Content-Length", String.valueOf(content.length));
		try {
			OutputStream outputStream = connection.getOutputStream();
			try {
				outputStream.write(content);
			} finally {
				outputStream.close();
			}
			return readResponse(connection);
		} catch (ConnectException e) {
			return new HttpResponse(e);
		} catch (IOException e) {
			return readResponse(connection);
		}
	}

	private HttpResponse readResponse(HttpURLConnection connection) {
		try {
			InputStream inputStream = connection.getInputStream();
			try {
				return new HttpResponse(200, readAll(inputStream));
			} finally {
				inputStream.close();
			}
		} catch (ConnectException e) {
			return new HttpResponse(e);
		} catch (IOException e) {
			try {
				int responseCode = connection.getResponseCode();
				InputStream errorStream = connection.getErrorStream();
				try {
					return new HttpResponse(responseCode, readAll(errorStream));
				} finally {
					errorStream.close();
				}
			} catch (IOException errorException) {
				return new HttpResponse(errorException);
			}
		}
	}

	private String readAll(InputStream inputStream) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		byte[] buffer = new byte[MAX_BUFFER_SIZE];
		int bytes;
		while ((bytes = inputStream.read(buffer)) != -1) {
			outputStream.write(buffer, 0, bytes);
		}
		try {
			return outputStream.toString("UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new Error("UTF-8 not supported", e);
		}
	}

	private HttpURLConnection openConnection(String path) throws HttpClientException {
		URL url;
		try {
			url = new URL(path);
		} catch (MalformedURLException e) {
			throw new HttpClientException(new HttpResponse(e));
		}
		HttpURLConnection connection;
		try {
			connection = openConnection(url);
		} catch (MalformedURLException e) {
			throw new HttpClientException(new HttpResponse(e));
		}
		connection.setRequestProperty("Connection", "Keep-Alive");
		connection.setUseCaches(false);
		connection.setInstanceFollowRedirects(true);
		connection.setConnectTimeout(connectTimeout);
		connection.setReadTimeout(readTimeout);
		return connection;
	}

	private HttpURLConnection openConnection(URL url) throws MalformedURLException, HttpClientException {
		URLConnection connection;
		try {
			connection = url.openConnection();
		} catch (IOException e) {
			throw new HttpClientException(new HttpResponse(e));
		}
		if (!(connection instanceof HttpURLConnection)) {
			throw new MalformedURLException("Expected HttpURLConnection, got " + connection.getClass().getName());
		}
		return (HttpURLConnection) connection;
	}

}
