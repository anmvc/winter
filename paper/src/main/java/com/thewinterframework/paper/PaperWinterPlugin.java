package com.thewinterframework.paper;

import com.google.inject.*;
import com.google.inject.Module;
import com.thewinterframework.expression.ExpressionResolver;
import com.thewinterframework.expression.JexlExpressionResolver;
import com.thewinterframework.paper.platform.PaperPlatform;
import com.thewinterframework.paper.scheduler.PaperPluginScheduler;
import com.thewinterframework.plugin.DataFolder;
import com.thewinterframework.plugin.WinterPlugin;
import com.thewinterframework.plugin.module.PluginModuleManager;
import com.thewinterframework.plugin.platform.PlatformPluginManager;
import com.thewinterframework.scheduler.PluginScheduler;
import net.kyori.adventure.key.KeyPattern;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A base class for Winter plugins that use Paper.
 */
public abstract class PaperWinterPlugin extends JavaPlugin implements WinterPlugin {

	protected final PluginModuleManager moduleManager = new PluginModuleManager(this);
	protected final PluginScheduler scheduler = new PaperPluginScheduler(this);
	protected final PlatformPluginManager platformPluginManager = new PaperPlatform();

	protected ExpressionResolver expressionResolver = new JexlExpressionResolver();

	protected @Inject Injector injector;

	private boolean modulesLoaded;
	private boolean enabled;
	private boolean stopped;

	@Override
	public final void onLoad() {
		final var startTime = System.currentTimeMillis();
		stopped = false;

		try {
			loadModules();
		} catch (final RuntimeException | LinkageError e) {
			getSLF4JLogger().error("Failed to load the plugin", e);
			failFast();
			return;
		}

		getSLF4JLogger().info("Plugin loaded in {}ms", System.currentTimeMillis() - startTime);
	}

	/**
	 * Scans and loads the Winter modules, this is only performed once per load cycle.
	 *
	 * @throws IllegalStateException if the modules cannot be loaded
	 */
	private void loadModules() {
		if (modulesLoaded) {
			return;
		}

		if (!moduleManager.scanModules() || !moduleManager.loadModules()) {
			throw new IllegalStateException("Failed to load the Winter modules");
		}

		modulesLoaded = true;
		onPluginLoad();
	}

	@Override
	public final void onEnable() {
		if (enabled) {
			return;
		}

		final var startTime = System.currentTimeMillis();
		stopped = false;

		try {
			// The platform can enable a plugin without loading it again (for instance after a /reload), so the
			// modules are loaded here too instead of failing when the load phase did not run.
			loadModules();

			injector = createInjector();
			injector.injectMembers(this);

			if (!moduleManager.injectModules() || !moduleManager.enableModules()) {
				throw new IllegalStateException("Failed to enable the Winter modules");
			}

			enabled = true;
			onPluginEnable();
			getSLF4JLogger().info("Plugin enabled in {}ms", System.currentTimeMillis() - startTime);
		} catch (final RuntimeException | LinkageError e) {
			getSLF4JLogger().error("Failed to enable the plugin", e);
			failFast(e);
		}
	}

	/**
	 * Releases everything that was already started and asks the platform to disable the plugin.
	 *
	 * @param failure the failure that triggered the shutdown, may be {@code null}
	 */
	private void failFast(final Throwable failure) {
		try {
			shutdown();
		} catch (final RuntimeException | LinkageError cleanupFailure) {
			if (failure != null) {
				failure.addSuppressed(cleanupFailure);
			} else {
				getSLF4JLogger().error("Failed to release the plugin resources", cleanupFailure);
			}
		}

		Bukkit.getPluginManager().disablePlugin(this);
	}

	protected Injector createInjector() {
		return Guice.createInjector(getGuiceStage(), getGuiceModules());
	}

	@Override
	public final void onDisable() {
		shutdown();
	}

	/**
	 * Disables the loaded modules and releases every resource held by the plugin.
	 * <p>
	 * This method is idempotent, it is called both when the platform disables the plugin and when the enable
	 * phase fails halfway through.
	 */
	private void shutdown() {
		if (stopped) {
			return;
		}

		stopped = true;
		final var startTime = System.currentTimeMillis();

		try {
			if (modulesLoaded && !moduleManager.disableModules()) {
				getSLF4JLogger().error("Failed to disable the Winter modules");
			}

			modulesLoaded = false;
			if (enabled) {
				enabled = false;
				onPluginDisable();
			}
		} finally {
			scheduler.cancelAllTasks();
		}

		getSLF4JLogger().info("Plugin disabled in {}ms", System.currentTimeMillis() - startTime);
	}

	@Override
	public PluginScheduler getScheduler() {
		return scheduler;
	}

	@Override
	public ExpressionResolver getExpressionResolver() {
		return expressionResolver;
	}

	@Override
	public void setExpressionResolver(final ExpressionResolver resolver) {
		this.expressionResolver = resolver;
	}

	@Override
	public Injector getInjector() {
		return injector;
	}

	@Override
	public @NotNull Logger getSLF4JLogger() {
		return super.getSLF4JLogger();
	}

	@Override
	public final PluginModuleManager getModuleManager() {
		return moduleManager;
	}

	@Override
	public PlatformPluginManager getPlatformManager() {
		return platformPluginManager;
	}

	protected List<Module> getGuiceModules() {
		final var modules = new ArrayList<Module>();
		modules.add(binder -> {
			binder.bind(WinterPlugin.class).toInstance(this);
			binder.bind(Plugin.class).toInstance(this);
			binder.bind(JavaPlugin.class).toInstance(this);
			binder.bind(Logger.class).toInstance(this.getSLF4JLogger());
			binder.bind(PluginScheduler.class).toInstance(scheduler);
			binder.bind(PlatformPluginManager.class).toInstance(platformPluginManager);
			binder.bind(ExpressionResolver.class).toInstance(expressionResolver);
			binder.bind(Path.class).annotatedWith(DataFolder.class).toInstance(this.getDataFolder().toPath());
		});

		modules.addAll(moduleManager.asGuiceModules());
		modules.add(this);

		return modules;
	}

	protected Stage getGuiceStage() {
		return Stage.PRODUCTION;
	}

	@Override
	@KeyPattern.Namespace
	@SuppressWarnings("all") // lol
	public @NotNull String namespace() {
		// Locale.ROOT: a lowercase performed with the server locale can produce characters that are not valid
		// inside an adventure key (for example the Turkish dotless i).
		return this.getName().toLowerCase(Locale.ROOT);
	}
}
