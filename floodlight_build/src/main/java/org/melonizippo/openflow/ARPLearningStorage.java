package org.melonizippo.openflow;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.ARP;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.types.ArpOpcode;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

import java.util.HashMap;
import java.util.Map;

public class ARPLearningStorage {
    private Map<Long, SwitchARPLearningStorage> storage = new HashMap<>();

    public HostL2Details getHostL2Details(IOFSwitch iofSwitch, IPv4Address hostAddress)
    {
        long switchId = iofSwitch.getId().getLong();
        SwitchARPLearningStorage switchStorage = storage.get(switchId);
        return  switchStorage == null ? switchStorage.getHostL2Details(hostAddress) : null;
    }

    public void learnFromARP(ARP arpPacket, OFPacketIn packetIn, IOFSwitch iofSwitch)
    {
        long switchId = iofSwitch.getId().getLong();
        SwitchARPLearningStorage switchStorage = storage.get(switchId);
        if(switchStorage == null)
        {
            switchStorage = new SwitchARPLearningStorage();
            storage.put(switchId, switchStorage);
        }

        switchStorage.learnFromARP(arpPacket, packetIn);
    }

    private static class SwitchARPLearningStorage
    {
        private Map<IPv4Address, HostL2Details> storage = new HashMap<>();

        public HostL2Details getHostL2Details(IPv4Address address)
        {
            return storage.get(address);
        }

        public void learnFromARP(ARP arpPacket, OFPacketIn packetIn)
        {
            IPv4Address hostAddress = arpPacket.getSenderProtocolAddress();
            MacAddress hostMac = arpPacket.getSenderHardwareAddress();
            int port = packetIn.getInPort().getPortNumber();

            HostL2Details entry = storage.get(hostAddress);
            if(entry == null)
            {
                entry = new HostL2Details();
                storage.put(hostAddress, entry);
            }

            entry.mac = hostMac;
            entry.port = port;
        }
    }
}

