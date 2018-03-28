package org.melonizippo.rest;

import com.google.gson.Gson;
import org.melonizippo.openflow.IIPv4MulticastService;
import org.restlet.Context;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MulticastTestResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(MulticastTestResource.class);

    @Get("test")
    public String Test()
    {
        IIPv4MulticastService multicastModule =
                (IIPv4MulticastService)getContext().getAttributes().
                        get(IIPv4MulticastService.class.getCanonicalName());

        List<String> response = new ArrayList<>();
        Gson g = new Gson();

        response.add( multicastModule == null ? "Module reference is null" : "Module reference is defined");

        Context c = getContext();
        response.add( c == null ? "Context is null" : "Context is not null");

        Set<String> attributes = c.getAttributes().keySet();
        for(String att : attributes)
            response.add(att);

        return g.toJson(response);
    }
}
