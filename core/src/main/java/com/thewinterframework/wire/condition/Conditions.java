package com.thewinterframework.wire.condition;

import com.google.inject.Injector;
import com.thewinterframework.plugin.module.Stage;
import com.thewinterframework.wire.condition.annotation.Requires;
import com.thewinterframework.wire.condition.context.ConditionContext;

import java.lang.reflect.AnnotatedElement;

/**
 * Evaluates the {@link Requires} conditions of a component or module.
 */
public final class Conditions {

	private Conditions() {
	}

	/**
	 * Checks every conditional annotation of the given element for the target stage.
	 * <p>
	 * Load stage conditions are created without the injector (they run before the injector exists), every other
	 * stage resolves the condition through it.
	 *
	 * @param element     the annotated element
	 * @param targetStage the stage being executed
	 * @param context     the condition context
	 * @param injector    the plugin injector, {@code null} while the load stage runs
	 * @return {@code true} when the element is active during the stage
	 * @throws Exception when a condition cannot be created or evaluated
	 */
	public static boolean matches(
			final AnnotatedElement element,
			final Stage targetStage,
			final ConditionContext context,
			final Injector injector
	) throws Exception {
		for (final var annotation : element.getAnnotations()) {
			final var conditional = annotation.annotationType().getAnnotation(Requires.class);
			if (conditional == null) {
				continue;
			}

			final var conditionType = conditional.value();
			final var condition = targetStage == Stage.LOAD || injector == null
					? conditionType.getDeclaredConstructor().newInstance()
					: injector.getInstance(conditionType);

			if (condition.getStage() == targetStage && !condition.matches(context, annotation)) {
				return false;
			}
		}

		return true;
	}
}
