package org.melonizippo.openflow;

import net.floodlightcontroller.core.module.IFloodlightService;
import org.melonizippo.exceptions.GroupAddressOutOfPoolException;
import org.melonizippo.exceptions.GroupAlreadyExistsException;
import org.melonizippo.exceptions.GroupNotFoundException;
import org.melonizippo.exceptions.HostAddressOutOfPoolException;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.Set;

public interface IIPv4MulticastService extends IFloodlightService
{
    Set<MulticastGroup> getMulticastGroups();
    void addGroup(IPv4Address groupIP) throws GroupAlreadyExistsException, GroupAddressOutOfPoolException;
    void deleteGroup(IPv4Address groupIP) throws GroupNotFoundException, GroupAddressOutOfPoolException;
    void addToGroup(IPv4Address groupIP, IPv4Address hostIP) throws GroupNotFoundException, GroupAddressOutOfPoolException, HostAddressOutOfPoolException;
    void removeFromGroup(IPv4Address groupIP, IPv4Address hostIP) throws GroupNotFoundException, GroupAddressOutOfPoolException, HostAddressOutOfPoolException;
}
