package org.melonizippo.openflow;

import org.melonizippo.exceptions.GroupAlreadyExistsException;
import org.melonizippo.exceptions.GroupNotFoundException;

import java.util.Set;

public interface IIPv4MulticastModule {

    Set<String> getGroupsSet();
    void addGroup(String group) throws GroupAlreadyExistsException;
    void deleteGroup(String group) throws GroupNotFoundException;
    void addToGroup(String group, String host) throws GroupNotFoundException;
    void removeFromGroup(String group, String host) throws GroupNotFoundException;
}
