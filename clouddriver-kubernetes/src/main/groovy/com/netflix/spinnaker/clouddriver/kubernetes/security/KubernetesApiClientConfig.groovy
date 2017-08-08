package com.netflix.spinnaker.clouddriver.kubernetes.security

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.SSLUtils;

import groovy.util.logging.Slf4j

import javax.net.ssl.KeyManager;

@Slf4j
public class KubernetesApiClientConfig extends Config {
  String kubeconfigFile
  Boolean serviceAccount
  String context
  String cluster
  String user
  List<String> namespaces


  public KubernetesApiClientConfig(String kubeconfigFile, String context, String cluster, String user, List<String> namespaces, Boolean serviceAccount) {
    this.kubeconfigFile = kubeconfigFile
    this.serviceAccount = serviceAccount
    this.context = context
    this.cluster = cluster
    this.user = user
    this.namespaces = namespaces
  }

  public ApiClient getApiCient() throws Exception {
    if (kubeconfigFile == null) {
      return Config.defaultClient()
    } else {
      return parseConfig(kubeconfigFile)
    }
  }

  public ApiClient parseConfig(String fileName) throws IOException {
    return this.parseConfig((Reader)(new FileReader(fileName)));
  }

  public ApiClient parseConfig(InputStream stream) {
    return parseConfig((Reader)(new InputStreamReader(stream)));
  }

  public ApiClient parseConfig(Reader input) {
    KubeConfig config = KubeConfig.loadKubeConfig(input);
    ApiClient client = new ApiClient();
    client.setBasePath(config.getServer());

    if (!context && !config.currentContext.get("user")) {
      throw new IllegalArgumentException("Context $context was not found in $kubeconfigFile".toString())
    }
    context = context ?: config.currentContext.get("user")
    //FIXME: I haven't handled namespace, apiVersion, noProxy, and additional fields yet.  These fields are
    // available in io.fabric8.kubernetes.api.model.Context but not k8s java ApiClient
    cluster = cluster ?: config.currentContext.get("cluster")
    user = user ?: config.currentUser.get("user")

    //Copy from io.kubernetes.client.util.Config for any additional implementation changes
    try {
      KeyManager[] mgrs = SSLUtils.keyManagers(
        config.getClientCertificateData(),
        config.getClientCertificateFile(),
        config.getClientKeyData(),
        config.getClientKeyFile(),
        "RSA", "",
        null, null);
      client.setKeyManagers(mgrs);
    } catch (Exception ex) {
      log.error "Failed to invoke build key managers ${ex}"
    }

    if (config.verifySSL()) {
      // It's silly to have to do it in this order, but each SSL setup
      // consumes the CA cert, so if we do this before the client certs
      // are injected the cert input stream is exhausted and things get
      // grumpy'
      String caCert = config.getCertificateAuthorityData();
      String caCertFile = config.getCertificateAuthorityFile();
      if (caCert != null || caCertFile != null) {
        try {
          client.setSslCaCert(SSLUtils.getInputStreamFromDataOrFile(caCert, caCertFile));
        } catch (FileNotFoundException ex) {
          log.error "Failed to find CA Cert file ${ex}"
        }
      }
    } else {
      client.setVerifyingSsl(false);
    }

    String token = config.getAccessToken();
    if (token != null) {
      // This is kind of a hack, except not, because I don't think we actually
      // want to use oauth here.
      client.setApiKey("Bearer " + token);
    }

    String username = config.getUsername();
    if (username != null) {
      client.setUsername(username);
    }

    String password = config.getPassword();
    if (password != null) {
      client.setPassword(password);
    }

    return client;
  }
}
