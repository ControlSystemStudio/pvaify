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
import java.util.Set;
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
    private final ServerPV pvtotal_pv, connected_pv, unconnected_pv, search_pv, client_rate_pv, server_rate_pv;
    private final PVAStructure pvtotal_data, connected_data, unconnected_data, search_data, client_rate_data, server_rate_data;
    private final Set<String> info_pv_names;

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

        search_data = new PVAStructure(prefix + "existTestRate",
                PVAScalar.SCALAR_STRUCT_NAME_STRING,
                new PVAInt("value", 0),
                new PVAStructure("display", "display_t",
                        new PVAString("units", "Hz"),
                        new PVAInt("precision", 1)),
                stamp);
        search_pv = server.createPV(search_data.getName(), search_data);

        client_rate_data = new PVAStructure(prefix + "clientEventRate",
                PVAScalar.SCALAR_STRUCT_NAME_STRING,
                new PVAInt("value", 0),
                new PVAStructure("display", "display_t",
                        new PVAString("units", "Hz"),
                        new PVAInt("precision", 1)),
                stamp);
        client_rate_pv = server.createPV(client_rate_data.getName(), client_rate_data);

        server_rate_data = new PVAStructure(prefix + "serverPostRate",
                PVAScalar.SCALAR_STRUCT_NAME_STRING,
                new PVAInt("value", 0),
                new PVAStructure("display", "display_t",
                        new PVAString("units", "Hz"),
                        new PVAInt("precision", 1)),
                stamp);
        server_rate_pv = server.createPV(server_rate_data.getName(), server_rate_data);

        info_pv_names = Set.of(pvtotal_pv.getName(),
                               connected_pv.getName(),
                               unconnected_pv.getName(),
                               search_pv.getName(),
                               client_rate_pv.getName(),
                               server_rate_pv.getName());

        logger.log(Level.CONFIG, "Info PVs: " + info_pv_names);
    }

    /** @param name PV name
     *  @return Is this a status PV name, one that should not be proxied?
     */
    public boolean isInfoPV(final String name)
    {
        return info_pv_names.contains(name);
    }

    /** @param total Number of proxied channels
     *  @param connected .. with data, i.e., connected on the client side
     *  @param unconnected .. without data, not connected on client side
     *  @param search_rate Received PV name searches
     *  @param client_rate Received subscription updates from client side
     *  @param server_rate Updates sent to server side
     */
    public void update(final int total, final int connected, final int unconnected,
                       final int search_rate, final int client_rate, final int server_rate)
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

            value = search_data.get("value");
            if (value.get() != search_rate)
            {
                value.set(search_rate);
                search_pv.update(search_data);
            }

            value = client_rate_data.get("value");
            if (value.get() != client_rate)
            {
                value.set(client_rate);
                client_rate_pv.update(client_rate_data);
            }

            value = server_rate_data.get("value");
            if (value.get() != server_rate)
            {
                value.set(server_rate);
                server_rate_pv.update(server_rate_data);
            }
        }
        catch (Exception ex)
        {
            logger.log(Level.WARNING, "Cannot update info PVs", ex);
        }
    }
}
