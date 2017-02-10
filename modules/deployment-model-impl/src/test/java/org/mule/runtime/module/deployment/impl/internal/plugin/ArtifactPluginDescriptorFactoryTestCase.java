/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.module.deployment.impl.internal.plugin;

import static java.io.File.createTempFile;
import static java.util.Collections.emptySet;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mule.runtime.container.internal.ClasspathModuleDiscoverer.EXPORTED_CLASS_PACKAGES_PROPERTY;
import static org.mule.runtime.container.internal.ClasspathModuleDiscoverer.EXPORTED_RESOURCE_PROPERTY;
import static org.mule.runtime.core.util.FileUtils.stringToFile;
import static org.mule.runtime.core.util.FileUtils.unzip;
import static org.mule.runtime.module.artifact.classloader.DefaultArtifactClassLoaderFilter.NULL_CLASSLOADER_FILTER;
import static org.mule.runtime.module.deployment.impl.internal.plugin.ArtifactPluginDescriptorFactory.PLUGIN_DEPENDENCIES;
import static org.mule.runtime.module.deployment.impl.internal.plugin.ArtifactPluginDescriptorFactory.PLUGIN_PROPERTIES;
import static org.mule.runtime.module.deployment.impl.internal.plugin.ArtifactPluginDescriptorFactory.invalidBundleDescriptorLoaderIdError;
import static org.mule.runtime.module.deployment.impl.internal.plugin.ArtifactPluginDescriptorFactory.invalidClassLoaderModelIdError;
import static org.mule.runtime.module.deployment.impl.internal.policy.FileSystemPolicyClassLoaderModelLoader.FILE_SYSTEM_POLICY_MODEL_LOADER_ID;
import static org.mule.runtime.module.deployment.impl.internal.policy.PropertiesBundleDescriptorLoader.ARTIFACT_ID;
import static org.mule.runtime.module.deployment.impl.internal.policy.PropertiesBundleDescriptorLoader.CLASSIFIER;
import static org.mule.runtime.module.deployment.impl.internal.policy.PropertiesBundleDescriptorLoader.GROUP_ID;
import static org.mule.runtime.module.deployment.impl.internal.policy.PropertiesBundleDescriptorLoader.PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID;
import static org.mule.runtime.module.deployment.impl.internal.policy.PropertiesBundleDescriptorLoader.TYPE;
import static org.mule.runtime.module.deployment.impl.internal.policy.PropertiesBundleDescriptorLoader.VERSION;
import org.mule.runtime.api.deployment.meta.MuleArtifactLoaderDescriptor;
import org.mule.runtime.api.deployment.meta.MulePluginModel;
import org.mule.runtime.core.util.FileUtils;
import org.mule.runtime.deployment.model.api.plugin.ArtifactPluginDescriptor;
import org.mule.runtime.module.artifact.classloader.ArtifactClassLoaderFilter;
import org.mule.runtime.module.artifact.classloader.ClassLoaderFilterFactory;
import org.mule.runtime.module.artifact.classloader.DefaultArtifactClassLoaderFilter;
import org.mule.runtime.module.artifact.descriptor.ArtifactDescriptorCreateException;
import org.mule.runtime.module.artifact.descriptor.BundleDependency;
import org.mule.runtime.module.artifact.descriptor.BundleDescriptor;
import org.mule.runtime.module.artifact.descriptor.BundleDescriptorLoader;
import org.mule.runtime.module.artifact.descriptor.ClassLoaderModel;
import org.mule.runtime.module.artifact.descriptor.ClassLoaderModelLoader;
import org.mule.runtime.module.deployment.impl.internal.artifact.DescriptorLoaderRepository;
import org.mule.runtime.module.deployment.impl.internal.artifact.LoaderNotFoundException;
import org.mule.runtime.module.deployment.impl.internal.builder.ArtifactPluginFileBuilder;
import org.mule.runtime.module.deployment.impl.internal.policy.FileSystemPolicyClassLoaderModelLoader;
import org.mule.runtime.module.deployment.impl.internal.policy.PropertiesBundleDescriptorLoader;
import org.mule.tck.junit4.AbstractMuleTestCase;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class ArtifactPluginDescriptorFactoryTestCase extends AbstractMuleTestCase {

  private static final String PLUGIN_NAME = "testPlugin";
  private static final String INVALID_LOADER_ID = "INVALID";
  private static final String MIN_MULE_VERSION = "4.0.0";

  @Rule
  public TemporaryFolder pluginsFolder = new TemporaryFolder();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private final ClassLoaderFilterFactory classLoaderFilterFactory = mock(ClassLoaderFilterFactory.class);
  private final DescriptorLoaderRepository descriptorLoaderRepository = mock(DescriptorLoaderRepository.class);
  private ArtifactPluginDescriptorFactory descriptorFactory = new ArtifactPluginDescriptorFactory(descriptorLoaderRepository);

  @Before
  public void setUp() throws Exception {
    when(classLoaderFilterFactory.create(null, null))
        .thenReturn(NULL_CLASSLOADER_FILTER);

    when(descriptorLoaderRepository.get(FILE_SYSTEM_POLICY_MODEL_LOADER_ID, ClassLoaderModelLoader.class))
        .thenReturn(new FileSystemPolicyClassLoaderModelLoader());
    when(descriptorLoaderRepository.get(INVALID_LOADER_ID, ClassLoaderModelLoader.class))
        .thenThrow(new LoaderNotFoundException(INVALID_LOADER_ID));

    when(descriptorLoaderRepository.get(PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID, BundleDescriptorLoader.class))
        .thenReturn(new PropertiesBundleDescriptorLoader());
    when(descriptorLoaderRepository.get(INVALID_LOADER_ID, BundleDescriptorLoader.class))
        .thenThrow(new LoaderNotFoundException(INVALID_LOADER_ID));
  }

  @Test
  public void parsesPluginWithNoDescriptor() throws Exception {
    final File pluginFolder = createPluginFolder();

    final ArtifactPluginDescriptor pluginDescriptor = descriptorFactory.create(pluginFolder);

    new PluginDescriptorChecker(pluginFolder).assertPluginDescriptor(pluginDescriptor);
  }

  @Test
  public void parsesLoaderExportClass() throws Exception {
    final File pluginFolder = createPluginFolder();

    final String exportedClassPackages = "org.foo, org.bar";
    new PluginPropertiesBuilder(pluginFolder).exportingClassesFrom(exportedClassPackages).build();

    final ArtifactClassLoaderFilter classLoaderFilter = mock(DefaultArtifactClassLoaderFilter.class);
    Set<String> parsedExportedPackages = new HashSet<>();
    parsedExportedPackages.add("org.foo");
    parsedExportedPackages.add("org.bar");
    when(classLoaderFilter.getExportedClassPackages()).thenReturn(parsedExportedPackages);
    when(classLoaderFilterFactory.create(exportedClassPackages, null)).thenReturn(classLoaderFilter);

    final ArtifactPluginDescriptor pluginDescriptor = descriptorFactory.create(pluginFolder);

    new PluginDescriptorChecker(pluginFolder).exportingPackages(parsedExportedPackages).assertPluginDescriptor(pluginDescriptor);
  }

  @Test
  public void parsesLoaderExportResource() throws Exception {
    final File pluginFolder = createPluginFolder();

    final String exportedResources = "META-INF, META-INF/xml";
    new PluginPropertiesBuilder(pluginFolder).exportingResourcesFrom(exportedResources).build();

    final ArtifactClassLoaderFilter classLoaderFilter = mock(DefaultArtifactClassLoaderFilter.class);
    Set<String> parsedExportedResources = new HashSet<>();
    parsedExportedResources.add("META-INF");
    parsedExportedResources.add("META-INF/xml");
    when(classLoaderFilter.getExportedResources()).thenReturn(parsedExportedResources);
    when(classLoaderFilterFactory.create(null, exportedResources)).thenReturn(classLoaderFilter);

    final ArtifactPluginDescriptor pluginDescriptor = descriptorFactory.create(pluginFolder);

    new PluginDescriptorChecker(pluginFolder).exportingResources(parsedExportedResources)
        .assertPluginDescriptor(pluginDescriptor);
  }

  @Test
  public void parsesLibraries() throws Exception {
    final File pluginFolder = createPluginFolder();

    final File pluginLibFolder = new File(pluginFolder, "lib");
    assertThat(pluginLibFolder.mkdir(), is(true));

    final File jar1 = createDummyJarFile(pluginLibFolder, "lib1.jar");
    final File jar2 = createDummyJarFile(pluginLibFolder, "lib2.jar");
    final URL[] libraries = new URL[] {jar1.toURI().toURL(), jar2.toURI().toURL()};

    final ArtifactPluginDescriptor pluginDescriptor = descriptorFactory.create(pluginFolder);

    new PluginDescriptorChecker(pluginFolder).containing(libraries).assertPluginDescriptor(pluginDescriptor);
  }

  @Test
  public void parsesNamedDefinedPluginDependency() throws Exception {
    final File pluginFolder = createPluginFolder();

    final String pluginDependencies = "foo";
    new PluginPropertiesBuilder(pluginFolder).dependingOn(pluginDependencies).build();

    final ArtifactPluginDescriptor pluginDescriptor = descriptorFactory.create(pluginFolder);

    BundleDescriptor descriptor =
        new BundleDescriptor.Builder().setArtifactId("foo").setGroupId("test").setVersion("1.0").build();
    new PluginDescriptorChecker(pluginFolder).dependingOn(descriptor).assertPluginDescriptor(pluginDescriptor);
  }

  @Test
  public void parsesFullyDefinedPluginDependency() throws Exception {
    final File pluginFolder = createPluginFolder();

    final String pluginDependencies = "org.foo:foo:2.0";
    new PluginPropertiesBuilder(pluginFolder).dependingOn(pluginDependencies).build();

    final ArtifactPluginDescriptor pluginDescriptor = descriptorFactory.create(pluginFolder);

    BundleDescriptor descriptor =
        new BundleDescriptor.Builder().setArtifactId("foo").setGroupId("org.foo").setVersion("2.0").build();
    new PluginDescriptorChecker(pluginFolder).dependingOn(descriptor).assertPluginDescriptor(pluginDescriptor);
  }

  @Test
  public void parsesMultiplePluginDependencies() throws Exception {
    final File pluginFolder = createPluginFolder();

    final String pluginDependencies = "org.foo:foo:2.0,org.bar:bar:1.0";
    new PluginPropertiesBuilder(pluginFolder).dependingOn(pluginDependencies).build();

    final ArtifactPluginDescriptor pluginDescriptor = descriptorFactory.create(pluginFolder);

    BundleDescriptor fooDescriptor =
        new BundleDescriptor.Builder().setArtifactId("foo").setGroupId("org.foo").setVersion("2.0").build();
    BundleDescriptor barDEscriptor =
        new BundleDescriptor.Builder().setArtifactId("bar").setGroupId("org.bar").setVersion("1.0").build();
    new PluginDescriptorChecker(pluginFolder).dependingOn(fooDescriptor).dependingOn(barDEscriptor)
        .assertPluginDescriptor(pluginDescriptor);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsTooParseDescriptorWithIncompletePluginDependency() throws Exception {
    final File pluginFolder = createPluginFolder();
    final String pluginDependencies = "org.foo:foo";
    new PluginPropertiesBuilder(pluginFolder).dependingOn(pluginDependencies).build();

    descriptorFactory.create(pluginFolder);
  }

  @Test
  public void detectsInvalidClassLoaderModelLoaderId() throws Exception {
    MulePluginModel.MulePluginModelBuilder pluginModelBuilder = new MulePluginModel.MulePluginModelBuilder().setName(PLUGIN_NAME)
        .setMinMuleVersion(MIN_MULE_VERSION)
        .withBundleDescriptorLoader(createBundleDescriptorLoader(PROPERTIES_BUNDLE_DESCRIPTOR_LOADER_ID));
    pluginModelBuilder.withClassLoaderModelDescriber().setId(INVALID_LOADER_ID);

    ArtifactPluginFileBuilder pluginFileBuilder =
        new ArtifactPluginFileBuilder(PLUGIN_NAME).describedBy(pluginModelBuilder.build());
    File tempFolder = createTempFolder();
    unzip(pluginFileBuilder.getArtifactFile(), tempFolder);

    expectedException.expect(ArtifactDescriptorCreateException.class);
    expectedException
        .expectMessage(invalidClassLoaderModelIdError(tempFolder, pluginModelBuilder.getClassLoaderModelDescriptorLoader()));

    descriptorFactory.create(tempFolder);
  }

  @Test
  public void detectsInvalidBundleDescriptorModelLoaderId() throws Exception {
    MulePluginModel.MulePluginModelBuilder pluginModelBuilder = new MulePluginModel.MulePluginModelBuilder().setName(PLUGIN_NAME)
        .setMinMuleVersion(MIN_MULE_VERSION).withBundleDescriptorLoader(createBundleDescriptorLoader(INVALID_LOADER_ID));
    pluginModelBuilder.withClassLoaderModelDescriber().setId(FILE_SYSTEM_POLICY_MODEL_LOADER_ID);

    ArtifactPluginFileBuilder pluginFileBuilder =
        new ArtifactPluginFileBuilder(PLUGIN_NAME).describedBy(pluginModelBuilder.build());
    File tempFolder = createTempFolder();
    unzip(pluginFileBuilder.getArtifactFile(), tempFolder);

    expectedException.expect(ArtifactDescriptorCreateException.class);
    expectedException
        .expectMessage(invalidBundleDescriptorLoaderIdError(tempFolder, pluginModelBuilder.getBundleDescriptorLoader()));

    descriptorFactory.create(tempFolder);
  }

  private File createPluginFolder() {
    final File pluginFolder = new File(pluginsFolder.getRoot(), PLUGIN_NAME);
    assertThat(pluginFolder.mkdir(), is(true));
    return pluginFolder;
  }

  private File createDummyJarFile(File pluginLibFolder, String child) throws IOException {
    final File jar1 = new File(pluginLibFolder, child);
    FileUtils.write(jar1, "foo");
    return jar1;
  }

  private File createTempFolder() throws IOException {
    File tempFolder = createTempFile("tempPolicy", null);
    Assert.assertThat(tempFolder.delete(), Matchers.is(true));
    Assert.assertThat(tempFolder.mkdir(), Matchers.is(true));
    return tempFolder;
  }

  private MuleArtifactLoaderDescriptor createBundleDescriptorLoader(String bundleDescriptorLoaderId) {
    Map<String, Object> attributes = new HashMap();
    attributes.put(VERSION, "1.0");
    attributes.put(GROUP_ID, "org.mule.test");
    attributes.put(ARTIFACT_ID, PLUGIN_NAME);
    attributes.put(CLASSIFIER, "mule-plugin");
    attributes.put(TYPE, "jar");
    return new MuleArtifactLoaderDescriptor(bundleDescriptorLoaderId, attributes);
  }

  private static class PluginDescriptorChecker {

    private final File pluginFolder;
    private URL[] libraries = new URL[0];;
    private Set<String> resources = emptySet();
    private Set<String> packages = emptySet();
    private List<BundleDescriptor> dependencies = new ArrayList<>();

    public PluginDescriptorChecker(File pluginFolder) {
      this.pluginFolder = pluginFolder;
    }

    public PluginDescriptorChecker exportingResources(Set<String> resources) {
      this.resources = resources;

      return this;
    }

    public PluginDescriptorChecker exportingPackages(Set<String> packages) {
      this.packages = packages;

      return this;
    }

    public PluginDescriptorChecker containing(URL[] libraries) {
      this.libraries = libraries;
      return this;
    }

    public PluginDescriptorChecker dependingOn(BundleDescriptor descriptor) {
      this.dependencies.add(descriptor);
      return this;
    }

    public void assertPluginDescriptor(ArtifactPluginDescriptor pluginDescriptor) throws Exception {
      assertThat(pluginDescriptor.getName(), equalTo(pluginFolder.getName()));
      try {
        assertThat(pluginDescriptor.getClassLoaderModel().getUrls()[0],
                   equalTo(pluginFolder.toURI().toURL()));
      } catch (MalformedURLException e) {
        throw new AssertionError("Can't compare classes dir", e);
      }

      assertUrls(pluginDescriptor);
      assertThat(pluginDescriptor.getRootFolder(), equalTo(pluginFolder));
      assertThat(pluginDescriptor.getClassLoaderModel().getExportedResources(), equalTo(resources));
      assertThat(pluginDescriptor.getClassLoaderModel().getExportedPackages(), equalTo(packages));
      assertPluginDependencies(pluginDescriptor.getClassLoaderModel());
    }

    private void assertPluginDependencies(ClassLoaderModel classLoaderModel) {
      assertThat(classLoaderModel.getDependencies().size(), equalTo(dependencies.size()));

      for (BundleDependency bundleDependency : classLoaderModel.getDependencies()) {
        assertThat(dependencies.contains(bundleDependency.getDescriptor()), is(true));
      }
    }

    private void assertUrls(ArtifactPluginDescriptor pluginDescriptor) throws Exception {
      assertThat(pluginDescriptor.getClassLoaderModel().getUrls().length, equalTo(libraries.length + 1));
      assertThat(pluginDescriptor.getClassLoaderModel().getUrls(),
                 hasItemInArray(equalTo(pluginFolder.toURI().toURL())));

      for (URL libUrl : libraries) {
        assertThat(pluginDescriptor.getClassLoaderModel().getUrls(), hasItemInArray(equalTo(libUrl)));
      }
    }
  }

  private static class PluginPropertiesBuilder {

    private final File pluginFolder;
    private String exportedClassPackages;
    private String exportedResources;
    private String pluginDependencies;

    public PluginPropertiesBuilder(File pluginFolder) {
      this.pluginFolder = pluginFolder;
    }

    public PluginPropertiesBuilder exportingClassesFrom(String packages) {
      this.exportedClassPackages = packages;

      return this;
    }

    public PluginPropertiesBuilder exportingResourcesFrom(String packages) {
      this.exportedResources = packages;

      return this;
    }

    public PluginPropertiesBuilder dependingOn(String pluginDependencies) {
      this.pluginDependencies = pluginDependencies;

      return this;
    }

    public File build() throws IOException {
      final File pluginProperties = new File(pluginFolder, PLUGIN_PROPERTIES);
      if (pluginProperties.exists()) {
        throw new IllegalStateException(String.format("File '%s' already exists", pluginProperties.getAbsolutePath()));
      }

      addDescriptorProperty(pluginProperties, EXPORTED_CLASS_PACKAGES_PROPERTY, this.exportedClassPackages);
      addDescriptorProperty(pluginProperties, EXPORTED_RESOURCE_PROPERTY, this.exportedResources);
      if (!isEmpty(pluginDependencies)) {
        addDescriptorProperty(pluginProperties, PLUGIN_DEPENDENCIES, pluginDependencies);
      }

      return pluginProperties;
    }

    private void addDescriptorProperty(File pluginProperties, String propertyName, String propertyValue) throws IOException {
      if (!isEmpty(propertyValue)) {
        final String descriptorProperty = generateDescriptorProperty(propertyName, propertyValue);

        stringToFile(pluginProperties.getAbsolutePath(), descriptorProperty, true);
      }
    }

    private String generateDescriptorProperty(String propertyName, String propertyValue) {
      StringBuilder builder = new StringBuilder(propertyName).append("=").append(propertyValue);

      return builder.toString();
    }
  }
}
