package org.melonizippo.rest;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.melonizippo.exceptions.GroupAlreadyExistsException;
import org.melonizippo.exceptions.GroupNotFoundException;
import org.melonizippo.openflow.IIPv4MulticastModule;
import org.python.antlr.ast.Str;
import org.restlet.Server;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MulticastJoinResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(MulticastJoinResource.class);


    /**
     *
     * @param fmJson a json object in the format { "group": string(IPv4 address), "host": string(IPv4 address) }
     * @return a json response
     */
    @Post("join")
    public String Join(String fmJson)
    {
        IIPv4MulticastModule multicastModule =
                (IIPv4MulticastModule)getContext().getAttributes().
                        get(IIPv4MulticastModule.class.getCanonicalName());

        Map<String, String> response = new HashMap<String, String>();
        Gson g = new Gson();

        try
        {
            Map<String,String> request = (Map<String,String>)g.fromJson(fmJson, new HashMap<String,String>().getClass());
            multicastModule.addToGroup(request.get("group"), request.get("host"));
        }
        catch(JsonSyntaxException ex)
        {
            response.put("error", "syntax");
            response.put("message", "Incorrect json syntax");
        }
        catch(GroupNotFoundException ex)
        {
            response.put("error", "group_not_found");
            response.put("message", "A multicast group with this address cannot be found");
        }

        return g.toJson(response);
    }
}
