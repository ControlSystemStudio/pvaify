/*******************************************************************************
 * Copyright (c) 2025-2026 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.phoebus.pvaify;

import java.io.FileInputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.epics.pva.acf.AccessConfig;
import org.epics.pva.acf.AccessConfigParser;
import org.epics.pva.pvlist.PVListFile;
import org.phoebus.framework.preferences.PropertyPreferenceLoader;

/** Proxy from CA (VType PV) to PVAccess
 *  @author Kay Kasemir
 */
public class Main
{
    private static void help()
    {
        System.out.println("Command-line arguments:");
        System.out.println();
        System.out.println("-help                       - This text");
        System.out.println("-settings settings.ini      - Import settings from file");
        System.out.println("-pvlist settings.pvlist     - PV name filters");
        System.out.println("-acf settings.acf           - Access security configuration file");
        System.out.println("-logging logging.properties - Logging configuration");
        System.out.println();
    }

    /** Concentrate all settings into phoebus preferences (-settings xxx.ini).
     *
     *  ProxyPreferences and core-pv-ca for client side already use phoebus preferences.
     *  PVAccess server settings are handled by core-pv, using environment or java properties.
     *  Populate them from preferences similar to what core-pv-pva does,
     *  but don't require core-pv-pva to be included
     */
    private static void configPVAfromPreferences()
    {
        final Preferences pva_prefs = Preferences.userRoot().node("/org/phoebus/pv/pva");
        for (var pref : List.of("EPICS_PVAS_BROADCAST_PORT",
                                "EPICS_PVA_SERVER_PORT",
                                "EPICS_PVAS_TLS_PORT",
                                "EPICS_PVAS_INTF_ADDR_LIST",
                                "EPICS_PVAS_TLS_KEYCHAIN",
                                "EPICS_PVAS_TLS_OPTIONS",
                                "EPICS_PVA_ADDR_LIST",
                                "EPICS_PVA_AUTO_ADDR_LIST"))
        {   // If there is a /org/phoebus/pv/pva/epics_pva_abc setting, turn into EPICS_PVA_ABC property for core-pva
            String value = pva_prefs.get(pref.toLowerCase(), null);
            if (value != null)
            {   // Patch true/false for this one 'boolean' property into YES/NO
                if ("EPICS_PVA_AUTO_ADDR_LIST".equals(pref))
                    value = value.replace("false", "NO").replace("true", "YES");
                System.setProperty(pref, value);
            }
        }
    }


    public static void main(String[] args) throws Exception
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
        System.out.println();

        // Load default logging configuration
        LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));
        Proxy.logger = Logger.getLogger(Main.class.getPackageName());

        PVListFile pvlist = PVListFile.getDefault();
        AccessConfig access = AccessConfig.getDefault();

        // Parse command line args
        for (int i=0; i<args.length; ++i)
        {
            if (args[i].startsWith("-h"))
            {
                help();
                return;
            }
            else if (args[i].startsWith("-s"))
            {
                if (i+1 >= args.length)
                {
                    help();
                    System.err.println("Missing -settings filename");
                    return;
                }
                PropertyPreferenceLoader.load(new FileInputStream(args[i+1]));
                ++i;
            }
            else if (args[i].startsWith("-pvl"))
            {
                if (i+1 >= args.length)
                {
                    help();
                    System.err.println("Missing -pvlist filename");
                    return;
                }
                pvlist = new PVListFile(args[i+1]);
                Proxy.logger.log(Level.CONFIG, "PVList rules:\n" + pvlist);
                ++i;
            }
            else if (args[i].startsWith("-acf"))
            {
                if (i+1 >= args.length)
                {
                    help();
                    System.err.println("Missing -acf filename");
                    return;
                }
                access = new AccessConfigParser().parse(args[i+1]);
                Proxy.logger.log(Level.CONFIG, "ACF rules:\n" + access);
                ++i;
            }
            else if (args[i].startsWith("-log"))
            {
                if (i+1 >= args.length)
                {
                    help();
                    System.err.println("Missing -logging file");
                    return;
                }
                LogManager.getLogManager().updateConfiguration(new FileInputStream(args[i+1]), (k) -> ((o, n) -> n == null ? o : n));
                ++i;
            }
            else
            {
                help();
                System.err.println("Unknown parameter " + args[i]);
                return;

            }
        }

        configPVAfromPreferences();

        final Proxy proxy = new Proxy(ProxyPreferences.prefix, pvlist, access);
        proxy.mainLoop();
        proxy.close();
    }
}
