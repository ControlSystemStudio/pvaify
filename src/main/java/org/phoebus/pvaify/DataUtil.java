/*******************************************************************************
 * Copyright (c) 2025 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.phoebus.pvaify;

import java.time.Instant;
import java.util.List;

import org.epics.pva.data.PVAByteArray;
import org.epics.pva.data.PVADoubleArray;
import org.epics.pva.data.PVAStructure;
import org.epics.pva.data.nt.PVAAlarm;
import org.epics.pva.data.nt.PVADisplay;
import org.epics.pva.data.nt.PVAEnum;
import org.epics.pva.data.nt.PVAScalar;
import org.epics.pva.data.nt.PVAScalar.Builder;
import org.epics.pva.data.nt.PVATimeStamp;
import org.epics.vtype.Alarm;
import org.epics.vtype.Display;
import org.epics.vtype.Time;
import org.epics.vtype.VByteArray;
import org.epics.vtype.VDouble;
import org.epics.vtype.VDoubleArray;
import org.epics.vtype.VEnum;
import org.epics.vtype.VNumber;
import org.epics.vtype.VString;
import org.epics.vtype.VType;

/** Data utility to convert {@link VType} to {@link PVAStructure} (normative type)
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
     *  @param name PV name
     *  @param value {@link VType} value
     *  @return {@link PVAStructure}
     *  @throws Exception on error
     */
    public static PVAStructure create(final String name, final VType value) throws Exception
    {
        Builder<?> builder;
        if (value instanceof VDouble val)
            builder = PVAScalar.doubleScalarBuilder(val.getValue().doubleValue());
        else if (value instanceof VNumber val)
           builder = PVAScalar.intScalarBuilder(val.getValue().intValue());
        else if (value instanceof VString val)
            builder = PVAScalar.stringScalarBuilder(val.getValue());
        else if (value instanceof VEnum val)
        {
            final List<String> choices = val.getDisplay().getChoices();
            final String[] labels = choices.toArray(new String[choices.size()]);
            builder = new Builder<PVAEnum>()
                      .value(new PVAEnum(PVAScalar.VALUE_NAME_STRING,
                                         val.getIndex(), labels));
        }
        else if (value instanceof VDoubleArray val)
        {
            final double[] array = val.getData().toArray(new double[val.getSizes().getInt(0)]);
            builder = new Builder<PVADoubleArray>()
                      .value(new PVADoubleArray(PVAScalar.VALUE_NAME_STRING, array));
        }
        else if (value instanceof VByteArray val)
        {
            final byte[] array = val.getData().toArray(new byte[val.getSizes().getInt(0)]);
            builder = new Builder<PVAByteArray>()
                      .value(new PVAByteArray(PVAScalar.VALUE_NAME_STRING, true, array));
        }
        // TODO Handle more data types
        else
            throw new Exception("Value type is not handled: " + value);

        final Display display = Display.displayOf(value);
        if (display != null)
            builder = builder.display(
                new PVADisplay(display.getDisplayRange().getMinimum(),
                               display.getDisplayRange().getMaximum(),
                               "",
                               display.getUnit(),
                               display.getFormat().getMinimumFractionDigits(),
                               PVADisplay.Form.DEFAULT));

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

        if (value instanceof VDouble val)
            data.get("value").setValue(val.getValue().doubleValue());
        else if (value instanceof VNumber val)
            data.get("value").setValue(val.getValue().intValue());
        else if (value instanceof VString val)
            data.get("value").setValue(val.getValue());
        else if (value instanceof VEnum val)
        {
            // TODO Update enum labels?
            data.get("value").setValue(val.getIndex());
        }
        else if (value instanceof VDoubleArray val)
            data.get("value")
                .setValue(val.getData()
                             .toArray(new double[val.getSizes().getInt(0)]));
        else if (value instanceof VByteArray val)
            data.get("value")
                .setValue(val.getData()
                             .toArray(new byte[val.getSizes().getInt(0)]));
        // TODO Handle more data types
        else
            throw new Exception("Value type is not handled: " + value);
    }
}
