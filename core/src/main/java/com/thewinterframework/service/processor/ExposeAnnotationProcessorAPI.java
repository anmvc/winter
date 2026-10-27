package com.thewinterframework.service.processor;

import com.google.auto.service.AutoService;
import com.thewinterframework.plugin.WinterBootPlugin;
import com.thewinterframework.processor.WinterProcessor;
import com.thewinterframework.processor.context.ProcessorContext;
import com.thewinterframework.processor.template.TemplateBuilder;
import com.thewinterframework.service.annotation.expose.Expose;
import com.thewinterframework.utils.reflect.ProcessorUtils;
import com.thewinterframework.utils.reflect.ProcessorUtils.ProjectType;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardLocation;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import static com.thewinterframework.service.processor.ExposeAnnotationProcessor.ELEMENT_PATTERN;

/**
 * Generates the {@code <PLUGIN>API} interface of a plugin from the classes annotated with {@link Expose}.
 * <p>
 * This processor is meant to run in the API project (or in the main project when {@code bypass.api} is
 * enabled). The generated interface is then implemented by
 * {@link ExposeAnnotationProcessor} in the implementation project.
 */
@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedOptions({
		"plugin.type",
		"plugin.package",
		"plugin.name",
		"bypass.api"
})
public class ExposeAnnotationProcessorAPI extends AbstractProcessor {

	private static final String EXPOSED_CLASSES_RESOURCE = "META-INF/winter/exposed-classes.txt";

	private boolean alreadyGenerated;

	@Override
	public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
		if (ProcessorUtils.getProjectType(processingEnv) == ProjectType.IMPL && !isAPIBypassed()) {
			return false;
		}

		if (roundEnv.processingOver() || annotations.isEmpty() || alreadyGenerated) {
			return false;
		}

		final Element pluginElement = isAPIBypassed() ? WinterProcessor.getPluginClass(roundEnv, processingEnv) : null;
		if (isAPIBypassed() && pluginElement == null) {
			processingEnv.getMessager().printError(
					"The API generation is bypassed but " + WinterBootPlugin.class.getCanonicalName()
							+ " annotation was not found in the project!"
			);
			return false;
		}

		final var pluginPkg = getPluginPkg(pluginElement);
		final var pluginName = getPluginName(pluginElement);

		final var ctx = new ProcessorContext(processingEnv, null, roundEnv);

		final var validElements = new TreeSet<TypeElement>(
				Comparator.comparing(element -> element.getQualifiedName().toString())
		);
		for (final var element : roundEnv.getElementsAnnotatedWith(Expose.class)) {
			// Only types can be exposed, the annotation itself is already restricted to types but a misused
			// annotation (for instance on a field of a type that is being generated) must not break the build.
			if (element instanceof final TypeElement typeElement) {
				validElements.add(typeElement);
			}
		}

		if (validElements.isEmpty()) {
			return false;
		}

		alreadyGenerated = true;

		generateInterface(ctx, pluginPkg, pluginName, validElements);
		writeExposedClassesFile(validElements);

		return true;
	}

	private void writeExposedClassesFile(final Iterable<TypeElement> elements) {
		try {
			final var builder = new StringBuilder();
			for (final var element : elements) {
				builder.append(element.getQualifiedName().toString()).append('\n');
			}

			final var file = processingEnv.getFiler()
					.createResource(StandardLocation.CLASS_OUTPUT, "", EXPOSED_CLASSES_RESOURCE);
			try (final var writer = file.openWriter()) {
				writer.write(builder.toString());
			}
		} catch (final Exception e) {
			processingEnv.getMessager().printError("Failed to write exposed classes to file: " + e.getMessage());
		}
	}

	private boolean isAPIBypassed() {
		return Boolean.parseBoolean(getOption("bypass.api", "false"));
	}

	private String getPluginPkg(final Element pluginElement) {
		if (pluginElement != null) {
			return processingEnv.getElementUtils().getPackageOf(pluginElement).getQualifiedName().toString();
		}

		return getOption("plugin.package", "com.default.api");
	}

	private String getPluginName(final Element pluginElement) {
		if (pluginElement != null) {
			return pluginElement.getSimpleName().toString();
		}

		return getOption("plugin.name", "Default");
	}

	private String getOption(final String key, final String defaultValue) {
		return processingEnv.getOptions().getOrDefault(key, defaultValue);
	}

	@SuppressWarnings("DuplicatedCode")
	private void generateInterface(final ProcessorContext ctx, final String pkg, final String pluginName, final Iterable<TypeElement> elements) {
		TemplateBuilder.fromResource("generated/ExposeInterfaceTemplate.java")
				.placeholder("PACKAGE", pkg)
				.placeholder("PLUGIN", pluginName)
				.custom(content -> {
					final var matcher = ELEMENT_PATTERN.matcher(content);
					final var result = new StringBuilder();
					int lastEnd = 0;

					while (matcher.find()) {
						result.append(content, lastEnd, matcher.start());
						final var block = matcher.group(1);
						final var builder = new StringBuilder();

						for (final var element : elements) {
							final String typeName = element.getSimpleName().toString();
							builder.append(block
									.replace("<TYPE>", element.getQualifiedName())
									.replace("<TYPE_NAME>", typeName)
							).append(System.lineSeparator());
						}
						result.append(builder);
						lastEnd = matcher.end();
					}
					result.append(content.substring(lastEnd));
					return result.toString();
				})
				.write(ctx, pkg + "." + pluginName + "API");
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return Set.of(Expose.class.getCanonicalName(), WinterBootPlugin.class.getCanonicalName());
	}
}
