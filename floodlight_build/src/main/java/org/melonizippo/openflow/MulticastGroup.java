package org.melonizippo.openflow;

import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.HashSet;
import java.util.Set;

public class MulticastGroup {
    IPv4Address IP;
    Set<IPv4Address> Partecipants;

    public MulticastGroup(IPv4Address ip)
    {
        IP = ip;
        Partecipants = new HashSet<>();
    }

    public IPv4Address getIP()
    {
        return IP;
    }

    public Set<IPv4Address> getPartecipants()
    {
        return Partecipants;
    }

    @Override
    public boolean equals(Object other)
    {
        if(other == null) return false;
        if(other == this) return true;
        if(!(other instanceof MulticastGroup)) return false;

        MulticastGroup otherGroup = (MulticastGroup) other;
        return otherGroup.IP == this.IP;
    }
}
