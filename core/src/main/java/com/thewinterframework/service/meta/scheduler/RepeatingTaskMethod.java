package com.thewinterframework.service.meta.scheduler;

import com.thewinterframework.plugin.WinterPlugin;
import com.thewinterframework.utils.TimeUnit;
import com.thewinterframework.utils.reflect.AnnotatedMethodHandle;

import static java.util.Objects.requireNonNull;

/**
 * Represents a repeating task method
 *
 * @param service The service class
 * @param method  The method to execute
 * @param delay   The delay before the task starts
 * @param every   The delay between each execution
 * @param unit    The time unit of the delay
 * @param async   Whether the task should be executed
 */
public record RepeatingTaskMethod(
		Class<?> service,
		AnnotatedMethodHandle<?> method,
		String delay,
		String every,
		String unit,
		String async
) implements SchedulerMethod {
	@Override
	public int schedule(final WinterPlugin plugin) {
		final var scheduler = plugin.getScheduler();
		final var expressionResolver = plugin.getExpressionResolver();

		final var delayParsed = requireNonNull(
				expressionResolver.resolve(delay, Long.class),
				() -> "Could not resolve the delay of the repeating task of " + service.getName()
		);
		final var everyParsed = requireNonNull(
				expressionResolver.resolve(every, Long.class),
				() -> "Could not resolve the interval of the repeating task of " + service.getName()
		);
		final var unitParsed = requireNonNull(
				expressionResolver.resolve(unit, TimeUnit.class),
				() -> "Could not resolve the time unit '" + unit + "' of the repeating task of " + service.getName()
		);
		final var asyncParsed = requireNonNull(
				expressionResolver.resolve(async, Boolean.class),
				() -> "Could not resolve the async flag of the repeating task of " + service.getName()
		);

		if (delayParsed < 0) {
			throw new IllegalArgumentException("The delay of a repeating task cannot be negative: " + delay);
		}

		if (everyParsed < 0) {
			throw new IllegalArgumentException("The interval of a repeating task cannot be negative: " + every);
		}

		final Runnable task = () -> {
			try {
				method.invoke(service, plugin.getInjector());
			} catch (final Throwable e) {
				plugin.getSLF4JLogger().error("Error while executing repeating task", e);
			}
		};

		if (asyncParsed) {
			return scheduler.runAtFixedRateAsync(task, delayParsed, everyParsed, unitParsed);
		}

		return scheduler.runAtFixedRateSync(task, delayParsed, everyParsed, unitParsed);
	}
}
