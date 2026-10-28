package com.thewinterframework.plugin.platform;

public interface PlatformPluginManager {

	/**
	 * Checks whether a plugin is enabled on the platform.
	 *
	 * @param pluginName the name of the plugin
	 * @return {@code true} if the plugin exists and is enabled
	 */
	boolean isPluginEnabled(final String pluginName);

	/**
	 * Checks whether a plugin is available, even if it is not enabled yet.
	 * <p>
	 * This is the check used by the {@code @RequiresPlugin} condition, which is evaluated before the
	 * platform enables its plugins, so {@link #isPluginEnabled(String)} would report every plugin as
	 * disabled at that point.
	 *
	 * @param pluginName the name of the plugin
	 * @return {@code true} if the plugin is present on the platform
	 */
	default boolean isPluginPresent(final String pluginName) {
		return isPluginEnabled(pluginName);
	}

}
