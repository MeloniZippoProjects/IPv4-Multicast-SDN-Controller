package org.melonizippo.openflow;

import java.util.ArrayList;
import java.util.List;

public class SwitchInfo {
    public long id;
    public List<Integer> knownGroups = new ArrayList<>();

    public SwitchInfo(long id)
    {
        this.id = id;
    }
}
