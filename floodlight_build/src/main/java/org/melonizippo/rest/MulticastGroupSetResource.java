package org.melonizippo.rest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;

import com.google.gson.JsonSyntaxException;
import org.melonizippo.exceptions.GroupAddressOutOfPoolException;
import org.melonizippo.exceptions.GroupAlreadyExistsException;
import org.melonizippo.openflow.IIPv4MulticastService;
import org.melonizippo.openflow.MulticastGroup;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.restlet.resource.Get;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MulticastGroupSetResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(MulticastGroupSetResource.class);

    /**
     *
     * @param fmJson a json object in the format { "group": string(IPv4 address) }
     * @return a json response
     */
    @Put("json")
    public String Create(String fmJson)
    {
        IIPv4MulticastService multicastModule =
                (IIPv4MulticastService)getContext().getAttributes().
                        get(IIPv4MulticastService.class.getCanonicalName());

        Map<String, String> response = new HashMap<String, String>();
        Gson g = new Gson();

        try {
            Map<String,Object> map = new HashMap<String,Object>();
            Map<String,String> request = (Map<String,String>)g.fromJson(fmJson, new HashMap<String,String>().getClass());
            IPv4Address multicastAddress = IPv4Address.of(request.get("group"));
            multicastModule.addGroup(multicastAddress);
            response.put("error","none");
            response.put("message","Group correctly created");
        }
        catch(JsonSyntaxException ex)
        {
            response.put("error", "syntax");
            response.put("message", "Incorrect json syntax");
        }
        catch(IllegalArgumentException ex)
        {
            response.put("error", "syntax_address");
            response.put("message", "Cannot parse the IP address");
        }
        catch(GroupAddressOutOfPoolException ex)
        {
            response.put("error", "group_address_invalid");
            response.put("message", "Group address is out of the configured pool");
        }
        catch(GroupAlreadyExistsException ex)
        {
            response.put("error", "group_duplicated");
            response.put("message", "A multicast group with the same address already exists");
        }

        return g.toJson(response);
    }

    @Get("json")
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
            response.put(group.getIp().toString(), hosts);
        }

        return g.toJson(response);
    }
}
