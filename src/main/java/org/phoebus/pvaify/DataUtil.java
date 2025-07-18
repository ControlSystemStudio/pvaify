/*******************************************************************************
 * Copyright (c) 2025 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.phoebus.pvaify;

import java.time.Instant;

import org.epics.pva.data.PVADouble;
import org.epics.pva.data.PVAStructure;
import org.epics.pva.data.nt.PVAAlarm;
import org.epics.pva.data.nt.PVAScalar;
import org.epics.pva.data.nt.PVATimeStamp;
import org.epics.pva.data.nt.PVAScalar.Builder;
import org.epics.pva.server.ServerPV;
import org.epics.vtype.Alarm;
import org.epics.vtype.Time;
import org.epics.vtype.VNumber;
import org.epics.vtype.VType;

/** Data utility to convert VType to PVAStrucure
 *  @author Kay Kasemir
 */
public class DataUtil
{
    /** @param alarm VType alarm
     *  @return PVA alarm structure
     */
    public static PVAAlarm convert(final Alarm alarm)
    {
        int index = alarm.getSeverity().ordinal();
        PVAAlarm.AlarmSeverity severity = PVAAlarm.AlarmSeverity.values()[index];

        index = alarm.getStatus().ordinal();
        PVAAlarm.AlarmStatus status = index < PVAAlarm.AlarmStatus.values().length
                                    ? PVAAlarm.AlarmStatus.values()[index]
                                    : PVAAlarm.AlarmStatus.UNDEFINED;
        return new PVAAlarm(severity, status, alarm.getStatus().name());
    }


    /** Create PVA from VType
     *  @param value {@link VType} value
     *  @return {@link PVAStructure}
     *  @throws Exception on error
     */
    public static PVAStructure create(final String name, final VType value) throws Exception
    {
        Builder<PVADouble> builder;
        if (value instanceof VNumber num)
           builder = PVAScalar.doubleScalarBuilder(num.getValue().doubleValue());
        // TODO Handle more data types
        else
            throw new Exception("Data type is not handled");

        return builder.name(name)
                      .timeStamp(new PVATimeStamp(Time.timeOf(value).getTimestamp()))
                      .alarm(convert(Alarm.alarmOf(value)))
                      .build();
    }

    /** Update PVA from VType
     *  @param data {@link PVAStructure} to update
     *  @param value {@link VType} from which to update
     *  @throws Exception on error
     */
    public static  void update(final PVAStructure data, final VType value) throws Exception
    {
        // Update server's PV data from received value
        final Instant time = Time.timeOf(value).getTimestamp();
        PVAStructure sub = data.get("timeStamp");
        sub.get(1).setValue(time.getEpochSecond());
        sub.get(2).setValue(time.getNano());

        final Alarm alarm = Alarm.alarmOf(value);
        sub = data.get("alarm");
        sub.get(1).setValue(alarm.getSeverity().ordinal());
        sub.get(2).setValue(alarm.getStatus().ordinal());
        sub.get(3).setValue(alarm.getName());

        if (value instanceof VNumber num)
            data.get("value").setValue(num.getValue().doubleValue());
        // TODO Handle more data types
        else
            throw new Exception("Value type is not handled");
    }
}
