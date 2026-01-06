package org.jlopezinc;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jlopezinc.model.UserModel;

@Path("/v2")
@ApplicationScoped
@Authenticated
public class V2Resource {
    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    EventV1Service eventV1Service;

    @PUT
    @Path("/{event}/{email}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<UserModel> updateUserMetadata(@PathParam("event") String event, @PathParam("email") String email, UserModel body){
        return eventV1Service.updateUserMetadata(event, email, body);
    }

    @PUT
    @Path("/{event}/{email}/checkin")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<UserModel> checkInToken(@PathParam("event") String event, @PathParam("email") String email){
        String cognitoUser = getCognitoUser();
        return  eventV1Service.checkInByEventAndEmail(event, email, cognitoUser);
    }

    @DELETE
    @Path("/{event}/{email}/checkin")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<UserModel> cancelCheckInToken(@PathParam("event") String event, @PathParam("email") String email){
        String cognitoUser = getCognitoUser();
        return  eventV1Service.cancelCheckInByEventAndEmail(event, email, cognitoUser);
    }

    private String getCognitoUser(){
        return securityIdentity.getPrincipal().getName();
    }
}
