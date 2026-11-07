package com.nylocas;

import com.google.gson.Gson;

final class ErrorBody
{
	private ErrorBody()
	{
	}

	static String message(Gson gson, String body)
	{
		try
		{
			Parsed parsed = gson.fromJson(body, Parsed.class);
			return parsed == null || parsed.error == null ? "" : parsed.error;
		}
		catch (RuntimeException ex)
		{
			return "";
		}
	}

	private static final class Parsed
	{
		String error;
	}
}
