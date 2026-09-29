package com.usm.servicerequest.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityJwksProviderTest {

    private static final String JWKS_URI = "http://localhost:8001/.well-known/jwks.json";
    private static final String KID_1 = "key-1";
    private static final String KID_2 = "key-2";

    @Mock
    private RestTemplate restTemplate;

    private IdentityServiceProperties properties;
    private IdentityJwksProvider provider;

    private RSAKey rsaJwk1;
    private RSAKey rsaJwk2;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);

        KeyPair kp1 = gen.generateKeyPair();
        rsaJwk1 = new RSAKey.Builder((RSAPublicKey) kp1.getPublic())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID(KID_1)
                .build();

        KeyPair kp2 = gen.generateKeyPair();
        rsaJwk2 = new RSAKey.Builder((RSAPublicKey) kp2.getPublic())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID(KID_2)
                .build();

        properties = new IdentityServiceProperties();
        properties.setJwksUri(JWKS_URI);

        provider = new IdentityJwksProvider(restTemplate, properties);
    }

    @Test
    void getPublicKey_whenKeyInCache_returnsKeyWithoutHttpCall() throws Exception {
        JWKSet jwkSet = new JWKSet(rsaJwk1);
        provider.setCachedJwkSet(jwkSet);

        Optional<RSAPublicKey> key = provider.getPublicKey(KID_1);

        assertThat(key).isPresent();
        assertThat(key.get()).isEqualTo(rsaJwk1.toPublicKey());
        verify(restTemplate, times(0)).getForObject(JWKS_URI, String.class);
    }

    @Test
    void getPublicKey_whenNotCached_fetchesFromJwksUriAndReturnsKey() throws Exception {
        JWKSet jwkSet = new JWKSet(rsaJwk1);
        when(restTemplate.getForObject(JWKS_URI, String.class)).thenReturn(jwkSet.toString());

        Optional<RSAPublicKey> key = provider.getPublicKey(KID_1);

        assertThat(key).isPresent();
        assertThat(key.get()).isEqualTo(rsaJwk1.toPublicKey());
        verify(restTemplate, times(1)).getForObject(JWKS_URI, String.class);

        // Second call for the same kid should use cache
        Optional<RSAPublicKey> cachedKey = provider.getPublicKey(KID_1);
        assertThat(cachedKey).isPresent();
        verify(restTemplate, times(1)).getForObject(JWKS_URI, String.class);
    }

    @Test
    void getPublicKey_whenUnknownKid_refetchesOnce() throws Exception {
        // Initial cache has key 1
        JWKSet initialSet = new JWKSet(rsaJwk1);
        provider.setCachedJwkSet(initialSet);

        // Refreshed set includes key 2
        JWKSet updatedSet = new JWKSet(java.util.List.of(rsaJwk1, rsaJwk2));
        when(restTemplate.getForObject(JWKS_URI, String.class)).thenReturn(updatedSet.toString());

        Optional<RSAPublicKey> key = provider.getPublicKey(KID_2);

        assertThat(key).isPresent();
        assertThat(key.get()).isEqualTo(rsaJwk2.toPublicKey());
        verify(restTemplate, times(1)).getForObject(JWKS_URI, String.class);
    }

    @Test
    void getPublicKey_whenUnknownKidAndStillMissingAfterRefetch_returnsEmpty() {
        JWKSet set = new JWKSet(rsaJwk1);
        provider.setCachedJwkSet(set);
        when(restTemplate.getForObject(JWKS_URI, String.class)).thenReturn(set.toString());

        Optional<RSAPublicKey> key = provider.getPublicKey("non-existent-kid");

        assertThat(key).isEmpty();
        verify(restTemplate, times(1)).getForObject(JWKS_URI, String.class);
    }

    @Test
    void getPublicKey_whenHttpFetchFails_handlesGracefullyAndReturnsEmpty() {
        when(restTemplate.getForObject(JWKS_URI, String.class))
                .thenThrow(new RestClientException("Connection refused: connect"));

        Optional<RSAPublicKey> key = provider.getPublicKey(KID_1);

        assertThat(key).isEmpty();
    }

    @Test
    void getPublicKey_nullOrBlankKid_returnsEmpty() {
        assertThat(provider.getPublicKey(null)).isEmpty();
        assertThat(provider.getPublicKey("   ")).isEmpty();
    }
}
