package com.nylocas;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ErrorBodyTest
{
	private final Gson gson = new Gson();

	@Test
	public void readsTheErrorField()
	{
		assertEquals("unauthorized", ErrorBody.message(gson, "{\"error\":\"unauthorized\"}"));
	}

	@Test
	public void emptyWhenTheBodyHasNoError()
	{
		assertEquals("", ErrorBody.message(gson, ""));
		assertEquals("", ErrorBody.message(gson, "{}"));
		assertEquals("", ErrorBody.message(gson, "null"));
		assertEquals("", ErrorBody.message(gson, "not json"));
	}
}
