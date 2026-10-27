package com.thewinterframework.paper.listener.handler;

import com.google.auto.service.AutoService;
import com.thewinterframework.component.context.ComponentContext;
import com.thewinterframework.component.handler.ComponentHandler;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Registers every listener declared with the unified component annotation. */
@AutoService(ComponentHandler.class)
public final class ListenerComponentHandler implements ComponentHandler {

	/**
	 * The listeners that are currently registered.
	 * <p>
	 * Keeping track of them makes the enable phase idempotent: registering the same listener twice would make every
	 * event be handled twice.
	 */
	private final Set<Listener> registered = ConcurrentHashMap.newKeySet();

	@Override
	public boolean supports(final Class<?> componentType) {
		return Listener.class.isAssignableFrom(componentType);
	}

	@Override
	public void onEnable(final ComponentContext context) {
		final var listener = (Listener) context.instance();
		if (registered.add(listener)) {
			Bukkit.getPluginManager().registerEvents(listener, (JavaPlugin) context.plugin());
		}
	}

	@Override
	public void onDisable(final ComponentContext context) {
		final var listener = (Listener) context.instance();
		if (registered.remove(listener)) {
			HandlerList.unregisterAll(listener);
		}
	}
}
