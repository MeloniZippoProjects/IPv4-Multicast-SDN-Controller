package org.melonizippo.openflow;

import org.melonizippo.exceptions.*;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class IPv4MulticastDelegate implements IIPv4MulticastService {
    IPv4AddressWithMask unicastPool;
    IPv4AddressWithMask multicastPool;
    Set<MulticastGroup> multicastGroups;

    public IPv4MulticastDelegate() {
        unicastPool = IPv4AddressWithMask.of("10.0.0.0/8");
        multicastPool = IPv4AddressWithMask.of("11.0.1.0/8");
        multicastGroups = ConcurrentHashMap.newKeySet();
    }

    public Set<MulticastGroup> getMulticastGroups() {
        return Collections.unmodifiableSet(multicastGroups);
    }

    public Integer addGroup(IPv4Address groupIP, String groupName) throws GroupAlreadyExistsException, GroupAddressOutOfPoolException {
        if (!multicastPool.contains(groupIP))
            throw new GroupAddressOutOfPoolException();

        Optional duplicate = multicastGroups.stream().filter(group -> group.getIp().equals(groupIP)).findFirst();
        if (!duplicate.isPresent()) {
            Integer groupID = MulticastGroup.IDFactory.incrementAndGet();
            MulticastGroup newGroup = new MulticastGroup(groupIP, groupName, groupID);
            multicastGroups.add(newGroup);
            return groupID;
        } else
            throw new GroupAlreadyExistsException();
    }

    public MulticastGroup getGroup(Integer groupID) throws GroupNotFoundException {
        Optional<MulticastGroup> target = multicastGroups.stream().filter(group -> group.getId() == groupID).findFirst();
        if (target.isPresent())
            return target.get();
        else
            throw new GroupNotFoundException();

    }

    public void deleteGroup(Integer groupID) throws GroupNotFoundException {
        MulticastGroup group = getGroup(groupID);
        multicastGroups.remove(group);
    }

    public void addToGroup(Integer groupID, IPv4Address hostIP) throws GroupNotFoundException, HostAddressOutOfPoolException {
        if (!unicastPool.contains(hostIP))
            throw new HostAddressOutOfPoolException();

        MulticastGroup group = getGroup(groupID);
        group.getPartecipants().add(hostIP);
    }

    public void removeFromGroup(Integer groupID, IPv4Address hostIP) throws GroupNotFoundException, HostAddressOutOfPoolException, HostNotFoundException {
        if (!unicastPool.contains(hostIP))
            throw new HostAddressOutOfPoolException();

        MulticastGroup group = getGroup(groupID);
        if (!group.getPartecipants().remove(hostIP))
            throw new HostNotFoundException();
    }
}