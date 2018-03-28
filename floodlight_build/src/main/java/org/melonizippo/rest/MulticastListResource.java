package org.melonizippo.rest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;

import org.melonizippo.openflow.IIPv4MulticastService;
import org.melonizippo.openflow.MulticastGroup;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MulticastListResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(MulticastListResource.class);

    @Get("list")
    public String List()
    {
        IIPv4MulticastService multicastModule =
                (IIPv4MulticastService)getContext().getAttributes().
                        get(IIPv4MulticastService.class.getCanonicalName());

        Map<String, Set<String>> response = new HashMap<String, Set<String>>();
        Gson g = new Gson();

        Set<MulticastGroup> multicastGroups = multicastModule.getMulticastGroups();
        for (MulticastGroup group: multicastGroups)
        {
            Set<String> hosts = new HashSet<>();
            for(IPv4Address host : group.getPartecipants())
            {
                hosts.add(host.toString());
            }
            response.put(group.getIP().toString(), hosts);
        }

        return g.toJson(response);
    }
}
