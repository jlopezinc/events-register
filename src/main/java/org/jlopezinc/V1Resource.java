package org.jlopezinc;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/v1")
@ApplicationScoped
public class V1Resource {

    @Inject
    EventV1Service eventV1Service;

    @GET
    @Path("/{event}/{qrToken}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<UserModel> getUser(@PathParam("event") String event, @PathParam("qrToken") String qrToken){
        return eventV1Service.getByEventAndQrToken(event, qrToken);
    }

    @GET
    @Path("/{event}/email/{email}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<UserModel> getUserByEmail(@PathParam("event") String event, @PathParam("email") String email){
        return eventV1Service.getByEventAndEmail(event, email);
    }

    @PUT
    @Path("/{event}/{qrToken}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<UserModel> checkInToken(@PathParam("event") String event, @PathParam("qrToken") String qrToken){
        return  eventV1Service.checkInByEventAndQrToken(event, qrToken, null); //todo
    }



}
