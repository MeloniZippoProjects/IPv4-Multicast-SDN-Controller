package org.melonizippo.openflow;

import java.net.Inet4Address;
import java.util.HashSet;
import java.util.Set;

public class MulticastGroup {
    Inet4Address IP;
    Set<Inet4Address> Partecipants;

    public MulticastGroup(Inet4Address ip)
    {
        IP = ip;
        Partecipants = new HashSet<>();
    }

    public Inet4Address getIP()
    {
        return IP;
    }

    public Set<Inet4Address> getPartecipants()
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
