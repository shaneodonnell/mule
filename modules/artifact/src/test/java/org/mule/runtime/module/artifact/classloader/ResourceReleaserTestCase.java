/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.artifact.classloader;

import static org.mule.runtime.module.artifact.api.classloader.ParentFirstLookupStrategy.PARENT_FIRST;

import static java.lang.Thread.currentThread;
import static java.sql.DriverManager.deregisterDriver;
import static java.sql.DriverManager.getDrivers;
import static java.sql.DriverManager.registerDriver;
import static java.util.Collections.list;
import static java.util.Locale.getDefault;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import org.mule.module.artifact.classloader.JdbcResourceReleaser;
import org.mule.runtime.module.artifact.api.classloader.ClassLoaderLookupPolicy;
import org.mule.runtime.module.artifact.api.classloader.LookupStrategy;
import org.mule.runtime.module.artifact.api.classloader.MuleArtifactClassLoader;
import org.mule.runtime.module.artifact.api.classloader.ResourceReleaser;
import org.mule.runtime.module.artifact.api.descriptor.ArtifactDescriptor;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.sql.Driver;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.stream.Stream;

import org.junit.Test;

@SmallTest
public class ResourceReleaserTestCase extends AbstractMuleTestCase {

  public static final String TEST_RESOURCE_RELEASER_CLASS_LOCATION =
      "/org/mule/runtime/module/artifact/classloader/TestResourceReleaser.class";

  @Test
  public void createdByCorrectArtifactClassLoader() throws Exception {
    ensureResourceReleaserIsCreatedByCorrectClassLoader(new TestArtifactClassLoader(new TestArtifactClassLoader(currentThread()
        .getContextClassLoader())));
  }

  @Test
  public void createdByCorrectParentArtifactClassLoader() throws Exception {
    ensureResourceReleaserIsCreatedByCorrectClassLoader(new TestArtifactClassLoader(currentThread().getContextClassLoader()));
  }

  @Test
  public void jdbcResourceReleaserShouldNotBeCreatedIfDriverIsNotLoaded() {
    TestArtifactClassLoader testArtifactClassLoader = new TestArtifactClassLoader(currentThread().getContextClassLoader());
    testArtifactClassLoader.setResourceReleaserClassLocation(TEST_RESOURCE_RELEASER_CLASS_LOCATION);
    testArtifactClassLoader.dispose();

    // We must call the getClassLoader method from TestResourceReleaser dynamically in order to not load the
    // class by the current class loader, if not a java.lang.LinkageError is raised.
    ResourceReleaser jdbcResourceReleaserInstance =
        ((KeepResourceReleaserInstance) testArtifactClassLoader).getResourceReleaserInstance();
    assertThat(jdbcResourceReleaserInstance, is(nullValue()));
  }

  @Test
  public void jdbcResourceReleaserShouldNotBeCreatedIfDriverInterfaceIsLoaded() throws ClassNotFoundException {
    TestArtifactClassLoader testArtifactClassLoader = new TestArtifactClassLoader(currentThread().getContextClassLoader());
    testArtifactClassLoader.setResourceReleaserClassLocation(TEST_RESOURCE_RELEASER_CLASS_LOCATION);

    testArtifactClassLoader.loadClass(Driver.class.getName());
    testArtifactClassLoader.loadClass(TestAbstractDriver.class.getName());
    testArtifactClassLoader.loadClass(TestInterfaceDriver.class.getName());

    testArtifactClassLoader.dispose();

    // We must call the getClassLoader method from TestResourceReleaser dynamically in order to not load the
    // class by the current class loader, if not a java.lang.LinkageError is raised.
    ResourceReleaser jdbcResourceReleaserInstance =
        ((KeepResourceReleaserInstance) testArtifactClassLoader).getResourceReleaserInstance();
    assertThat(jdbcResourceReleaserInstance, is(nullValue()));
  }

  @Test
  public void notDeregisterJdbcDriversDifferentClassLoaders() throws Exception {
    Driver jdbcDriver = mock(Driver.class);
    TestArtifactClassLoader classLoader =
        new TestArtifactClassLoader(new TestArtifactClassLoader(currentThread().getContextClassLoader()));
    try {
      registerDriver(jdbcDriver);

      assertThat(list(getDrivers()), hasItem(jdbcDriver));
      classLoader.dispose();
      assertThat(list(getDrivers()), hasItem(jdbcDriver));
    } finally {
      deregisterDriver(jdbcDriver);
      classLoader.close();
    }
  }

  @Test
  public void deregisterJdbcDriversSameClassLoaders() throws Exception {
    Driver jdbcDriver = mock(Driver.class);
    registerDriver(jdbcDriver);

    assertThat(list(getDrivers()), hasItem(jdbcDriver));
    new JdbcResourceReleaser().release();
    assertThat(list(getDrivers()), not(hasItem(jdbcDriver)));
  }

  private void ensureResourceReleaserIsCreatedByCorrectClassLoader(MuleArtifactClassLoader classLoader) throws Exception {
    assertThat(classLoader.getClass().getClassLoader(), is(currentThread().getContextClassLoader()));
    classLoader.setResourceReleaserClassLocation(TEST_RESOURCE_RELEASER_CLASS_LOCATION);

    // Have to force run the jdbc resource releaser by loading a class that is assignable to Driver
    classLoader.loadClass(TestDriver.class.getName());

    classLoader.dispose();

    // We must call the getClassLoader method from TestResourceReleaser dynamically in order to not load the
    // class by the current class loader, if not a java.lang.LinkageError is raised.
    ResourceReleaser resourceReleaserInstance = ((KeepResourceReleaserInstance) classLoader).getResourceReleaserInstance();
    Method getClassLoaderMethod = resourceReleaserInstance.getClass().getMethod("getClassLoader");
    ClassLoader resourceReleaserInstanceClassLoader = (ClassLoader) getClassLoaderMethod.invoke(resourceReleaserInstance);

    assertThat(resourceReleaserInstanceClassLoader, is(classLoader));
  }

  @Test
  public void cleanUpResourcesBundleFromDisposedClassLoader() throws Exception {
    TestArtifactClassLoader classLoader =
        new TestArtifactClassLoader(new TestArtifactClassLoader(currentThread().getContextClassLoader()));
    String resourceReleaserClassLocation = "/".concat(JdbcResourceReleaser.class.getName().replace(".", "/")).concat(".class");
    classLoader.setResourceReleaserClassLocation(resourceReleaserClassLocation);

    Field cacheListField = ResourceBundle.class.getDeclaredField("cacheList");
    cacheListField.setAccessible(true);
    ((Map) cacheListField.get(null)).clear();

    try {
      ResourceBundle.getBundle("aBundle", getDefault(), classLoader);
      fail("Found a bundle that is not supposed to present for this test");
    } catch (MissingResourceException e) {
      // Expected
    }

    classLoader.dispose();

    Map actualCacheList = (Map) cacheListField.get(null);

    assertThat(actualCacheList.size(), equalTo(0));
  }

  @Test
  public void createsInstanceOnlyOnce() throws IOException {
    try (TestArtifactClassLoader testArtifactClassLoader = new TestArtifactClassLoader(currentThread().getContextClassLoader())) {
      ResourceReleaser firstInstance = testArtifactClassLoader.createResourceReleaserInstance();
      ResourceReleaser secondInstance = testArtifactClassLoader.createResourceReleaserInstance();

      assertThat(firstInstance, sameInstance(secondInstance));
    }
  }

  private interface KeepResourceReleaserInstance {

    ResourceReleaser getResourceReleaserInstance();
  }

  private static class TestArtifactClassLoader extends MuleArtifactClassLoader implements KeepResourceReleaserInstance {

    private ResourceReleaser resourceReleaserInstance;

    public TestArtifactClassLoader(ClassLoader parentCl) {
      super("testId", new ArtifactDescriptor("test"), new URL[0], parentCl, new ClassLoaderLookupPolicy() {

        @Override
        public LookupStrategy getClassLookupStrategy(String className) {
          return PARENT_FIRST;
        }

        @Override
        public LookupStrategy getPackageLookupStrategy(String packageName) {
          return null;
        }

        @Override
        public ClassLoaderLookupPolicy extend(Map<String, LookupStrategy> lookupStrategies) {
          return null;
        }

        @Override
        public ClassLoaderLookupPolicy extend(Stream<String> packages, LookupStrategy lookupStrategy) {
          return null;
        }

        @Override
        public ClassLoaderLookupPolicy extend(Map<String, LookupStrategy> lookupStrategies, boolean overwrite) {
          return null;
        }

        @Override
        public ClassLoaderLookupPolicy extend(Stream<String> packages, LookupStrategy lookupStrategy, boolean overwrite) {
          return null;
        }
      });
    }

    @Override
    protected ResourceReleaser createResourceReleaserInstance() {
      resourceReleaserInstance = super.createResourceReleaserInstance();
      return resourceReleaserInstance;
    }

    @Override
    public ResourceReleaser getResourceReleaserInstance() {
      return resourceReleaserInstance;
    }
  }
}

