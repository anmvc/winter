package com.thewinterframework.service.annotation.lifecycle;

import com.thewinterframework.service.annotation.Service;
import com.thewinterframework.service.decorator.ServiceDecorator;
import com.thewinterframework.service.decorator.lifecycle.OnDisableDecoratorHandler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a method should be called when the plugin is disabled.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ServiceDecorator(OnDisableDecoratorHandler.class)
public @interface OnDisable {

	/**
	 * The services whose disable methods must be executed after this method.
	 * @return The services this method is executed before
	 */
	Class<?>[] before() default {};
}