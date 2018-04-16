package org.melonizippo.openflow;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ARPLearningStorage {
    protected final static Logger logger = LoggerFactory.getLogger(ARPLearningStorage.class);

    private Map<Long, SwitchARPLearningStorage> storage = new ConcurrentHashMap<>();
    protected IPv4MulticastModule multicastModule;

    public ARPLearningStorage(IPv4MulticastModule multicastModule)
    {
        this.multicastModule = multicastModule;
    }

    public HostL2Details getHostL2Details(IOFSwitch iofSwitch, IPv4Address hostAddress)
    {
        long switchId = iofSwitch.getId().getLong();
        SwitchARPLearningStorage switchStorage = storage.get(switchId);
        if(switchStorage == null)
        {
            switchStorage = new SwitchARPLearningStorage(iofSwitch);
            storage.put(switchId, switchStorage);
        }

        return  switchStorage.getHostL2Details(hostAddress);
    }

    public void learnFromARP(ARP arpPacket, OFPacketIn packetIn, IOFSwitch iofSwitch)
    {
        if(arpPacket.getSenderHardwareAddress() == multicastModule.virtualGatewayMacAddress)
            return;

        long switchId = iofSwitch.getId().getLong();
        SwitchARPLearningStorage switchStorage = storage.get(switchId);
        if(switchStorage == null)
        {
            switchStorage = new SwitchARPLearningStorage(iofSwitch);
            storage.put(switchId, switchStorage);
        }

        switchStorage.learnFromARP(arpPacket, packetIn);
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

    private class SwitchARPLearningStorage
    {
        private Map<IPv4Address, HostL2Details> storage = new HashMap<>();
        private IOFSwitch iofSwitch;

        public SwitchARPLearningStorage(IOFSwitch iofSwitch)
        {
            this.iofSwitch = iofSwitch;
        }

        public HostL2Details getHostL2Details(IPv4Address ipAddress)
        {
            HostL2Details details = storage.get(ipAddress);
            if(details != null)
                return details;
            else
            {
                sendARPRequest(ipAddress);
                return HostL2Details.FLOOD;
            }
        }

        public void sendARPRequest(IPv4Address ipAddress)
        {
            IPv4MulticastModule multicastModule = ARPLearningStorage.this.multicastModule;

            IPacket arpRequest = new Ethernet()
                    .setSourceMACAddress(multicastModule.virtualGatewayMacAddress)
                    .setDestinationMACAddress(MacAddress.BROADCAST)
                    .setEtherType(EthType.ARP)
                    .setPriorityCode(IPv4MulticastModule.ARP_ETH_PRIORITY)
                    .setPayload(
                            new ARP()
                                    .setHardwareType(ARP.HW_TYPE_ETHERNET)
                                    .setProtocolType(ARP.PROTO_TYPE_IP)
                                    .setHardwareAddressLength((byte)6)
                                    .setProtocolAddressLength((byte)4)
                                    .setOpCode(ARP.OP_REQUEST)
                                    .setSenderHardwareAddress(multicastModule.virtualGatewayMacAddress)
                                    .setSenderProtocolAddress(multicastModule.virtualGatewayIpAddress)
                                    .setTargetHardwareAddress(MacAddress.NONE)
                                    .setTargetProtocolAddress(ipAddress)
                    );

            //send OFPacketOut with request
            OFFactory factory = iofSwitch.getOFFactory();
            OFPacketOut.Builder poBuilder = factory.buildPacketOut();
            poBuilder.setBufferId(OFBufferId.NO_BUFFER);
            poBuilder.setInPort(OFPort.CONTROLLER);

            OFActionOutput.Builder actionBuilder = factory.actions().buildOutput();
            actionBuilder.setPort(OFPort.FLOOD);
            poBuilder.setActions(Collections.singletonList((OFAction) actionBuilder.build()));

            poBuilder.setData(arpRequest.serialize());

            iofSwitch.write(poBuilder.build());
        }

        public void learnFromARP(ARP arpPacket, OFPacketIn packetIn)
        {
            IPv4Address hostAddress = arpPacket.getSenderProtocolAddress();
            MacAddress hostMac = arpPacket.getSenderHardwareAddress();
            OFPort port = packetIn.getMatch().get(MatchField.IN_PORT);

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
                        storageEntry.getValue().mac + ", " + storageEntry.getValue().port.getPortNumber() + ")");
            }
        }
    }
}

