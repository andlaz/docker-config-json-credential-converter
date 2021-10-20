A [kubernetes-credentials-provider-plugin](https://github.com/jenkinsci/kubernetes-credentials-provider-plugin) [SecretToCredentialConverter](https://github.com/jenkinsci/kubernetes-credentials-provider-plugin/blob/f5b51375593fe1712be0888e9d56580ca712e99f/src/main/java/com/cloudbees/jenkins/plugins/kubernetes_credentials_provider/SecretToCredentialConverter.java#L35) implementation that 
converts [dockerconfigjson type Secret objects](https://kubernetes.io/docs/concepts/configuration/secret/#docker-config-secrets) to `UsernamePassword` Jenkins credentials

Create a Secret with the `jenkins.io/credentials-type=dockerConfigJson` annotation

```bash
# requires
# https://github.com/kislyuk/yq

( DOCKER_USER=some-user; \
  DOCKER_TOKEN=some-token; \
  kubectl create secret docker-registry some-docker-secret \
    --docker-username=$DOCKER_USER \
    --docker-password=$DOCKER_TOKEN \
    --docker-email=someone@somewhere.tld \
    --dry-run=client -o yaml | \
    yq ".metadata.labels.\"jenkins.io/credentials-type\"=\"dockerConfigJson\"" | \
    yq ".metadata.annotations.\"jenkins.io/credentials-description\"=\"Some docker registry credentials\"" | \
    kubectl apply -f - )
```