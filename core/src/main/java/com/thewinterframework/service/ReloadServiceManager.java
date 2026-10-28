package com.thewinterframework.service;

import com.google.inject.Inject;
import com.thewinterframework.component.ComponentManager;
import com.thewinterframework.plugin.WinterPlugin;
import com.thewinterframework.service.decorator.lifecycle.LifeCycleDecoratorHandler.LifeCycleResult;
import com.thewinterframework.service.decorator.lifecycle.OnReloadDecoratorHandler;

/**
 * Manages the reloading of services.
 */
public class ReloadServiceManager {

	private final ComponentManager componentManager;
	private final WinterPlugin plugin;

	@Inject
	public ReloadServiceManager(final ComponentManager componentManager, final WinterPlugin plugin) {
		this.componentManager = componentManager;
		this.plugin = plugin;
	}

	/**
	 * Adds a service to be reloaded.
	 *
	 * @param service       The service
	 * @param reloadService The reload service
	 */
	public void addOnReload(final Class<?> service, final Runnable reloadService) {
		componentManager.getOrCreateDecorator(OnReloadDecoratorHandler.class).addReloadMethod(service, reloadService);
	}

	/**
	 * Reloads all services.
	 *
	 * @return The result of the reload
	 */
	public LifeCycleResult reload() {
		// Nothing has to be reloaded when no component declares @OnReload, but asking for the handler keeps the
		// programmatic reload methods (see #addOnReload) working even when no component uses the decorator.
		return componentManager.getOrCreateDecorator(OnReloadDecoratorHandler.class).execute(plugin);
	}
}
