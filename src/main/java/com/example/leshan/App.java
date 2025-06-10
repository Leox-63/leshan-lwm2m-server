package com.example.leshan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.eclipse.leshan.server.LeshanServer;
import org.eclipse.leshan.server.LeshanServerBuilder;
import org.eclipse.leshan.server.californium.endpoint.CaliforniumServerEndpointsProvider;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.core.link.Link;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.example.leshan.ClientInfo;

@SpringBootApplication
@RestController
public class App {
    private static LeshanServer server;

    public static void main(String[] args) {
        LeshanServerBuilder builder = new LeshanServerBuilder();
        builder.setEndpointsProviders(new CaliforniumServerEndpointsProvider());
        server = builder.build();
        server.start();
        SpringApplication.run(App.class, args);
        System.out.println("Servidor Leshan + Spring Boot iniciado. API REST en http://localhost:8080");
    }

    @GetMapping("/api/clients")
    public List<Map<String, String>> getClients() {
        List<Registration> regs = new java.util.ArrayList<>();
        java.util.Iterator<Registration> it = server.getRegistrationService().getAllRegistrations();
        while (it.hasNext()) {
            regs.add(it.next());
        }
        return regs.stream()
                .map(reg -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("endpoint", reg.getEndpoint());
                    map.put("link", "/api/clients/" + reg.getEndpoint());
                    return map;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/api/clients/{endpoint}")
    public ResponseEntity<?> getClientInfo(@PathVariable("endpoint") String endpoint) {
        Registration reg = server.getRegistrationService().getByEndpoint(endpoint);
        if (reg == null) {
            return ResponseEntity.notFound().build();
        }
        ClientInfo info = new ClientInfo();
        info.endpoint = reg.getEndpoint();
        info.registrationId = reg.getId();
        info.address = reg.getAddress().toString();
        info.registrationDate = reg.getRegistrationDate().toString();
        info.lastUpdate = reg.getLastUpdate().toString();
        info.lifetime = reg.getLifeTimeInSec();
        info.smsNumber = reg.getSmsNumber();
        info.objectLinks = java.util.Arrays.stream(reg.getObjectLinks())
            .map(Link::toString)
            .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(info);
    }

}
