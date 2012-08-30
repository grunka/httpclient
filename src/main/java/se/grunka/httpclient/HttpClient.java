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

import com.google.gson.Gson;

public class HttpClient {
	private final int connectTimeout;
	private final int readTimeout;
	private final Gson gson = new Gson();
	private static final int MAX_BUFFER_SIZE = 4096;

	public HttpClient(int connectTimeout, int readTimeout) {
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
	}

	public HttpResponse get(String path) {
		return get(path, "text/plain");
	}

	public HttpResponse get(String path, String accept) {
		HttpURLConnection connection;
		try {
			connection = openConnection(path, accept);
		} catch (HttpClientException e) {
			return e.getResponse();
		}
		return readResponse(connection);
	}

	public HttpResponse postJson(String path, Object value) {
		return postJson(path, value, "application/json");
	}

	public HttpResponse postJson(String path, Object value, String accept) {
		byte[] content;
		try {
			content = gson.toJson(value).getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new Error("UTF-8 not supported");
		}
		return postContent(path, "application/json", content, accept);
	}

	public HttpResponse post(String path, Parameters parameters) {
		return post(path, parameters, "text/plain");
	}

	public HttpResponse post(String path, Parameters parameters, String accept) {
		byte[] content;
		try {
			content = parameters.toString().getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new Error("UTF-8 not supported", e);
		}
		return postContent(path, "application/x-www-form-urlencoded", content, accept);
	}

	private HttpResponse postContent(String path, String contentType, byte[] content, String accept) {
		HttpURLConnection connection;
		try {
			connection = openConnection(path, accept);
		} catch (HttpClientException e) {
			return e.getResponse();
		}
		try {
			connection.setRequestMethod("POST");
		} catch (ProtocolException e) {
			throw new Error("POST method not supported", e);
		}
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", contentType);
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

	private HttpURLConnection openConnection(String path, String accept) throws HttpClientException {
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
		connection.setRequestProperty("User-Agent", "httpclient/1.0");
		connection.setRequestProperty("Accept", accept);
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
