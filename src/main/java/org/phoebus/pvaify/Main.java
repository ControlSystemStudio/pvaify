/*******************************************************************************
 * Copyright (c) 2025 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.phoebus.pvaify;

import java.io.FileInputStream;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

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
        System.out.println("-help                    - This text");
        System.out.println("-settings settings.ini   - Import settings from file");
        System.out.println("-pvlist settings.pvlist  - PV name filters");
        System.out.println();
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

        // Load logging configuration
        LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));
        Proxy.logger = Logger.getLogger(Main.class.getPackageName());

        Preferences prefs = Preferences.userNodeForPackage(org.phoebus.pv.ca.JCA_PVFactory.class);
        prefs.putBoolean("dbe_property_supported", true);

        PVListFile pvlist = null;

        for (int i=0; i<args.length; ++i)
        {
            if (args[i].startsWith("-h"))
            {
                help();
                return;
            }
            if (args[i].startsWith("-s"))
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
            if (args[i].startsWith("-pvl"))
            {
                if (i+1 >= args.length)
                {
                    help();
                    System.err.println("Missing -pvlist filename");
                    return;
                }
                pvlist = new PVListFile(args[i+1]);
                ++i;
            }
        }

        final Proxy proxy = new Proxy(ProxyPreferences.prefix, pvlist);
        proxy.mainLoop();
        proxy.close();
    }
}
