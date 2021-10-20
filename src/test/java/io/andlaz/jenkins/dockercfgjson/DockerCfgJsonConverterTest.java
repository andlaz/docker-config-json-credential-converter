package io.andlaz.jenkins.dockercfgjson;

import com.cloudbees.jenkins.plugins.kubernetes_credentials_provider.CredentialsConvertionException;
import com.cloudbees.jenkins.plugins.kubernetes_credentials_provider.SecretUtils;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.andlaz.jenkins.dockercfgjson.DockerCfgJsonConverter.*;
import org.junit.Test;
import static org.junit.Assert.*;


import java.util.Collections;

import static org.mockito.Mockito.*;

public class DockerCfgJsonConverterTest {

    @Test
    public void test_can_convert_type_alias() {

        DockerCfgJsonConverter converter = new DockerCfgJsonConverter();

        assert (converter.canConvert(DockerCfgJsonConverter.TYPE_ALIAS) == true);
    }

    @Test(expected = NoDataKeyFieldOnSecretObjectException.class)
    public void test_convert_throws_for_secret_with_no_dockercfgjson_data_field() throws CredentialsConvertionException {

        DockerCfgJsonConverter converter = new DockerCfgJsonConverter();
        Secret secret = mock(Secret.class);
        when(secret.getData()).thenReturn(Collections.emptyMap());

        converter.convert(secret);


    }

    @Test(expected = DataKeyValueFailedToParseAsJsonException.class)
    public void test_convert_throws_for_secret_with_non_json_dockercfgjson_data_field() throws CredentialsConvertionException {

        DockerCfgJsonConverter converter = new DockerCfgJsonConverter();
        ObjectMeta secretMeta = mock(ObjectMeta.class);
        Secret secret = mock(Secret.class);
        when(secret.getMetadata()).thenReturn(secretMeta);
        when(secretMeta.getAnnotations()).thenReturn(null);
        when(secret.getData()).thenReturn(Collections.singletonMap(DockerCfgJsonConverter.DATA_KEY, "i'm totally json"));

        converter.convert(secret);

    }

    @Test(expected = DataKeyValueFailedToParseAsJsonException.class)
    public void test_convert_throws_for_secret_with_json_thats_not_a_dockercfgjson_representation_data_field() throws CredentialsConvertionException {

        DockerCfgJsonConverter converter = new DockerCfgJsonConverter();
        ObjectMeta secretMeta = mock(ObjectMeta.class);
        Secret secret = mock(Secret.class);
        when(secret.getMetadata()).thenReturn(secretMeta);
        when(secretMeta.getAnnotations()).thenReturn(null);
        when(secret.getData()).thenReturn(Collections.singletonMap(DockerCfgJsonConverter.DATA_KEY, "{\"foo\":\"bar\"}"));

        converter.convert(secret);

    }

    @Test(expected = MultipleOrNoRegistriesInConfigJsonException.class)
    public void test_convert_throws_for_secret_with_multiple_auths() throws CredentialsConvertionException {

        DockerCfgJsonConverter converter = new DockerCfgJsonConverter();
        ObjectMeta secretMeta = mock(ObjectMeta.class);
        Secret secret = mock(Secret.class);
        when(secret.getMetadata()).thenReturn(secretMeta);
        when(secretMeta.getAnnotations()).thenReturn(null);
        when(secret.getData()).thenReturn(Collections.singletonMap(DockerCfgJsonConverter.DATA_KEY,
                "{" +
                    "\"auths\": {" +
                        "\"foo\": {" +
                            "\"username\": \"some-user\"," +
                            "\"password\": \"some-password\"," +
                            "\"email\": \"someone@somewhere.tld\"," +
                            "\"auth\": \"c29tZS11c2VyOnNvbWUtcGFzc3dvcmQ=\"" +
                        "}, " +
                        "\"bar\": {" +
                            "\"username\": \"some-user\"," +
                            "\"password\": \"some-password\"," +
                            "\"email\": \"someone@somewhere.tld\"," +
                            "\"auth\": \"c29tZS11c2VyOnNvbWUtcGFzc3dvcmQ=\"" +
                        "}" +
                    "}" +
                "}"
                ));

        converter.convert(secret);

    }

    @Test
    public void test_convert_converts_well_formed_dockercfgjson_secret() throws CredentialsConvertionException {

        DockerCfgJsonConverter converter = new DockerCfgJsonConverter();

        ObjectMeta secretMeta = mock(ObjectMeta.class);
        Secret secret = mock(Secret.class);
        when(secret.getMetadata()).thenReturn(secretMeta);
        when(secretMeta.getName()).thenReturn("some-secret");
        when(secret.getData()).thenReturn(Collections.singletonMap(DockerCfgJsonConverter.DATA_KEY,
                "{" +
                        "\"auths\": {" +
                        "\"foo\": {" +
                        "\"username\": \"c29tZS11c2Vy\"," + // some-user
                        "\"password\": \"c29tZS1wYXNzd29yZA==\"," + // some-password
                        "\"email\": \"someone@somewhere.tld\"," +
                        "\"auth\": \"c29tZS11c2VyOnNvbWUtcGFzc3dvcmQ=\"" +
                        "}" +
                        "}" +
                "}"
        ));

        IdCredentials idCredential = converter.convert(secret);
        assertEquals(
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, SecretUtils.getCredentialId(secret), SecretUtils.getCredentialDescription(secret), "some-user", "some-password"),
                idCredential);

    }
}
