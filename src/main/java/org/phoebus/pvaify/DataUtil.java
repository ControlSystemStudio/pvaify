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
import java.util.Objects;

import org.epics.pva.data.PVAByteArray;
import org.epics.pva.data.PVAData;
import org.epics.pva.data.PVADouble;
import org.epics.pva.data.PVADoubleArray;
import org.epics.pva.data.PVAFloatArray;
import org.epics.pva.data.PVAInt;
import org.epics.pva.data.PVAIntArray;
import org.epics.pva.data.PVANumber;
import org.epics.pva.data.PVAShortArray;
import org.epics.pva.data.PVAString;
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
import org.epics.vtype.VFloatArray;
import org.epics.vtype.VIntArray;
import org.epics.vtype.VNumber;
import org.epics.vtype.VShortArray;
import org.epics.vtype.VString;
import org.epics.vtype.VType;
import org.phoebus.pv.PV;

/** Data utility to convert {@link VType} to {@link PVAStructure} (normative type)
 *  @author Kay Kasemir
 */
public class DataUtil
{
    /** @param o Object
     *  @param max_len Maximum string length
     *  @return String representation of the object
     */
    public static String shorten(final Object o, final int max_len)
    {
        String text = Objects.toString(o);
        if (text.length() > max_len)
            return text.substring(0, max_len) + "...";
        return text;
    }

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
        else if (value instanceof VFloatArray val)
        {
            final float[] array = val.getData().toArray(new float[val.getSizes().getInt(0)]);
            builder = new Builder<PVAFloatArray>()
                      .value(new PVAFloatArray(PVAScalar.VALUE_NAME_STRING, array));
        }
        else if (value instanceof VIntArray val)
        {
            final int[] array = val.getData().toArray(new int[val.getSizes().getInt(0)]);
            builder = new Builder<PVAIntArray>()
                      .value(new PVAIntArray(PVAScalar.VALUE_NAME_STRING, false, array));
        }
        else if (value instanceof VShortArray val)
        {
            final short[] array = val.getData().toArray(new short[val.getSizes().getInt(0)]);
            builder = new Builder<PVAShortArray>()
                      .value(new PVAShortArray(PVAScalar.VALUE_NAME_STRING, false, array));
        }
        else if (value instanceof VByteArray val)
        {
            final byte[] array = val.getData().toArray(new byte[val.getSizes().getInt(0)]);
            builder = new Builder<PVAByteArray>()
                      .value(new PVAByteArray(PVAScalar.VALUE_NAME_STRING, false, array));
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

    /** Update PVA 'display' from VType
     *  @param data {@link PVAStructure} to update
     *  @param new_value {@link VType} from which to update
     *  @throws Exception on error
     */
    private static void updateDisplay(final PVAStructure data, final VType new_value) throws Exception
    {
        final Display display = Display.displayOf(new_value);
        if (display == null)
            return;

        final PVAStructure data_display = data.get("display");
        if (data_display == null)
            return;

        PVAString txt = data_display.get("units");
        if (txt != null)
            txt.set(display.getUnit());

        PVAInt dec = data_display.get("precision");
        if (dec != null)
            dec.set(display.getFormat().getMinimumFractionDigits());

        // TODO Update more elements...
    }

    /** Update PVA from VType
     *  @param data {@link PVAStructure} to update
     *  @param new_value {@link VType} from which to update
     *  @throws Exception on error
     */
    public static void update(final PVAStructure data, final VType new_value) throws Exception
    {
        // Update server's PV data from received value
        final Instant time = Time.timeOf(new_value).getTimestamp();
        PVAStructure sub = data.get("timeStamp");
        sub.get(1).setValue(time.getEpochSecond());
        sub.get(2).setValue(time.getNano());

        final Alarm alarm = Alarm.alarmOf(new_value);
        sub = data.get("alarm");
        sub.get(1).setValue(alarm.getSeverity().ordinal());
        sub.get(2).setValue(alarm.getStatus().ordinal());
        sub.get(3).setValue(alarm.getName());

        final PVAData value = data.get("value");
        if (new_value instanceof VDouble val)
            value.setValue(val.getValue().doubleValue());
        else if (new_value instanceof VNumber val)
            value.setValue(val.getValue().intValue());
        else if (new_value instanceof VString val)
            value.setValue(val.getValue());
        else if (new_value instanceof VEnum val)
        {
            // TODO Update enum labels?
            value.setValue(val.getIndex());
        }
        else if (new_value instanceof VDoubleArray val)
            value.setValue(val.getData().toArray(new double[val.getSizes().getInt(0)]));
        else if (new_value instanceof VFloatArray val)
            value.setValue(val.getData().toArray(new float[val.getSizes().getInt(0)]));
        else if (new_value instanceof VIntArray val)
            value.setValue(val.getData().toArray(new int[val.getSizes().getInt(0)]));
        else if (new_value instanceof VShortArray val)
            value.setValue(val.getData().toArray(new short[val.getSizes().getInt(0)]));
        else if (new_value instanceof VByteArray val)
            value.setValue(val.getData().toArray(new byte[val.getSizes().getInt(0)]));
        // TODO Handle more data types
        else
            throw new Exception("Value type is not handled: " + new_value);

        updateDisplay(data, new_value);
    }

    /** Write data received on server side back to client
     *  @param client_pv Client side (CA) PV
     *  @param data Data received on server side
     *  @throws Exception on error
     */
    public static void writeCA_PV(final PV client_pv, final PVAStructure data) throws Exception
    {
        final PVAData value = data.get("value");
        if (value instanceof PVADouble val)
            client_pv.write(val.get());
        else if (value instanceof PVANumber val)
            client_pv.write(val.getNumber().longValue());
        else if (value instanceof PVAStructure val  &&
                 val.getStructureName().equals("enum_t"))
        {
            final PVAInt index = val.get("index");
            client_pv.write(index.get());
        }
        else if (value instanceof PVAString val)
            client_pv.write(val.get());
        // TODO Handle more data types
        else
            throw new Exception("Data type not handled: " + data + (data != null ? " (" + data.getClass() + ")" : ""));
    }
}
