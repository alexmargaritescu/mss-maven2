package ro.happyhyppo.mss.web.rest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import ro.happyhyppo.mss.MainAgent;
import ro.happyhyppo.mss.net.NetworkElement;
import ro.happyhyppo.mss.web.model.NetworkElementDTO;

@Path("/network")
public class NetworkResource {

    private Client client;

    public NetworkResource() {
        this.client = ClientBuilder.newClient();
    }

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response createElement(NetworkElementDTO networkElement) {
		if (networkElement.getIpAddress() == null) {
			return Response.status(Response.Status.BAD_REQUEST).entity("Missing ipAddress\n").build();
		}
		if (networkElement.getNeClass() == null) {
			return Response.status(Response.Status.BAD_REQUEST).entity("Missing class\n").build();
		}
        NetworkElement element = MainAgent.instance().addElement(networkElement.getIpAddress(),
                networkElement.getPort(), networkElement.getReadCommunity(), networkElement.getWriteCommunity(),
                networkElement.getNeClass(), networkElement.getHttp(), networkElement.getTrapManagerIpAddress(),
                networkElement.getTrapManagerPort());
		if (element == null) {
			return Response.status(Response.Status.BAD_REQUEST).entity("IP address already exists\n").build();
		}
		return Response.status(Response.Status.OK).entity(new NetworkElementDTO(element)).build();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<NetworkElementDTO> findAll() {
		Collection<NetworkElement> list = MainAgent.instance().listElements();
		List<NetworkElementDTO> result = new ArrayList<>();
		list.forEach(networkElement -> {
			result.add(new NetworkElementDTO(networkElement));
		});
		return result;
	}

	@GET
	@Path("/{param}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response findByIpAddress(@PathParam("param") String ipAddress) {
		NetworkElement networkElement = MainAgent.instance().findElement(ipAddress);
		if (networkElement != null) {
			return Response.status(Response.Status.OK).entity(new NetworkElementDTO(networkElement)).build();
		}
		return Response.status(Response.Status.NOT_FOUND).build();
	}

	@DELETE
	@Path("/{param}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deleteElement(@PathParam("param") String ipAddress) {
	    NetworkElement networkElement = MainAgent.instance().removeElement(ipAddress);
	    if (networkElement != null) {
            return Response.status(Response.Status.OK).entity(new NetworkElementDTO(networkElement)).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
	}

	@GET
    @Path("/{ipAddress}/oid")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getValues(@PathParam("ipAddress") String ipAddress, @Context UriInfo info) {
        NetworkElement networkElement = MainAgent.instance().findElement(ipAddress);
        if (networkElement != null) {
            List<String> instances = info.getQueryParameters().keySet().stream().collect(Collectors.toList());
            Map<String, String> values = networkElement.getOids(instances);
            return Response.status(Response.Status.OK).entity(values).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

	@PUT
    @Path("/{ipAddress}/oid")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setOid(@PathParam("ipAddress") String ipAddress, @Context UriInfo info) {
        NetworkElement networkElement = MainAgent.instance().findElement(ipAddress);
        if (networkElement != null) {
            Map<String, String> values = info.getQueryParameters().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0)));
            values = networkElement.setOids(values);
            return Response.status(Response.Status.OK).entity(values).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

	@GET
    @Path("/{ipAddress}/api/{resource: .*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getJsonConfig(@PathParam("ipAddress") String ipAddress, @PathParam("resource") String resource) {
        NetworkElement networkElement = MainAgent.instance().findElement(ipAddress);
        if (networkElement != null) {
            Object response = client.target("http://" + ipAddress).path(resource).request().get().getEntity();
            return Response.status(Response.Status.OK).entity(response).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

	@PUT
    @Path("/{ipAddress}/api/{resource: .*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setJsonConfig(@PathParam("ipAddress") String ipAddress, @PathParam("resource") String resource, Object json) {
        NetworkElement networkElement = MainAgent.instance().findElement(ipAddress);
        if (networkElement != null) {
            Object response = client.target("http://" + ipAddress).path(resource).request().put(Entity.json(json)).getEntity();
            return Response.status(Response.Status.OK).entity(response).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

	@GET
    @Path("/{ipAddress}/json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getJsonTree(@PathParam("ipAddress") String ipAddress, @Context UriInfo info) {
        NetworkElement networkElement = MainAgent.instance().findElement(ipAddress);
        if (networkElement != null) {
            List<String> paths = info.getQueryParameters().keySet().stream().collect(Collectors.toList());
            Map<String, Object> values = networkElement.getSubTrees(paths);
            return Response.status(Response.Status.OK).entity(values).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

	@PUT
    @Path("/{ipAddress}/json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setJsonTree(@PathParam("ipAddress") String ipAddress, @Context UriInfo info) {
        NetworkElement networkElement = MainAgent.instance().findElement(ipAddress);
        if (networkElement != null) {
            Map<String, Object> values = info.getQueryParameters().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0)));
            Thread.currentThread().setName(ipAddress);
            values = networkElement.setSubTrees(values);
            return Response.status(Response.Status.OK).entity(values).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

}
