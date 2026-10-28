package com.thewinterframework.service.decorator.injection;

import com.google.inject.Binder;
import com.google.inject.Singleton;
import com.thewinterframework.service.annotation.injection.Primary;
import com.thewinterframework.service.decorator.ServiceDecoratorHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings({"unchecked", "rawtypes"})
public class PrimaryHandler implements ServiceDecoratorHandler<Primary> {

	/**
	 * The service bound for each primary super type.
	 * <p>
	 * Insertion ordered so the resulting bindings (and their error messages) are deterministic.
	 */
	private final Map<Class<?>, Class<?>> bindAsMap = new LinkedHashMap<>();

	@Override
	public Class<Primary> getAnnotationType() {
		return Primary.class;
	}

	@Override
	public void onDiscoverOnType(final Class<?> service, final Primary annotation) {
		final var superType = annotation.value();
		if (superType != Void.class) {
			bind(superType, service);
			return;
		}

		final var interfaces = service.getInterfaces();
		if (interfaces.length == 0) {
			throw new IllegalStateException("Service " + service.getName() + " has no interfaces! and is annotated with @Primary");
		}

		// Every interface implemented by the service is bound, otherwise which interface becomes injectable would
		// depend on the (unrelated) declaration order of the {@code implements} clause.
		for (final var anInterface : interfaces) {
			if (!anInterface.equals(service)) {
				bind(anInterface, service);
			}
		}
	}

	/**
	 * Registers the given service as the primary implementation of the given super type.
	 *
	 * @param superType the type to bind
	 * @param service   the primary implementation
	 * @throws IllegalStateException if another service already claimed the super type, since picking one of the
	 *                              two would silently depend on the scan order
	 */
	private void bind(final Class<?> superType, final Class<?> service) {
		final var previous = bindAsMap.putIfAbsent(superType, service);
		if (previous != null && !previous.equals(service)) {
			throw new IllegalStateException(
					"Both " + previous.getName() + " and " + service.getName() + " are annotated with @Primary for "
							+ superType.getName() + ", use @Primary(" + superType.getSimpleName() + ".class) only on the one "
							+ "that must be injected"
			);
		}
	}

	@Override
	public void onConfigure(final Binder binder) {
		for (final var entry : bindAsMap.entrySet()) {
			final var superType = (Class) entry.getKey();
			final var impl = (Class) entry.getValue();

			if (superType.equals(impl)) {
				continue;
			}

			binder.bind(superType).to(impl).in(Singleton.class);
		}
	}
}
