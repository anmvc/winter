package com.thewinterframework.service.annotation.lifecycle;

import com.thewinterframework.component.decorator.ComponentDecorator;
import com.thewinterframework.service.decorator.lifecycle.OnEnableDecoratorHandler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be executed when the service is enabled.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ComponentDecorator(OnEnableDecoratorHandler.class)
public @interface OnEnable {

	/**
	 * The services whose enable methods must be executed before this method.
	 * @return The services this method is executed after
	 */
	Class<?>[] after() default {};
}
