package com.thewinterframework.number;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Utility methods for numbers.
 */
public class NumberUtils {

	private static final String ABBREVIATION_SUFFIXES = "kMBTPE";

	private NumberUtils() {
	}

	/**
	 * Format a number with a specific number of decimals.
	 * @param number The number to format.
	 * @param decimals The number of decimals to format the number with.
	 * @return The formatted number.
	 */
	public static String formatNumber(final double number, final int decimals) {
		final var pattern = new StringBuilder("0");
		if (decimals > 0) {
			pattern.append(".");
			pattern.append("0".repeat(decimals));
		}

		// Locale.ROOT keeps the output stable, the decimal separator must not depend on the machine running the server.
		final var decimalFormat = new DecimalFormat(pattern.toString(), DecimalFormatSymbols.getInstance(Locale.ROOT));
		return decimalFormat.format(number);
	}

	/**
	 * Abbreviate a number to a more readable format. For example, 1000 becomes 1k.
	 * @param number The number to abbreviate.
	 * @return The abbreviated number.
	 */
	public static String abbreviateNumber(final long number) {
		if (number > -1000 && number < 1000) return String.valueOf(number);

		// Abs on a double, Math.abs(Long.MIN_VALUE) is still negative.
		final var absolute = Math.abs((double) number);
		final var exponent = Math.min((int) (Math.log(absolute) / Math.log(1000)), ABBREVIATION_SUFFIXES.length());
		final var suffix = ABBREVIATION_SUFFIXES.charAt(exponent - 1);
		return String.format(Locale.ROOT, "%.1f%c", number / Math.pow(1000, exponent), suffix);
	}

}
