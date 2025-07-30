/*******************************************************************************
 * Copyright (c) 2025 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/

package org.phoebus.pvaify;

import static org.phoebus.pvaify.Proxy.logger;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.epics.vtype.VType;

/** Cache for updates received on client side
 *
 *  Instead of handling every received client update
 *  right away, we cache them in here to control
 *  the schedule.
 */
class ClientUpdateCache
{
    private final Map<ProxiedPV, VType> cache = new HashMap<>();
    private final Map<ProxiedPV, VType> save_copy = new HashMap<>();

    // TODO Handle scalars different from arrays?
    //      Prevent array updates from delaying scalar updates?

    /** @param proxy_pv {@link ProxiedPV} that received a client side update
     *  @param value Received client side value
     */
    void add(final ProxiedPV proxy_pv, final VType value)
    {
        final VType previous;
        synchronized (cache)
        {
            previous = cache.put(proxy_pv, value);
        }
        if (previous != null)
            logger.log(Level.FINE, () -> proxy_pv.getName() + " client side overrun");
    }

    /** Process accumulated valued */
    public void process()
    {
        synchronized (cache)
        {
            save_copy.putAll(cache);
            cache.clear();
        }

        save_copy.forEach(this::process);
        save_copy.clear();
    }

    /** @param proxy_pv Proxy where sender side should be updated
     *  @param value Value to send out on server side
     */
    private void process(final ProxiedPV proxy_pv, final VType value)
    {
        proxy_pv.updateServerSide(value);
    }
}
