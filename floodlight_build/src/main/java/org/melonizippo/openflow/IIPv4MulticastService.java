package org.melonizippo.openflow;

import net.floodlightcontroller.core.module.IFloodlightService;
import org.melonizippo.exceptions.*;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.Set;

public interface IIPv4MulticastService extends IFloodlightService
{
    Set<MulticastGroup> getMulticastGroups();
    Integer addGroup(IPv4Address groupIP, String groupName) throws GroupAlreadyExistsException, GroupAddressOutOfPoolException;
    void deleteGroup(Integer groupID) throws GroupNotFoundException;
    void addToGroup(Integer groupID, IPv4Address hostIP) throws GroupNotFoundException, HostAddressOutOfPoolException;
    void removeFromGroup(Integer groupID, IPv4Address hostIP) throws GroupNotFoundException, HostAddressOutOfPoolException, HostNotFoundException;
}
