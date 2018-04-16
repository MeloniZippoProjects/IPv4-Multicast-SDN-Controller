package org.melonizippo.openflow;

import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;

//todo: find better name
public class HostL2Details
{
    public MacAddress mac;
    public OFPort port;

    public static final HostL2Details FLOOD = new HostL2Details() {{
       mac = MacAddress.BROADCAST;
       port = OFPort.FLOOD;
    }};
}
