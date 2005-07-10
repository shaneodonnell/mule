/*
 * $Header$
 * $Revision$
 * $Date$
 * ------------------------------------------------------------------------------------------------------
 *
 * Copyright (c) SymphonySoft Limited. All rights reserved.
 * http://www.symphonysoft.com
 *
 * The software in this package is published under the terms of the BSD
 * style license a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 */
package org.mule.impl.container;

import org.mule.umo.UMOException;

import javax.ejb.EJBObject;
import javax.naming.NamingException;
import java.rmi.RemoteException;

/**
 * <code>Sender</code> TODO
 *
 * @author <a href="mailto:ross.mason@symphonysoft.com">Ross Mason</a>
 * @version $Revision$
 */
public interface DummyEjb extends EJBObject
{
    public void dummy() throws RemoteException;
}
