package gg.modl.backend.auth;

import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import gg.modl.backend.auth.data.WebAuthnChallenge;
import gg.modl.backend.auth.data.WebAuthnCredential;
import gg.modl.backend.database.DynamicMongoTemplateProvider;
import gg.modl.backend.server.data.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yubico.webauthn.data.exception.Base64UrlException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebAuthnService {
    private final DynamicMongoTemplateProvider mongoProvider;
    private final AuthConfiguration authConfiguration;

    private RelyingParty buildRelyingParty(Server server, MongoTemplate mongo) {
        String rpId = resolveRpId(server);
        Set<String> origins = resolveOrigins(server, rpId);
        CredentialRepositoryAdapter credRepo = new CredentialRepositoryAdapter(mongo);

        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder()
                        .id(rpId)
                        .name("modl")
                        .build())
                .credentialRepository(credRepo)
                .origins(origins)
                .attestationConveyancePreference(AttestationConveyancePreference.NONE)
                .build();
    }

    private String resolveRpId(Server server) {
        String domain = server.getCustomDomain();
        if (domain != null && domain.endsWith(".modl.gg")) {
            return "modl.gg";
        }
        if (domain != null && !domain.isBlank()) {
            return domain;
        }
        return "modl.gg";
    }

    private Set<String> resolveOrigins(Server server, String rpId) {
        Set<String> origins = new HashSet<>();
        if (authConfiguration.isDevelopmentMode()) {
            origins.add("http://localhost:3000");
            origins.add("http://localhost:5173");
        }
        String domain = server.getCustomDomain();
        if (domain != null && !domain.isBlank()) {
            origins.add("https://" + domain);
        }
        if (!rpId.equals(domain)) {
            origins.add("https://" + rpId);
        }
        return origins;
    }

    private ByteArray userHandle(String email) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return new ByteArray(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // --- Registration ---

    public StartRegistrationResult startRegistration(Server server, String email) {
        MongoTemplate mongo = getMongoTemplate(server);
        RelyingParty rp = buildRelyingParty(server, mongo);

        UserIdentity userIdentity = UserIdentity.builder()
                .name(email)
                .displayName(email)
                .id(userHandle(email))
                .build();

        PublicKeyCredentialCreationOptions options = rp.startRegistration(
                StartRegistrationOptions.builder()
                        .user(userIdentity)
                        .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                                .residentKey(ResidentKeyRequirement.PREFERRED)
                                .userVerification(UserVerificationRequirement.PREFERRED)
                                .build())
                        .build()
        );

        String challengeId = UUID.randomUUID().toString();
        try {
            WebAuthnChallenge challenge = new WebAuthnChallenge();
            challenge.setId(challengeId);
            challenge.setChallengeJson(options.toJson());
            challenge.setEmail(email.trim().toLowerCase(Locale.ROOT));
            challenge.setExpiresAt(new Date(System.currentTimeMillis() + 5 * 60 * 1000));
            mongo.save(challenge);

            return new StartRegistrationResult(challengeId, options.toCredentialsCreateJson());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize registration options", e);
        }
    }

    public void finishRegistration(Server server, String email, String challengeId, String responseJson, String credentialName) throws Exception {
        MongoTemplate mongo = getMongoTemplate(server);
        RelyingParty rp = buildRelyingParty(server, mongo);

        WebAuthnChallenge challenge = findAndDeleteChallenge(mongo, challengeId);
        if (challenge == null) {
            throw new IllegalStateException("Challenge not found or expired");
        }
        if (!email.trim().toLowerCase(Locale.ROOT).equals(challenge.getEmail())) {
            throw new IllegalStateException("Email mismatch");
        }

        PublicKeyCredentialCreationOptions options = PublicKeyCredentialCreationOptions.fromJson(challenge.getChallengeJson());
        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc =
                PublicKeyCredential.parseRegistrationResponseJson(responseJson);

        RegistrationResult result;
        try {
            result = rp.finishRegistration(
                    FinishRegistrationOptions.builder()
                            .request(options)
                            .response(pkc)
                            .build()
            );
        } catch (RegistrationFailedException e) {
            throw new IllegalStateException("Registration verification failed: " + e.getMessage(), e);
        }

        WebAuthnCredential cred = new WebAuthnCredential();
        cred.setEmail(email.trim().toLowerCase(Locale.ROOT));
        cred.setUserHandle(userHandle(email).getBase64Url());
        cred.setCredentialId(result.getKeyId().getId().getBase64Url());
        cred.setPublicKeyCose(result.getPublicKeyCose().getBytes());
        cred.setSignatureCount(result.getSignatureCount());
        cred.setName(credentialName != null && !credentialName.isBlank() ? credentialName.trim() : "Passkey");
        cred.setCreatedAt(new Date());
        cred.setLastUsedAt(new Date());
        mongo.save(cred);
    }

    // --- Authentication ---

    public boolean checkHasPasskeys(Server server, String email) {
        MongoTemplate mongo = getMongoTemplate(server);
        Query query = new Query(Criteria.where("email").is(email.trim().toLowerCase(Locale.ROOT)));
        return mongo.exists(query, WebAuthnCredential.class);
    }

    public StartAuthenticationResult startDiscoverableAuthentication(Server server) {
        MongoTemplate mongo = getMongoTemplate(server);
        RelyingParty rp = buildRelyingParty(server, mongo);

        AssertionRequest assertionRequest = rp.startAssertion(
                StartAssertionOptions.builder()
                        .userVerification(UserVerificationRequirement.PREFERRED)
                        .build()
        );

        String challengeId = UUID.randomUUID().toString();
        try {
            WebAuthnChallenge challenge = new WebAuthnChallenge();
            challenge.setId(challengeId);
            challenge.setChallengeJson(assertionRequest.toJson());
            challenge.setEmail(null);
            challenge.setExpiresAt(new Date(System.currentTimeMillis() + 5 * 60 * 1000));
            mongo.save(challenge);

            return new StartAuthenticationResult(challengeId, assertionRequest.toCredentialsGetJson(), true);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize assertion request", e);
        }
    }

    public StartAuthenticationResult startAuthentication(Server server, String email) {
        MongoTemplate mongo = getMongoTemplate(server);
        RelyingParty rp = buildRelyingParty(server, mongo);

        AssertionRequest assertionRequest = rp.startAssertion(
                StartAssertionOptions.builder()
                        .username(email.trim().toLowerCase(Locale.ROOT))
                        .userVerification(UserVerificationRequirement.PREFERRED)
                        .build()
        );

        String challengeId = UUID.randomUUID().toString();
        try {
            WebAuthnChallenge challenge = new WebAuthnChallenge();
            challenge.setId(challengeId);
            challenge.setChallengeJson(assertionRequest.toJson());
            challenge.setEmail(email.trim().toLowerCase(Locale.ROOT));
            challenge.setExpiresAt(new Date(System.currentTimeMillis() + 5 * 60 * 1000));
            mongo.save(challenge);

            return new StartAuthenticationResult(challengeId, assertionRequest.toCredentialsGetJson(), true);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize assertion request", e);
        }
    }

    public String finishAuthentication(Server server, String challengeId, String responseJson) throws Exception {
        MongoTemplate mongo = getMongoTemplate(server);
        RelyingParty rp = buildRelyingParty(server, mongo);

        WebAuthnChallenge challenge = findAndDeleteChallenge(mongo, challengeId);
        if (challenge == null) {
            throw new IllegalStateException("Challenge not found or expired");
        }

        AssertionRequest assertionRequest = AssertionRequest.fromJson(challenge.getChallengeJson());
        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc =
                PublicKeyCredential.parseAssertionResponseJson(responseJson);

        AssertionResult result;
        try {
            result = rp.finishAssertion(
                    FinishAssertionOptions.builder()
                            .request(assertionRequest)
                            .response(pkc)
                            .build()
            );
        } catch (AssertionFailedException e) {
            throw new IllegalStateException("Authentication verification failed: " + e.getMessage(), e);
        }

        if (!result.isSuccess()) {
            throw new IllegalStateException("Authentication failed");
        }

        // Update signature count and last used
        String credentialId = result.getCredential().getCredentialId().getBase64Url();
        Query updateQuery = new Query(Criteria.where("credentialId").is(credentialId));
        Update update = new Update()
                .set("signatureCount", result.getSignatureCount())
                .set("lastUsedAt", new Date());
        mongo.updateFirst(updateQuery, update, WebAuthnCredential.class);

        // Resolve email: from challenge (email-based flow) or from credential lookup (discoverable flow)
        String email = challenge.getEmail();
        if (email == null || email.isBlank()) {
            String userHandleBase64 = result.getCredential().getUserHandle().getBase64Url();
            Query uhQuery = new Query(Criteria.where("userHandle").is(userHandleBase64));
            WebAuthnCredential cred = mongo.findOne(uhQuery, WebAuthnCredential.class);
            if (cred == null) {
                throw new IllegalStateException("Could not determine user identity");
            }
            email = cred.getEmail();
        }
        return email;
    }

    // --- Credential management ---

    public List<CredentialInfo> listCredentials(Server server, String email) {
        MongoTemplate mongo = getMongoTemplate(server);
        Query query = new Query(Criteria.where("email").is(email.trim().toLowerCase(Locale.ROOT)));
        List<WebAuthnCredential> credentials = mongo.find(query, WebAuthnCredential.class);
        return credentials.stream()
                .map(c -> new CredentialInfo(c.getId(), c.getName(), c.getCreatedAt(), c.getLastUsedAt()))
                .collect(Collectors.toList());
    }

    public boolean renameCredential(Server server, String email, String credentialMongoId, String newName) {
        MongoTemplate mongo = getMongoTemplate(server);
        Query query = new Query(Criteria.where("_id").is(credentialMongoId)
                .and("email").is(email.trim().toLowerCase(Locale.ROOT)));
        Update update = new Update().set("name", newName.trim());
        return mongo.updateFirst(query, update, WebAuthnCredential.class).getModifiedCount() > 0;
    }

    public boolean deleteCredential(Server server, String email, String credentialMongoId) {
        MongoTemplate mongo = getMongoTemplate(server);
        Query query = new Query(Criteria.where("_id").is(credentialMongoId)
                .and("email").is(email.trim().toLowerCase(Locale.ROOT)));
        return mongo.remove(query, WebAuthnCredential.class).getDeletedCount() > 0;
    }

    // --- Helpers ---

    private MongoTemplate getMongoTemplate(Server server) {
        return mongoProvider.getFromDatabaseName(server.getDatabaseName());
    }

    private WebAuthnChallenge findAndDeleteChallenge(MongoTemplate mongo, String challengeId) {
        Query query = new Query(Criteria.where("_id").is(challengeId)
                .and("expiresAt").gt(new Date()));
        return mongo.findAndRemove(query, WebAuthnChallenge.class);
    }

    // --- CredentialRepository adapter ---

    private class CredentialRepositoryAdapter implements CredentialRepository {
        private final MongoTemplate mongo;

        CredentialRepositoryAdapter(MongoTemplate mongo) {
            this.mongo = mongo;
        }

        @Override
        public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
            Query query = new Query(Criteria.where("email").is(username.trim().toLowerCase(Locale.ROOT)));
            List<WebAuthnCredential> creds = mongo.find(query, WebAuthnCredential.class);
            return creds.stream()
                    .map(c -> {
                        try {
                            return PublicKeyCredentialDescriptor.builder()
                                    .id(ByteArray.fromBase64Url(c.getCredentialId()))
                                    .type(PublicKeyCredentialType.PUBLIC_KEY)
                                    .build();
                        } catch (Base64UrlException e) {
                            log.error("Invalid base64url credential ID for {}", c.getId(), e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        @Override
        public Optional<ByteArray> getUserHandleForUsername(String username) {
            return Optional.of(userHandle(username));
        }

        @Override
        public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
            String userHandleBase64 = userHandle.getBase64Url();
            Query query = new Query(Criteria.where("userHandle").is(userHandleBase64));
            WebAuthnCredential cred = mongo.findOne(query, WebAuthnCredential.class);
            if (cred == null) {
                return Optional.empty();
            }
            return Optional.of(cred.getEmail());
        }

        @Override
        public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
            String credIdBase64 = credentialId.getBase64Url();
            Query query = new Query(Criteria.where("credentialId").is(credIdBase64));
            WebAuthnCredential cred = mongo.findOne(query, WebAuthnCredential.class);
            if (cred == null) {
                return Optional.empty();
            }
            return Optional.of(RegisteredCredential.builder()
                    .credentialId(credentialId)
                    .userHandle(userHandle(cred.getEmail()))
                    .publicKeyCose(new ByteArray(cred.getPublicKeyCose()))
                    .signatureCount(cred.getSignatureCount())
                    .build());
        }

        @Override
        public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
            String credIdBase64 = credentialId.getBase64Url();
            Query query = new Query(Criteria.where("credentialId").is(credIdBase64));
            List<WebAuthnCredential> creds = mongo.find(query, WebAuthnCredential.class);
            return creds.stream()
                    .map(c -> RegisteredCredential.builder()
                            .credentialId(credentialId)
                            .userHandle(userHandle(c.getEmail()))
                            .publicKeyCose(new ByteArray(c.getPublicKeyCose()))
                            .signatureCount(c.getSignatureCount())
                            .build())
                    .collect(Collectors.toSet());
        }
    }

    // --- Result records ---

    public record StartRegistrationResult(String challengeId, String optionsJson) {}
    public record StartAuthenticationResult(String challengeId, String optionsJson, boolean hasPasskeys) {}
    public record CredentialInfo(String id, String name, Date createdAt, Date lastUsedAt) {}
}
