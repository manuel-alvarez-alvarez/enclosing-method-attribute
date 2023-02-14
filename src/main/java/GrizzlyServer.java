import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

public class GrizzlyServer implements Server {

  private final HttpServer server;
  private String address;

  public GrizzlyServer() throws URISyntaxException {
    ResourceConfig rc = new ResourceConfig();
    rc.register(Endpoint.class);
    server = GrizzlyHttpServerFactory.createHttpServer(new URI("http://localhost:0"), rc, false);
  }

  @Override
  public void start() throws IOException {
    server.start();
    address = String.format("http://127.0.0.1:%s", server.getListener("grizzly").getPort());
  }

  @Override
  public void close() throws Exception {
    this.server.shutdownNow();
  }

  @Override
  public String getAddress() throws Exception {
    return address;
  }

  @Path("/")
  public static class Endpoint {
    @GET
    @Path("hello")
    public Response success(@QueryParam("name") final String hello) {
      return Response.status(HttpStatus.OK_200.getStatusCode()).entity(hello).build();
    }
  }
}
