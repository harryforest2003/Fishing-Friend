package io.github.harryforest2003.fishingfriend;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognises the chat messages servers send about overfishing. */
public final class ServerMessages {
	private static final Pattern FORMATTING_CODE = Pattern.compile("§.");
	private static final Pattern BLOCKS = Pattern.compile("(\\d+)\\s*blocks?\\b", Pattern.CASE_INSENSITIVE);

	/** Whether {@code message} contains any of {@code phrases}, ignoring case and colour codes. */
	public static boolean containsAny(String message, List<String> phrases) {
		String text = plain(message);
		for (String phrase : phrases) {
			if (!phrase.isBlank() && text.contains(phrase.toLowerCase(Locale.ROOT).trim())) {
				return true;
			}
		}
		return false;
	}

	/** A distance like "3 blocks" mentioned in {@code message}, or 0 if there is none. */
	public static int blocksMentioned(String message) {
		Matcher matcher = BLOCKS.matcher(plain(message));
		if (!matcher.find()) {
			return 0;
		}
		try {
			return Math.min(256, Integer.parseInt(matcher.group(1)));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String plain(String message) {
		return FORMATTING_CODE.matcher(message).replaceAll("").toLowerCase(Locale.ROOT);
	}

	private ServerMessages() {
	}
}
