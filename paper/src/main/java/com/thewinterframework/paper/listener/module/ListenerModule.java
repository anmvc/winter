package com.thewinterframework.paper.listener.module;

import com.google.inject.Binder;
import com.thewinterframework.paper.listener.ListenerComponent;
import com.thewinterframework.plugin.WinterPlugin;
import com.thewinterframework.wire.module.AbstractProcessorModule;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A module that handles listener components.
 */
public class ListenerModule extends AbstractProcessorModule {

	/**
	 * The listeners registered by this module.
	 * <p>
	 * Keeping track of them allows unregistering only the listeners owned by the framework: a plugin is free to
	 * register its own listeners with {@code PluginManager#registerEvents} and those must not be removed.
	 */
	private final Map<Class<?>, Listener> registeredListeners = new ConcurrentHashMap<>();

	public ListenerModule() {
		super(ListenerComponent.class);
	}

	@Override
	public void configure(final Binder binder) {
	}

	@Override
	protected void enableComponent(final WinterPlugin plugin, final Class<?> componentClass) {
		final var javaPlugin = (JavaPlugin) plugin;
		final var listener = (Listener) plugin.getInjector().getInstance(componentClass);

		// A second enable of the same component must not register the listener twice, otherwise every event
		// would be handled more than once.
		if (registeredListeners.putIfAbsent(componentClass, listener) == null) {
			Bukkit.getServer().getPluginManager().registerEvents(listener, javaPlugin);
		}
	}

	@Override
	protected void disableComponent(final WinterPlugin plugin, final Class<?> componentClass) {
		// The instance is taken from the map instead of the injector: the disable phase may run while the
		// injector is being torn down (for instance when the enable phase fails halfway through).
		final var listener = registeredListeners.remove(componentClass);
		if (listener != null) {
			HandlerList.unregisterAll(listener);
		}
	}
}
