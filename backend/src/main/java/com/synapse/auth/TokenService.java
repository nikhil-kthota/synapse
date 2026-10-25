package com.synapse.auth;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.synapse.config.SynapseProperties;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final SynapseProperties properties;

    public TokenService(JwtEncoder encoder, SynapseProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public String issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("synapse")
            .issuedAt(now)
            .expiresAt(now.plus(properties.jwt().ttl()))
            .subject(String.valueOf(user.getId()))
            .claim("email", user.getEmail())
            .claim("name", user.getName())
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
