package org.jlopezinc;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

@Path("/v1")
@ApplicationScoped
@Authenticated
public class V1Resource {

    private static final Logger LOG = Logger.getLogger(V1Resource.class);

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    EventV1Service eventV1Service;


    @GET
    @Path("/{event}/{email}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<UserModel> getUserByEmail(@PathParam("event") String event, @PathParam("email") String email){
        return eventV1Service.getByEventAndEmail(event, email);
    }

    @GET
    @Path("/{event}/counters")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<CountersModel> getCountersByEvent(@PathParam("event") String event){
        return eventV1Service.getCountersByEvent(event);
    }

    @PUT
    @Path("/{event}/{email}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<UserModel> checkInToken(@PathParam("event") String event, @PathParam("email") String email){
        String cognitoUser = getCognitoUser();
        return  eventV1Service.checkInByEventAndEmail(event, email, cognitoUser);
    }

    @DELETE
    @Path("/{event}/{email}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<UserModel> cancelCheckInToken(@PathParam("event") String event, @PathParam("email") String email){
        String cognitoUser = getCognitoUser();
        return  eventV1Service.cancelCheckInByEventAndEmail(event, email, cognitoUser);
    }

    private String getCognitoUser(){
        return securityIdentity.getPrincipal().getName();
    }



}
