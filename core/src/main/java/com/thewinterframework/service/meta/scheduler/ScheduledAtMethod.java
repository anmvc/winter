package com.thewinterframework.service.meta.scheduler;

import com.thewinterframework.plugin.WinterPlugin;
import com.thewinterframework.utils.TimeUnit;
import com.thewinterframework.utils.reflect.AnnotatedMethodHandle;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNull;

/**
 * Schedules a method to be executed at a fixed time of the day.
 */
public record ScheduledAtMethod(
		Class<?> service,
		AnnotatedMethodHandle<?> method,
		List<ScheduledAtTime> schedules
) implements SchedulerMethod {

	public record ScheduledAtTime(
			String hour,
			String minute,
			String second,
			String async
	) {
	}

	public record ParsedScheduledAtTime(
			int hour,
			int minute,
			int second
	) {
	}

	@Override
	public int schedule(final WinterPlugin plugin) {
		final var scheduler = plugin.getScheduler();
		final var expressionResolver = plugin.getExpressionResolver();

		var shouldBeAsync = false;
		for (final var schedule : schedules) {
			final var asyncParsed = expressionResolver.resolve(schedule.async, Boolean.class);
			if (asyncParsed != null && asyncParsed) {
				shouldBeAsync = true;
				break;
			}
		}

		final var parsedSchedules = schedules.stream()
				.map(schedule -> {
					final var hourParsed = requireNonNull(
							expressionResolver.resolve(schedule.hour, Integer.class),
							"Could not resolve the hour of a @ScheduledAt annotation"
					);
					final var minuteParsed = expressionResolver.resolve(schedule.minute, Integer.class);
					final var secondParsed = expressionResolver.resolve(schedule.second, Integer.class);

					return new ParsedScheduledAtTime(
							hourParsed,
							minuteParsed != null ? minuteParsed : 0,
							secondParsed != null ? secondParsed : 0
					);
				})
				.toList();

		// The scheduler only offers fixed rate tasks, so the current time is polled once per second. Every second
		// between two polls is inspected, otherwise a delayed poll (server lag) could silently skip a schedule.
		final var lastPolledSecond = new AtomicLong(Long.MIN_VALUE);
		final Runnable task = () -> {
			final var pluginTimeZone = plugin.getZoneId();
			final var now = ZonedDateTime.now(pluginTimeZone);
			final var currentSecond = now.toEpochSecond();
			final var previousSecond = lastPolledSecond.getAndSet(currentSecond);
			final var firstSecond = previousSecond == Long.MIN_VALUE ? currentSecond : previousSecond + 1;

			for (var second = firstSecond; second <= currentSecond; second++) {
				final var time = Instant.ofEpochSecond(second).atZone(pluginTimeZone);
				final var isScheduledTime = parsedSchedules.stream()
						.anyMatch(schedule ->
								schedule.hour() == time.getHour() &&
										schedule.minute() == time.getMinute() &&
										schedule.second() == time.getSecond()
						);

				if (!isScheduledTime) {
					continue;
				}

				try {
					method.invoke(service, plugin.getInjector());
				} catch (final Throwable e) {
					plugin.getSLF4JLogger().error("Error while executing scheduled task", e);
				}
			}
		};

		if (shouldBeAsync) {
			return scheduler.runAtFixedRateAsync(task, 0, 1, TimeUnit.SECONDS);
		} else {
			return scheduler.runAtFixedRateSync(task, 0, 1, TimeUnit.SECONDS);
		}
	}
}
