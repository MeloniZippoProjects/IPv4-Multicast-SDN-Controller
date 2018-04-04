package org.melonizippo.openflow;

import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class MulticastGroup {
    public static AtomicInteger IDFactory;

    Integer id;
    String name;
    IPv4Address ip;
    Set<IPv4Address> partecipants;

    public MulticastGroup(IPv4Address ip, String name, int id)
    {
        this.id = id;
        this.name = name;
        this.ip = ip;
        partecipants = new HashSet<>();
    }

    public IPv4Address getIp()
    {
        return ip;
    }

    public Set<IPv4Address> getPartecipants()
    {
        return partecipants;
    }

    public Integer getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
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
