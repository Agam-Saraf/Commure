package com.commure.backend.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Loads the repo-root {@code .env} (shell {@code export KEY='v'} or plain {@code KEY=v}) as the
 * lowest-precedence property source, so real environment variables always win.
 */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor {

	static final String PROPERTY_SOURCE_NAME = "dotenv";

	private static final List<Path> CANDIDATES = List.of(Path.of(".env"), Path.of("..", ".env"));

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		CANDIDATES.stream()
				.filter(Files::isRegularFile)
				.findFirst()
				.ifPresent(path -> environment.getPropertySources()
						.addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, parse(readLines(path)))));
	}

	static Map<String, Object> parse(List<String> lines) {
		Map<String, Object> values = new LinkedHashMap<>();
		for (String raw : lines) {
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			if (line.startsWith("export ")) {
				line = line.substring("export ".length()).strip();
			}
			int eq = line.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			values.put(line.substring(0, eq).strip(), unquote(line.substring(eq + 1).strip()));
		}
		return values;
	}

	private static String unquote(String value) {
		if (value.length() >= 2) {
			char first = value.charAt(0);
			if ((first == '\'' || first == '"') && value.charAt(value.length() - 1) == first) {
				return value.substring(1, value.length() - 1);
			}
		}
		return value;
	}

	private static List<String> readLines(Path path) {
		try {
			return Files.readAllLines(path);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read " + path.toAbsolutePath(), ex);
		}
	}
}
