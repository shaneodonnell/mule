/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.extension;

import static org.mule.runtime.core.api.extension.MuleExtensionModelProvider.getOperationDslExtensionModel;

import org.mule.runtime.api.meta.model.ExtensionModel;

/**
 * Provides the {@link ExtensionModel} for operations Mule DSL
 *
 * @since 4.5.0
 */
public final class OperationDslExtensionModelProvider implements RuntimeExtensionModelProvider {

  @Override
  public ExtensionModel createExtensionModel() {
    return getOperationDslExtensionModel();
  }
}
