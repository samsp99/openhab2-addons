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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.hdmicec.internal.discovery.HdmiEquipmentDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HdmiCecBridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Andrew Nagle - Initial contribution
 */
@NonNullByDefault
public class HdmiCecBridgeHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(HdmiCecBridgeHandler.class);

    // List of Configurations constants
    public static final String CEC_CLIENT_PATH = "cecClientPath";
    public static final String COM_PORT = "comPort";
    public static final String DEVICE_STATEMENT_REGEX = "deviceStatementRegex";
    public static final String POWER_ON_REGEX = "powerOnRegex";
    public static final String POWER_OFF_REGEX = "powerOffRegex";
    public static final String ACTIVE_SOURCE_ON_REGEX = "activeSourceOnRegex";
    public static final String ACTIVE_SOURCE = "ActiveSourceOffRegex";

    private String cecClientPath;
    private String comPort;
    private @Nullable String deviceIndex = null;

    // we're betting on the fact that the first value in () is the device ID. Seems valid from what I've seen!
    private Pattern deviceStatement = Pattern.compile("DEBUG.* \\((.)\\).*");
    private Pattern powerOn = Pattern.compile(".*: power status changed from '(.*)' to 'on'");
    private Pattern powerOff = Pattern.compile(".*: power status changed from '(.*)' to 'standby'");
    private Pattern activeSourceOn = Pattern.compile(".*making .* \\((.)\\) the active source");
    private Pattern activeSourceOff = Pattern.compile(".*marking .* \\((.)\\) as inactive source");
    private Pattern eventPattern = Pattern.compile("^(?!.*(<<|>>)).*: (.*)$"); // the 2nd group is the event
    private Pattern deviceDiscoveryPattern = Pattern.compile("device #([0-9]|[A-F]).+$");
    private Pattern addressDiscoveryPattern = Pattern.compile("Addresses controlled by libCEC:\\s*([0-9]|[a-f]|[A-F])");

    private boolean isRunning;

    private @Nullable Thread thread;
    private @Nullable Process process;
    private @Nullable BufferedReader bufferedReader;
    private @Nullable BufferedWriter writer;

    @Nullable
    private HdmiEquipmentDiscoveryService discoveryService = null;

    public HdmiCecBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Bridge commands not supported.");
    }

    @Override
    public void initialize() {
        logger.debug("Initializing the HdmiCec Bridge handler");
        cecClientPath = (String) this.getConfig().get(CEC_CLIENT_PATH);
        comPort = (String) this.getConfig().get(COM_PORT);
        if (this.getConfig().containsKey(DEVICE_STATEMENT_REGEX)) {
            deviceStatement = Pattern.compile((String) this.getConfig().get(DEVICE_STATEMENT_REGEX));
        }
        if (this.getConfig().containsKey(POWER_ON_REGEX)) {
            powerOn = Pattern.compile((String) this.getConfig().get(POWER_ON_REGEX));
        }
        if (this.getConfig().containsKey(POWER_OFF_REGEX)) {
            powerOff = Pattern.compile((String) this.getConfig().get(POWER_OFF_REGEX));
        }
        if (this.getConfig().containsKey(ACTIVE_SOURCE_ON_REGEX)) {
            activeSourceOn = Pattern.compile((String) this.getConfig().get(ACTIVE_SOURCE_ON_REGEX));
        }
        if (this.getConfig().containsKey(ACTIVE_SOURCE)) {
            activeSourceOff = Pattern.compile((String) this.getConfig().get(ACTIVE_SOURCE));
        }

        logger.debug("initialize client: {}, com port: {}", cecClientPath, comPort);

        File cecClientFile = new File(cecClientPath);

        if (!cecClientFile.exists()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "cec-client executable not found.");
            return;
        }
        try {
            startCecClient();
            updateStatus(ThingStatus.ONLINE);
        } catch (Exception e) {
            logger.debug("Bridge handler exception.", e);
        }
    }

    @Override
    public void dispose() {
        logger.debug("Disposing bridge handler.");
        try {
            sendCommand("q");
        } catch (Exception e) {
            logger.debug("Bridge handler exception.", e);
        }
        super.dispose();
        logger.debug("Bridge handler disposed.");
    }

    private void startCecClient() throws Exception {
        logger.debug("startCecClient()");
        ProcessBuilder builder = new ProcessBuilder();
        builder.redirectErrorStream(true); // This is the important part
        // builder.command(cecClientPath, "--log-level", "1", comPort);
        builder.command("/usr/bin/cec-client-4.0.4", "--log-level", "1"); // , comPort);

        logger.debug("startCecClient:builder.start()");
        process = builder.start();
        bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()));
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        open();
    }

    private void stopCecClient() {
        try {
            if (process != null) {
                process.destroy();
            }

            if (writer != null) {
                writer.close();
            }

            if (bufferedReader != null) {
                bufferedReader.close();
            }
        } catch (Exception e) {
            logger.debug("Exception in stopCecClient", e);
        }

        process = null;
        thread = null;
        bufferedReader = null;
        writer = null;
        deviceIndex = null;
    }

    private void logStream(BufferedReader reader) throws IOException {
        String line = null;
        while ((line = reader.readLine()) != null) {
            logger.debug("inputStream: {}", line);
        }
    }

    @SuppressWarnings({ "unused", "null" })
    private void open() {
        logger.debug("open()");
        isRunning = true;

        if (thread == null) {
            thread = new Thread() {
                @Override
                public void run() {
                    while (isRunning) {
                        String line = null;
                        try {
                            if (process == null || !process.isAlive()) {
                                isRunning = false;
                                logStream(bufferedReader);
                                callbackCecClientStatus(false, "process ended");
                                return;
                            }

                            line = bufferedReader.readLine();
                        } catch (IOException e) {
                            logger.error("Error reading from cec-client: {}", e.toString());
                            isRunning = false;
                            callbackCecClientStatus(false, e.getMessage());
                            return;
                        }

                        if (line != null) {
                            logger.debug("Line trace: {}", line);
                        }

                        if (line == null) {
                            try {
                                Thread.sleep(150);
                            } catch (InterruptedException ie) {
                                logger.error("Sleep error: {}", ie.toString());
                                isRunning = false;
                                Thread.currentThread().interrupt();
                                callbackCecClientStatus(false, "thread aborted");
                                return;
                            }
                        } else if (line.contains("connection opened")) {
                            callbackCecClientStatus(true, "connection opened");
                        } else if (line.contains("communication thread ended")) {
                            callbackCecClientStatus(false, "communication thread ended");
                            isRunning = false;
                        } else if (line.contains("could not start CEC communications")) {
                            callbackCecClientStatus(false, "could not start CEC communications");
                            isRunning = false;
                        } else if (line.contains("waiting for input") && deviceIndex == null) {
                            // Fetch our own address - should only be needed as part of initialization
                            sendCommand("self");
                        } else if (line.contains("Addresses controlled by libCEC:")) {
                            logger.debug("detecting self-address");
                            handleSelfAddressOutput(line);
                        } else if (line.contains("requesting CEC bus information")) {
                            logger.debug("requesting CEC bus info found");
                            handleDiscoveryOutput(bufferedReader);
                        } else {
                            // Matcher matcher = deviceStatement.matcher(line);
                            // if (matcher.matches()) {
                            // for (Thing thing : getThing().getThings()) {
                            // HdmiCecEquipmentHandler equipment = (HdmiCecEquipmentHandler) thing.getHandler();
                            // if (equipment != null && equipment.getDevice().equalsIgnoreCase(matcher.group(1))) {
                            // equipment.cecMatchLine(line);
                            // }
                            // }
                            // }
                        }
                    }
                }
            };
        } else {
            throw new IllegalStateException("The logger is already running");
        }
        thread.start();
    }

    protected void handleSelfAddressOutput(String line) {
        Matcher m = addressDiscoveryPattern.matcher(line);
        if (m.matches()) {
            this.deviceIndex = m.group(1);
        }
    }

    public void startDeviceDiscovery(HdmiEquipmentDiscoveryService hdmiEquipmentDiscoveryService) {
        logger.debug("startDeviceDiscovery()");
        if (isRunning) {
            this.discoveryService = hdmiEquipmentDiscoveryService;
            sendCommand("scan");
        }
    }

    protected void handleDiscoveryOutput(BufferedReader reader) {
        ArrayList<String> lines = new ArrayList<String>();
        long timeout = new Date().getTime() + (20 * 1000);
        String line;
        boolean finished = false;

        do {
            try {
                line = reader.readLine();
            } catch (IOException ex) {
                logger.debug("handleDiscoveryOutput():Exception {}", ex.getMessage());
                return;
            }

            if (line != null) {
                lines.add(line);
                logger.debug("read:{}", line);
                if (line.contains("currently active source")) {
                    logger.debug("handleDiscoveryOutput():finished with {} lines", lines.size());
                    finished = true;
                }

            } else {
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ex) {
                    return;
                }
            }
            if (new Date().getTime() > timeout) {
                logger.debug("handleDiscoveryOutput():timed out with {} lines", lines.size());
                finished = true;
            }
        } while (!finished);
        if (lines.size() > 0 && discoveryService != null) {
            ArrayList<DiscoveryResult> deviceResults = processDiscoveryOutput(lines);
            discoveryService.processDevices(deviceResults);
            discoveryService = null;
        }
    }

    private ArrayList<DiscoveryResult> processDiscoveryOutput(ArrayList<String> lines) {
        logger.debug("processDiscoveryOutput({} lines)" + lines.size());
        String line;
        ArrayList<DiscoveryResult> results = new ArrayList<DiscoveryResult>();
        do {
            line = lines.get(0);
            logger.debug("processDiscoveryOutput(): line={}", line);
            if (line.startsWith("device")) {
                DiscoveryResult result = processSingleDevice(lines);
                if (result != null) {
                    results.add(result);
                }
            } else {
                lines.remove(0);
            }
        } while (lines.size() > 1);
        return results;
    }

    @Nullable
    private String Pop(ArrayList<String> lines) {
        if (lines.size() > 0) {
            String line = lines.get(0);
            lines.remove(0);
            return line;
        } else {
            return null;
        }
    }

    @SuppressWarnings("unused")
    @Nullable
    private DiscoveryResult processSingleDevice(ArrayList<String> lines) {
        logger.debug("processSingleDevice(): {}", lines.size());
        String logicalAddr = null;
        String address = null;
        String vendor = null;
        String osd = null;
        int hashcode = 0;
        boolean isActive = false;
        boolean isOn = false;
        String key = null;
        Map<String, Object> properties = new HashMap<>();

        String line = Pop(lines);
        Matcher matcher = deviceDiscoveryPattern.matcher(line);
        if (matcher.matches()) {
            logicalAddr = matcher.group(1);
            properties.put(DEVICE_INDEX, logicalAddr);
        }

        do {
            line = Pop(lines);
            logger.debug("processSingleDevice(): {}", line);
            if (line == null) {
                return null;
            }
            int i = line.indexOf(':');
            if (i > 0) {
                key = line.substring(0, i);
                String value = line.substring(i + 1).trim();

                switch (key) {
                    case "address":
                        address = value;
                        properties.put(ADDRESS, value);
                        break;

                    case "active source":
                        isActive = (value == "yes");
                        break;

                    case "vendor":
                        vendor = value;
                        properties.put(VENDOR, value);
                        break;

                    case "osd string":
                        osd = value;
                        properties.put(OSD, value);
                        break;

                    case "power status":
                        isOn = (value == "on");
                        break;
                }
            }
        } while (!key.contentEquals("language"));

        logger.debug("processSingleDevice(): Found #{} - {} at {}", logicalAddr, osd, address);
        hashcode = (vendor + osd).hashCode();
        properties.put(UNIQUE_ID, String.valueOf(hashcode));
        ThingUID thingUID = new ThingUID(THING_TYPE_EQUIPMENT, this.thing.getUID(), makeID(vendor, osd));
        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withThingType(THING_TYPE_EQUIPMENT)
                .withProperties(properties).withBridge(this.thing.getUID()).withRepresentationProperty(UNIQUE_ID)
                .withLabel(osd).build();
        return discoveryResult;
    }

    private String makeID(@Nullable String vendor, @Nullable String osd) {
        String v = (vendor == null || vendor.contentEquals("")) ? "unknown" : vendor.replaceAll("\\W", "");
        String n = (osd == null || osd.contentEquals("")) ? "unknown" : osd.replaceAll("\\W", "");
        return v + "_" + n;
    }

    Pattern getDeviceStatement() {
        return deviceStatement;
    }

    Pattern getPowerOn() {
        return powerOn;
    }

    Pattern getPowerOff() {
        return powerOff;
    }

    Pattern getActiveSourceOn() {
        return activeSourceOn;
    }

    Pattern getActiveSourceOff() {
        return activeSourceOff;
    }

    Pattern getEventPattern() {
        return eventPattern;
    }

    public void sendCommand(String command) {
        logger.debug("sendCommand({})", command);
        if (writer != null) {
            try {
                writer.write(command);
                writer.newLine();
                writer.flush();
            } catch (Exception e) {
                logger.debug("Bridge handler exception in sendCommand: " + command, e);
            }
        } else {
            logger.debug("sendCommand():failed - writer is null");
        }

    }

    private void callbackCecClientStatus(boolean online, String status) {
        logger.debug("callbackCecClientStatus(" + online + "," + status + ")");
        if (!online) {
            updateStatus(ThingStatus.OFFLINE);
            stopCecClient();
        }
        for (Thing thing : getThing().getThings()) {
            HdmiCecEquipmentHandler equipment = (HdmiCecEquipmentHandler) thing.getHandler();
            if (equipment != null) {
                // actually, do we want to do this?
                equipment.cecClientStatus(online, status);
            }
        }
    }

    public @Nullable String getBridgeIndex() {
        return this.deviceIndex;
    }

}
