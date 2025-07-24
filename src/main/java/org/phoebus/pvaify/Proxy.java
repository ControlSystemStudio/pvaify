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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final CountDownLatch done = new CountDownLatch(1);

    /** PVA server side: Detects searches, provides PVA PVs */
    final PVAServer server;

    /** Proxy info PVs */
    private final ProxyInfo info;

    /** PVA PVs that we proxy by name */
    private final ConcurrentHashMap<String, ProxiedPV> pvs = new ConcurrentHashMap<>();

    /** Counter for received name searches */
    private final AtomicInteger search_counter = new AtomicInteger();

    /** Counter for subscription updates received on client side */
    final AtomicInteger client_update_counter = new AtomicInteger();

    /** Counter for updates sent to server side */
    final AtomicInteger server_update_counter = new AtomicInteger();

    public Proxy(final String prefix) throws Exception
    {
        server = new PVAServer(this::handleSearchRequest);
        info = new ProxyInfo(prefix , server);
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
        logger.log(Level.FINE, () -> client + " searches for " + name + " [CID " + cid + ", seq " + seq + "]");

        search_counter.incrementAndGet();

        // TODO Make this one of the status/control PVs
        if (name.equals("QUIT"))
        {
            logger.log(Level.INFO, "Exit");
            done.countDown();
        }

        // Info PVs are already handled by `server`, no need to proxy
        if (! info.isInfoPV(name))
        {
            // Create proxy PV unless it already exists
            final ProxiedPV pv = pvs.computeIfAbsent(name, pv_name -> new ProxiedPV(this, pv_name, reply_sender));
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

    public void mainLoop() throws InterruptedException
    {
        while (! done.await(1, TimeUnit.SECONDS))
        {
            int total = 0, connected = 0;
            for (ProxiedPV pv : pvs.values())
            {
                // Collect stats
                ++total;
                final boolean is_connected = pv.isConnected();
                if (is_connected)
                    ++connected;

                // Remove unused proxies.
                // Need a long timeout because client searches will settle to 15 sec
                // and we don't want to cull channels between a search that triggered
                // their creation and the next search that'll then find them.
                // (in case we don't get an earlier search reply out)
                if (! (is_connected && pv.isSubscribed())
                    &&
                    pv.getSecsInState() > 60.0)
                {
                    logger.log(Level.FINER, () -> "Removing unused proxy " + pv);
                    pv.close();
                }
            }

            info.update(total, connected, total - connected,
                        search_counter.getAndSet(0),
                        client_update_counter.getAndSet(0),
                        server_update_counter.getAndSet(0));
        }
    }

   public void close()
   {
       server.close();
   }
}
