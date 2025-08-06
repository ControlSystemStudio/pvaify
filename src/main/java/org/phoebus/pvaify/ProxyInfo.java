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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.logging.Level;

import org.epics.pva.data.PVADouble;
import org.epics.pva.data.PVAInt;
import org.epics.pva.data.PVAString;
import org.epics.pva.data.PVAStringArray;
import org.epics.pva.data.PVAStructure;
import org.epics.pva.data.nt.PVAScalar;
import org.epics.pva.data.nt.PVATable;
import org.epics.pva.data.nt.PVATimeStamp;
import org.epics.pva.server.PVAServer.ClientInfo;
import org.epics.pva.server.ServerPV;

/** PVA PVs with  proxy status
 *  @author Kay Kasemir
 */
class ProxyInfo
{
    private final Proxy proxy;
    private final PVATimeStamp stamp = new PVATimeStamp();
    private final ServerPV pvtotal_pv, connected_pv, unconnected_pv,
                           search_pv, client_rate_pv, server_rate_pv,
                           clients_table_pv,
                           list_disconnected_pv;
    private final PVAStructure pvtotal_data, connected_data, unconnected_data, search_data, client_rate_data, server_rate_data;
    private final Set<String> info_pv_names;


    /** Compare {@link ClientInfo} by address */
    private static Comparator<ClientInfo> sort_by_address = (a, b) ->
        a.address().getAddress().getHostAddress().compareTo(b.address().getAddress().getHostAddress());

    /** Compare {@link ClientInfo} by port */
    private static Comparator<ClientInfo> sort_by_port = (a, b) ->
        Integer.compare(a.address().getPort(), b.address().getPort());

    /** Client table address column */
    private final PVAStringArray client_table_addr = new PVAStringArray("addr");

    /** Client table auth column */
    private final PVAStringArray client_table_auth = new PVAStringArray("auth");

    /** Client table */
    private final PVAStructure client_table = new PVAStructure("clients", PVATable.STRUCT_NAME,
            new PVAStringArray(PVATable.LABELS_NAME, "Address", "Authentication"),
            new PVAStructure(PVATable.VALUE_NAME, "",
                    client_table_addr,
                    client_table_auth));


    /** @param prefix Status/Command PV prefix
     *  @param proxy {@link Proxy}
     *  @throws Exception on error
     */
    public ProxyInfo(final String prefix, final Proxy proxy) throws Exception
    {
        this.proxy = proxy;
        pvtotal_data = new PVAStructure(prefix + "pvtotal",
                         PVAScalar.SCALAR_STRUCT_NAME_STRING,
                         new PVAInt("value", 0),
                         new PVAStructure("display", "display_t",
                                 new PVAString("units", "PVs"),
                                 new PVAInt("precision", 0)),
                         stamp);
        pvtotal_pv = proxy.server.createPV(pvtotal_data.getName(), pvtotal_data);

        connected_data = new PVAStructure(prefix + "connected",
                PVAScalar.SCALAR_STRUCT_NAME_STRING,
                new PVAInt("value", 0),
                new PVAStructure("display", "display_t",
                        new PVAString("units", "PVs"),
                        new PVAInt("precision", 0)),
                stamp);
        connected_pv = proxy.server.createPV(connected_data.getName(), connected_data);

        unconnected_data = new PVAStructure(prefix + "unconnected",
                PVAScalar.SCALAR_STRUCT_NAME_STRING,
                new PVAInt("value", 0),
                new PVAStructure("display", "display_t",
                        new PVAString("units", "PVs"),
                        new PVAInt("precision", 0)),
                stamp);
        unconnected_pv = proxy.server.createPV(unconnected_data.getName(), unconnected_data);

        search_data = new PVAStructure(prefix + "existTestRate",
                PVAScalar.SCALAR_STRUCT_NAME_STRING,
                new PVADouble("value", 0),
                new PVAStructure("display", "display_t",
                        new PVAString("units", "Hz"),
                        new PVAInt("precision", 1)),
                stamp);
        search_pv = proxy.server.createPV(search_data.getName(), search_data);

        client_rate_data = new PVAStructure(prefix + "clientEventRate",
                PVAScalar.SCALAR_STRUCT_NAME_STRING,
                new PVADouble("value", 0),
                new PVAStructure("display", "display_t",
                        new PVAString("units", "Hz"),
                        new PVAInt("precision", 1)),
                stamp);
        client_rate_pv = proxy.server.createPV(client_rate_data.getName(), client_rate_data);

        server_rate_data = new PVAStructure(prefix + "serverPostRate",
                PVAScalar.SCALAR_STRUCT_NAME_STRING,
                new PVADouble("value", 0),
                new PVAStructure("display", "display_t",
                        new PVAString("units", "Hz"),
                        new PVAInt("precision", 1)),
                stamp);
        server_rate_pv = proxy.server.createPV(server_rate_data.getName(), server_rate_data);

        clients_table_pv = proxy.server.createPV(prefix + "clients", client_table);

        list_disconnected_pv = proxy.server.createPV(prefix + "listDisconnected", this::listDisconnected);

        info_pv_names = Set.of(pvtotal_pv.getName(),
                               connected_pv.getName(),
                               unconnected_pv.getName(),
                               search_pv.getName(),
                               client_rate_pv.getName(),
                               server_rate_pv.getName(),
                               clients_table_pv.getName(),
                               list_disconnected_pv.getName());

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
     *  @param search_rate Received PV name searches
     *  @param client_rate Received subscription updates from client side
     *  @param server_rate Updates sent to server side
     */
    public void update(final int total, final int connected,
                       final double search_rate, final double client_rate, final double server_rate)
    {
        try
        {
            // Update common time stamp
            stamp.set(Instant.now());

            PVAInt ival = pvtotal_data.get("value");
            if (ival.get() != total)
            {
                ival.set(total);
                pvtotal_pv.update(pvtotal_data);
            }

            ival = connected_data.get("value");
            if (ival.get() != connected)
            {
                ival.set(connected);
                connected_pv.update(connected_data);
            }

            ival = unconnected_data.get("value");
            final int unconnected = total - connected;
            if (ival.get() != unconnected)
            {
                ival.set(unconnected);
                unconnected_pv.update(unconnected_data);
            }

            PVADouble dval = search_data.get("value");
            if (dval.get() != search_rate)
            {
                dval.set(search_rate);
                search_pv.update(search_data);
            }

            dval = client_rate_data.get("value");
            if (dval.get() != client_rate)
            {
                dval.set(client_rate);
                client_rate_pv.update(client_rate_data);
            }

            dval = server_rate_data.get("value");
            if (dval.get() != server_rate)
            {
                dval.set(server_rate);
                server_rate_pv.update(server_rate_data);
            }

            clients_table_pv.update(updateClientTable());
        }
        catch (Exception ex)
        {
            logger.log(Level.WARNING, "Cannot update info PVs", ex);
        }
    }

    private PVAStructure updateClientTable()
    {
        final Collection<ClientInfo> clients = proxy.server.getClientInfos();
        final ArrayList<ClientInfo> sorted = new ArrayList<>(clients);
        sorted.sort(sort_by_address.thenComparing(sort_by_port));
        final int N = sorted.size();
        final String[] addr = new String[N], auth = new String[N];
        int i = 0;
        for (ClientInfo client : sorted)
        {
            addr[i] = client.address().getAddress().getHostAddress() + ":" + client.address().getPort();
            auth[i] = client.authentication().toString();
            ++i;
        }
        client_table_addr.set(addr);
        client_table_auth.set(auth);
        return client_table;
    }

    /** List disconnected PVs
     *  @param parameters Optional parameters (ignored)
     *  @return List of disconnected PVs
     *  @throws Exception on error
     */
    private PVAStructure listDisconnected(final PVAStructure parameters) throws Exception
    {
        return new PVAStructure("disconnected", PVATable.STRUCT_NAME,
                new PVAStringArray(PVATable.LABELS_NAME, "PV"),
                new PVAStructure(PVATable.VALUE_NAME, "",
                        new PVAStringArray("disconnected", proxy.getDisconnectedPVs())));
    }
}
