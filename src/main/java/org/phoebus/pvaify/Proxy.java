/*******************************************************************************
 * Copyright (c) 2025 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.phoebus.pvaify;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.epics.pva.server.PVAServer;

/** Proxy from CA (really PV pool VType PV) to PVAccess
 *
 *  Starts PVA server, creating a {@link ProxiedPV} for
 *  each observed search request
 *
 *  @author Kay Kasemir
 */
class Proxy
{
    /** Logger for all proxy code */
    public static Logger logger;

    /** Proxy runs until this counts to zero */
    private final CountDownLatch run = new CountDownLatch(1);

    /** PVA server side: Detects searches, provides PVA PVs */
    final PVAServer server;

    /** PVA PVs that we proxy by name */
    private final ConcurrentHashMap<String, ProxiedPV> pvs = new ConcurrentHashMap<>();

    Proxy() throws Exception
    {
        server = new PVAServer(this::handleSearchRequest);
    }

    /** Server invokes this for every received name search.
     *
     *  @param seq Client's search sequence
     *  @param cid Client channel ID or -1
     *  @param name Channel name or <code>null</code>
     *  @param client Client's address
     *  @param reply_sender Callback for TCP address of server
     *  @return <code>true</code> if the search request was handled
     */
    private boolean handleSearchRequest(final int seq, final int cid, final String name,
                                        final InetSocketAddress client,
                                        final Consumer<InetSocketAddress> reply_sender)
    {
        logger.log(Level.INFO, () -> client + " searches for " + name + " [CID " + cid + ", seq " + seq + "]");

        // TODO Make this one of the status/control PVs
        if (name.equals("QUIT"))
        {
            logger.log(Level.INFO, "Exit");
            run.countDown();
        }

        // Create proxy PV unless it already exists
        final ProxiedPV pv = pvs.computeIfAbsent(name, pv_name -> new ProxiedPV(this, pv_name));
        try
        {   // Start the proxy PV
            //
            // This might fail to connect, then dispose the PV and remove it from `pvs`.
            // To avoid 'recursive update' errors, adding the PV to pvs via computeIfAbsent
            // and starting (and potentially again removing the PV) thus need to be
            // separate steps
            pv.start();
        }
        catch (Exception ex)
        {
            logger.log(Level.WARNING, "Cannot create client PV " + name, ex);
        }

        // Always return false.
        // If all goes well, ProxiedPV will soon register a ServerPV
        // and PVA server will then reply to the search with that server PV.
        // If we returned true, the PVA server would assume we already replied
        // to the search.
        return false;
    }

   /** @param pv {@link ProxiedPV} that has been disposed and should no longer been tracked */
   void forgetProxiedPV(final ProxiedPV pv)
   {
       if (! pvs.remove(pv.getName(), pv))
           logger.log(Level.WARNING, "Tried to forget unknown PV " + pv.getName(),
                      new Exception("Stack trace"));
   }

   void close()
   {
       server.close();
   }

   void awaitShutdown() throws InterruptedException
   {
       run.await();
   }
}
