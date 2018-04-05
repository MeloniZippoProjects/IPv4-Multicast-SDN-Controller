package org.melonizippo.rest;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.melonizippo.exceptions.*;
import org.melonizippo.openflow.IIPv4MulticastService;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.restlet.resource.Delete;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MulticastGroupHostsResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(MulticastGroupHostsResource.class);


    /**
     *
     * @param fmJson a json object in the format { "host": string(IPv4 address) }
     * @return a json response
     */
    @Put("json")
    public String Join(String fmJson)
    {
        IIPv4MulticastService multicastModule =
                (IIPv4MulticastService)getContext().getAttributes().
                        get(IIPv4MulticastService.class.getCanonicalName());

        Map<String, String> response = new HashMap<String, String>();
        Gson g = new Gson();

        try
        {
            Map<String,String> request = (Map<String,String>)g.fromJson(fmJson, new HashMap<String,String>().getClass());
            //todo: add syntax check for required fields
            int groupId = Integer.parseInt((String)getRequestAttributes().get("groupId"));
            IPv4Address hostAddress = IPv4Address.of(request.get("host"));
            multicastModule.addToGroup(groupId, hostAddress);

            response.put("error", "none");
            response.put("message", "Host joined this group");
        }
        catch(JsonSyntaxException ex)
        {
            response.put("error", "syntax");
            response.put("message", "Incorrect json syntax");
        }
        catch (IllegalArgumentException e)
        {
            response.put("error", "syntax_address");
            response.put("message", "Cannot parse the IP address");
        }
        catch(GroupNotFoundException ex)
        {
            response.put("error", "group_not_found");
            response.put("message", "A multicast group with this id cannot be found");
        }
        catch(HostAddressOutOfPoolException ex)
        {
            response.put("error", "host_address_invalid");
            response.put("message", "Host address is out of the configured pool");
        }

        return g.toJson(response);
    }

    @Delete("json")
    public String Unjoin(String fmJson)
    {
        IIPv4MulticastService multicastModule =
                (IIPv4MulticastService)getContext().getAttributes().
                        get(IIPv4MulticastService.class.getCanonicalName());

        Map<String, String> response = new HashMap<String, String>();
        Gson g = new Gson();


        //todo get group id from url
        try
        {
            Map<String,String> request = (Map<String,String>)g.fromJson(fmJson, new HashMap<String,String>().getClass());
            int groupId = Integer.parseInt((String)getRequestAttributes().get("groupId"));
            IPv4Address hostAddress = IPv4Address.of(request.get("host"));
            multicastModule.removeFromGroup(groupId, hostAddress);

            response.put("error", "none");
            response.put("message", "Host unjoined this group");
        }
        catch(JsonSyntaxException ex)
        {
            response.put("error", "syntax");
            response.put("message", "Incorrect json syntax");
        }
        catch (IllegalArgumentException e)
        {
            response.put("error", "syntax_address");
            response.put("message", "Cannot parse the IP address");
        }
        catch(HostAddressOutOfPoolException ex)
        {
            response.put("error", "host_address_invalid");
            response.put("message", "Host address is out of the configured pool");
        }
        catch(GroupNotFoundException ex)
        {
            response.put("error", "group_not_found");
            response.put("message", "A multicast group with this id cannot be found");
        }
        catch(HostNotFoundException ex)
        {
            response.put("error", "host_not_found");
            response.put("message", "The multicast group does not contain this host");
        }

        return g.toJson(response);
    }
}
