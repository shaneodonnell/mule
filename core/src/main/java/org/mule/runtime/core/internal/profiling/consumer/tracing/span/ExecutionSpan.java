/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.profiling.consumer.tracing.span;

import org.mule.runtime.api.profiling.tracing.Span;
import org.mule.runtime.api.profiling.tracing.SpanDuration;
import org.mule.runtime.api.profiling.tracing.SpanIdentifier;

/**
 * A {@link Span} that represents the trace corresponding to the execution of mule flow or component.
 *
 * @since 4.5.0
 */
public class ExecutionSpan implements Span {

  private final String name;
  private final SpanIdentifier identifier;
  private final Span parent;
  private final Long startTime;
  private final Long endTime;

  public ExecutionSpan(String name, SpanIdentifier identifier, Long startTime, Long endTime,
                       Span parent) {
    this.name = name;
    this.identifier = identifier;
    this.startTime = startTime;
    this.endTime = endTime;
    this.parent = parent;
  }

  @Override
  public Span getParent() {
    return parent;
  }

  @Override
  public SpanIdentifier getIdentifier() {
    return identifier;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public SpanDuration getDuration() {
    return new DefaultSpanDuration(startTime, endTime);
  }

  /**
   * An default implementation for a {@link SpanDuration}
   */
  private class DefaultSpanDuration implements SpanDuration {

    private final Long startTime;
    private final Long endTime;

    public DefaultSpanDuration(Long startTime, Long endTime) {
      this.startTime = startTime;
      this.endTime = endTime;
    }

    @Override
    public Long getStart() {
      return startTime;
    }

    @Override
    public Long getEnd() {
      return endTime;
    }
  }
}
