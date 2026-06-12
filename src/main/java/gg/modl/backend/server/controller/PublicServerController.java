package gg.modl.backend.server.controller;

import gg.modl.backend.infrastructure.rest.RESTMappingV1;
import gg.modl.proto.modl.v1.ServerAvailabilityRequest;
import gg.modl.proto.modl.v1.ServerAvailabilityResponse;
import gg.modl.proto.modl.v1.ServerRegisterRequest;
import gg.modl.proto.modl.v1.ServerRegisterResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RESTMappingV1.PUBLIC_SERVER)
public class PublicServerController {

    @PostMapping("/register")
    public ResponseEntity<ServerRegisterResponse> register(@RequestBody ServerRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.GONE).body(ServerRegisterResponse.newBuilder()
            .setSuccess(false)
            .setMessage("Use /v1/public/registration instead.")
            .build());
    }

    @PostMapping("/check-availability")
    public ResponseEntity<ServerAvailabilityResponse> checkAvailability(@RequestBody ServerAvailabilityRequest request) {
        return ResponseEntity.status(HttpStatus.GONE).body(ServerAvailabilityResponse.newBuilder()
            .setEmailAvailable(false)
            .setNameAvailable(false)
            .setSubdomainAvailable(false)
            .setMessage("Use /v1/public/registration/check-availability instead.")
            .build());
    }
}
