package com.thewinterframework.number;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumberUtilsTest {

	@Test
	void smallNumbersAreNotAbbreviated() {
		assertEquals("0", NumberUtils.abbreviateNumber(0));
		assertEquals("999", NumberUtils.abbreviateNumber(999));
		assertEquals("-999", NumberUtils.abbreviateNumber(-999));
	}

	@Test
	void thousandsAreAbbreviated() {
		assertEquals("1.0k", NumberUtils.abbreviateNumber(1000));
		assertEquals("1.5k", NumberUtils.abbreviateNumber(1500));
		assertEquals("1.0M", NumberUtils.abbreviateNumber(1_000_000));
		assertEquals("1.0B", NumberUtils.abbreviateNumber(1_000_000_000));
		assertEquals("1.0T", NumberUtils.abbreviateNumber(1_000_000_000_000L));
		assertEquals("1.0P", NumberUtils.abbreviateNumber(1_000_000_000_000_000L));
	}

	@Test
	void negativeNumbersAreAbbreviatedWithTheirSign() {
		assertEquals("-1.0k", NumberUtils.abbreviateNumber(-1000));
		assertEquals("-1.5M", NumberUtils.abbreviateNumber(-1_500_000));
	}

	@Test
	void theSmallestLongKeepsItsSign() {
		// Math.abs(Long.MIN_VALUE) is still negative, the abbreviation must not lose the sign because of it.
		assertEquals("-9.2E", NumberUtils.abbreviateNumber(Long.MIN_VALUE));
	}

	@Test
	void numbersAtTheLastSuffixKeepAValidSuffix() {
		assertEquals("1.0E", NumberUtils.abbreviateNumber(1_000_000_000_000_000_000L));
		assertEquals("9.2E", NumberUtils.abbreviateNumber(Long.MAX_VALUE));
	}

	@Test
	void formattingUsesTheRequestedDecimals() {
		assertEquals("1.23", NumberUtils.formatNumber(1.2345, 2));
		assertEquals("1.2", NumberUtils.formatNumber(1.24, 1));
		assertEquals("1", NumberUtils.formatNumber(1.4, 0));
		assertEquals("2", NumberUtils.formatNumber(1.5, 0));
	}

	@Test
	void formattingDoesNotDependOnTheDefaultLocale() {
		final var previous = Locale.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("de-DE"));
			// A German locale would use a comma as the decimal separator.
			assertEquals("1.50", NumberUtils.formatNumber(1.5, 2));
		} finally {
			Locale.setDefault(previous);
		}
	}
}
