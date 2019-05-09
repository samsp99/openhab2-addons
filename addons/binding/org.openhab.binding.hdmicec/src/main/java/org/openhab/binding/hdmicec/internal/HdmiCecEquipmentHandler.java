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
package org.openhab.binding.hdmicec.internal;

import static org.openhab.binding.hdmicec.internal.HdmiCecBindingConstants.*;

import java.util.regex.Matcher;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HdmiCecEquipmentHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author David Masshardt - Initial contribution
 */

@NonNullByDefault
public class HdmiCecEquipmentHandler extends BaseThingHandler {

    private Logger logger = LoggerFactory.getLogger(HdmiCecEquipmentHandler.class);

    // config paramaters
    private String deviceIndex; // hex number, like 0 or e
    private String address; // of the form 0.0.0.0

    private HdmiCecBridgeHandler bridgeHandler;

    public HdmiCecEquipmentHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String id = channelUID.getId();
        if (id.equals(HdmiCecBindingConstants.CHANNEL_POWER)) {
            if (command.equals(OnOffType.ON)) {
                bridgeHandler.sendCommand("on " + getDeviceIndex());
            } else if (command.equals(OnOffType.OFF)) {
                bridgeHandler.sendCommand("standby " + getDeviceIndex());
            }
        } else if (id.equals(HdmiCecBindingConstants.CHANNEL_ACTIVE_SOURCE)) {
            if (command.equals(OnOffType.ON)) {
                bridgeHandler.sendCommand("tx " + bridgeDeviceIndex() + "F:82:" + getAddressAsFrame());
            } else if (command.equals(OnOffType.OFF)) {
                bridgeHandler.sendCommand("tx " + bridgeDeviceIndex() + "F:9D:" + getAddressAsFrame());
            }
        } else if (id.equals(HdmiCecBindingConstants.CHANNEL_SEND)) {
            if (command instanceof StringType) {
                // think about this, do we want to have a controlled vocabulary or just transmit something raw, or
                // both?
                bridgeHandler.sendCommand(command.toString());
            }
        } else if (id.equals(HdmiCecBindingConstants.CHANNEL_SEND_CEC)) {
            /* Sends message from bridge device to the target device index */
            if (command instanceof StringType) {
                bridgeHandler.sendCommand("tx " + bridgeDeviceIndex() + deviceIndex + ":" + command.toString());
            }
        } else if (channelUID.getId().equals(HdmiCecBindingConstants.CHANNEL_REMOTE_BUTTON)) {
            if (command instanceof StringType) {
                sendRemoteButton(command.toString());
            }
        }

    }

    private void sendRemoteButton(String command) {
        String opcode = RemoteButtonCode.opcodeFromString(command);
        bridgeHandler.sendCommand("txn " + bridgeDeviceIndex() + deviceIndex + ":44:" + opcode);
        bridgeHandler.sendCommand("txn " + bridgeDeviceIndex() + deviceIndex + ":45");
    }

    private @Nullable String bridgeDeviceIndex() {
        return (bridgeHandler != null) ? bridgeHandler.getBridgeIndex() : null;
    }

    public String getDeviceIndex() {
        return deviceIndex;
    }

    public String getAddress() {
        return address;
    }

    public String getAddressAsFrame() {
        return address.replace(".", "").substring(0, 2) + ":" + address.replace(".", "").substring(2);
    }

    @Override
    public void initialize() {
        try {
            getThing().setLabel(getThing().getLabel().replace("Equipment", getThing().getUID().getId()));
            deviceIndex = (String) this.getConfig().get(DEVICE_INDEX);
            address = (String) this.getConfig().get(ADDRESS);

            logger.debug("Initializing thing {}", getThing().getUID());
            bridgeHandler = (HdmiCecBridgeHandler) getBridge().getHandler();

            if (getBridge().getStatus() == ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            }
        } catch (Exception e) {
            logger.error("Error in initialize: {} at {}", e.toString(), e.getStackTrace());
        }
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    public void cecClientStatus(boolean online, String status) {
        if (online) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, status);
        }

        logger.debug("Cec client status: online = {} status = {}", online, status);
    }

    void cecMatchLine(String line) {
        Matcher matcher = bridgeHandler.getPowerOn().matcher(line);
        if (matcher.matches()) {
            updateState(HdmiCecBindingConstants.CHANNEL_POWER, OnOffType.ON);
            return;
        }
        matcher = bridgeHandler.getPowerOff().matcher(line);
        if (matcher.matches()) {
            updateState(HdmiCecBindingConstants.CHANNEL_POWER, OnOffType.OFF);
            return;
        }
        matcher = bridgeHandler.getActiveSourceOn().matcher(line);
        if (matcher.matches()) {
            updateState(HdmiCecBindingConstants.CHANNEL_ACTIVE_SOURCE, OnOffType.ON);
            return;
        }
        matcher = bridgeHandler.getActiveSourceOff().matcher(line);
        if (matcher.matches()) {
            updateState(HdmiCecBindingConstants.CHANNEL_ACTIVE_SOURCE, OnOffType.OFF);
            return;
        }
        matcher = bridgeHandler.getEventPattern().matcher(line);
        if (matcher.matches()) {
            triggerChannel(HdmiCecBindingConstants.CHANNEL_EVENT, matcher.group(2));
            return;
        }
    }

}
