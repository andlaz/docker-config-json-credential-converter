package io.andlaz.jenkins.dockercfgjson;

import com.cloudbees.jenkins.plugins.kubernetes_credentials_provider.CredentialsConvertionException;
import com.cloudbees.jenkins.plugins.kubernetes_credentials_provider.SecretToCredentialConverter;
import com.cloudbees.jenkins.plugins.kubernetes_credentials_provider.SecretUtils;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import io.fabric8.kubernetes.api.model.Secret;

import javax.mail.Part;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Extension
public class DockerCfgJsonConverter extends SecretToCredentialConverter {

    public DockerCfgJsonConverter() {
        mapper = new ObjectMapper().registerModule(new ParameterNamesModule());
    }

    public static final String TYPE_ALIAS = "dockerConfigJson";
    public static final String DATA_KEY = ".dockerconfigjson";

    public static final class PartialDockerConfiguration {

        @JsonCreator
        public PartialDockerConfiguration(Map<String, Auth> auths) {
            this.auths = auths;
        }

        public static class Auth {

            private final String username;
            private final String password;
            private final String email;
            private final String auth;

            @JsonCreator
            public Auth(@JsonProperty("username") String username, @JsonProperty("password") String password, @JsonProperty("email") String email, @JsonProperty("auth") String auth) {
                this.username = username;
                this.password = password;
                this.email = email;
                this.auth = auth;
            }

            public String getUsername() {
                return username;
            }

            public String getPassword() {
                return password;
            }

            public String getEmail() {
                return email;
            }

            public String getAuth() {
                return auth;
            }
        }

        private final Map<String, Auth> auths;

        public Map<String, Auth> getAuths() {
            return auths;
        }
    }

    public static final class NoDataKeyFieldOnSecretObjectException extends CredentialsConvertionException {

        public NoDataKeyFieldOnSecretObjectException(String message) {
            super(message);
        }
    }

    public static final class DataKeyValueFailedToParseAsJsonException extends CredentialsConvertionException {

        public DataKeyValueFailedToParseAsJsonException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }

    public static final class MultipleOrNoRegistriesInConfigJsonException extends CredentialsConvertionException {

        public MultipleOrNoRegistriesInConfigJsonException(String message) {
            super(message);
        }
    }

    private final ObjectMapper mapper;

    @Override
    public boolean canConvert(String s) {
        return s.equals(TYPE_ALIAS);
    }

    @Override
    public IdCredentials convert(Secret secret) throws CredentialsConvertionException {
        if (secret.getData().containsKey(DATA_KEY)) {
            try {
                PartialDockerConfiguration dockerConfiguration = mapper.readValue(SecretUtils.getNonNullSecretData(secret, DATA_KEY, ".dockercfgjson field is missing"), PartialDockerConfiguration.class);

                if (dockerConfiguration.auths.size() == 1) {
                    // safe because of check above
                    PartialDockerConfiguration.Auth auth = dockerConfiguration.getAuths().entrySet().stream().map(e -> e.getValue()).findFirst().get();
                    return new UsernamePasswordCredentialsImpl(
                            CredentialsScope.GLOBAL,
                            SecretUtils.getCredentialId(secret),
                            SecretUtils.getCredentialDescription(secret),
                            decodeBase64(auth.getUsername(), "Not a valid username"),
                            decodeBase64(auth.getPassword(), "Not a valid password")
                    );

                } else {
                    throw new MultipleOrNoRegistriesInConfigJsonException(String.format("Json object on data key %1$s, on %2$s does not contain exactly one entry", DATA_KEY, secret));
                }

            } catch (JsonProcessingException e) {
                throw new DataKeyValueFailedToParseAsJsonException(String.format("Data key %1$s on %2$s failed to parse as JSON", DATA_KEY, secret), e);
            }
        } else {
            throw new NoDataKeyFieldOnSecretObjectException(String.format("Couldn't find data key %1$s on %2$s", DATA_KEY, secret));
        }

    }

    private String decodeBase64(String base64, String errorMessage) throws CredentialsConvertionException {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes != null) {
                CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
                decoder.onMalformedInput(CodingErrorAction.REPORT);
                decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
                CharBuffer decode = decoder.decode(ByteBuffer.wrap(bytes));
                return SecretUtils.requireNonNull(decode.toString(), errorMessage);
            } else {
                throw new CredentialsConvertionException(errorMessage);
            }

        } catch (CharacterCodingException e) {
            throw new CredentialsConvertionException(errorMessage, e);
        }
    }
}
