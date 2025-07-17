/*******************************************************************************
 * Copyright (c) 2025 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.phoebus.pvaify;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.epics.pva.data.PVADouble;
import org.epics.pva.data.PVAStructure;
import org.epics.pva.data.nt.PVAAlarm;
import org.epics.pva.data.nt.PVAScalar;
import org.epics.pva.data.nt.PVAScalar.Builder;
import org.epics.pva.data.nt.PVATimeStamp;
import org.epics.pva.server.PVAServer;
import org.epics.pva.server.SearchHandler;
import org.epics.pva.server.ServerPV;
import org.epics.vtype.VNumber;
import org.epics.vtype.VType;
import org.epics.vtype.Alarm;
import org.epics.vtype.Time;
import org.phoebus.pv.PV;
import org.phoebus.pv.PVFactory;
import org.phoebus.pv.ca.JCA_PVFactory;

/** CA (PV) to PVAccess converter
 *  @author Kay Kasemir
 */
public class Main
{
    public static Logger logger;

    /** Latch that's counted to zero to stop */
    private final CountDownLatch run = new CountDownLatch(1);

    /** PVA server side of this converter: Detects searches, provides PVA PVs */
    private PVAServer server;

    /** PVA PVs that we serve by name */
    private final ConcurrentHashMap<String, ServerPV> server_pvs = new ConcurrentHashMap<>();

    /** PVA PVs' most recent value */
    private final ConcurrentHashMap<String, PVAStructure> server_data = new ConcurrentHashMap<>();

    /** CA client side of this converter, used to look for CA PVs */
    private final PVFactory client_pv_factory = new JCA_PVFactory();

    /** CA client PVs, subscription updates are send to server side */
    private final ConcurrentHashMap<String, PV> client_pvs = new ConcurrentHashMap<>();

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

        final SearchHandler search_handler =
            (int seq, int cid, String name, InetSocketAddress client, Consumer<InetSocketAddress> reply_sender) ->
        {
            logger.log(Level.INFO, () -> client + " searches for " + name + " [CID " + cid + ", seq " + seq + "]");

            if (name.equals("QUIT"))
            {
                logger.log(Level.INFO, "Exit");
                run.countDown();
            }

            // TODO Create client PV
            client_pvs.computeIfAbsent(name, this::createClientPV);

            // Always return false to then check for ServerPV
            return false;
        };
        server = new PVAServer(search_handler);
    }

    /** Create a client PV that will receive CA updates
     *  @param name PV name for which we received a search
     *  @return CA PV
     */
    private PV createClientPV(final String name)
    {
        try
        {
            PV pv;
            pv = client_pv_factory.createPV(name, name);
            pv.onValueEvent().subscribe(value -> handleClientUpdate(name, value));
            return pv;
        }
        catch (Exception ex)
        {
            logger.log(Level.WARNING, "Cannot create client PV " + name, ex);
        }
        return null;
    }

    /** Handle a CA update: Create PVA PV on first update, then keep updating its value
     *  @param name PV name
     *  @param value Value received on client side
     */
    private void handleClientUpdate(final String name, final VType value)
    {
        logger.log(Level.INFO, "Client: " + name + " = " + value);
        ServerPV server_pv = server_pvs.computeIfAbsent(name, pv_name -> createServerPV(pv_name, value));

        try
        {
            // Update server's PV data from received value
            final PVAStructure data = server_data.get(name);

            final Instant time = Time.timeOf(value).getTimestamp();
            PVAStructure sub = data.get("timeStamp");
            sub.get(1).setValue(time.getEpochSecond());
            sub.get(2).setValue(time.getNano());

            final Alarm alarm = Alarm.alarmOf(value);
            sub = data.get("alarm");
            sub.get(1).setValue(alarm.getSeverity().ordinal());
            sub.get(2).setValue(alarm.getStatus().ordinal());

            if (value instanceof VNumber num)
                data.get("value").setValue(num.getValue().doubleValue());
            // TODO Handle more data types
            else
                throw new Exception("Value type is not handled");

            logger.log(Level.FINE, () -> "Sending update : " + data);
            server_pv.update(data);
        }
        catch (Exception ex)
        {
            logger.log(Level.WARNING, "Cannot update server PV " + name + " for " + value, ex);
        }
    }

    /** @param alarm VType alarm
     *  @return PVA alarm structure
     */
    static PVAAlarm convert(final Alarm alarm)
    {
        int index = alarm.getSeverity().ordinal();
        PVAAlarm.AlarmSeverity severity = PVAAlarm.AlarmSeverity.values()[index];

        index = alarm.getStatus().ordinal();
        PVAAlarm.AlarmStatus status = index < PVAAlarm.AlarmStatus.values().length
                                    ? PVAAlarm.AlarmStatus.values()[index]
                                    : PVAAlarm.AlarmStatus.UNDEFINED;
        return new PVAAlarm(severity, status, "");
    }

    /** Create PVA server PV with initial value
     *  @param name PV name
     *  @param value Initial value
     *  @return
     */
    private ServerPV createServerPV(final String name, final VType value)
    {
        try
        {
            Builder<PVADouble> builder;
            if (value instanceof VNumber num)
               builder = PVAScalar.doubleScalarBuilder(num.getValue().doubleValue());
            // TODO Handle more data types
            else
                throw new Exception("Data type is not handled");

            PVAScalar<PVADouble> data = builder.name(name)
                                               .timeStamp(new PVATimeStamp(Time.timeOf(value).getTimestamp()))
                                               .alarm(convert(Alarm.alarmOf(value)))
                                               .build();
            server_data.put(name, data);
            return server.createPV(name, data);
        }
        catch (Exception ex)
        {
            logger.log(Level.WARNING, "Cannot create server PV " + name + " for " + value, ex);
        }
        return null;
    }

    /** Stop */
    private void stop()
    {
        server.close();
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
