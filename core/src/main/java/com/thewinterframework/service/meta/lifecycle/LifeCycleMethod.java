package com.thewinterframework.service.meta.lifecycle;

/**
 * Represents a lifecycle method.
 */
public interface LifeCycleMethod {

	/**
	 * The service that this method is associated with.
	 * @return The service
	 */
	Class<?> service();

	/**
	 * The services this method must be executed before.
	 * @return The services this method is executed before
	 */
	Class<?>[] before();

	/**
	 * The services this method must be executed after.
	 * @return The services this method is executed after
	 */
	Class<?>[] after();
}
