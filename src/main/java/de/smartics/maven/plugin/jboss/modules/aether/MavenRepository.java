/*
 * Copyright 2013 smartics, Kronseder & Reiner GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.smartics.maven.plugin.jboss.modules.aether;

import java.util.ArrayList;
import java.util.List;

import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyFilter;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.resolution.DependencyResult;
import org.sonatype.aether.util.filter.AndDependencyFilter;
import org.sonatype.aether.util.graph.PreorderNodeListGenerator;

import de.smartics.maven.plugin.jboss.modules.aether.filter.DependencyFlagger;
import de.smartics.maven.plugin.jboss.modules.aether.filter.DirectDependenciesOnlyFilter;

/**
 * The repository to access artifacts to resolve for property descriptor
 * information.
 */
public final class MavenRepository
{ // NOPMD
  // ********************************* Fields *********************************

  // --- constants ------------------------------------------------------------

  // --- members --------------------------------------------------------------

  /**
   * Resolver for artifact repositories.
   */
  private final RepositorySystem repositorySystem;

  /**
   * The current repository/network configuration of Maven.
   */
  private final RepositorySystemSession session;

  /**
   * The project's remote repositories to use for the resolution of
   * dependencies.
   */
  private final List<RemoteRepository> remoteRepositories;

  /**
   * The list of dependency filters to apply to the dependency request. This
   * allows to limit the collect request since every unresolved dependency is
   * skipped.
   */
  private final List<DependencyFilter> dependencyFilters;

  /**
   * The list of managed dependencies to allow to resolve the appropriat
   * versions of artifacts.
   */
  private final List<Dependency> managedDependencies;

  /**
   * The flag to control accessing the local repository only.
   */
  private final boolean offline;

  // ****************************** Initializer *******************************

  // ****************************** Constructors ******************************

  MavenRepository(final RepositoryBuilder builder)
  {
    this.remoteRepositories = builder.getRemoteRepositories();
    this.session = builder.getSession();
    this.repositorySystem = builder.getRepositorySystem();
    this.dependencyFilters = builder.getDependencyFilters();
    this.managedDependencies = builder.getManagedDependencies();
    this.offline = builder.isOffline();
  }

  // ****************************** Inner Classes *****************************

  // ********************************* Methods ********************************

  // --- init -----------------------------------------------------------------

  // --- get&set --------------------------------------------------------------

  // --- business -------------------------------------------------------------

  /**
   * Resolves the dependency so that it is locally accessible.
   *
   * @param dependency the dependency to resolve.
   * @return the reference to the resolved artifact that is now stored locally
   *         ready for access.
   * @throws DependencyResolutionException if the dependency tree could not be
   *           built or any dependency artifact could not be resolved.
   */
  public MavenResponse resolve(final Dependency dependency)
    throws DependencyResolutionException
  {
    final DependencyRequest dependencyRequest = createRequest(dependency, true);
    return configureRequest(dependencyRequest);
  }

  /**
   * Resolves direct dependencies of the dependency so that they are locally
   * accessible.
   *
   * @param dependency the dependency to resolve.
   * @return the reference to the resolved artifact that is now stored locally
   *         ready for access.
   * @throws DependencyResolutionException if the dependency tree could not be
   *           built or any dependency artifact could not be resolved.
   */
  public MavenResponse resolveDirect(final Dependency dependency)
    throws DependencyResolutionException
  {
    final DependencyRequest dependencyRequest =
        createRequest(dependency, false);
    return configureRequest(dependencyRequest);
  }

  private DependencyRequest createRequest(final Dependency dependency,
      final boolean transitive)
  {
    final CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRoot(dependency);
    collectRequest.setManagedDependencies(managedDependencies);
    return configureRequest(collectRequest, transitive);
  }

  private static MavenResponse createResult(
      final PreorderNodeListGenerator generator)
  {
    final MavenResponse response = new MavenResponse();
    final List<DependencyNode> nodes = generator.getNodes();

    // This is a kind of workaround: We should limit the collect request, but
    // have not enough information with the DependencySelector interface
    // (we need the parents!). Therefore we use the DependencyFilter interface
    // (which has access to the parents) to resolve only those that meet our
    // constraints. Afterwards we skip all unresolved dependencies (which are
    // those that do not reference a file).
    for (final DependencyNode node : nodes)
    {
      final Dependency dependency = node.getDependency();
      final Artifact artifact = dependency.getArtifact();
      if (!(artifact.getFile() == null || DependencyFlagger.INSTANCE
          .isFlagged(dependency)))
      {
        response.add(dependency);
      }
    }
    return response;
  }

  /**
   * Resolves the dependencies so that it is locally accessible.
   *
   * @param dependencies the rootDependencies to resolve.
   * @return the reference to the resolved artifact that is now stored locally
   *         ready for access.
   * @throws DependencyResolutionException if the dependency tree could not be
   *           built or any dependency artifact could not be resolved.
   */
  public MavenResponse resolve(final List<Dependency> dependencies)
    throws DependencyResolutionException
  {
    final DependencyRequest dependencyRequest =
        createRequest(dependencies, true);
    return configureRequest(dependencyRequest);
  }

  private MavenResponse configureRequest(
      final DependencyRequest dependencyRequest)
    throws DependencyResolutionException
  {
    try
    {
      final DependencyResult result =
          repositorySystem.resolveDependencies(session, dependencyRequest);
      final DependencyNode rootNode = result.getRoot();
      final PreorderNodeListGenerator generator =
          new PreorderNodeListGenerator();
      rootNode.accept(generator);

      final MavenResponse response = createResult(generator);
      return response;
    }
    catch (final NullPointerException e) // NOPMD aether problem
    {
      // Only occurs if a parent dependency of the resource cannot be resolved
      throw new DependencyResolutionException(new DependencyResult(
          dependencyRequest), e);
    }
  }

  private DependencyRequest createRequest(final List<Dependency> dependencies,
      final boolean transitive)
  {
    final CollectRequest collectRequest = new CollectRequest();
    collectRequest.setDependencies(dependencies);
    return configureRequest(collectRequest, transitive);
  }

  private DependencyRequest configureRequest(
      final CollectRequest collectRequest, final boolean transitive)
  {
    if (!offline && !remoteRepositories.isEmpty())
    {
      collectRequest.setRepositories(remoteRepositories);
    }

    final DependencyRequest dependencyRequest = new DependencyRequest();
    dependencyRequest.setCollectRequest(collectRequest);
    applyFilters(dependencyRequest, transitive);

    return dependencyRequest;
  }

  private void applyFilters(final DependencyRequest dependencyRequest,
      final boolean transitive)
  {
    final List<DependencyFilter> filters = new ArrayList<DependencyFilter>();
    if (dependencyFilters != null)
    {
      filters.addAll(dependencyFilters);
    }
    if (!transitive)
    {
      filters.add(DirectDependenciesOnlyFilter.INSTANCE);
    }
    if (!filters.isEmpty())
    {
      final AndDependencyFilter dependencyFilter =
          new AndDependencyFilter(filters);
      dependencyRequest.setFilter(dependencyFilter);
    }
  }

  // --- object basics --------------------------------------------------------

}
