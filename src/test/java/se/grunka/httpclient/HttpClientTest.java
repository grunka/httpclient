package se.grunka.httpclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PrepareForTest(HttpClient.class)
public class HttpClientTest {

	private HttpClient httpClient;
	private String requestedUrl;
	private URL url;
	private String response;
	private String request;
	private String errorResponse;

	@Before
	public void before() throws Exception {
		requestedUrl = null;
		url = mock(URL.class);
		HttpURLConnection connection = mock(HttpURLConnection.class);
		when(connection.getResponseCode()).thenReturn(200);
		response = "";
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
		});
		errorResponse = "";
		when(connection.getErrorStream()).thenReturn(new InputStream() {
			private int position = 0;
			@Override
			public int read() throws IOException {
				if (position < errorResponse.length()) {
					return errorResponse.charAt(position++);
				} else {
					return -1;
				}
			}
		});
		request = "";
		when(connection.getOutputStream()).thenReturn(new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				request += Character.toString((char) b);
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
		httpClient = new HttpClient(1000, 1000);
	}

	@Test
	public void shouldGet() throws Exception {
		response = "get response";
		HttpResponse result = httpClient.get("http://localhost:7890/some/path");
		assertEquals("http://localhost:7890/some/path", requestedUrl);
		assertEquals("", request);
		assertEquals("get response", result.getContent());
	}

	@Test
	public void shouldPost() throws Exception {
		response = "post response";
		HttpResponse result = httpClient.post("http://localhost:7890/some/path", new Parameters().add("hello", "world").add("one", "1"));
		assertEquals("http://localhost:7890/some/path", requestedUrl);
		assertEquals("hello=world&one=1", request);
		assertEquals("post response", result.getContent());
	}

	//TODO test ALL THE CODE
}
