package com.thewinterframework.expression.converter;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConverterRegistry {
	private final Map<ConverterKey<?, ?>, Converter<?, ?>> converters = new HashMap<>();

	public <S, T> void register(final Class<S> source, final Class<T> target, final Converter<S, T> converter) {
		converters.put(new ConverterKey<>(source, target), converter);
	}

	/**
	 * Converts the given source value into the target type.
	 *
	 * @param source the value to convert, may be {@code null}
	 * @param target the expected type
	 * @param <S>    the type of the source value
	 * @param <T>    the type of the target value
	 * @return the converted value, {@code null} if the source is {@code null}
	 * @throws IllegalArgumentException if no converter is registered for the given types
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	public <S, T> T convert(final @Nullable S source, final Class<T> target) {
		if (source == null) {
			return null;
		}

		if (target.isInstance(source)) return (T) source;

		final var converter = (Converter<S, T>) findConverter(source.getClass(), target);
		if (converter == null) {
			throw new IllegalArgumentException("No converter found for " + source.getClass().getName() + " to " + target.getName());
		}
		return converter.convert(source);
	}

	/**
	 * Looks up a converter for the given source type, falling back to the closest registered super type (or
	 * interface) so that, for example, any {@link Number} can be converted into a {@link Long}.
	 *
	 * @param source the runtime type of the value to convert
	 * @param target the expected type
	 * @return the converter, or {@code null} if there is none
	 */
	@Nullable
	private Converter<?, ?> findConverter(final Class<?> source, final Class<?> target) {
		// Walk up the class hierarchy first, so Number converters also apply to any of their subtypes.
		var current = source;
		while (current != null && current != Object.class) {
			final var converter = converters.get(new ConverterKey<>(current, target));
			if (converter != null) {
				return converter;
			}
			current = current.getSuperclass();
		}

		// Interfaces are inspected last, they are less specific than the class hierarchy.
		for (final var iface : allInterfaces(source)) {
			final var converter = converters.get(new ConverterKey<>(iface, target));
			if (converter != null) {
				return converter;
			}
		}

		return null;
	}

	private static List<Class<?>> allInterfaces(final Class<?> type) {
		final var interfaces = new LinkedHashSet<Class<?>>();
		var current = type;
		while (current != null && current != Object.class) {
			for (final var iface : current.getInterfaces()) {
				collectInterfaces(iface, interfaces);
			}
			current = current.getSuperclass();
		}

		// A sub-interface must always be tried before its super-interfaces, even when the class declares the
		// super-interface first in its `implements` clause.
		final var ordered = new ArrayList<Class<?>>(interfaces.size());
		for (final var candidate : interfaces) {
			var insertAt = ordered.size();
			for (var i = 0; i < ordered.size(); i++) {
				if (ordered.get(i).isAssignableFrom(candidate)) {
					insertAt = i;
					break;
				}
			}
			ordered.add(insertAt, candidate);
		}
		return ordered;
	}

	private static void collectInterfaces(final Class<?> iface, final Set<Class<?>> target) {
		if (!target.add(iface)) {
			return;
		}

		for (final var parent : iface.getInterfaces()) {
			collectInterfaces(parent, target);
		}
	}

	private record ConverterKey<S, T>(Class<S> source, Class<T> target) {
	}
}
