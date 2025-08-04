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

    /** Cache for value updates from client side */
    final ClientUpdateCache client_update_cache;

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
        client_update_cache = new ClientUpdateCache();
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
        // (During startup, info can be null because we're just constructing it)
        if (info != null  &&  !info.isInfoPV(name))
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
        long last_ms = System.currentTimeMillis();
        while (! done.await(ProxyPreferences.main_loop_ms, TimeUnit.MILLISECONDS))
        {
            // Process client side updates
            client_update_cache.process();

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

            // Publish stats
            long ms = System.currentTimeMillis();
            double sec = ms == last_ms
                       ? 1.0
                       : (ms - last_ms) / 1000.0;
            if (sec >= 1.0)
            {
                info.update(total, connected,
                            search_counter.getAndSet(0) / sec,
                            client_update_counter.getAndSet(0) / sec,
                            server_update_counter.getAndSet(0) / sec);
                last_ms = ms;
            }
        }
    }

   public void close()
   {
       server.close();
   }
}
