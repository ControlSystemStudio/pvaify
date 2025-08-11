/*******************************************************************************
 * Copyright (c) 2025 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package pvaifyorg.phoebus.pvaify;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.phoebus.pvaify.PVListFile;

/** Unit test for {@link PVListFile} */
class PVListFileTest
{
    private static PVListFile pvlist;

    @BeforeAll
    static void setUpBeforeClass() throws Exception
    {
        pvlist = new PVListFile("pvaify.pvlist");
    }

    @Test
    void testFileLoading()
    {
        System.out.println(pvlist);
    }

    @Test
    void testDeny() throws Exception
    {
        assertFalse(pvlist.mayAccess("Ignore:This",  null));
        assertFalse(pvlist.mayAccess("Basically:OK", InetAddress.getByName("11.12.13.14")));
    }

    @Test
    void testAllow() throws Exception
    {
        assertTrue(pvlist.mayAccess("Basically:OK", null));
    }
}
