package de.evosec.version;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;

/**
 * Filter to only retain objects in the given scope.
 */
public class SingleScopeArtifactFilter implements ArtifactFilter {

	private boolean compileScope;
	private boolean runtimeScope;
	private boolean testScope;
	private boolean providedScope;
	private boolean systemScope;

	public SingleScopeArtifactFilter(String scope) {
		if (Artifact.SCOPE_COMPILE.equals(scope)) {
			compileScope = true;
		} else if (Artifact.SCOPE_COMPILE_PLUS_RUNTIME.equals(scope)) {
			compileScope = true;
			runtimeScope = true;
		} else if (Artifact.SCOPE_PROVIDED.equals(scope)) {
			providedScope = true;
		} else if (Artifact.SCOPE_RUNTIME.equals(scope)) {
			runtimeScope = true;
		} else if (Artifact.SCOPE_RUNTIME_PLUS_SYSTEM.equals(scope)) {
			systemScope = true;
			runtimeScope = true;
		} else if (Artifact.SCOPE_SYSTEM.equals(scope)) {
			testScope = true;
		} else if (Artifact.SCOPE_TEST.equals(scope)) {
			testScope = true;
		}
	}

	@Override
	public boolean include(Artifact artifact) {
		if (Artifact.SCOPE_COMPILE.equals(artifact.getScope())) {
			return compileScope;
		} else if (Artifact.SCOPE_PROVIDED.equals(artifact.getScope())) {
			return providedScope;
		} else if (Artifact.SCOPE_RUNTIME.equals(artifact.getScope())) {
			return runtimeScope;
		} else if (Artifact.SCOPE_SYSTEM.equals(artifact.getScope())) {
			return systemScope;
		} else if (Artifact.SCOPE_TEST.equals(artifact.getScope())) {
			return testScope;
		} else {
			return false;
		}
	}

}
