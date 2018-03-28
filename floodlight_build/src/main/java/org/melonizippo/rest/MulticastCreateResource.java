package org.melonizippo.rest;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.melonizippo.exceptions.GroupAlreadyExistsException;
import org.melonizippo.openflow.IIPv4MulticastService;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MulticastCreateResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(MulticastCreateResource.class);

    /**
     *
     * @param fmJson a json object in the format { "group": string(IPv4 address) }
     * @return a json response
     */
    @Post("create")
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
        catch(GroupAlreadyExistsException ex)
        {
            response.put("error", "group_duplicated");
            response.put("message", "A multicast group with the same address already exists");
        }

        return g.toJson(response);
    }
}
