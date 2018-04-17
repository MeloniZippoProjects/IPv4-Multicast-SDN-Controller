package org.melonizippo.openflow;

import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.*;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.util.FlowModUtils;
import org.melonizippo.exceptions.*;
import org.melonizippo.rest.MulticastWebRoutable;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class IPv4MulticastModule implements IOFMessageListener, IFloodlightModule, IIPv4MulticastService, IOFSwitchListener {
    private final static Logger logger = LoggerFactory.getLogger(IPv4MulticastModule.class);
    public static final int MTU = 1500;

    protected IFloodlightProviderService floodlightProvider;
    protected IRestApiService restApiService;
    private IOFSwitchService iofSwitchService;

    public IPv4Address virtualGatewayIpAddress;
    public MacAddress virtualGatewayMacAddress;

    private IPv4AddressWithMask unicastPool;
    private IPv4AddressWithMask multicastPool;

    private Set<MulticastGroup> multicastGroups;

    private Map<Long, SwitchInfo> connectedSwitches = new ConcurrentHashMap<>();

    private ARPLearningStorage arpLearningStorage;

    // Rule timeouts
    public static final short IDLE_TIMEOUT = 10; // in seconds
    public static final short HARD_TIMEOUT = 20; // every 20 seconds drop the entry

    public static final byte ARP_ETH_PRIORITY = 1;
    public static final byte ICMP_ETH_PRIORITY = 1;


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
        removeOFGroupFromSwitches(group);
        multicastGroups.remove(group);
    }

    public void addToGroup(Integer groupID, IPv4Address hostIP) throws GroupNotFoundException, HostAddressOutOfPoolException
    {
        if(!unicastPool.contains(hostIP))
            throw new HostAddressOutOfPoolException();

        MulticastGroup group = getGroup(groupID);
        group.getPartecipants().add(hostIP);

        updateOFGroupInSwitches(group);
    }

    public void removeFromGroup(Integer groupID, IPv4Address hostIP) throws GroupNotFoundException, HostAddressOutOfPoolException, HostNotFoundException
    {
        if(!unicastPool.contains(hostIP))
            throw new HostAddressOutOfPoolException();

        MulticastGroup group = getGroup(groupID);
        if (!group.getPartecipants().remove(hostIP))
            throw new HostNotFoundException();

        updateOFGroupInSwitches(group);
    }

    public Command receive(IOFSwitch iofSwitch, OFMessage ofMessage, FloodlightContext floodlightContext) {

        OFPacketIn packetIn = (OFPacketIn) ofMessage;
        Ethernet eth =
                IFloodlightProviderService.bcStore.get(floodlightContext,
                        IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        //intercept arp requests
        if(eth.getEtherType() == EthType.ARP)
        {
            ARP arpPacket = (ARP) eth.getPayload();
            return processARPPacket(arpPacket, packetIn, iofSwitch);
        }

        //consider only ipv4 packets
        if(eth.getEtherType() == EthType.IPv4)
        {
            IPv4 ipv4Packet = (IPv4) eth.getPayload();
            IPv4Address destinationAddress = ipv4Packet.getDestinationAddress();

            //answer to ICMP pings to virtual gateway
            if(ipv4Packet.getDestinationAddress().equals(virtualGatewayIpAddress) &&
                    ipv4Packet.getPayload() instanceof ICMP)
            {
                ICMP icmpPacket = (ICMP) ipv4Packet.getPayload();
                if(icmpPacket.getIcmpType() == ICMP.ECHO_REQUEST)
                    interceptVirtualGatewayEchoRequest(
                            icmpPacket,
                            eth.getSourceMACAddress(),
                            ipv4Packet.getSourceAddress(),
                            packetIn,
                            iofSwitch);
                return Command.STOP;
            }

            //todo: should check if the destinationAddress is a multicast address in IPv4 sense?

            //if set contains the dest address, it is a valid multicast group
            if(multicastPool.contains(destinationAddress))
            {
                Optional<MulticastGroup> target =
                        multicastGroups.stream()
                                .filter(group -> group.getIp().equals(destinationAddress))
                                .findFirst();
                if(target.isPresent())
                {
                    setMulticastRule(target.get(), iofSwitch);
                    return Command.STOP;
                }
                else
                {
                    sendICMPDestinationUnreachable(
                            ipv4Packet,
                            (byte) 7,
                            eth.getSourceMACAddress(),
                            ipv4Packet.getSourceAddress(),
                            packetIn,
                            iofSwitch
                            );
                    return Command.STOP;
                }
            }
        }

        return Command.CONTINUE;
    }

    private void sendICMPDestinationUnreachable(IPacket originalPacket,
                                                byte code,
                                                MacAddress requestMacAddress,
                                                IPv4Address requestIPAddress,
                                                OFPacketIn packetIn,
                                                IOFSwitch iofSwitch)
    {
        IPacket icmpDestinationUnreachable = new Ethernet()
                .setSourceMACAddress(virtualGatewayMacAddress)
                .setDestinationMACAddress(requestMacAddress)
                .setEtherType(EthType.IPv4)
                .setPriorityCode(ICMP_ETH_PRIORITY)
                .setPayload(
                        new IPv4()
                                .setProtocol(IpProtocol.ICMP)
                                .setDestinationAddress(requestIPAddress)
                                .setSourceAddress(virtualGatewayIpAddress)
                                .setTtl((byte)64)
                                // Set the same payload included in the request
                                .setPayload(
                                        new ICMP()
                                                .setIcmpType(ICMP.DESTINATION_UNREACHABLE)
                                                .setIcmpCode(code)
                                                .setPayload(originalPacket)
                                )
                );

        //send OFPacketOut with reply
        OFPacketOut packetOut = encapsulateReply(packetIn, iofSwitch.getOFFactory(), icmpDestinationUnreachable );
        iofSwitch.write(packetOut);
    }

    private OFPacketOut encapsulateReply(OFPacketIn packetIn, OFFactory factory, IPacket replyPacket)
    {
        OFPacketOut.Builder pob = factory.buildPacketOut();
        pob.setBufferId(OFBufferId.NO_BUFFER);
        pob.setInPort(OFPort.CONTROLLER);

        //set output action
        OFActionOutput.Builder actionBuilder = factory.actions().buildOutput();
        OFPort requestPort = packetIn.getMatch().get(MatchField.IN_PORT);
        actionBuilder.setPort(requestPort);
        pob.setActions(Collections.singletonList((OFAction) actionBuilder.build()));

        //set arp reply as pkt data
        pob.setData(replyPacket.serialize());

        return pob.build();
    }

    public Command processARPPacket(ARP arpPacket, OFPacketIn packetIn, IOFSwitch iofSwitch)
    {
        if(!arpPacket.getSenderProtocolAddress().equals(virtualGatewayIpAddress)){
            if(arpLearningStorage.learnFromARP(arpPacket, packetIn, iofSwitch)){
                arpLearningStorage.logStorageStatus();
            }
        }

        //if it is an arp request to the gateway ip address we must build an arp reply
        if(arpPacket.getTargetProtocolAddress().equals(virtualGatewayIpAddress)
                && arpPacket.getOpCode() == ARP.OP_REQUEST)
        {
            interceptVirtualGatewayARPRequest(arpPacket, packetIn, iofSwitch);
            return Command.STOP;
        }

        return Command.CONTINUE;
    }

    private void interceptVirtualGatewayARPRequest(ARP arpPacket, OFPacketIn packetIn, IOFSwitch iofSwitch)
    {
        IPacket arpReply = new Ethernet()
                .setSourceMACAddress(virtualGatewayMacAddress)
                .setDestinationMACAddress(arpPacket.getSenderHardwareAddress())
                .setEtherType(EthType.ARP)
                .setPriorityCode(ARP_ETH_PRIORITY)
                .setPayload(
                        new ARP()
                                .setHardwareType(ARP.HW_TYPE_ETHERNET)
                                .setProtocolType(ARP.PROTO_TYPE_IP)
                                .setHardwareAddressLength((byte)6)
                                .setProtocolAddressLength((byte)4)
                                .setOpCode(ARP.OP_REPLY)
                                .setSenderHardwareAddress(virtualGatewayMacAddress)
                                .setSenderProtocolAddress(virtualGatewayIpAddress)
                                .setTargetHardwareAddress(arpPacket.getSenderHardwareAddress())
                                .setTargetProtocolAddress(arpPacket.getSenderProtocolAddress())
                );

        //send OFPacketOut with reply
        OFPacketOut packetOut = encapsulateReply(packetIn, iofSwitch.getOFFactory(), arpReply );
        iofSwitch.write(packetOut);
    }

    private void interceptVirtualGatewayEchoRequest(
            ICMP echoRequest,
            MacAddress requestMacAddress,
            IPv4Address requestIPAddress,
            OFPacketIn packetIn,
            IOFSwitch iofSwitch)
    {
        IPacket icmpReply = new Ethernet()
                .setSourceMACAddress(virtualGatewayMacAddress)
                .setDestinationMACAddress(requestMacAddress)
                .setEtherType(EthType.IPv4)
                .setPriorityCode(ICMP_ETH_PRIORITY)
                .setPayload(
                        new IPv4()
                                .setProtocol(IpProtocol.ICMP)
                                .setDestinationAddress(requestIPAddress)
                                .setSourceAddress(virtualGatewayIpAddress)
                                .setTtl((byte)64)
                                // Set the same payload included in the request
                                .setPayload(
                                        new ICMP()
                                                .setIcmpType(ICMP.ECHO_REPLY)
                                                .setIcmpCode((byte) 0)
                                                .setPayload(echoRequest.getPayload())
                                )
                );

        //send OFPacketOut with reply
        OFPacketOut packetOut = encapsulateReply(packetIn, iofSwitch.getOFFactory(), icmpReply );
        iofSwitch.write(packetOut);
    }

    public void setMulticastRule(MulticastGroup multicastGroup, IOFSwitch iofSwitch)
    {
        //todo: check if toString is correct output

        SwitchInfo switchInfo = connectedSwitches.get(iofSwitch.getId().getLong());
        if(!switchInfo.knownGroups.contains(multicastGroup.getId())) {
            addOFGroupToSwitch(multicastGroup, iofSwitch);
            switchInfo.knownGroups.add(multicastGroup.getId());
        }

        setMulticastFlowMod(multicastGroup, iofSwitch);
    }

    private void setMulticastFlowMod(MulticastGroup multicastGroup, IOFSwitch iofSwitch)
    {
        //get available action types
        OFActions actions = iofSwitch.getOFFactory().actions();

        //create flow-mod for this packet in
        OFFlowAdd.Builder flowModBuilder = iofSwitch.getOFFactory().buildFlowAdd()
                .setIdleTimeout(IDLE_TIMEOUT)
                .setHardTimeout(HARD_TIMEOUT)
                .setPriority(FlowModUtils.PRIORITY_MAX);

        ArrayList<OFAction> actionList = new ArrayList<OFAction>();
        int groupId = multicastGroup.getId();
        actionList.add(actions.buildGroup().setGroup(OFGroup.of(groupId)).build());

        flowModBuilder.setActions(actionList);

        //create matcher for this multicast ip
        Match.Builder matchBuilder = iofSwitch.getOFFactory().buildMatch();
        matchBuilder.setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IPV4_DST, multicastGroup.getIp());

        flowModBuilder.setMatch(matchBuilder.build());
        iofSwitch.write(flowModBuilder.build());
    }

    private void addOFGroupToSwitch(MulticastGroup multicastGroup, IOFSwitch iofSwitch)
    {
        OFGroupAdd addMulticastGroupMsg = iofSwitch.getOFFactory().buildGroupAdd()
                .setGroup(OFGroup.of(multicastGroup.getId()))
                .setGroupType(OFGroupType.ALL)
                .setBuckets(multicastGroup.getBuckets(iofSwitch, arpLearningStorage))
                .build();
        iofSwitch.write(addMulticastGroupMsg);

        logger.info("Installed group " + multicastGroup.getId() + " in switch " + iofSwitch.getId().getLong());
    }

    public void updateHostForwardInSwitches(IPv4Address hostAddress)
    {
        List<MulticastGroup> groupsJoined = multicastGroups.stream()
                .filter(multicastGroup -> multicastGroup.getPartecipants().contains(hostAddress))
                .collect(Collectors.toList());
        for(MulticastGroup group : groupsJoined)
        {
           updateOFGroupInSwitches(group);
        }
    }

    private void updateOFGroupInSwitches(MulticastGroup multicastGroup)
    {
        for ( SwitchInfo switchInfo:
                connectedSwitches.values().stream()
                        .filter(sw -> sw.knownGroups.contains(multicastGroup.getId()))
                        .collect(Collectors.toList())
            )
        {
            IOFSwitch iofSwitch = iofSwitchService.getSwitch(DatapathId.of(switchInfo.id));

            OFGroupMod modifyMulticastGroupMsg = iofSwitch.getOFFactory().buildGroupModify()
                    .setGroup(OFGroup.of(multicastGroup.getId()))
                    .setGroupType(OFGroupType.ALL)
                    .setBuckets(multicastGroup.getBuckets(iofSwitch, arpLearningStorage))
                    .build();
            iofSwitch.write(modifyMulticastGroupMsg);
        }
    }

    private void removeOFGroupFromSwitches(MulticastGroup multicastGroup)
    {
        for ( SwitchInfo switchInfo:
                connectedSwitches.values().stream()
                        .filter(sw -> sw.knownGroups.contains(multicastGroup.getId()))
                        .collect(Collectors.toList())
                )
        {
            IOFSwitch iofSwitch = iofSwitchService.getSwitch(DatapathId.of(switchInfo.id));

            OFGroupDelete deleteMulticastGroupMsg = iofSwitch.getOFFactory().buildGroupDelete()
                    .setGroup(OFGroup.of(multicastGroup.getId()))
                    .setGroupType(OFGroupType.ALL)
                    .build();
            iofSwitch.write(deleteMulticastGroupMsg);
        }
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
        l.add(IOFSwitchService.class);
        return l;
    }

    public void init(FloodlightModuleContext floodlightModuleContext) throws FloodlightModuleException
    {
        logger.info("Init...");

        floodlightProvider = floodlightModuleContext.getServiceImpl(IFloodlightProviderService.class);
        restApiService = floodlightModuleContext.getServiceImpl(IRestApiService.class);
        iofSwitchService = floodlightModuleContext.getServiceImpl(IOFSwitchService.class);

        //set defaultGateway virtual IPv4 address
        virtualGatewayIpAddress = IPv4Address.of("10.0.0.254");
        virtualGatewayMacAddress = MacAddress.of("00:00:00:00:00:FE");

        //todo: maybe change it in a configuration file
        unicastPool = IPv4AddressWithMask.of("10.0.0.0/8");
        multicastPool = IPv4AddressWithMask.of("11.0.1.0/8");
        multicastGroups = ConcurrentHashMap.newKeySet();

        //initialize arp storage
        arpLearningStorage = new ARPLearningStorage(this);
    }

    public void startUp(FloodlightModuleContext floodlightModuleContext) throws FloodlightModuleException
    {
        logger.info("Startup...");
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        iofSwitchService.addOFSwitchListener(this);
        restApiService.addRestletRoutable(new MulticastWebRoutable());
    }

    @Override
    public void switchAdded(DatapathId switchId) {
        logger.info("Switch " + switchId.getLong() + " connected!");
        connectedSwitches.put(switchId.getLong(), new SwitchInfo(switchId.getLong()));
    }

    @Override
    public void switchRemoved(DatapathId switchId) {
        logger.info("Switch " + switchId.getLong() + " disconnected!");
        connectedSwitches.remove(switchId.getLong());
    }

    @Override
    public void switchActivated(DatapathId switchId) {
        //we don't care about switch role
    }

    @Override
    public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
        //we don't care about switch ports
    }

    @Override
    public void switchChanged(DatapathId switchId) {
        //unused by floodlight
    }

    @Override
    public void switchDeactivated(DatapathId switchId) {
        //we don't care about switch role
    }
}
