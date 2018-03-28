package org.melonizippo.openflow;

import org.melonizippo.exceptions.GroupAlreadyExistsException;
import org.melonizippo.exceptions.GroupNotFoundException;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.Set;

public interface IIPv4MulticastModule {

    Set<MulticastGroup> getMulticastGroups();
    void addGroup(IPv4Address groupIP) throws GroupAlreadyExistsException;
    void deleteGroup(IPv4Address groupIP) throws GroupNotFoundException;
    void addToGroup(IPv4Address groupIP, IPv4Address hostIP) throws GroupNotFoundException;
    void removeFromGroup(IPv4Address groupIP, IPv4Address hostIP) throws GroupNotFoundException;
}
