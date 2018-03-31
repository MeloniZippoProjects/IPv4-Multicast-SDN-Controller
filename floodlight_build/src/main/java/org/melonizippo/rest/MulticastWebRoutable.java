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
        router.attach("/", MulticastGroupSetResource.class);
        router.attach("/{groupId}", MulticastGroupResource.class);
        router.attach("/{groupId}/hosts", MulticastGroupHostsResource.class);
        return router;
    }

    /**
     * Set the base path for the Topology
     */
    public String basePath() {
        return "/multicastgroups";
    }
}