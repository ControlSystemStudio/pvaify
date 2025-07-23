/*******************************************************************************
 * Copyright (c) 2025 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.phoebus.pvaify;

import static org.phoebus.pvaify.Proxy.logger;

import java.time.Instant;
import java.util.logging.Level;

import org.epics.pva.data.PVAInt;
import org.epics.pva.data.PVAString;
import org.epics.pva.data.PVAStructure;
import org.epics.pva.data.nt.PVAScalar;
import org.epics.pva.data.nt.PVATimeStamp;
import org.epics.pva.server.PVAServer;
import org.epics.pva.server.ServerPV;

/** PVA PVs with  proxy status
 *  @author Kay Kasemir
 */
class ProxyInfo
{
    private final PVATimeStamp stamp = new PVATimeStamp();
    private final ServerPV pvtotal_pv, connected_pv, unconnected_pv;
    private final PVAStructure pvtotal_data, connected_data, unconnected_data;

    /** @param prefix Status PV prefix
     *  @param server {@link PVAServer}
     *  @throws Exception on error
     */
    public ProxyInfo(final String prefix, final PVAServer server) throws Exception
    {
        pvtotal_data = new PVAStructure(prefix + "pvtotal",
                         PVAScalar.SCALAR_STRUCT_NAME_STRING,
                         new PVAInt("value", 0),
                         new PVAStructure("display", "display_t",
                                 new PVAString("units", "PVs")),
                         stamp);
        pvtotal_pv = server.createPV(pvtotal_data.getName(), pvtotal_data);

        connected_data = new PVAStructure(prefix + "connected",
                PVAScalar.SCALAR_STRUCT_NAME_STRING,
                new PVAInt("value", 0),
                new PVAStructure("display", "display_t",
                        new PVAString("units", "PVs")),
                stamp);
        connected_pv = server.createPV(connected_data.getName(), connected_data);

        unconnected_data = new PVAStructure(prefix + "unconnected",
                PVAScalar.SCALAR_STRUCT_NAME_STRING,
                new PVAInt("value", 0),
                new PVAStructure("display", "display_t",
                        new PVAString("units", "PVs")),
                stamp);
        unconnected_pv = server.createPV(unconnected_data.getName(), unconnected_data);

        logger.log(Level.CONFIG, "Info PVs: " +
                                 pvtotal_data.getName() + ", " +
                                 connected_data.getName() + ", " +
                                 unconnected_data.getName());
    }

    /** @param name PV name
     *  @return Is this a status PV name, one that should not be proxied?
     */
    public boolean isInfoPV(final String name)
    {
        return pvtotal_pv.getName().equals(name)     ||
               connected_pv.getName().equals(name)   ||
               unconnected_pv.getName().equals(name);
    }

    /** @param total Number of proxied channels
     *  @param connected .. with data, i.e., connected on the client side
     *  @param unconnected .. without data, not connected on client side
     */
    public void update(final int total, final int connected, final int unconnected)
    {
        try
        {
            // Update common time stamp
            stamp.set(Instant.now());

            PVAInt value = pvtotal_data.get("value");
            if (value.get() != total)
            {
                value.set(total);
                pvtotal_pv.update(pvtotal_data);
            }

            value = connected_data.get("value");
            if (value.get() != connected)
            {
                value.set(connected);
                connected_pv.update(connected_data);
            }

            value = unconnected_data.get("value");
            if (value.get() != unconnected)
            {
                value.set(unconnected);
                unconnected_pv.update(unconnected_data);
            }
        }
        catch (Exception ex)
        {
            logger.log(Level.WARNING, "Cannot update info PVs", ex);
        }
    }
}
