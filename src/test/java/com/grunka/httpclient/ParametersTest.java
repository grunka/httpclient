package com.grunka.httpclient;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;

public class ParametersTest {

	private Parameters parameters;

	@Before
	public void before() {
		parameters = new Parameters();
	}

	@Test
	public void shouldAddSingleParameter() {
		parameters.add("one", "1");
		assertEquals("one=1", parameters.toString());
	}

	@Test
	public void shouldAddTwoParameters() {
		parameters.add("one", "1").add("two", "2");
		assertEquals("one=1&two=2", parameters.toString());
	}

	@Test
	public void shouldAddSameNameParameterMultipleTimes() {
		parameters.add("one", "1").add("two", "2").add("two", "22");
		assertEquals("one=1&two=2&two=22", parameters.toString());
	}

	@Test
	public void shouldEncodeCharactersAsNeeded() {
		parameters.add("åäö", "a=&b").add("åäö", "c/d");
		assertEquals("%C3%A5%C3%A4%C3%B6=a%3D%26b&%C3%A5%C3%A4%C3%B6=c%2Fd", parameters.toString());
	}

	@Test
	public void shouldInitializeUsingMap() {
		HashMap<String, String> initializer = new HashMap<>();
		initializer.put("a", "A");
		initializer.put("b", "B");
		parameters = new Parameters(initializer);
		assertEquals("a=A&b=B", parameters.toString());
	}
}
