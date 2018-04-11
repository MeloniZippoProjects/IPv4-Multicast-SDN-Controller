package org.melonizippo.openflow;

import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.*;
import net.floodlightcontroller.restserver.IRestApiService;
import org.melonizippo.exceptions.*;
import org.melonizippo.rest.MulticastWebRoutable;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.*;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IPv4MulticastModule implements IOFMessageListener, IFloodlightModule, IIPv4MulticastService {
    private final static Logger logger = LoggerFactory.getLogger(IPv4MulticastModule.class);

    protected IFloodlightProviderService floodlightProvider;
    protected IRestApiService restApiService;

    private IPv4Address defaultGatewayIpAddress;
    private MacAddress defaultGatewayMacAddress;

    private IPv4AddressWithMask unicastPool;
    private IPv4AddressWithMask multicastPool;

    private Set<MulticastGroup> multicastGroups;
    private Map<String, Integer> OFGroupsIds;

    // Rule timeouts
    private static short IDLE_TIMEOUT = 10; // in seconds
    private static short HARD_TIMEOUT = 20; // every 20 seconds drop the entry
    ;

    public Set<MulticastGroup> getMulticastGroups()
    {
        return Collections.unmodifiableSet(multicastGroups);
    }

    public Integer addGroup(IPv4Address groupIP, String groupName) throws GroupAlreadyExistsException, GroupAddressOutOfPoolException
    {
        if(!multicastPool.contains(groupIP))
            throw new GroupAddressOutOfPoolException();

        Optional duplicate = multicastGroups.stream().filter( group -> group.getIp().equals(groupIP)).findFirst();
        if(!duplicate.isPresent())
        {
            Integer groupID = MulticastGroup.IDFactory.incrementAndGet();
            MulticastGroup newGroup = new MulticastGroup(groupIP, groupName, groupID);
            multicastGroups.add(newGroup);
            return groupID;
        }
        else
            throw new GroupAlreadyExistsException();
    }

    public MulticastGroup getGroup(Integer groupID) throws GroupNotFoundException
    {
        Optional<MulticastGroup> target = multicastGroups.stream().filter(group -> group.getId() == groupID).findFirst();
        if(target.isPresent())
            return target.get();
        else
            throw new GroupNotFoundException();

    }

    public void deleteGroup(Integer groupID) throws GroupNotFoundException
    {
        MulticastGroup group = getGroup(groupID);
        multicastGroups.remove(group);
    }

    public void addToGroup(Integer groupID, IPv4Address hostIP) throws GroupNotFoundException, HostAddressOutOfPoolException
    {
        if(!unicastPool.contains(hostIP))
            throw new HostAddressOutOfPoolException();

        MulticastGroup group = getGroup(groupID);
        group.getPartecipants().add(hostIP);
    }

    public void removeFromGroup(Integer groupID, IPv4Address hostIP) throws GroupNotFoundException, HostAddressOutOfPoolException, HostNotFoundException
    {
        if(!unicastPool.contains(hostIP))
            throw new HostAddressOutOfPoolException();

        MulticastGroup group = getGroup(groupID);
        if (!group.getPartecipants().remove(hostIP))
            throw new HostNotFoundException();
    }

    public OFPacketOut encapsulatePacket(OFPacketIn packetIn, OFFactory factory , IPacket packet)
    {
        OFPacketOut.Builder pob = factory.buildPacketOut();
        pob.setBufferId(OFBufferId.NO_BUFFER);
        pob.setInPort(OFPort.ANY);

        //set output action
        OFActionOutput.Builder actionBuilder = factory.actions().buildOutput();
        OFPort inPort = packetIn.getMatch().get(MatchField.IN_PORT);
        actionBuilder.setPort(inPort);
        pob.setActions(Collections.singletonList((OFAction) actionBuilder.build()));

        //set arp reply as pkt data
        pob.setData(packet.serialize());

        return pob.build();
    }

    public Command receive(IOFSwitch iofSwitch, OFMessage ofMessage, FloodlightContext floodlightContext) {

        OFPacketIn packetIn = (OFPacketIn) ofMessage;
        Ethernet eth =
                IFloodlightProviderService.bcStore.get(floodlightContext,
                        IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        //intercept arp requests
        if(eth.getEtherType() == EthType.ARP)
        {
            ARP payload = (ARP) eth.getPayload();

            //if it is the gateway ip address we must also build an arp response
            if(payload.getTargetProtocolAddress().equals(defaultGatewayIpAddress))
            {
                IPacket arpReply = new Ethernet()
                        .setSourceMACAddress(defaultGatewayMacAddress)
                        .setDestinationMACAddress(eth.getSourceMACAddress())
                        .setEtherType(EthType.ARP)
                        .setPriorityCode(eth.getPriorityCode())
                        .setPayload(
                                new ARP()
                                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                                .setProtocolType(ARP.PROTO_TYPE_IP)
                                .setHardwareAddressLength((byte)6)
                                .setProtocolAddressLength((byte)4)
                                .setOpCode(ARP.OP_REPLY)
                                .setSenderHardwareAddress(defaultGatewayMacAddress)
                                .setSenderProtocolAddress(defaultGatewayIpAddress)
                                .setTargetHardwareAddress(payload.getSenderHardwareAddress())
                                .setTargetProtocolAddress(payload.getSenderProtocolAddress())
                        );

                //send OFPacketOut with reply
                OFPacketOut packetOut = encapsulatePacket(packetIn, iofSwitch.getOFFactory(), arpReply );
                iofSwitch.write(packetOut);
            }
        }

        //consider only ipv4 packets
        if(eth.getEtherType() == EthType.IPv4)
        {
            IPv4 ipv4Packet = (IPv4) eth.getPayload();
            IPv4Address destinationAddress = ipv4Packet.getDestinationAddress();

            //answer to ICMP pings
            if(ipv4Packet.getDestinationAddress().equals(defaultGatewayIpAddress) &&
                    ipv4Packet.getPayload() instanceof ICMP)
            {
                ICMP icmpPacket = (ICMP) ipv4Packet.getPayload();

                IPacket icmpReply = new Ethernet()
                        .setSourceMACAddress(defaultGatewayMacAddress)
                        .setDestinationMACAddress(eth.getSourceMACAddress())
                        .setEtherType(EthType.IPv4)
                        .setPriorityCode(eth.getPriorityCode())
                        .setPayload(
                                new IPv4()
                                        .setProtocol(IpProtocol.ICMP)
                                        .setDestinationAddress(ipv4Packet.getSourceAddress())
                                        .setSourceAddress(defaultGatewayIpAddress)
                                        .setTtl((byte)64)
                                // Set the same payload included in the request
                                        .setPayload(
                                                new ICMP()
                                                    .setIcmpType(ICMP.ECHO_REPLY)
                                                    .setIcmpCode(icmpPacket.getIcmpCode())
                                                    .setPayload(icmpPacket.getPayload())
                                        )
                        );

                //send OFPacketOut with reply
                OFPacketOut packetOut = encapsulatePacket(packetIn, iofSwitch.getOFFactory(), icmpReply );
                iofSwitch.write(packetOut);
            }

            //todo: should check if the destinationAddress is a multicast address in IPv4 sense?

            //if set contains the dest address, it is a valid multicast group
            Optional<MulticastGroup> target = multicastGroups.stream().filter(group -> group.getIp() == destinationAddress).findFirst();
            if(target.isPresent())
            {
                //todo: check if toString is correct output
                if(!OFGroupsIds.containsKey(destinationAddress.toString()))
                    createNewOFGroup(iofSwitch, destinationAddress);

                //get available action types
                OFActions actions = iofSwitch.getOFFactory().actions();

                //create flow-mod for this packet in
                OFFlowAdd.Builder flowModBuilder = iofSwitch.getOFFactory().buildFlowAdd();
                flowModBuilder.setIdleTimeout(IDLE_TIMEOUT);
                flowModBuilder.setHardTimeout(HARD_TIMEOUT);

                ArrayList<OFAction> actionList = new ArrayList<OFAction>();
                int groupId = OFGroupsIds.get(destinationAddress.toString());
                actionList.add(actions.buildGroup().setGroup(OFGroup.of(groupId)).build());

                flowModBuilder.setActions(actionList);

                //create matcher for this multicast ip
                Match.Builder matchBuilder = iofSwitch.getOFFactory().buildMatch();
                matchBuilder.setExact(MatchField.ETH_TYPE, EthType.IPv4)
                        .setExact(MatchField.IPV4_DST, destinationAddress);

                flowModBuilder.setMatch(matchBuilder.build());
                iofSwitch.write(flowModBuilder.build());
            }
        }

        return Command.CONTINUE;
    }

    private void createNewOFGroup(IOFSwitch iofSwitch, IPv4Address multicastAddress) 
    {
        MulticastGroup multicastGroup = multicastGroups.stream().filter(group -> group.getIp() == multicastAddress).findFirst().get();
        int groupId;
        if(!OFGroupsIds.isEmpty())
             groupId = Collections.max(OFGroupsIds.values()) + 1;
        else
            groupId = 1;

        OFGroupAdd multicastActionGroup = iofSwitch.getOFFactory().buildGroupAdd()
                .setGroup(OFGroup.of(groupId))    //todo: is it an id? make them unique to avoid overwriting?
                .setGroupType(OFGroupType.ALL)
                .build();

        List<OFBucket> buckets = multicastActionGroup.getBuckets();

        //get available action types
        OFActions actions = iofSwitch.getOFFactory().actions();
        //Open Flow extendable matches, needed to create actions
        OFOxms oxms = iofSwitch.getOFFactory().oxms();

        for(IPv4Address hostIP : multicastGroup.getPartecipants())
        {
            ArrayList<OFAction> actionList = new ArrayList<OFAction>();
            OFActionSetField forwardAction = actions.buildSetField()
                    .setField(
                            oxms.buildIpv4Dst()
                                    .setValue(hostIP)
                                    .build()
                    ).build();
            actionList.add(forwardAction);

            OFBucket forwardPacket = iofSwitch.getOFFactory().buildBucket()
                    .setActions(actionList)
                    .build();

            buckets.add(forwardPacket);
        }

        iofSwitch.write(multicastActionGroup);

        OFGroupsIds.put(multicastAddress.toString(), groupId);
    }

    public String getName() 
    {
        return "IPv4MulticastModule";
    }

    public boolean isCallbackOrderingPrereq(OFType ofType, String s) 
    {
        return false;
    }

    public boolean isCallbackOrderingPostreq(OFType ofType, String s) 
    {
        return false;
    }

    public Collection<Class<? extends IFloodlightService>> getModuleServices()
    {
        List<Class<? extends IFloodlightService>> l = new ArrayList<>();
        l.add(IIPv4MulticastService.class);
        return l;
    }

    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls()
    {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<>();
        m.put(IIPv4MulticastService.class, this);
        return m;
    }

    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() 
    {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IRestApiService.class);
        return l;
    }

    public void init(FloodlightModuleContext floodlightModuleContext) throws FloodlightModuleException 
    {
        logger.info("Init...");

        floodlightProvider = floodlightModuleContext.getServiceImpl(IFloodlightProviderService.class);
        restApiService = floodlightModuleContext.getServiceImpl(IRestApiService.class);

        //set defaultGateway virtual IPv4 address
        defaultGatewayIpAddress = IPv4Address.of("10.0.0.100");
        defaultGatewayMacAddress = MacAddress.of("00:00:00:00:00:64");

        //todo: maybe change it in a configuration file
        unicastPool = IPv4AddressWithMask.of("10.0.0.0/8");
        multicastPool = IPv4AddressWithMask.of("11.0.1.0/8");
        multicastGroups = ConcurrentHashMap.newKeySet();
    }

    public void startUp(FloodlightModuleContext floodlightModuleContext) throws FloodlightModuleException 
    {
        logger.info("Startup...");
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        restApiService.addRestletRoutable(new MulticastWebRoutable());
    }
}
