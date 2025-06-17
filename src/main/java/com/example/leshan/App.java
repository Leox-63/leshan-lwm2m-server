package com.example.leshan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.eclipse.leshan.server.LeshanServer;
import org.eclipse.leshan.server.LeshanServerBuilder;
import org.eclipse.leshan.server.californium.endpoint.CaliforniumServerEndpointsProvider;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.core.link.Link;
import org.eclipse.leshan.core.request.*;
import org.eclipse.leshan.core.response.*;
import org.eclipse.leshan.core.node.*;

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

    // 🔍 Endpoint simplificado para leer un recurso específico
    @GetMapping("/api/clients/{endpoint}/read/{objectId}/{instanceId}/{resourceId}")
    public ResponseEntity<?> readResource(
            @PathVariable("endpoint") String endpoint,
            @PathVariable("objectId") int objectId,
            @PathVariable("instanceId") int instanceId,
            @PathVariable("resourceId") int resourceId) {
        
        Registration reg = server.getRegistrationService().getByEndpoint(endpoint);
        if (reg == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Client not found");
            return ResponseEntity.status(404).body(error);
        }

        try {
            // Crear la petición de lectura
            ReadRequest request = new ReadRequest(objectId, instanceId, resourceId);
            
            // Enviar la petición al cliente (con timeout de 5 segundos)
            ReadResponse response = server.send(reg, request, 5000);
            
            if (response.isSuccess()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("path", "/" + objectId + "/" + instanceId + "/" + resourceId);
                result.put("value", response.getContent().toString());
                result.put("timestamp", java.time.Instant.now().toString());
                return ResponseEntity.ok(result);
            } else {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Failed to read resource: " + response.getErrorMessage());
                return ResponseEntity.status(400).body(error);
            }
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Exception reading resource: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    // ✍️ Endpoint simplificado para escribir a un recurso específico
    @PostMapping("/api/clients/{endpoint}/write/{objectId}/{instanceId}/{resourceId}")
    public ResponseEntity<?> writeResource(
            @PathVariable("endpoint") String endpoint,
            @PathVariable("objectId") int objectId,
            @PathVariable("instanceId") int instanceId,
            @PathVariable("resourceId") int resourceId,
            @RequestBody Map<String, Object> payload) {
        
        Registration reg = server.getRegistrationService().getByEndpoint(endpoint);
        if (reg == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Client not found");
            return ResponseEntity.status(404).body(error);
        }

        try {
            // Extraer el valor del payload
            Object value = payload.get("value");
            if (value == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Missing 'value' field in request body");
                return ResponseEntity.status(400).body(error);
            }

            // Crear la petición de escritura según el tipo
            WriteRequest request;
            if (value instanceof String) {
                request = new WriteRequest(objectId, instanceId, resourceId, (String) value);
            } else if (value instanceof Integer) {
                request = new WriteRequest(objectId, instanceId, resourceId, ((Integer) value).longValue());
            } else if (value instanceof Double) {
                request = new WriteRequest(objectId, instanceId, resourceId, (Double) value);
            } else if (value instanceof Boolean) {
                request = new WriteRequest(objectId, instanceId, resourceId, (Boolean) value);
            } else {
                request = new WriteRequest(objectId, instanceId, resourceId, value.toString());
            }
            
            // Enviar la petición al cliente
            WriteResponse response = server.send(reg, request, 5000);
            
            if (response.isSuccess()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("path", "/" + objectId + "/" + instanceId + "/" + resourceId);
                result.put("writtenValue", value);
                result.put("timestamp", java.time.Instant.now().toString());
                return ResponseEntity.ok(result);
            } else {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Failed to write resource: " + response.getErrorMessage());
                return ResponseEntity.status(400).body(error);
            }
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Exception writing resource: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    // 🎬 Endpoint para ejecutar un recurso (execute)
    @PostMapping("/api/clients/{endpoint}/execute/{objectId}/{instanceId}/{resourceId}")
    public ResponseEntity<?> executeResource(
            @PathVariable("endpoint") String endpoint,
            @PathVariable("objectId") int objectId,
            @PathVariable("instanceId") int instanceId,
            @PathVariable("resourceId") int resourceId,
            @RequestBody(required = false) Map<String, Object> payload) {
        
        Registration reg = server.getRegistrationService().getByEndpoint(endpoint);
        if (reg == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Client not found");
            return ResponseEntity.status(404).body(error);
        }

        try {
            // Extraer parámetros opcionales
            String arguments = "";
            if (payload != null && payload.containsKey("arguments")) {
                arguments = payload.get("arguments").toString();
            }

            // Crear la petición de ejecución
            ExecuteRequest request = new ExecuteRequest(objectId, instanceId, resourceId, arguments);
            
            // Enviar la petición al cliente
            ExecuteResponse response = server.send(reg, request, 5000);
            
            if (response.isSuccess()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("path", "/" + objectId + "/" + instanceId + "/" + resourceId);
                result.put("executed", true);
                result.put("arguments", arguments);
                result.put("timestamp", java.time.Instant.now().toString());
                return ResponseEntity.ok(result);
            } else {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Failed to execute resource: " + response.getErrorMessage());
                return ResponseEntity.status(400).body(error);
            }
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Exception executing resource: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

}
