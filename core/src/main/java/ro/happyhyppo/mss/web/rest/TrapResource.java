package ro.happyhyppo.mss.web.rest;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import ro.happyhyppo.mss.MainAgent;
import ro.happyhyppo.mss.net.NetworkElement;
import ro.happyhyppo.mss.web.model.TrapDTO;

@Path("/trap")
public class TrapResource {

    @PUT
    @Path("/{param}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response sendTrap(@PathParam("param") String ipAddress, TrapDTO trap) {
        NetworkElement networkElement = MainAgent.instance().findElement(ipAddress);
        if (networkElement == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Map<String, Object> objects = new HashMap<>();
        trap.getObjects().forEach(trapObject -> {
            objects.put(trapObject.getOid(), trapObject.getValue());
        });
        try {
			networkElement.sendTrap(trap.getOid(), null, objects);
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Trap sending failed from " + ipAddress + "\n").build();
		}
        return Response.status(Response.Status.OK).entity("Trap sent from " + ipAddress + "\n").build();
    }

}
