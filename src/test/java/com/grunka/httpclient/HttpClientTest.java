package com.grunka.httpclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(HttpClient.class)
public class HttpClientTest {

	private HttpClient httpClient;
	private String requestedUrl;
	private URL url;
	private String response;
	private String request;
	private String errorResponse;
	private URLConnection connection;
	private boolean errorResponseStreamClosed;
	private boolean responseStreamClosed;
	private boolean requestStreamClosed;

	@Before
	public void before() throws Exception {
		requestedUrl = null;
		url = mock(URL.class);
		connection = mock(HttpURLConnection.class);
		when(((HttpURLConnection) connection).getResponseCode()).thenReturn(200);
		response = "";
		responseStreamClosed = false;
		when(connection.getInputStream()).thenReturn(new InputStream() {
			private int position = 0;
			@Override
			public int read() throws IOException {
				if (position < response.length()) {
					return response.charAt(position++);
				} else {
					return -1;
				}
			}

			@Override
			public void close() throws IOException {
				responseStreamClosed = true;
			}
		});
		errorResponse = "";
		errorResponseStreamClosed = false;
		when(((HttpURLConnection) connection).getErrorStream()).thenReturn(new InputStream() {
			private int position = 0;

			@Override
			public int read() throws IOException {
				if (position < errorResponse.length()) {
					return errorResponse.charAt(position++);
				} else {
					return -1;
				}
			}

			@Override
			public void close() throws IOException {
				errorResponseStreamClosed = true;
			}
		});
		request = "";
		requestStreamClosed = false;
		when(connection.getOutputStream()).thenReturn(new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				request += Character.toString((char) b);
			}

			@Override
			public void close() throws IOException {
				requestStreamClosed = true;
			}
		});
		when(url.openConnection()).thenReturn(connection);
		whenNew(URL.class).withArguments(anyString()).thenAnswer(new Answer<URL>() {
			@Override
			public URL answer(InvocationOnMock invocation) throws Throwable {
				requestedUrl = (String) invocation.getArguments()[0];
				return url;
			}
		});
		httpClient = new HttpClient(1234, 5678);
	}

	private void verifyBasicConnectionProperties(String accept) {
		verify(connection).setRequestProperty("Connection", "Keep-Alive");
		verify(connection).setRequestProperty("User-Agent", "httpclient/1.0");
		verify(connection).setRequestProperty("Accept", accept);
		verify(connection).setConnectTimeout(1234);
		verify(connection).setReadTimeout(5678);
		assertTrue(responseStreamClosed);
	}

	private void verifyPostConnectionProperties(String contentType, int length) throws ProtocolException {
		verify((HttpURLConnection) connection).setRequestMethod("POST");
		verify(connection).setRequestProperty("Content-Type", contentType);
		verify(connection).setRequestProperty("Content-Length", String.valueOf(length));
		assertTrue(requestStreamClosed);
	}

	@Test
	public void shouldSetBasicConnectionPropertiesForGet() throws Exception {
		httpClient.get("http://example.com/", "what I accept");
		verifyBasicConnectionProperties("what I accept");
	}

	@Test
	public void shouldSetBasicConnectionPropertiesForGetWithDefaultAccept() throws Exception {
		httpClient.get("http://example.com/");
		verifyBasicConnectionProperties("text/plain");
	}

	@Test
	public void shouldSetBasicConnectionPropertiesForPost() throws Exception {
		Parameters content = new Parameters().add("hello", "world");
		httpClient.post("http://example.com/", content, "what I accept");
		verifyBasicConnectionProperties("what I accept");
		verifyPostConnectionProperties("application/x-www-form-urlencoded", content.toString().length());
	}

	@Test
	public void shouldSetBasicConnectionPropertiesForPostWithDefaultAccept() throws Exception {
		Parameters content = new Parameters().add("hello", "world");
		httpClient.post("http://example.com/", content);
		verifyBasicConnectionProperties("text/plain");
		verifyPostConnectionProperties("application/x-www-form-urlencoded", content.toString().length());
	}

	@Test
	public void shouldSetBasicConnectionPropertiesForPostJson() throws Exception {
		String content = "hello world";
		httpClient.postJson("http://example.com/", content, "what I accept");
		verifyBasicConnectionProperties("what I accept");
		verifyPostConnectionProperties("application/json", new Gson().toJson(content).length());
	}

	@Test
	public void shouldSetBasicConnectionPropertiesForPostJsonWithDefaultAccept() throws Exception {
		String content = "hello world";
		httpClient.postJson("http://example.com/", content);
		verifyBasicConnectionProperties("application/json");
		verifyPostConnectionProperties("application/json", new Gson().toJson(content).length());
	}

	@Test
	public void shouldHandleMalformedUrl() throws Exception {
		whenNew(URL.class).withArguments(anyString()).thenThrow(new MalformedURLException("that is no good"));
		HttpResponse result = httpClient.get("a really bad url");
		assertTrue(result.hasError());
		assertTrue(result.malformedUrl());
	}

	@Test
	public void shouldHandleMalformedUrlForPost() throws Exception {
		whenNew(URL.class).withArguments(anyString()).thenThrow(new MalformedURLException("that is no good"));
		HttpResponse result = httpClient.post("a really bad url", new Parameters());
		assertTrue(result.hasError());
		assertTrue(result.malformedUrl());
	}

	@Test
	public void shouldHandleProblemsOpeningConnection() throws Exception {
		when(url.openConnection()).thenThrow(new IOException("this is really rare I guess but should still be handled"));
		HttpResponse result = httpClient.get("http://example.com/");
		assertTrue(result.hasError());
		assertTrue(result.otherError());
	}

	@Test
	public void shouldHandleProblemsOpeningConnectionForPost() throws Exception {
		when(url.openConnection()).thenThrow(new IOException("this is really rare I guess but should still be handled"));
		HttpResponse result = httpClient.post("http://example.com/", new Parameters());
		assertTrue(result.hasError());
		assertTrue(result.otherError());
	}

	@Test
	public void shouldFailNonHttpUrlsGracefully() throws Exception {
		connection = mock(JarURLConnection.class);
		when(url.openConnection()).thenReturn(connection);
		HttpResponse result = httpClient.get("jar://some_file.jar");
		assertTrue(result.hasError());
		assertTrue(result.malformedUrl());
	}

	@Test
	public void shouldHandleConnectFailureOnGets() throws Exception {
		when(connection.getInputStream()).thenThrow(new ConnectException("could not connect"));
		HttpResponse result = httpClient.get("http://example.com/");
		assertTrue(result.hasError());
		assertTrue(result.couldNotConnect());
	}

	@Test
	public void shouldHandleConnectTimeoutOnGets() throws Exception {
		when(connection.getInputStream()).thenThrow(new SocketTimeoutException("timed out"));
		HttpResponse result = httpClient.get("http://example.com/");
		assertTrue(result.hasError());
		assertTrue(result.timedOut());
	}

	@Test
	public void shouldHandleReadTimeoutOnGets() throws Exception {
		InputStream inputStream = mockTimeoutInputStream();
		when(connection.getInputStream()).thenReturn(inputStream);
		HttpResponse result = httpClient.get("http://example.com/");
		assertTrue(result.hasError());
		assertTrue(result.timedOut());
		verify(inputStream).close();
	}

	private InputStream mockTimeoutInputStream() throws IOException {
		InputStream inputStream = mock(InputStream.class);
		when(inputStream.read()).thenThrow(new SocketTimeoutException());
		when(inputStream.read(any(byte[].class))).thenThrow(new SocketTimeoutException());
		when(inputStream.read(any(byte[].class), anyInt(), anyInt())).thenThrow(new SocketTimeoutException());
		return inputStream;
	}

	private InputStream mockExceptionInputStream() throws IOException {
		InputStream inputStream = mock(InputStream.class);
		when(inputStream.read()).thenThrow(new IOException());
		when(inputStream.read(any(byte[].class))).thenThrow(new IOException());
		when(inputStream.read(any(byte[].class), anyInt(), anyInt())).thenThrow(new IOException());
		return inputStream;
	}

	@Test
	public void shouldReadNonOkResponse() throws Exception {
		errorResponse = "error message";
		when(connection.getInputStream()).thenThrow(new IOException("uh oh that request was bad"));
		when(((HttpURLConnection) connection).getResponseCode()).thenReturn(123);
		HttpResponse result = httpClient.get("http://example.com/");
		assertEquals(123, result.getCode());
		assertEquals("error message", result.getContent());
		assertFalse(result.hasError());
		assertTrue(errorResponseStreamClosed);
	}

	@Test
	public void shouldHandleErrorOnReadNonOkResponse() throws Exception {
		errorResponse = "error message";
		when(connection.getInputStream()).thenThrow(new IOException("uh oh that request was bad"));
		InputStream errorStream = mockExceptionInputStream();
		when(((HttpURLConnection) connection).getErrorStream()).thenReturn(errorStream);
		HttpResponse result = httpClient.get("http://example.com/");
		assertTrue(result.hasError());
		assertTrue(result.otherError());
		verify(errorStream).close();
	}

	@Test
	public void shouldHandleFailureToConnectOnPost() throws Exception {
		when(connection.getOutputStream()).thenThrow(new ConnectException("could not connect to server"));
		when(connection.getInputStream()).thenThrow(new Error("should not even try to get input stream"));
		HttpResponse result = httpClient.post("http://example.com", new Parameters());
		assertTrue(result.hasError());
		assertTrue(result.couldNotConnect());
	}

	@Test
	public void shouldHandleConnectTimeoutOnPost() throws Exception {
		when(connection.getOutputStream()).thenThrow(new SocketTimeoutException("could not connect to server"));
		when(connection.getInputStream()).thenThrow(new Error("should not even try to get input stream"));
		HttpResponse result = httpClient.post("http://example.com", new Parameters());
		assertTrue(result.hasError());
		assertTrue(result.timedOut());
	}

	@Test
	public void shouldReadErrorResponseOnErrorWhilePosting() throws Exception {
		errorResponse = "error message";
		when(connection.getOutputStream()).thenThrow(new IOException("not allowed to post"));
		when(connection.getInputStream()).thenThrow(new IOException("not OK response"));
		Mockito.when(((HttpURLConnection) connection).getResponseCode()).thenReturn(400);
		HttpResponse result = httpClient.post("http://example.com", new Parameters());
		assertFalse(result.isOk());
		assertFalse(result.hasError());
		assertEquals(400, result.getCode());
		assertEquals("error message", result.getContent());
		assertTrue(errorResponseStreamClosed);
	}

	@Test
	public void shouldGet() throws Exception {
		response = "get response";
		HttpResponse result = httpClient.get("http://localhost:7890/some/path");
		assertEquals("http://localhost:7890/some/path", requestedUrl);
		assertEquals("", request);
		assertEquals("get response", result.getContent());
		assertTrue(responseStreamClosed);
	}

	@Test
	public void shouldPost() throws Exception {
		response = "post response";
		HttpResponse result = httpClient.post("http://localhost:7890/some/path", new Parameters().add("hello", "world").add("one", "1"));
		assertEquals("http://localhost:7890/some/path", requestedUrl);
		assertEquals("hello=world&one=1", request);
		assertEquals(200, result.getCode());
		assertEquals("post response", result.getContent());
		assertTrue(requestStreamClosed);
		assertTrue(responseStreamClosed);
	}

	@Test
	public void shouldPostJsonContent() throws Exception {
		response = "json post response";
		Map<String, String> requestContent = new TreeMap<String, String>();
		requestContent.put("1", "one");
		requestContent.put("2", "two");
		HttpResponse result = httpClient.postJson("http://example.com/post/json/", requestContent);
		assertEquals("http://example.com/post/json/", requestedUrl);
		assertEquals("{\"1\":\"one\",\"2\":\"two\"}", request);
		assertEquals(200, result.getCode());
		assertEquals("json post response", result.getContent());
		assertTrue(requestStreamClosed);
		assertTrue(responseStreamClosed);
	}

	@Test(expected = Error.class)
	public void shouldFailHorriblyIfPostIsNotSupported() throws Exception {
		doThrow(new ProtocolException()).when(((HttpURLConnection) connection)).setRequestMethod("POST");
		httpClient.post("http://example.com/", new Parameters());
	}
}
