package org.melonizippo.openflow;

import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.oxm.OFOxms;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

public class MulticastGroup {
    public static AtomicInteger IDFactory = new AtomicInteger(0);

    private int id;
    private IPv4Address ip;
    private Set<IPv4Address> partecipants;

    private String name;
    private String description;

    public MulticastGroup(IPv4Address ip, String name, int id)
    {
        this.id = id;
        this.name = name;
        this.ip = ip;
        partecipants = new ConcurrentSkipListSet<>();
    }

    public IPv4Address getIp()
    {
        return ip;
    }

    public Set<IPv4Address> getPartecipants()
    {
        return partecipants;
    }

    public int getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public Map<String, Object> getInfo()
    {
        Map<String, Object> groupMap = new HashMap<String, Object>();
        groupMap.put("name", getName());
        groupMap.put("description", getDescription());
        groupMap.put("ip", getIp().toString());
        groupMap.put("id", getId());
        Set<String> hosts = new HashSet<>();
        for(IPv4Address host : getPartecipants())
        {
            hosts.add(host.toString());
        }
        groupMap.put("hosts", hosts);

        return groupMap;
    }

    @Override
    public boolean equals(Object other)
    {
        if(other == null) return false;
        if(other == this) return true;
        if(!(other instanceof MulticastGroup)) return false;

        MulticastGroup otherGroup = (MulticastGroup) other;
        return otherGroup.ip == this.ip;
    }

    public List<OFBucket> getBuckets(IOFSwitch iofSwitch, ARPLearningStorage arpLearningStorage)
    {
        List<OFBucket> buckets = new ArrayList<>();

        //get available action types
        OFActions actions = iofSwitch.getOFFactory().actions();
        //Open Flow extendable matches, needed to create actions
        OFOxms oxms = iofSwitch.getOFFactory().oxms();

        for(IPv4Address hostIP : getPartecipants())
        {
            ArrayList<OFAction> actionList = new ArrayList<OFAction>();

            HostL2Details hostDetails = arpLearningStorage.getHostL2Details(iofSwitch, hostIP);
            OFActionSetField setIpv4Field = actions.buildSetField()
                    .setField(
                            oxms.buildIpv4Dst()
                                    .setValue(hostIP)
                                    .build()
                    ).build();
            actionList.add(setIpv4Field);

            OFActionSetField setMacField = actions.buildSetField()
                    .setField(
                            oxms.buildEthDst()
                                    .setValue(hostDetails.mac)
                                    .build()
                    ).build();
            actionList.add(setMacField);

            OFActionOutput outputPacket = actions.output(OFPort.of(hostDetails.port), IPv4MulticastModule.MTU);
            actionList.add(outputPacket);

            OFBucket forwardPacket = iofSwitch.getOFFactory().buildBucket()
                    .setActions(actionList)
                    .setWatchPort(OFPort.ANY)
                    .setWatchGroup(OFGroup.ANY)
                    .build();

            buckets.add(forwardPacket);
        }

        return buckets;
    }
}
