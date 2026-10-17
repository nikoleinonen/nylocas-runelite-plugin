package com.nylocas;

final class LiveText
{
	private LiveText()
	{
	}

	static String approved(String teamName, String item, boolean complete, String tileTitle)
	{
		String line = teamName + "'s drop " + item + " got approved";
		if (complete)
		{
			line += " and that completed " + tileTitle;
		}
		return line;
	}

	static String rank(String leader, boolean winner)
	{
		if (leader == null || leader.isEmpty())
		{
			return "First place is tied.";
		}
		if (winner)
		{
			return leader + " won the event! Congratulations!";
		}
		return leader + " took the lead!";
	}
}
