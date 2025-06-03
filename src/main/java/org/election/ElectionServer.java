package org.election;

import com.sun.net.httpserver.HttpServer;
import org.election.handler.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class ElectionServer {
    public static void main(String[] args) throws IOException {
        int port = 8000;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Create contexts for each endpoint
        server.createContext("/election/create", new CreateElectionHandler());
        server.createContext("/province", new AddProvinceHandler()); // Combined GET/POST
        server.createContext("/district", new AddDistrictHandler()); // Combined GET/POST
        server.createContext("/election/assign", new AssignElectionHandler());
        server.createContext("/party/add", new AddPartyHandler());
        server.createContext("/district/details", new DistrictDetailsHandler());
        server.createContext("/party/assign", new AssignPartyHandler());
        server.createContext("/parties", new GetPartiesHandler());
        server.createContext("/results", new GetResultsHandler());
        server.createContext("/calculate/seats", new CalculateSeatsHandler());
        server.createContext("/results/district", new AddDistrictResultsHandler());
        server.createContext("/results/party", new AddPartyResultsHandler());

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Server started on port " + port);
    }
}