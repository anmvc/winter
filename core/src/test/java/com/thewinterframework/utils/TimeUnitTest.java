package com.thewinterframework.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeUnitTest {

	@Test
	void zeroIsZeroTicks() {
		assertEquals(0L, TimeUnit.SECONDS.toTicks(0));
		assertEquals(0L, TimeUnit.NANOS.toTicks(0));
	}

	@Test
	void ticksAreNotConvertedTwice() {
		assertEquals(1L, TimeUnit.TICKS.toTicks(1));
		assertEquals(40L, TimeUnit.TICKS.toTicks(40));
	}

	@Test
	void wholeSecondsBecomeTwentyTicks() {
		assertEquals(20L, TimeUnit.SECONDS.toTicks(1));
		assertEquals(60L, TimeUnit.SECONDS.toTicks(3));
		assertEquals(1200L, TimeUnit.MINUTES.toTicks(1));
	}

	@Test
	void millisecondsAreRoundedUp() {
		assertEquals(1L, TimeUnit.MILLIS.toTicks(1));
		assertEquals(1L, TimeUnit.MILLIS.toTicks(50));
		assertEquals(2L, TimeUnit.MILLIS.toTicks(51));
	}

	@Test
	void unitsSmallerThanATickNeverBecomeANoOp() {
		assertEquals(1L, TimeUnit.NANOS.toTicks(1));
		assertEquals(1L, TimeUnit.MICROS.toTicks(1));
	}

	@Test
	void negativeAmountsKeepTheirSign() {
		assertEquals(-20L, TimeUnit.SECONDS.toTicks(-1));
		assertEquals(-1L, TimeUnit.MILLIS.toTicks(-1));
	}

	@Test
	void hugeAmountsAreClampedInsteadOfOverflowing() {
		assertEquals(Long.MAX_VALUE, TimeUnit.ERAS.toTicks(Long.MAX_VALUE));
		assertEquals(Long.MAX_VALUE, TimeUnit.SECONDS.toTicks(Long.MAX_VALUE));
	}

	@Test
	void timeUnitsAreTimeBasedAndDateUnitsAreNot() {
		assertTrue(TimeUnit.MILLIS.isTimeBased());
		assertFalse(TimeUnit.MILLIS.isDurationEstimated());
		assertTrue(TimeUnit.DAYS.isDateBased());
		assertTrue(TimeUnit.DAYS.isDurationEstimated());
	}

	@Test
	void toStringUsesThePrettyName() {
		assertEquals("Seconds", TimeUnit.SECONDS.toString());
	}
}
