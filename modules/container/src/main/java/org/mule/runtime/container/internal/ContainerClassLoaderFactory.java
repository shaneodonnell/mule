/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.container.internal;

import static java.lang.Boolean.valueOf;
import static java.lang.System.getProperty;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static org.mule.runtime.api.util.MuleSystemProperties.MULE_ALLOW_JRE_EXTENSION;
import static org.mule.runtime.api.util.MuleSystemProperties.MULE_JRE_EXTENSION_PACKAGES;
import static org.mule.runtime.api.util.Preconditions.checkArgument;
import static org.mule.runtime.module.artifact.api.classloader.ParentFirstLookupStrategy.PARENT_FIRST;

import org.mule.runtime.container.api.ModuleRepository;
import org.mule.runtime.container.api.MuleModule;
import org.mule.runtime.core.internal.util.EnumerationAdapter;
import org.mule.runtime.module.artifact.api.classloader.ArtifactClassLoader;
import org.mule.runtime.module.artifact.api.classloader.ClassLoaderLookupPolicy;
import org.mule.runtime.module.artifact.api.classloader.ExportedService;
import org.mule.runtime.module.artifact.api.classloader.FilteringArtifactClassLoader;
import org.mule.runtime.module.artifact.api.classloader.LookupStrategy;
import org.mule.runtime.module.artifact.api.classloader.MuleArtifactClassLoader;
import org.mule.runtime.module.artifact.api.descriptor.ArtifactDescriptor;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

/**
 * Creates the classLoader for the Mule container.
 * <p>
 * This classLoader must be used as the parent classLoader for any other Mule artifact depending only on the container.
 */
public class ContainerClassLoaderFactory {

  private static final String DEFAULT_JRE_EXTENSION_PACKAGES = "javax.,org.w3c.dom,org.omg.,org.xml.sax,org.ietf.jgss";
  private static final String MULE_SDK_API_PACKAGE = "org.mule.sdk.api";
  private static final boolean ALLOW_JRE_EXTENSION = valueOf(getProperty(MULE_ALLOW_JRE_EXTENSION, "true"));
  private static final String[] JRE_EXTENDABLE_PACKAGES =
      getProperty(MULE_JRE_EXTENSION_PACKAGES, DEFAULT_JRE_EXTENSION_PACKAGES).split(",");

  private static final class MuleContainerClassLoader extends MuleArtifactClassLoader {

    static {
      registerAsParallelCapable();
    }

    private MuleContainerClassLoader(ArtifactDescriptor artifactDescriptor, URL[] urls, ClassLoader parent,
                                     ClassLoaderLookupPolicy lookupPolicy) {
      super("container", artifactDescriptor, urls, parent, lookupPolicy);
    }

    @Override
    public URL findResource(String name) {
      // Container classLoader is just an adapter, it does not owns any resource
      return null;
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
      // Container classLoader is just an adapter, it does not owns any resource
      return new EnumerationAdapter<>(emptyList());
    }
  }

  // TODO(pablo.kraan): MULE-9524: Add a way to configure system and boot packages used on class loading lookup
  /**
   * System package define all the prefixes that must be loaded only from the container classLoader, but then are filtered
   * depending on what is part of the exposed API.
   */
  public static final Set<String> SYSTEM_PACKAGES = ImmutableSet.of("org.mule.runtime", "com.mulesoft.mule.runtime");

  /**
   * Boot packages define all the prefixes that must be loaded from the container classLoader without being filtered
   */
  public static final Set<String> BOOT_PACKAGES =
      ImmutableSet.of(// MULE-10194 Mechanism to add custom boot packages to be exported by the container
                      "com.yourkit");

  private final ModuleRepository moduleRepository;

  /**
   * Creates a custom factory
   *
   * @param moduleRepository provides access to the modules available on the container. Non null.
   */
  public ContainerClassLoaderFactory(ModuleRepository moduleRepository) {
    checkArgument(moduleRepository != null, "moduleRepository cannot be null");

    this.moduleRepository = moduleRepository;
  }

  /**
   * Creates a default factory
   */
  public ContainerClassLoaderFactory() {
    this(new DefaultModuleRepository(new ContainerModuleDiscoverer(ContainerClassLoaderFactory.class.getClassLoader())));
  }

  /**
   * Creates the classLoader to represent the Mule container.
   *
   * @param parentClassLoader parent classLoader. Can be null.
   * @return a non null {@link ArtifactClassLoader} containing container code that can be used as parent classloader for other
   *         mule artifacts.
   */
  public ArtifactClassLoader createContainerClassLoader(final ClassLoader parentClassLoader) {
    final List<MuleModule> muleModules = moduleRepository.getModules();

    final ClassLoaderLookupPolicy containerLookupPolicy = getContainerClassLoaderLookupPolicy(parentClassLoader, muleModules);

    return createArtifactClassLoader(parentClassLoader, muleModules, containerLookupPolicy, new ArtifactDescriptor("mule"));
  }

  /**
   * Creates the container lookup policy to be used by child class loaders.
   *
   * @param parentClassLoader classloader used as parent of the container's. Is the classLoader that will load Mule classes.
   * @param muleModules       list of modules that would be used to register in the filter based of the class loader.
   * @return a non null {@link ClassLoaderLookupPolicy} that contains the lookup policies for boot, system packages. plus exported
   *         packages by the given list of {@link MuleModule}.
   */
  protected ClassLoaderLookupPolicy getContainerClassLoaderLookupPolicy(ClassLoader parentClassLoader,
                                                                        List<MuleModule> muleModules) {
    final Set<String> parentOnlyPackages = new HashSet<>(getBootPackages());
    parentOnlyPackages.addAll(SYSTEM_PACKAGES);
    final Map<String, LookupStrategy> lookupStrategies = buildClassLoaderLookupStrategy(parentClassLoader, muleModules);
    return new MuleClassLoaderLookupPolicy(lookupStrategies, parentOnlyPackages);
  }

  /**
   * Creates an {@link ArtifactClassLoader} that always resolves resources by delegating to the parentClassLoader.
   *
   * @param parentClassLoader     the parent {@link ClassLoader} for the container
   * @param muleModules           the list of {@link MuleModule}s to be used for defining the filter
   * @param containerLookupPolicy the {@link ClassLoaderLookupPolicy} to be used by the container
   * @param artifactDescriptor    descriptor for the artifact owning the created class loader instance.
   * @return a {@link ArtifactClassLoader} to be used in a {@link FilteringContainerClassLoader}
   */
  protected ArtifactClassLoader createArtifactClassLoader(final ClassLoader parentClassLoader, List<MuleModule> muleModules,
                                                          final ClassLoaderLookupPolicy containerLookupPolicy,
                                                          ArtifactDescriptor artifactDescriptor) {
    return createContainerFilteringClassLoader(parentClassLoader, muleModules,
                                               new MuleContainerClassLoader(artifactDescriptor, new URL[0], parentClassLoader,
                                                                            containerLookupPolicy));
  }

  /**
   * Creates a {@link Map<String, LookupStrategy>} for the packages exported on the container.
   *
   * @param containerClassLoader class loader containing container's classes. Non null.
   * @param modules              to be used for collecting the exported packages. Non null
   * @return a {@link Map<String, LookupStrategy>} for the packages exported on the container
   */
  private Map<String, LookupStrategy> buildClassLoaderLookupStrategy(ClassLoader containerClassLoader,
                                                                     List<MuleModule> modules) {
    checkArgument(containerClassLoader != null, "containerClassLoader cannot be null");
    checkArgument(modules != null, "modules cannot be null");

    ContainerOnlyLookupStrategy containerOnlyLookupStrategy = new ContainerOnlyLookupStrategy(containerClassLoader);

    final Map<String, LookupStrategy> result = new HashMap<>();
    for (MuleModule muleModule : modules) {
      for (String exportedPackage : muleModule.getExportedPackages()) {
        LookupStrategy specialLookupStrategy = getSpecialLookupStrategy(exportedPackage);
        result.put(exportedPackage, specialLookupStrategy == null ? containerOnlyLookupStrategy : specialLookupStrategy);
      }
    }

    return result;
  }

  /**
   * Returns the {@link LookupStrategy} if the one to use for the exportedPackage is other than a
   * {@link ContainerOnlyLookupStrategy} or null.
   *
   * @param exportedPackage name of the package
   * @return
   */
  private LookupStrategy getSpecialLookupStrategy(String exportedPackage) {
    // If an extension uses a class provided by the mule-sdk-api artifact, the container classloader should use
    // the class with which the extension was compiled only if the class is not present in the distribution.
    if (exportedPackage.startsWith(MULE_SDK_API_PACKAGE)) {
      return PARENT_FIRST;
    }
    // Let artifacts extend non "java." JRE packages
    if (ALLOW_JRE_EXTENSION && stream(JRE_EXTENDABLE_PACKAGES).anyMatch(exportedPackage::startsWith)) {
      return PARENT_FIRST;
    }
    return null;
  }

  /**
   * Creates a {@link FilteringArtifactClassLoader} to filter the {@link ArtifactClassLoader} containerClassLoader given based on
   * {@link List<MuleModule>} of muleModules.
   *
   * @param parentClassLoader    the parent {@link ClassLoader} for the container
   * @param muleModules          the list of {@link MuleModule}s to be used for defining the filter
   * @param containerClassLoader the {@link ArtifactClassLoader} for the container that will be used to delegate by the
   *                             {@link FilteringContainerClassLoader}
   * @return a {@link FilteringContainerClassLoader} that would be the one used as the parent of plugins and applications
   *         {@link ArtifactClassLoader}
   */
  protected FilteringArtifactClassLoader createContainerFilteringClassLoader(final ClassLoader parentClassLoader,
                                                                             List<MuleModule> muleModules,
                                                                             ArtifactClassLoader containerClassLoader) {
    return new FilteringContainerClassLoader(parentClassLoader, containerClassLoader,
                                             new ContainerClassLoaderFilterFactory().create(getBootPackages(),
                                                                                            muleModules),
                                             getExportedServices(muleModules));
  }

  private List<ExportedService> getExportedServices(List<MuleModule> muleModules) {
    List<ExportedService> exportedServices = new ArrayList<>();

    for (MuleModule muleModule : muleModules) {
      exportedServices.addAll(muleModule.getExportedServices());
    }

    return exportedServices;
  }

  /**
   * @return a {@link Set} of packages that define all the prefixes that must be loaded from the container classLoader without
   *         being filtered
   */
  protected Set<String> getBootPackages() {
    return BOOT_PACKAGES;
  }

}
