package com.thewinterframework.service.annotation.lifecycle;

import com.thewinterframework.service.decorator.ServiceDecorator;
import com.thewinterframework.service.decorator.lifecycle.OnReloadDecoratorHandler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be executed when the service is reloaded.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ServiceDecorator(OnReloadDecoratorHandler.class)
public @interface OnReload {
	/**
	 * The services whose reload methods must be executed before this method.
	 * @return The services this method is executed after
	 */
	Class<?>[] after() default {};

	/**
	 * The services whose reload methods must be executed after this method.
	 * @return The services this method is executed before
	 */
	Class<?>[] before() default {};
}
