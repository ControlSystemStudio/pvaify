/*******************************************************************************
 * Copyright (c) 2025 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.phoebus.pvaify;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.phoebus.pvaify.PVListFileRule.Type;

/** Handle a `*.pvlist` file with DENY and ALLOW rules */
public class PVListFile
{
    private List<PVListFileRule> deny = new ArrayList<>();
    private List<PVListFileRule> allow = new ArrayList<>();

    /** @param filename `*.pvlist` file to partse
     *  @throws Exception on error
     */
    public PVListFile(final String filename) throws Exception
    {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename)))
        {
            String line;
            int lineno = 0;
            while ((line = reader.readLine()) != null)
            {
                ++lineno;
                line = line.strip();
                if (line.isBlank()  ||  line.startsWith("#"))
                    continue;

                PVListFileRule rule;
                try
                {
                    rule = PVListFileRule.create(line);
                }
                catch (Exception ex)
                {
                    throw new Exception(filename + " line " + lineno, ex);
                }
                if (rule.type == Type.ALLOW)
                    allow.add(rule);
                else
                    deny.add(rule);
            }
        }
    }

    public boolean mayAccess(final String pv_name, final InetAddress address)
    {
        // Does any rule specifically deny access?
        for (var rule : deny)
            if (rule.matches(pv_name, address))
                return false;
        // Does any rule specifically allow access?
        for (var rule : allow)
            if (rule.matches(pv_name, address))
                return true;
        // No match: Deny
        return false;
    }

    @Override
    public String toString()
    {
        final StringBuilder buf = new StringBuilder();
        for (var rule : deny)
            buf.append(rule).append('\n');
        for (var rule : allow)
            buf.append(rule).append('\n');
        return buf.toString();
    }
}
