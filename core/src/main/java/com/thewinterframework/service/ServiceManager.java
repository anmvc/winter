package com.thewinterframework.service;

import com.google.inject.Binder;
import com.thewinterframework.component.ComponentManager;
import com.thewinterframework.component.meta.ComponentDescriptor;
import com.thewinterframework.plugin.WinterPlugin;
import com.thewinterframework.service.decorator.ServiceDecoratorHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compatibility facade for the pre-3.0 service registry.
 *
 * @deprecated Inject {@link ComponentManager} instead.
 */
@Deprecated(forRemoval = false)
public class ServiceManager extends ComponentManager {

	public void registerService(final @NotNull Class<?> service) throws ReflectiveOperationException {
		registerComponent(ComponentDescriptor.from(service));
	}

	public void loadHandlers(final WinterPlugin plugin) {
		loadDecorators(plugin);
	}

	public void configureHandlers(final Binder binder) {
		configureDecorators(binder);
	}

	public void startHandlers(final WinterPlugin plugin) {
		enableDecorators(plugin);
	}

	public void stopHandlers(final WinterPlugin plugin) {
		disableDecorators(plugin);
	}

	@Nullable
	public <T extends ServiceDecoratorHandler<?>> T getHandler(final Class<T> handlerType) {
		return getDecorator(handlerType);
	}

	/**
	 * Gets the handler of the given type, creating it when no component used its decorator yet.
	 *
	 * @param handlerType the decorator handler class
	 * @param <T>         the type of the decorator handler
	 * @return the handler, never {@code null}
	 * @deprecated use {@link ComponentManager#getOrCreateDecorator(Class)}
	 */
	@Deprecated(forRemoval = false)
	@NotNull
	public <T extends ServiceDecoratorHandler<?>> T getOrCreateHandler(final Class<T> handlerType) {
		return getOrCreateDecorator(handlerType);
	}

	public Set<Class<?>> services() {
		return components().stream().map(ComponentDescriptor::type).collect(Collectors.toUnmodifiableSet());
	}
}
