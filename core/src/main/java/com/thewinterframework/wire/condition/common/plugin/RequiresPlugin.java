package com.thewinterframework.wire.condition.common.plugin;

import com.thewinterframework.wire.condition.annotation.Requires;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p> Registers a component only when the plugin with the given name is present on the platform. </p>
 * <p> The condition is evaluated in the {@link com.thewinterframework.plugin.module.Stage#LOAD load stage}, before the
 * platform enables its plugins, therefore the (optional) dependency must also be declared in the platform plugin
 * descriptor so its classes are visible. </p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Requires(RequiresPluginCondition.class)
public @interface RequiresPlugin {
	String value();
}
