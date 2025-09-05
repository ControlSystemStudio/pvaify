/*******************************************************************************
 * Copyright (c) 2025 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.phoebus.pvaify;

import org.phoebus.framework.preferences.AnnotatedPreferences;
import org.phoebus.framework.preferences.Preference;

/** Preference settings for Proxy
 *  @author Kay Kasemir
 */
public class ProxyPreferences
{
    // See src/main/resources/pvaify_preferences.properties
    @Preference public static String prefix;

    @Preference public static int client_throttle_ms;

    @Preference public static int main_loop_ms;

    @Preference public static double unused_pv_purge_sec;

    static
    {
        AnnotatedPreferences.initialize(ProxyPreferences.class, "/pvaify_preferences.properties");
    }
}
