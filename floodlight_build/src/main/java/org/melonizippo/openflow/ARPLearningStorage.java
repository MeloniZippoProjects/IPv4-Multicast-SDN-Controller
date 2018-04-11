package org.melonizippo.openflow;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.ARP;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ARPLearningStorage {
    protected final static Logger logger = LoggerFactory.getLogger(ARPLearningStorage.class);

    private Map<Long, SwitchARPLearningStorage> storage = new HashMap<>();

    public HostL2Details getHostL2Details(IOFSwitch iofSwitch, IPv4Address hostAddress)
    {
        long switchId = iofSwitch.getId().getLong();
        SwitchARPLearningStorage switchStorage = storage.get(switchId);
        return  switchStorage != null ? switchStorage.getHostL2Details(hostAddress) : null;
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
            int port = packetIn.getMatch().get(MatchField.IN_PORT).getPortNumber();

            HostL2Details entry = storage.get(hostAddress);
            if(entry == null)
            {
                entry = new HostL2Details();
                storage.put(hostAddress, entry);
            }

            entry.mac = hostMac;
            entry.port = port;
        }

        public void logStorageStatus()
        {
            for (Map.Entry<IPv4Address, HostL2Details> storageEntry :
                    storage.entrySet())
            {
                logger.info(storageEntry.getKey().toString() + " => (" +
                    storageEntry.getValue().mac + ", " + storageEntry.getValue().port + ")");
            }
        }
    }

    public void logStorageStatus()
    {
        logger.info("Printing ARP storage current status: ");
        for(Map.Entry<Long, SwitchARPLearningStorage> switchStorageEntry :
                storage.entrySet())
        {
            logger.info("Switch " + switchStorageEntry.getKey().toString() + ":");
            switchStorageEntry.getValue().logStorageStatus();
        }
    }
}

