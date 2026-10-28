package com.thewinterframework.expression;

import com.thewinterframework.utils.TimeUnit;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JexlExpressionResolverTest {

	@Test
	void arithmeticExpressionsAreResolved() {
		final var resolver = new JexlExpressionResolver();

		final int sum = resolver.resolve("1 + 2", Integer.class);
		final long product = resolver.resolve("2 * 3", Long.class);
		final double division = resolver.resolve("1.0 / 2", Double.class);

		assertEquals(3, sum);
		assertEquals(6L, product);
		assertEquals(0.5d, division);
	}

	@Test
	void contextValuesAreResolved() {
		final var resolver = new JexlExpressionResolver();
		resolver.addContext("timer", 30);

		final int doubled = resolver.resolve("timer * 2", Integer.class);
		final int timer = resolver.resolve("timer", Integer.class);

		assertEquals(60, doubled);
		assertEquals(30, timer);
	}

	@Test
	void unknownExpressionsResolveToNull() {
		final var resolver = new JexlExpressionResolver();

		assertNull(resolver.resolve("unknownVariable", Integer.class));
		assertNull(resolver.resolve("unknownVariable *", Integer.class));
	}

	@Test
	void booleanExpressionsAreResolved() {
		final var resolver = new JexlExpressionResolver();
		resolver.addContext("enabled", true);

		assertTrue(resolver.resolve("enabled", Boolean.class));
		assertFalse(resolver.resolve("!enabled", Boolean.class));
		assertTrue(resolver.resolve("'true'", Boolean.class));
	}

	@Test
	void timeUnitsCanBeReferencedByTheirName() {
		final var resolver = new JexlExpressionResolver();

		assertSame(TimeUnit.SECONDS, resolver.resolve("SECONDS", TimeUnit.class));
		assertSame(TimeUnit.MILLIS, resolver.resolve("millis", TimeUnit.class));
	}

	@Test
	void timeUnitsCanBeParsedFromStrings() {
		final var resolver = new JexlExpressionResolver();

		assertSame(TimeUnit.SECONDS, resolver.resolve("'SECONDS'", TimeUnit.class));
		assertSame(TimeUnit.MILLIS, resolver.resolve("'millis'", TimeUnit.class));
	}

	@Test
	void timeUnitNamesAcceptTheCommonSpellings() {
		assertSame(TimeUnit.SECONDS, JexlExpressionResolver.toTimeUnit("SECOND"));
		assertSame(TimeUnit.SECONDS, JexlExpressionResolver.toTimeUnit(" seconds "));
		assertSame(TimeUnit.MILLIS, JexlExpressionResolver.toTimeUnit("MILLISECONDS"));
		assertSame(TimeUnit.TICKS, JexlExpressionResolver.toTimeUnit("ticks"));
		assertSame(TimeUnit.MINUTES, JexlExpressionResolver.toTimeUnit("MINUTES"));
	}

	@Test
	void anUnknownTimeUnitIsReported() {
		assertThrows(IllegalArgumentException.class, () -> JexlExpressionResolver.toTimeUnit("fortnights"));
		assertThrows(IllegalArgumentException.class, () -> JexlExpressionResolver.toTimeUnit("  "));
	}

	@Test
	void customConvertersAreUsed() {
		final var resolver = new JexlExpressionResolver();
		resolver.addConverter(String.class, StringBuilder.class, StringBuilder::new);

		assertEquals("hi", resolver.resolve("'hi'", StringBuilder.class).toString());
	}

	@Test
	void customContextsAreVisibleToOtherThreads() throws Exception {
		final var resolver = new JexlExpressionResolver();
		resolver.addContext("shared", 21);

		// Components are resolved from the scheduler threads, the context must be thread safe.
		final var result = new CompletableFuture<Integer>();
		final var thread = new Thread(() -> result.complete(resolver.resolve("shared * 2", Integer.class)));
		thread.start();
		thread.join();

		assertEquals(42, result.get().intValue());
	}
}
