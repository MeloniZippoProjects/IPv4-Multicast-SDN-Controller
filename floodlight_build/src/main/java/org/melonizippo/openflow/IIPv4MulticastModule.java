package org.melonizippo.openflow;

import org.melonizippo.exceptions.GroupAlreadyExistsException;
import org.melonizippo.exceptions.GroupNotFoundException;

import java.net.Inet4Address;
import java.util.Set;

public interface IIPv4MulticastModule {

    Set<MulticastGroup> getMulticastGroups();
    void addGroup(Inet4Address groupIP) throws GroupAlreadyExistsException;
    void deleteGroup(Inet4Address groupIP) throws GroupNotFoundException;
    void addToGroup(Inet4Address groupIP, Inet4Address hostIP) throws GroupNotFoundException;
    void removeFromGroup(Inet4Address groupIP, Inet4Address hostIP) throws GroupNotFoundException;
}
