/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.loxone.internal.controls;

import static org.openhab.binding.loxone.internal.LxBindingConstants.*;

import java.io.IOException;

import org.eclipse.smarthome.core.library.types.IncreaseDecreaseType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.loxone.internal.types.LxUuid;

/**
 * A dimmer type of control on Loxone Miniserver.
 * <p>
 * According to Loxone API documentation, a dimmer control is:
 * <ul>
 * <li>a virtual input of dimmer type
 * </ul>
 *
 * @author Stephan Brunner - initial contribution
 *
 */
class LxControlDimmer extends LxControl {

    static class Factory extends LxControlInstance {
        @Override
        LxControl create(LxUuid uuid) {
            return new LxControlDimmer(uuid);
        }

        @Override
        String getType() {
            return "dimmer";
        }
    }

    /**
     * States
     */
    private static final String STATE_POSITION = "position";
    private static final String STATE_MIN = "min";
    private static final String STATE_MAX = "max";
    private static final String STATE_STEP = "step";

    /**
     * Command string used to set the dimmer ON
     */
    private static final String CMD_ON = "On";
    /**
     * Command string used to set the dimmer to OFF
     */
    private static final String CMD_OFF = "Off";

    private LxControlDimmer(LxUuid uuid) {
        super(uuid);
    }

    @Override
    public void initialize(LxControlConfig config) {
        super.initialize(config);
        addChannel("Dimmer", new ChannelTypeUID(BINDING_ID, MINISERVER_CHANNEL_TYPE_DIMMER), defaultChannelLabel,
                "Dimmer", tags, this::handleCommands, this::getChannelState);
    }

    private void handleCommands(Command command) throws IOException {
        if (command instanceof OnOffType) {
            if (command == OnOffType.ON) {
                sendAction(CMD_ON);
            } else {
                sendAction(CMD_OFF);
            }
        } else if (command instanceof PercentType) {
            PercentType percentCmd = (PercentType) command;
            setPosition(percentCmd.doubleValue());
        } else if (command instanceof IncreaseDecreaseType) {
            Double value = getStateDoubleValue(STATE_POSITION);
            Double min = getStateDoubleValue(STATE_MIN);
            Double max = getStateDoubleValue(STATE_MAX);
            Double step = getStateDoubleValue(STATE_STEP);
            if (value != null && max != null && min != null && step != null && min >= 0 && max >= 0 && max > min) {
                if ((IncreaseDecreaseType) command == IncreaseDecreaseType.INCREASE) {
                    value += step;
                    if (value > max) {
                        value = max;
                    }
                } else {
                    value -= step;
                    if (value < min) {
                        value = min;
                    }
                }
                sendAction(value.toString());
            }
        }
    }

    private PercentType getChannelState() {
        Double value = mapLoxoneToOH(getStateDoubleValue(STATE_POSITION));
        if (value != null && value >= 0 && value <= 100) {
            return new PercentType(value.intValue());
        }
        return null;
    }

    /**
     * Sets the current position of the dimmer
     *
     * @param position position to move to (0-100, 0 - full off, 100 - full on)
     * @throws IOException error communicating with the Miniserver
     */
    private void setPosition(Double position) throws IOException {
        Double loxonePosition = mapOHToLoxone(position);
        if (loxonePosition != null) {
            sendAction(loxonePosition.toString());
        }
    }

    private Double mapLoxoneToOH(Double loxoneValue) {
        if (loxoneValue != null) {
            // 0 means turn dimmer off, any value above zero should be mapped from min-max range
            if (Double.compare(loxoneValue, 0.0) == 0) {
                return 0.0;
            }
            Double max = getStateDoubleValue(STATE_MAX);
            Double min = getStateDoubleValue(STATE_MIN);
            if (max != null && min != null && max > min && min >= 0 && max >= 0) {
                return 100 * (loxoneValue - min) / (max - min);
            }
        }
        return null;
    }

    private Double mapOHToLoxone(Double ohValue) {
        if (ohValue != null) {
            // 0 means turn dimmer off, any value above zero should be mapped to min-max range
            if (Double.compare(ohValue, 0.0) == 0) {
                return 0.0;
            }
            Double max = getStateDoubleValue(STATE_MAX);
            Double min = getStateDoubleValue(STATE_MIN);
            if (max != null && min != null) {
                double value = min + ohValue * (max - min) / 100;
                return value; // no rounding to integer value is needed as loxone is accepting floating point values
            }
        }
        return null;
    }
}
