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
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.epics.pva.server.PVAServer;

/** CA (PV) to PVAccess converter
 *  @author Kay Kasemir
 */
public class Main
{
    public static Logger logger;

    /** Latch that's counted to zero to stop */
    private final CountDownLatch run = new CountDownLatch(1);

    /** PVA PVs that we proxy by name */
    private final ConcurrentHashMap<String, ProxiedPV> pvs = new ConcurrentHashMap<>();

    /** Initialize */
    Main() throws Exception
    {
        // Load logging configuration
        LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));
        Main.logger = Logger.getLogger(Main.class.getPackageName());
    }

    /** Start server */
    private void start() throws Exception
    {
        // http://patorjk.com/software/taag/#p=display&f=Epic&t=PVA-i-fy
        System.out.println(" _______           _______     _________     _______          ");
        System.out.println("(  ____ )|\\     /|(  ___  )    \\__   __/    (  ____ \\|\\     /|");
        System.out.println("| (    )|| )   ( || (   ) |       ) (       | (    \\/( \\   / )");
        System.out.println("| (____)|| |   | || (___) | _____ | | _____ | (__     \\ (_) / ");
        System.out.println("|  _____)( (   ) )|  ___  |(_____)| |(_____)|  __)     \\   /  ");
        System.out.println("| (       \\ \\_/ / | (   ) |       | |       | (         ) (   ");
        System.out.println("| )        \\   /  | )   ( |    ___) (___    | )         | |   ");
        System.out.println("|/          \\_/   |/     \\|    \\_______/    |/          \\_/   ");

        ProxiedPV.server = new PVAServer(this::handleSearchRequest);
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
             final InetSocketAddress client, final Consumer<InetSocketAddress> reply_sender)
    {
        logger.log(Level.INFO, () -> client + " searches for " + name + " [CID " + cid + ", seq " + seq + "]");

        // TODO Make this one of the status/control PVs
        if (name.equals("QUIT"))
        {
            logger.log(Level.INFO, "Exit");
            run.countDown();
        }

        // Create proxy PV unless it already exists
        pvs.computeIfAbsent(name, this::createProxy);

        // Always return false to then find the ServerPV that we might just have created
        return false;
    }

    /** Create a proxy PV that will receive updates and forward them to PVA
     *  @param name PV name for which we received a search
     *  @return ProxiedPV
     */
    private ProxiedPV createProxy(final String name)
    {
        try
        {
            return new ProxiedPV(name, this::forgetProxy);
        }
        catch (Exception ex)
        {
            logger.log(Level.WARNING, "Cannot create client PV " + name, ex);
        }
        return null;
    }

    /** @param pv {@link ProxiedPV} that has been disposed and should no longer been tracked */
    private void forgetProxy(final ProxiedPV pv)
    {
        if (! pvs.remove(pv.getName(), pv))
            logger.log(Level.WARNING, "Tried to forget unknown PV " + pv.getName(),
                       new Exception("Stack trace"));
    }

    /** Stop */
    private void stop()
    {
        ProxiedPV.server.close();
    }

    /** 'main' */
    public static void main(String[] args) throws Exception
    {
        final Main main = new Main();
        main.start();
        main.run.await();
        main.stop();
    }
}
