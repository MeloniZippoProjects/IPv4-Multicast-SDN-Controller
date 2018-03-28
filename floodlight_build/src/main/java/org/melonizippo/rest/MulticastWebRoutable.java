package org.melonizippo.rest;

import net.floodlightcontroller.restserver.RestletRoutable;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;


public class MulticastWebRoutable implements RestletRoutable {
    /**
     * Create the Restlet router and bind to the proper resources.
     */
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/create", MulticastCreateResource.class);
        router.attach("/delete", MulticastDeleteResource.class);
        router.attach("/join", MulticastJoinResource.class);
        router.attach("/unjoin", MulticastJoinResource.class);
        router.attach("/list", MulticastListResource.class);
        router.attach("/test", MulticastTestResource.class);
        return router;
    }

    /**
     * Set the base path for the Topology
     */
    public String basePath() {
        return "/multicastgroups";
    }
}