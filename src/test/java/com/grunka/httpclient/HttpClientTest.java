package com.grunka.httpclient;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Ignore
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
    public void before() {
        requestedUrl = null;
        //url = mock(URL.class);
        //connection = mock(HttpURLConnection.class);
        //when(((HttpURLConnection) connection).getResponseCode()).thenReturn(200);
        response = "";
        responseStreamClosed = false;
		/*
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
		*/
        errorResponse = "";
        errorResponseStreamClosed = false;
		/*
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
		 */
        request = "";
        requestStreamClosed = false;
		/*
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
		 */
        //when(url.openConnection()).thenReturn(connection);
		/*
		whenNew(URL.class).withArguments(anyString()).thenAnswer(new Answer<URL>() {
			@Override
			public URL answer(InvocationOnMock invocation) throws Throwable {
				requestedUrl = (String) invocation.getArguments()[0];
				return url;
			}
		});
		 */
        //httpClient = new HttpClient(1234, 5678);
    }

    private void verifyBasicConnectionProperties(String accept) {
        //verify(connection).setRequestProperty("Connection", "Keep-Alive");
        //verify(connection).setRequestProperty("User-Agent", "httpclient/1.0");
        //verify(connection).setRequestProperty("Accept", accept);
        //verify(connection).setConnectTimeout(1234);
        //verify(connection).setReadTimeout(5678);
        assertTrue(responseStreamClosed);
    }

    private void verifyPostConnectionProperties(String contentType, int length) {
        //verify((HttpURLConnection) connection).setRequestMethod("POST");
        //verify(connection).setRequestProperty("Content-Type", contentType);
        //verify(connection).setRequestProperty("Content-Length", String.valueOf(length));
        assertTrue(requestStreamClosed);
    }

    @Test
    public void shouldSetBasicConnectionPropertiesForGet() {
        httpClient.get("http://example.com/");
        verifyBasicConnectionProperties("what I accept");
    }

    @Test
    public void shouldSetBasicConnectionPropertiesForGetWithDefaultAccept() {
        httpClient.get("http://example.com/");
        verifyBasicConnectionProperties("text/plain");
    }

    @Test
    public void shouldSetBasicConnectionPropertiesForPost() {
        Parameters content = new Parameters().add("hello", "world");
        httpClient.post("http://example.com/", content);
        verifyBasicConnectionProperties("what I accept");
        verifyPostConnectionProperties("application/x-www-form-urlencoded", content.toString().length());
    }

    @Test
    public void shouldSetBasicConnectionPropertiesForPostWithDefaultAccept() {
        Parameters content = new Parameters().add("hello", "world");
        httpClient.post("http://example.com/", content);
        verifyBasicConnectionProperties("text/plain");
        verifyPostConnectionProperties("application/x-www-form-urlencoded", content.toString().length());
    }

    @Test
    public void shouldSetBasicConnectionPropertiesForPostJson() {
        String content = "hello world";
        httpClient.postJson("http://example.com/", content);
        verifyBasicConnectionProperties("what I accept");
        verifyPostConnectionProperties("application/json", content.length());
    }

    @Test
    public void shouldSetBasicConnectionPropertiesForPostJsonWithDefaultAccept() {
        String content = "hello world";
        httpClient.postJson("http://example.com/", content);
        verifyBasicConnectionProperties("application/json");
        verifyPostConnectionProperties("application/json", content.length());
    }

    @Test
    public void shouldHandleMalformedUrl() {
        //whenNew(URL.class).withArguments(anyString()).thenThrow(new MalformedURLException("that is no good"));
        HttpResponse result = httpClient.get("a really bad url").join();
        //assertTrue(result.hasError());
        //assertTrue(result.malformedUrl());
    }

    @Test
    public void shouldHandleMalformedUrlForPost() {
        //whenNew(URL.class).withArguments(anyString()).thenThrow(new MalformedURLException("that is no good"));
        HttpResponse result = httpClient.post("a really bad url", new Parameters()).join();
        //assertTrue(result.hasError());
        //assertTrue(result.malformedUrl());
    }

    @Test
    public void shouldHandleProblemsOpeningConnection() {
        //when(url.openConnection()).thenThrow(new IOException("this is really rare I guess but should still be handled"));
        HttpResponse result = httpClient.get("http://example.com/").join();
        //assertTrue(result.hasError());
        //assertTrue(result.otherError());
    }

    @Test
    public void shouldHandleProblemsOpeningConnectionForPost() {
        //when(url.openConnection()).thenThrow(new IOException("this is really rare I guess but should still be handled"));
        HttpResponse result = httpClient.post("http://example.com/", new Parameters()).join();
        //assertTrue(result.hasError());
        //assertTrue(result.otherError());
    }

    @Test
    public void shouldFailNonHttpUrlsGracefully() {
        //connection = mock(JarURLConnection.class);
        //when(url.openConnection()).thenReturn(connection);
        HttpResponse result = httpClient.get("jar://some_file.jar").join();
        //assertTrue(result.hasError());
        //assertTrue(result.malformedUrl());
    }

    @Test
    public void shouldHandleConnectFailureOnGets() {
        //when(connection.getInputStream()).thenThrow(new ConnectException("could not connect"));
        HttpResponse result = httpClient.get("http://example.com/").join();
        //assertTrue(result.hasError());
        //assertTrue(result.couldNotConnect());
    }

    @Test
    public void shouldHandleConnectTimeoutOnGets() {
        //when(connection.getInputStream()).thenThrow(new SocketTimeoutException("timed out"));
        HttpResponse result = httpClient.get("http://example.com/").join();
        //assertTrue(result.hasError());
        //assertTrue(result.timedOut());
    }

    @Test
    public void shouldHandleReadTimeoutOnGets() {
        InputStream inputStream = mockTimeoutInputStream();
        //when(connection.getInputStream()).thenReturn(inputStream);
        HttpResponse result = httpClient.get("http://example.com/").join();
        //assertTrue(result.hasError());
        //assertTrue(result.timedOut());
        //verify(inputStream).close();
    }

    private InputStream mockTimeoutInputStream() {
        //InputStream inputStream = mock(InputStream.class);
        //when(inputStream.read()).thenThrow(new SocketTimeoutException());
        //when(inputStream.read(any(byte[].class))).thenThrow(new SocketTimeoutException());
        //when(inputStream.read(any(byte[].class), anyInt(), anyInt())).thenThrow(new SocketTimeoutException());
        //return inputStream;
		return null;
    }

    private InputStream mockExceptionInputStream() {
        //InputStream inputStream = mock(InputStream.class);
        //when(inputStream.read()).thenThrow(new IOException());
        //when(inputStream.read(any(byte[].class))).thenThrow(new IOException());
        //when(inputStream.read(any(byte[].class), anyInt(), anyInt())).thenThrow(new IOException());
        //return inputStream;
		return null;
    }

    @Test
    public void shouldReadNonOkResponse() {
        errorResponse = "error message";
        //when(connection.getInputStream()).thenThrow(new IOException("uh oh that request was bad"));
        //when(((HttpURLConnection) connection).getResponseCode()).thenReturn(123);
        HttpResponse result = httpClient.get("http://example.com/").join();
        assertEquals(123, result.getCode());
        assertEquals("error message", result.getBody());
        //assertFalse(result.hasError());
        assertTrue(errorResponseStreamClosed);
    }

    @Test
    public void shouldHandleErrorOnReadNonOkResponse() {
        errorResponse = "error message";
        //when(connection.getInputStream()).thenThrow(new IOException("uh oh that request was bad"));
        InputStream errorStream = mockExceptionInputStream();
        //when(((HttpURLConnection) connection).getErrorStream()).thenReturn(errorStream);
        HttpResponse result = httpClient.get("http://example.com/").join();
        //assertTrue(result.hasError());
        //assertTrue(result.otherError());
        //verify(errorStream).close();
    }

    @Test
    public void shouldHandleFailureToConnectOnPost() {
        //when(connection.getOutputStream()).thenThrow(new ConnectException("could not connect to server"));
        //when(connection.getInputStream()).thenThrow(new Error("should not even try to get input stream"));
        HttpResponse result = httpClient.post("http://example.com", new Parameters()).join();
        //assertTrue(result.hasError());
        //assertTrue(result.couldNotConnect());
    }

    @Test
    public void shouldHandleConnectTimeoutOnPost() {
        //when(connection.getOutputStream()).thenThrow(new SocketTimeoutException("could not connect to server"));
        //when(connection.getInputStream()).thenThrow(new Error("should not even try to get input stream"));
        HttpResponse result = httpClient.post("http://example.com", new Parameters()).join();
        //assertTrue(result.hasError());
        //assertTrue(result.timedOut());
    }

    @Test
    public void shouldReadErrorResponseOnErrorWhilePosting() {
        errorResponse = "error message";
        //when(connection.getOutputStream()).thenThrow(new IOException("not allowed to post"));
        //when(connection.getInputStream()).thenThrow(new IOException("not OK response"));
        //Mockito.when(((HttpURLConnection) connection).getResponseCode()).thenReturn(400);
        HttpResponse result = httpClient.post("http://example.com", new Parameters()).join();
        assertFalse(result.isOk());
        //assertFalse(result.hasError());
        assertEquals(400, result.getCode());
        assertEquals("error message", result.getBody());
        assertTrue(errorResponseStreamClosed);
    }

    @Test
    public void shouldGet() {
        response = "get response";
        HttpResponse result = httpClient.get("http://localhost:7890/some/path").join();
        assertEquals("http://localhost:7890/some/path", requestedUrl);
        assertEquals("", request);
        assertEquals("get response", result.getBody());
        assertTrue(responseStreamClosed);
    }

    @Test
    public void shouldPost() {
        response = "post response";
        HttpResponse result = httpClient.post("http://localhost:7890/some/path", new Parameters().add("hello", "world").add("one", "1")).join();
        assertEquals("http://localhost:7890/some/path", requestedUrl);
        assertEquals("hello=world&one=1", request);
        assertEquals(200, result.getCode());
        assertEquals("post response", result.getBody());
        assertTrue(requestStreamClosed);
        assertTrue(responseStreamClosed);
    }

    @Test
    public void shouldPostJsonContent() {
        response = "json post response";
        HttpResponse result = httpClient.postJson("http://example.com/post/json/", "requestContent").join();
        assertEquals("http://example.com/post/json/", requestedUrl);
        assertEquals("requestContent", request);
        assertEquals(200, result.getCode());
        assertEquals("json post response", result.getBody());
        assertTrue(requestStreamClosed);
        assertTrue(responseStreamClosed);
    }

    @Test(expected = Error.class)
    public void shouldFailHorriblyIfPostIsNotSupported() {
        //doThrow(new ProtocolException()).when(((HttpURLConnection) connection)).setRequestMethod("POST");
        httpClient.post("http://example.com/", new Parameters());
    }

    private static class MockHttpURLConnection extends HttpURLConnection {
        protected MockHttpURLConnection(URL u) {
            super(u);
        }

        @Override
        public void disconnect() {

        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {

        }
    }
}
