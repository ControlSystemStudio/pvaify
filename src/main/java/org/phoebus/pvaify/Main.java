package org.phoebus.pvaify;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.epics.pva.data.PVADouble;
import org.epics.pva.data.nt.PVAScalar;
import org.epics.pva.server.PVAServer;
import org.epics.pva.server.SearchHandler;
import org.epics.pva.server.ServerPV;
import org.epics.vtype.VNumber;
import org.epics.vtype.VType;
import org.phoebus.pv.PV;
import org.phoebus.pv.PVFactory;
import org.phoebus.pv.ca.JCA_PVFactory;

public class Main
{
    public static Logger logger;

    private final CountDownLatch run = new CountDownLatch(1);
    private PVAServer server;

    private final PVFactory client_pv_factory = new JCA_PVFactory();
    private final ConcurrentHashMap<String, PV> client_pvs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ServerPV> server_pvs = new ConcurrentHashMap<>();

    Main() throws Exception
    {
        //Load logging configuration
        LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));
        Main.logger = Logger.getLogger(Main.class.getPackageName());
    }


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

    private void handleClientUpdate(final String name, final VType value)
    {
        logger.log(Level.INFO, "Client: " + name + " = " + value);
        ServerPV server_pv = server_pvs.computeIfAbsent(name, pv_name -> createServerPV(pv_name, value));

        // TODO update server_pv with value
    }

    private ServerPV createServerPV(final String name, final VType value)
    {
        try
        {
            if (value instanceof VNumber num)
            {
                PVAScalar<PVADouble> data = PVAScalar.doubleScalarBuilder(num.getValue().doubleValue())
                                                     .name(name)
                                                     .build();
                return server.createPV(name, data);
            }
        }
        catch (Exception ex)
        {
            logger.log(Level.WARNING, "Cannot create server PV " + name + " for " + value, ex);
        }
        return null;
    }


    private void stop()
    {
        server.close();
    }

    public static void main(String[] args) throws Exception
    {
        final Main main = new Main();
        main.start();
        main.run.await();
        main.stop();
    }
}
