package com.thewinterframework.expression;

import com.thewinterframework.component.ComponentUtils;
import com.thewinterframework.expression.converter.Converter;
import com.thewinterframework.expression.converter.ConverterRegistry;
import com.thewinterframework.utils.TimeUnit;
import net.kyori.adventure.text.Component;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.jexl3.introspection.JexlPermissions;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link ExpressionResolver} based on <a href="https://commons.apache.org/proper/commons-jexl/">JEXL</a>.
 */
public class JexlExpressionResolver implements ExpressionResolver {

	private static final Map<String, TimeUnit> TIME_UNIT_ALIASES = Map.ofEntries(
			Map.entry("MILLISECOND", TimeUnit.MILLIS),
			Map.entry("MILLISECONDS", TimeUnit.MILLIS),
			Map.entry("MILLI", TimeUnit.MILLIS),
			Map.entry("MILLIS", TimeUnit.MILLIS),
			Map.entry("MICROSECOND", TimeUnit.MICROS),
			Map.entry("MICROSECONDS", TimeUnit.MICROS),
			Map.entry("MICRO", TimeUnit.MICROS),
			Map.entry("MICROS", TimeUnit.MICROS),
			Map.entry("NANOSECOND", TimeUnit.NANOS),
			Map.entry("NANOSECONDS", TimeUnit.NANOS),
			Map.entry("NANO", TimeUnit.NANOS),
			Map.entry("NANOS", TimeUnit.NANOS),
			Map.entry("SECOND", TimeUnit.SECONDS),
			Map.entry("SECONDS", TimeUnit.SECONDS),
			Map.entry("MINUTE", TimeUnit.MINUTES),
			Map.entry("MINUTES", TimeUnit.MINUTES),
			Map.entry("HOUR", TimeUnit.HOURS),
			Map.entry("HOURS", TimeUnit.HOURS),
			Map.entry("DAY", TimeUnit.DAYS),
			Map.entry("DAYS", TimeUnit.DAYS),
			Map.entry("WEEK", TimeUnit.WEEKS),
			Map.entry("WEEKS", TimeUnit.WEEKS),
			Map.entry("TICK", TimeUnit.TICKS),
			Map.entry("TICKS", TimeUnit.TICKS)
	);

	private final JexlEngine jexl = new JexlBuilder()
			.cache(512)
			.strict(true)
			.silent(false)
			.permissions(JexlPermissions.UNRESTRICTED)
			.create();

	private final JexlContext context = new ConcurrentMapContext();
	private final ConverterRegistry converters = new ConverterRegistry();

	public JexlExpressionResolver() {
		addConverter(String.class, TimeUnit.class, JexlExpressionResolver::toTimeUnit);
		addConverter(Number.class, Long.class, Number::longValue);
		addConverter(Number.class, Integer.class, Number::intValue);
		addConverter(Number.class, Double.class, Number::doubleValue);
		addConverter(Number.class, Float.class, Number::floatValue);
		addConverter(Number.class, Short.class, Number::shortValue);
		addConverter(Number.class, Byte.class, Number::byteValue);
		addConverter(String.class, Boolean.class, Boolean::parseBoolean);
		addConverter(String.class, Long.class, value -> Long.parseLong(value.trim()));
		addConverter(String.class, Integer.class, value -> Integer.parseInt(value.trim()));
		addConverter(String.class, Double.class, value -> Double.parseDouble(value.trim()));
		addConverter(String.class, Float.class, value -> Float.parseFloat(value.trim()));
		addConverter(Integer.class, Long.class, Long::valueOf);
		addConverter(Integer.class, Double.class, Double::valueOf);
		addConverter(Integer.class, Float.class, Float::valueOf);
		addConverter(Long.class, Integer.class, Long::intValue);
		addConverter(Long.class, Double.class, Double::valueOf);
		addConverter(Long.class, Float.class, Float::valueOf);
		addConverter(Double.class, Integer.class, Double::intValue);
		addConverter(Double.class, Long.class, Double::longValue);
		addConverter(Double.class, Float.class, Double::floatValue);
		addConverter(Float.class, Integer.class, Float::intValue);
		addConverter(Float.class, Long.class, Float::longValue);
		addConverter(Float.class, Double.class, Float::doubleValue);
		addConverter(String.class, Component.class, ComponentUtils::miniMessage);

		for (final var value : TimeUnit.values()) {
			addContext(value.name(), value);
			addContext(value.name().toLowerCase(Locale.ROOT), value);
		}
	}

	/**
	 * Resolves the given expression for the expected return type.
	 * <p>
	 * An expression that cannot be evaluated (for example because it references a context value that does not
	 * exist) resolves to {@code null} instead of throwing, so optional expressions can be handled by the caller.
	 *
	 * @param expression         the expression to resolve
	 * @param expectedReturnType the expected return type
	 * @param <T>                the type of the resolved value
	 * @return the resolved value, or {@code null} if it cannot be resolved
	 */
	@Override
	public <T> @Nullable T resolve(final String expression, final Class<T> expectedReturnType) {
		final Object result;
		try {
			final var compiled = jexl.createExpression(expression);
			if (compiled == null) {
				return null;
			}

			result = compiled.evaluate(context);
		} catch (final JexlException e) {
			return null;
		}

		if (result == null) {
			return null;
		}

		return converters.convert(result, expectedReturnType);
	}

	@Override
	public void addContext(final String key, final Object value) {
		this.context.set(key, value);
	}

	@Override
	public <S, T> void addConverter(final Class<S> source, final Class<T> target, final Converter<S, T> converter) {
		this.converters.register(source, target, converter);
	}

	/**
	 * Converts the given name into a {@link TimeUnit}, accepting both the enum names and the common
	 * ({@code MILLISECONDS}, {@code SECONDS}, ...) spellings, ignoring the case.
	 *
	 * @param name the name of the time unit
	 * @return the time unit
	 * @throws IllegalArgumentException if the name does not match any time unit
	 */
	public static TimeUnit toTimeUnit(final String name) {
		final var normalized = name == null ? "" : name.trim().toUpperCase(Locale.ROOT).replace("_", "");
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("A time unit name cannot be empty");
		}

		for (final var value : TimeUnit.values()) {
			if (value.name().equals(normalized)) {
				return value;
			}
		}

		final var aliased = TIME_UNIT_ALIASES.get(normalized);
		if (aliased != null) {
			return aliased;
		}

		throw new IllegalArgumentException("Unknown time unit: " + name);
	}

	/**
	 * Thread safe {@link JexlContext}, contexts are read from tasks running on other threads than the one adding
	 * values to them.
	 */
	private static final class ConcurrentMapContext implements JexlContext {

		private final Map<String, Object> values = new ConcurrentHashMap<>();

		@Override
		public Object get(final String name) {
			return values.get(name);
		}

		@Override
		public void set(final String name, final Object value) {
			if (value == null) {
				values.remove(name);
			} else {
				values.put(name, value);
			}
		}

		@Override
		public boolean has(final String name) {
			return values.containsKey(name);
		}
	}
}
