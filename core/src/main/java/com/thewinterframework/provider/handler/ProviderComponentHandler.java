package com.thewinterframework.provider.handler;

import com.google.auto.service.AutoService;
import com.google.inject.Binder;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.thewinterframework.component.handler.ComponentHandler;
import com.thewinterframework.component.meta.ComponentDescriptor;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collection;

/** Adds Guice provider bindings for provider components. */
@AutoService(ComponentHandler.class)
public final class ProviderComponentHandler implements ComponentHandler {

	@Override
	public boolean supports(final Class<?> componentType) {
		return Provider.class.isAssignableFrom(componentType);
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void configure(final Binder binder, final Collection<ComponentDescriptor> components) {
		for (final var component : components) {
			final var providerType = (Class<? extends Provider<?>>) component.type();
			final var providedType = resolveProvidedType(providerType);
			binder.bind((TypeLiteral) TypeLiteral.get(providedType)).toProvider((Class) providerType);
		}
	}

	/**
	 * Resolves the type provided by the given provider class.
	 * <p>
	 * A provider does not have to declare {@code Provider<T>} itself: it can extend a base provider, so the type is
	 * resolved through the class hierarchy instead of looking at the first generic interface only.
	 *
	 * @param providerType the provider class
	 * @return the provided type
	 * @throws IllegalStateException if the provided type cannot be resolved
	 */
	private Type resolveProvidedType(final Class<? extends Provider<?>> providerType) {
		try {
			final var providedType = TypeLiteral.get(providerType).getSupertype(Provider.class).getType();
			if (providedType instanceof final ParameterizedType parameterized
					&& !(parameterized.getActualTypeArguments()[0] instanceof TypeVariable<?>)) {
				return parameterized.getActualTypeArguments()[0];
			}
		} catch (final RuntimeException e) {
			throw new IllegalStateException("Could not resolve the provided type of " + providerType.getName(), e);
		}

		throw new IllegalStateException(
				"Could not resolve the provided type of " + providerType.getName()
						+ ", make sure it implements Provider with a concrete type argument"
		);
	}
}
