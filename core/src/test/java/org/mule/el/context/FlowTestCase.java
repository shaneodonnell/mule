/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.el.context;

import static org.junit.Assert.assertEquals;

import org.mule.DefaultMuleEvent;
import org.mule.DefaultMuleMessage;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

public class FlowTestCase extends AbstractELTestCase
{
    public FlowTestCase(Variant variant)
    {
        super(variant);
    }

    @Test
    public void flowName() throws Exception
    {
        MuleEvent event = new DefaultMuleEvent(new DefaultMuleMessage("", muleContext),
            MessageExchangePattern.ONE_WAY, getTestService("flowName", Object.class));
        assertEquals("flowName", evaluate("flow.name", event));
    }

    @Test
    public void assignToFlowName() throws Exception
    {
        MuleEvent event = new DefaultMuleEvent(new DefaultMuleMessage("", muleContext),
            MessageExchangePattern.ONE_WAY, getTestService("flowName", Object.class));
        assertFinalProperty("flow.name='foo'", event);
    }

    @Parameters
    public static List<Object[]> parameters()
    {
        return Arrays.asList(new Object[][]{{Variant.EVALUATOR_LANGUAGE}});
    }

}
