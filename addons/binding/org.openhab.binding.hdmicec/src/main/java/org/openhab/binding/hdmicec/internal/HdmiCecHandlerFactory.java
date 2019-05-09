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

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
//import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.openhab.binding.hdmicec.internal.discovery.HdmiEquipmentDiscoveryService;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Component;

/**
 * The {@link HdmiCecHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Sam Spencer - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.hdmicec", service = ThingHandlerFactory.class)
public class HdmiCecHandlerFactory extends BaseThingHandlerFactory {

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Stream.of(THING_TYPE_BRIDGE, THING_TYPE_EQUIPMENT)
            .collect(Collectors.toSet());

    private final Map<ThingUID, @Nullable ServiceRegistration<?>> discoveryServiceRegs = new HashMap<>();

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Nullable
    @Override
    protected ThingHandler createHandler(Thing thing) {

        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (thingTypeUID.equals(THING_TYPE_EQUIPMENT)) {
            return new HdmiCecEquipmentHandler(thing);
        } else if (thingTypeUID.equals(THING_TYPE_BRIDGE)) {
            HdmiCecBridgeHandler handler = new HdmiCecBridgeHandler((Bridge) thing);
            registerDiscoveryService(handler);
            return handler;
        }

        return null;
    }

    private synchronized void registerDiscoveryService(HdmiCecBridgeHandler bridgeHandler) {
        HdmiEquipmentDiscoveryService discoveryService = new HdmiEquipmentDiscoveryService(bridgeHandler);
        this.discoveryServiceRegs.put(bridgeHandler.getThing().getUID(), bundleContext
                .registerService(DiscoveryService.class.getName(), discoveryService, new Hashtable<String, Object>()));
    }
}
