package org.melonizippo.openflow;

import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

public class MulticastGroup {
    public static AtomicInteger IDFactory = new AtomicInteger(0);

    private int id;
    private String name;
    private IPv4Address ip;
    private Set<IPv4Address> partecipants;

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

    public Map<String, Object> getInfo()
    {
        Map<String, Object> groupMap = new HashMap<String, Object>();
        groupMap.put("id", getId());
        groupMap.put("name", getName());
        groupMap.put("ip", getIp().toString());
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
}
