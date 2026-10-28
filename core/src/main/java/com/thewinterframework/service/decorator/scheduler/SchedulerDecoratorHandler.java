package com.thewinterframework.service.decorator.scheduler;

import com.thewinterframework.plugin.WinterPlugin;
import com.thewinterframework.service.decorator.ServiceDecoratorHandler;
import com.thewinterframework.service.meta.scheduler.SchedulerMethod;
import com.thewinterframework.utils.reflect.AnnotatedMethodHandle;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

public abstract class SchedulerDecoratorHandler<A extends Annotation> implements ServiceDecoratorHandler<A> {

	private final List<SchedulerMethod> registeredMethods = new ArrayList<>();
	private final List<Integer> scheduledTaskIds = new ArrayList<>();

	@Override
	public void onDiscover(final Class<?> service, final AnnotatedMethodHandle<A> method) {
		final var annotation = method.annotation();
		registeredMethods.add(map(service, annotation, method));
	}

	protected abstract SchedulerMethod map(Class<?> service, A annotation, AnnotatedMethodHandle<A> method);

	@Override
	public void onPluginEnable(final WinterPlugin plugin) {
		if (!scheduledTaskIds.isEmpty()) {
			// The tasks were already scheduled, scheduling them again would duplicate every execution.
			return;
		}

		try {
			for (final var method : registeredMethods) {
				scheduledTaskIds.add(method.schedule(plugin));
			}
		} catch (final RuntimeException e) {
			// Do not leave half of the tasks running behind a failed enable.
			cancelTasks(plugin);
			throw e;
		}
	}

	@Override
	public void onPluginDisable(final WinterPlugin plugin) {
		cancelTasks(plugin);
	}

	/**
	 * Cancels every task scheduled by this handler.
	 *
	 * @param plugin the plugin which owns the tasks
	 */
	protected void cancelTasks(final WinterPlugin plugin) {
		for (final var taskId : scheduledTaskIds) {
			try {
				plugin.getScheduler().cancelTask(taskId);
			} catch (final RuntimeException e) {
				plugin.getSLF4JLogger().warn("Failed to cancel scheduled task {}", taskId, e);
			}
		}

		scheduledTaskIds.clear();
	}
}
