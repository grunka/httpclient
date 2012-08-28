package se.grunka.httpclient;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class ParametersTest {

	private Parameters parameters;

	@Before
	public void before() throws Exception {
		parameters = new Parameters();
	}

	@Test
	public void shouldAddSingleParameter() throws Exception {
		parameters.add("one", "1");
		assertEquals("one=1", parameters.toString());
	}

	@Test
	public void shouldAddTwoParameters() throws Exception {
		parameters.add("one", "1").add("two", "2");
		assertEquals("one=1&two=2", parameters.toString());
	}

	@Test
	public void shouldAddSameNameParameterMultipleTimes() throws Exception {
		parameters.add("one", "1").add("two", "2").add("two", "22");
		assertEquals("one=1&two=2&two=22", parameters.toString());
	}

	@Test
	public void shouldEncodeCharactersAsNeeded() throws Exception {
		parameters.add("åäö", "a=&b").add("åäö", "c/d");
		assertEquals("%C3%A5%C3%A4%C3%B6=a%3D%26b&%C3%A5%C3%A4%C3%B6=c%2Fd", parameters.toString());
	}

}
