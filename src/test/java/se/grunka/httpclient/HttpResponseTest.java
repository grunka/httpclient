package se.grunka.httpclient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HttpResponseTest {
	@Test
	public void shouldBeMalformedUrlResponse() throws Exception {
		MalformedURLException exception = new MalformedURLException("test");
		HttpResponse response = new HttpResponse(exception);
		assertTrue(response.hasError());
		assertTrue(response.malformedUrl());
		assertFalse(response.couldNotConnect());
		assertFalse(response.readError());
		assertSame(exception, response.getCause());
		assertFalse(response.isOk());
		assertEquals(-1, response.getCode());
		assertEquals(null, response.getContent());
	}

	@Test
	public void shouldBeCouldNotConnectResponse() throws Exception {
		HttpResponse response = new HttpResponse(new ConnectException("test"));
		assertTrue(response.hasError());
		assertFalse(response.malformedUrl());
		assertTrue(response.couldNotConnect());
		assertFalse(response.readError());
		assertFalse(response.isOk());
		assertEquals(-1, response.getCode());
		assertEquals(null, response.getContent());
	}

	@Test
	public void shouldBeReadError() throws Exception {
		HttpResponse response = new HttpResponse(new IOException("test"));
		assertTrue(response.hasError());
		assertFalse(response.malformedUrl());
		assertFalse(response.couldNotConnect());
		assertTrue(response.readError());
		assertFalse(response.isOk());
		assertEquals(-1, response.getCode());
		assertEquals(null, response.getContent());
	}

	@Test
	public void shouldNonErrorResponse() throws Exception {
		HttpResponse response = new HttpResponse(123, "content");
		assertFalse(response.hasError());
		assertFalse(response.malformedUrl());
		assertFalse(response.couldNotConnect());
		assertFalse(response.readError());
		assertFalse(response.isOk());
		assertEquals(123, response.getCode());
		assertEquals("content", response.getContent());
	}

	@Test
	public void shouldBeOkResponse() throws Exception {
		HttpResponse response = new HttpResponse(200, "things");
		assertFalse(response.hasError());
		assertFalse(response.malformedUrl());
		assertFalse(response.couldNotConnect());
		assertFalse(response.readError());
		assertTrue(response.isOk());
		assertEquals(200, response.getCode());
		assertEquals("things", response.getContent());
	}
}