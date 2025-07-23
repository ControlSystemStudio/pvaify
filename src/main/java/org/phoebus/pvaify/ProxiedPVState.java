/*******************************************************************************
 * Copyright (c) 2025 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.phoebus.pvaify;

public class ProxiedPVState
{
    public enum State
    {
        /** Just created, needs to be started */
        Created,
        /** Started client side, awaiting first value */
        Started,
        /** Created server PV with value, have not seen any client on the server side */
        FreshServer,
        /** Server has proxied at least one update, there was a client to the server side */
        Active,
        /** No client any longer on the server side */
        Idle,
        /** Closing server and client end down */
        Disposed
    }

    private State state = State.Created;
    private long millis = System.currentTimeMillis();

    synchronized boolean compareAndSet(State expectedValue, State newValue)
    {
        if (state != expectedValue)
            return false;
        set(newValue);
        return true;
    }

    synchronized State get()
    {
        return state;
    }

    synchronized void set(State newValue)
    {
        if (state == newValue)
            return;
        state = newValue;
        millis = System.currentTimeMillis();
    }

    synchronized public double getSecsInState()
    {
        long now = System.currentTimeMillis();
        return (now - millis) / 1000.0;
    }
}
