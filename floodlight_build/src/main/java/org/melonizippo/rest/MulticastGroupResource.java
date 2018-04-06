package org.melonizippo.rest;

import java.util.*;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.melonizippo.exceptions.GroupAlreadyExistsException;
import org.melonizippo.exceptions.GroupAddressOutOfPoolException;
import org.melonizippo.exceptions.GroupNotFoundException;
import org.melonizippo.exceptions.HostAddressOutOfPoolException;
import org.melonizippo.openflow.IIPv4MulticastService;

import org.melonizippo.openflow.MulticastGroup;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.restlet.resource.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MulticastGroupResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(MulticastGroupResource.class);

    /**
     *
     * Takes groupID from URL
     * @return a json response
     */
    @Get("json")
    public String List()
    {
        IIPv4MulticastService multicastModule =
                (IIPv4MulticastService)getContext().getAttributes().
                        get(IIPv4MulticastService.class.getCanonicalName());

        Map<String, Object> response = new HashMap<>();

        Gson g = new Gson();

        try
        {
            int groupId = Integer.parseInt((String)getRequestAttributes().get("groupId"));
            MulticastGroup group = multicastModule.getGroup(groupId);
            response.put("error", "none");
            response.put("message", "Group found");
            response.put("content", group.getInfo());
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

        return g.toJson(response);
    }

    /**
     *
     * Takes groupID from URL
     * @return a json response
     */
    @Delete("json")
    public String Delete()
    {
        IIPv4MulticastService multicastModule =
                (IIPv4MulticastService)getContext().getAttributes().
                        get(IIPv4MulticastService.class.getCanonicalName());

        Map<String, String> response = new HashMap<String, String>();
        Gson g = new Gson();

        try
        {
            int groupId = Integer.parseInt((String)getRequestAttributes().get("groupId"));
            multicastModule.deleteGroup(groupId);
            response.put("error", "none");
            response.put("message", "Group deleted");
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

        return g.toJson(response);
    }
}
