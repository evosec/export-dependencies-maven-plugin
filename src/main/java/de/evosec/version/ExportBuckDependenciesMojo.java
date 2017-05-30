package de.evosec.version;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ExclusionSetFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;

@Mojo(name = "buck", threadSafe = true)
public class ExportBuckDependenciesMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	@Parameter(defaultValue = "${localRepository}", readonly = true)
	private ArtifactRepository localRepository;

	@Parameter(defaultValue = "${session}")
	private MavenSession session;

	@Parameter(
	        defaultValue = "${project.build.directory}/BUCK",
	        property = "outputFile")
	private File outputFile;

	@Component
	private DependencyGraphBuilder dependencyGraphBuilder;

	private DependencyNode dependencyGraph;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			Path outputPath = outputFile.toPath();

			Path parent = outputPath.getParent();
			if (parent != null && !Files.exists(parent)) {
				Files.createDirectories(parent);
			}

			if (session.getProjectDependencyGraph() != null) {
				List<MavenProject> reactorProjects =
				        session.getProjectDependencyGraph().getSortedProjects();

				Set<String> reactorArtifactIds =
				        reactorProjects.stream().map(m -> m.getArtifactId())
				                .filter(s -> !s.equalsIgnoreCase(
				                        project.getArtifactId()))
				        .collect(Collectors.toSet());

				dependencyGraph = dependencyGraphBuilder.buildDependencyGraph(
				        project, new ExclusionSetFilter(reactorArtifactIds),
				        reactorProjects);
			} else {
				dependencyGraph = dependencyGraphBuilder
				        .buildDependencyGraph(project, null, null);
			}

			List<String> output = new ArrayList<>();

			// add scope definitions first
			output.addAll(serializeDependencies(Artifact.SCOPE_COMPILE));
			output.addAll(serializeDependencies("optional"));
			output.addAll(serializeDependencies(Artifact.SCOPE_PROVIDED));
			output.addAll(serializeDependencies(Artifact.SCOPE_RUNTIME));
			output.addAll(serializeDependencies(Artifact.SCOPE_TEST));

			// add prebuilt_jar definitions
			collectDependencies(dependencyGraph, null).stream().sorted()
			        .map(this::serializeArtifact).forEach(output::addAll);

			Files.write(outputPath, output);
		} catch (DependencyGraphBuilderException e) {
			throw new MojoExecutionException(
			        "Cannot build project dependency graph", e);
		} catch (IOException exception) {
			throw new MojoExecutionException("Cannot write buck file",
			        exception);
		}
	}

	private List<String> serializeDependencies(String scope)
	        throws DependencyGraphBuilderException {
		Set<Artifact> dependencies =
		        collectDependencies(dependencyGraph, scope);

		List<String> result = new ArrayList<>();
		result.add("java_library(");
		result.add("  name = '" + scope.toUpperCase() + "',");
		result.add("  visibility = ['PUBLIC'],");
		result.add(dependencies.stream().map(this::artifactToDepsString)
		        .collect(Collectors.joining(",\n    ",
		                "  exported_deps = [\n    ", "\n  ],")));
		result.add(")");
		result.add("");

		return result;
	}

	private Set<Artifact> collectDependencies(DependencyNode node,
	        String scope) {
		Artifact artifact = node.getArtifact();
		Set<Artifact> result = new LinkedHashSet<>();

		if (project.getArtifact().equals(artifact)) {
			node.getChildren().stream().map(d -> collectDependencies(d, scope))
			        .forEach(result::addAll);
		} else if (scope == null) {
			result.add(artifact);
			node.getChildren().stream().map(d -> collectDependencies(d, null))
			        .forEach(result::addAll);
		} else if (artifact.isOptional() && "optional".equals(scope)) {
			result.add(artifact);
			node.getChildren().stream().map(d -> collectDependencies(d, null))
			        .forEach(result::addAll);
		} else if (artifact.isOptional()) {
			return result;
		} else if (scope.equals(artifact.getScope())) {
			result.add(artifact);
			node.getChildren().stream().map(d -> collectDependencies(d, scope))
			        .forEach(result::addAll);
		} else {
			node.getChildren().stream().map(d -> collectDependencies(d, scope))
			        .forEach(result::addAll);
		}

		return result;
	}

	private List<String> serializeArtifact(Artifact artifact) {
		List<String> result = new ArrayList<>();

		String classifier = getClassifier(artifact);

		String name = String.format("%s-%s-%s", artifact.getGroupId(),
		        artifact.getArtifactId(), classifier);
		String out =
		        String.format("%s-%s.jar", name, artifact.getBaseVersion());
		String url = String.format("mvn:%s:%s:%s:%s", artifact.getGroupId(),
		        artifact.getArtifactId(), classifier, artifact.getVersion());
		String sha1 = getSha1(artifact);

		result.add("prebuilt_jar(");
		result.add("  name = '" + name + "',");
		result.add("  binary_jar = ':remote-" + name + "',");
		result.add(")");
		result.add("");

		result.add("remote_file(");
		result.add("  name = 'remote-" + name + "',");
		result.add("  out = '" + out + "',");
		result.add("  url = '" + url + "',");
		result.add("  sha1 = '" + sha1 + "',");
		result.add(")");
		result.add("");

		return result;
	}

	private String artifactToDepsString(Artifact artifact) {
		return String.format("':%s-%s-%s'", artifact.getGroupId(),
		        artifact.getArtifactId(), getClassifier(artifact));
	}

	private String getClassifier(Artifact artifact) {
		String classifier = artifact.getClassifier();
		if (classifier == null || "".equalsIgnoreCase(classifier)) {
			return "jar";
		}
		return classifier;
	}

	private String getSha1(Artifact artifact) {
		try {
			Path path = Paths.get(localRepository.getBasedir(),
			        localRepository.pathOf(artifact) + ".sha1");
			if (Files.exists(path)) {
				// The sha1 file sometimes has stuff after the sha. Example:
				// https://repo1.maven.org/maven2/javax/annotation/jsr250-api/1.0/jsr250-api-1.0.jar.sha1
				String firstLine = Files.readAllLines(path).get(0);
				return firstLine.split(" ")[0];
			} else {
				Path artifactPath = Paths.get(localRepository.getBasedir(),
				        localRepository.pathOf(artifact));
				return sha1(artifactPath);
			}
		} catch (Exception e) {
			getLog().error(e);
		}
		return "";
	}

	private static String sha1(final Path path)
	        throws NoSuchAlgorithmException, IOException {
		final MessageDigest messageDigest = MessageDigest.getInstance("SHA1");

		try (InputStream is =
		        new BufferedInputStream(Files.newInputStream(path))) {
			final byte[] buffer = new byte[1024];
			for (int read = 0; (read = is.read(buffer)) != -1;) {
				messageDigest.update(buffer, 0, read);
			}
		}

		// Convert the byte to hex format
		try (Formatter formatter = new Formatter()) {
			for (final byte b : messageDigest.digest()) {
				formatter.format("%02x", b);
			}
			return formatter.toString();
		}
	}

}
