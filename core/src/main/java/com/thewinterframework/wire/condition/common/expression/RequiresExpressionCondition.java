package com.thewinterframework.wire.condition.common.expression;

import com.thewinterframework.plugin.module.Stage;
import com.thewinterframework.wire.condition.ComponentCondition;
import com.thewinterframework.wire.condition.context.ConditionContext;

import java.lang.annotation.Annotation;

public class RequiresExpressionCondition implements ComponentCondition {
	@Override
	public Stage getStage() {
		return Stage.ENABLE;
	}

	@Override
	public boolean matches(final ConditionContext context, final Annotation rawAnnotation) {
		if (!(rawAnnotation instanceof final RequiresExpr annotation)) {
			return false;
		}

		final var resolver = context.getPlugin().getExpressionResolver();
		if (resolver == null) {
			return false;
		}

		final var expression = annotation.value();
		if (expression == null || expression.isBlank()) {
			// A blank expression carries no requirement at all, treating it as a failure would silently disable
			// a component that only forgot to fill the expression in.
			return true;
		}

		// The resolver returns {@code null} when the expression cannot be evaluated, so anything that is not
		// explicitly {@code true} is treated as a failed condition.
		return Boolean.TRUE.equals(resolver.resolve(expression, Boolean.class));
	}
}
