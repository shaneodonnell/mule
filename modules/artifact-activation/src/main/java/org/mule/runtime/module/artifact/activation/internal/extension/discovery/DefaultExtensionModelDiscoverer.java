/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.artifact.activation.internal.extension.discovery;

import static java.lang.Thread.currentThread;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toSet;

import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.core.api.extension.MuleExtensionModelProvider;
import org.mule.runtime.core.api.extension.RuntimeExtensionModelProvider;
import org.mule.runtime.core.api.registry.SpiServiceRegistry;
import org.mule.runtime.module.artifact.activation.api.extension.discovery.ExtensionDiscoveryRequest;
import org.mule.runtime.module.artifact.activation.api.extension.discovery.ExtensionModelDiscoverer;
import org.mule.runtime.module.artifact.activation.internal.PluginsDependenciesProcessor;

import com.google.common.collect.ImmutableSet;

import java.util.HashSet;
import java.util.Set;

/**
 * Default implementation of {@link ExtensionModelDiscoverer}.
 *
 * @since 4.5
 */
public class DefaultExtensionModelDiscoverer implements ExtensionModelDiscoverer {

  private final ExtensionModelGenerator extensionModelGenerator;

  public DefaultExtensionModelDiscoverer(ExtensionModelGenerator extensionModelGenerator) {
    this.extensionModelGenerator = extensionModelGenerator;
  }

  @Override
  public Set<ExtensionModel> discoverRuntimeExtensionModels() {
    return unmodifiableSet(new SpiServiceRegistry()
        .lookupProviders(RuntimeExtensionModelProvider.class, currentThread().getContextClassLoader())
        .stream()
        .map(RuntimeExtensionModelProvider::createExtensionModel)
        .collect(toSet()));
  }

  @Override
  public Set<ExtensionModel> discoverPluginsExtensionModels(ExtensionDiscoveryRequest discoveryRequest) {
    return unmodifiableSet(new HashSet<>(PluginsDependenciesProcessor
        .process(discoveryRequest.getArtifactPluginDescriptors(), discoveryRequest.isParallelDiscovery(),
                 (extensions, artifactPlugin) -> {
                   Set<ExtensionModel> dependencies = new HashSet<>();

                   dependencies.addAll(extensions);
                   dependencies.addAll(discoveryRequest.getParentArtifactExtensions());
                   if (!dependencies.contains(MuleExtensionModelProvider.getExtensionModel())) {
                     dependencies = ImmutableSet.<ExtensionModel>builder()
                         .addAll(extensions)
                         .addAll(discoverRuntimeExtensionModels())
                         .build();
                   }

                   ExtensionModel extension =
                       extensionModelGenerator.obtainExtensionModel(discoveryRequest, artifactPlugin, dependencies);
                   if (extension != null) {
                     extensions.add(extension);
                   }
                 })));
  }
}
