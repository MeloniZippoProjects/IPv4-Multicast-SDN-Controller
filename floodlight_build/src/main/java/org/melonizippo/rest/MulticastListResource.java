package org.melonizippo.rest;

import java.net.Inet4Address;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.melonizippo.exceptions.GroupAlreadyExistsException;
import org.melonizippo.exceptions.GroupNotFoundException;
import org.melonizippo.openflow.IIPv4MulticastModule;
import org.melonizippo.openflow.MulticastGroup;
import org.python.antlr.ast.Str;
import org.restlet.Server;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MulticastListResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(MulticastListResource.class);

    @Get("list")
    public String List()
    {
        IIPv4MulticastModule multicastModule =
                (IIPv4MulticastModule)getContext().getAttributes().
                        get(IIPv4MulticastModule.class.getCanonicalName());

        Map<String, Set<String>> response = new HashMap<String, Set<String>>();
        Gson g = new Gson();

        Set<MulticastGroup> multicastGroups = multicastModule.getMulticastGroups();
        for (MulticastGroup group: multicastGroups)
        {
            Set<String> hosts = new HashSet<>();
            for(Inet4Address host : group.getPartecipants())
            {
                hosts.add(host.getHostAddress());
            }
            response.put(group.getIP().getHostAddress(), hosts);
        }

        return g.toJson(response);
    }
}
