/*******************************************************************************
 * Copyright (c) 2025 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.phoebus.pvaify;

import java.net.InetAddress;
import java.util.regex.Pattern;

/** Rule that allows or denies access by PV name or client host */
abstract public class PVListFileRule
{
    protected static enum Type { ALLOW, DENY };

    protected final Type type;

    protected final Pattern pv_pattern;

    private PVListFileRule(final Type type, final String pv_pattern)
    {
        this.type = type;
        this.pv_pattern = Pattern.compile(pv_pattern);
    }

    private static class AllowingRule extends PVListFileRule
    {
        AllowingRule(final String pv_pattern)
        {
            super(Type.ALLOW, pv_pattern);
        }
    }

    private static class DenyingRule extends PVListFileRule
    {
        protected final InetAddress address;

        DenyingRule(final String pv_pattern, final InetAddress address)
        {
            super(Type.DENY, pv_pattern);
            this.address = address;
        }

        public boolean matches(final String pv_name, final InetAddress address)
        {
            return super.matches(pv_name, address) &&
                   (this.address == null || this.address.equals(address));
        }

        @Override
        public String toString()
        {
            String result = super.toString();
            if (address != null)
                result += " FROM " + address.getHostAddress();
            return result;
        }
    }

    static PVListFileRule create(final String line) throws Exception
    {
        final String[] parts = line.split("\\s+");
        if (parts.length < 2)
            throw new Exception("Missing {pattern} DENY|ALLOW");

        final String pv_pattern = parts[0];

        Type type;
        try
        {
            type = Type.valueOf(parts[1]);
        }
        catch (IllegalArgumentException ex)
        {
            throw new Exception("Expect " + Type.ALLOW + " or " + Type.DENY);
        }

        if (type == Type.ALLOW)
            return new AllowingRule(pv_pattern);
        else
        {
            InetAddress address = null;
            if (parts.length >= 4  &&  "FROM".equals(parts[2]))
                address = InetAddress.getByName(parts[3]);
            return new DenyingRule(pv_pattern, address);
        }
    }

    public boolean matches(final String pv_name, final InetAddress address)
    {
        return pv_pattern.matcher(pv_name).matches();
    }

    @Override
    public String toString()
    {
        return pv_pattern.pattern() + "\t" + type.name();
    }
}
