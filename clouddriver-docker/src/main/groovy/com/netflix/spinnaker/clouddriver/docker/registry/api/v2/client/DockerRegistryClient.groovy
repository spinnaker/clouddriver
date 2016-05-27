/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client

import com.google.gson.GsonBuilder
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth.DockerBearerToken
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth.DockerBearerTokenService
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.exception.DockerRegistryOperationException
import com.squareup.okhttp.OkHttpClient
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import retrofit.RestAdapter
import retrofit.RetrofitError
import retrofit.client.OkClient
import retrofit.client.Response
import retrofit.converter.GsonConverter
import retrofit.http.*

import java.util.concurrent.TimeUnit

@Slf4j
class DockerRegistryClient {
  private DockerBearerTokenService tokenService

  public String address
  private DockerRegistryService registryService
  private GsonConverter converter
  private String basicAuth

  @Autowired
  String dockerApplicationName

  final int paginateSize

  public getBasicAuth() {
    return basicAuth
  }

  DockerRegistryClient(String address, String email, String username, String password, long clientTimeoutMillis, int paginateSize) {
    this.paginateSize = paginateSize
    this.tokenService = new DockerBearerTokenService(username, password)
    this.basicAuth = this.tokenService.basicAuth
    OkHttpClient client = new OkHttpClient()
    client.setReadTimeout(clientTimeoutMillis, TimeUnit.MILLISECONDS)
    this.registryService = new RestAdapter.Builder()
      .setEndpoint(address)
      .setClient(new OkClient(client))
      .setLogLevel(RestAdapter.LogLevel.NONE)
      .build()
      .create(DockerRegistryService)
    this.converter = new GsonConverter(new GsonBuilder().create())
    this.address = address
  }

  interface DockerRegistryService {
    @GET("/v2/{repository}/tags/list")
    @Headers([
      "Docker-Distribution-API-Version: registry/2.0"
    ])
    Response getTags(@Path(value="repository", encode=false) String repository, @Header("Authorization") String token, @Header("User-Agent") String agent)

    @GET("/v2/{name}/manifests/{reference}")
    @Headers([
      "Docker-Distribution-API-Version: registry/2.0"
    ])
    Response getManifest(@Path(value="name", encode=false) String name, @Path(value="reference", encode=false) String reference, @Header("Authorization") String token, @Header("User-Agent") String agent)

    @GET("/v2/_catalog")
    @Headers([
        "Docker-Distribution-API-Version: registry/2.0"
    ])
    Response getCatalog(@Query(value="n") int paginateSize, @Header("Authorization") String token, @Header("User-Agent") String agent)

    @GET("/{path}")
    @Headers([
        "Docker-Distribution-API-Version: registry/2.0"
    ])
    Response get(@Path(value="path", encode=false) String path, @Header("Authorization") String token, @Header("User-Agent") String agent)

    @GET("/v2/")
    @Headers([
      "User-Agent: Spinnaker-Clouddriver",
      "Docker-Distribution-API-Version: registry/2.0"
    ])
    Response checkVersion(@Header("Authorization") String token, @Header("User-Agent") String agent)
  }

  public DockerRegistryTags getTags(String repository) {
    def response = request({
      registryService.getTags(repository, tokenService.basicAuthHeader, dockerApplicationName)
    }, { token ->
      registryService.getTags(repository, token, dockerApplicationName)
    }, repository)

    (DockerRegistryTags) converter.fromBody(response.body, DockerRegistryTags)
  }

  public String getDigest(String name, String tag) {
    def response = request({
      registryService.getManifest(name, tag, tokenService.basicAuthHeader, dockerApplicationName)
    }, { token ->
      registryService.getManifest(name, tag, token, dockerApplicationName)
    }, name)

    def headers = response.headers

    def digest = headers?.find {
      it.name == "Docker-Content-Digest"
    }

    return digest?.value
  }

  private static String parseLink(retrofit.client.Header header) {
    if (!header.name.equalsIgnoreCase("link")) {
      return null
    }

    def links = header.value.split(";").collect { it.trim() }

    if (!(links.findAll { String tok ->
      tok.replace(" ", "").equalsIgnoreCase("rel=\"next\"")
    })) {
      return null
    }

    def path = links.find { String tok ->
      tok && tok.getAt(0) == "<" && tok.getAt(tok.length() - 1) == ">"
    }

    return path?.substring(1, path.length() - 1)
  }

  private static String findNextLink(List<retrofit.client.Header> headers) {
    if (!headers) {
      return null
    }

    def paths = headers.collect { header ->
      parseLink(header)
    }.findAll { it }

    // We are at the end of the pagination.
    if (!paths || paths.size() == 0) {
      return null
    } else if (paths.size() > 1) {
      throw new DockerRegistryOperationException("Ambiguous number of Link headers provided, the following paths were identified: $paths")
    }

    return paths[0]
  }

  /*
   * This method will get all repositories available on this registry. It may fail, as some registries
   * don't want you to download their whole catalog (it's potentially a lot of data).
   */
  public DockerRegistryCatalog getCatalog(String path = null) {
    def response
    try {
      response = request({
        path ? registryService.get(path, tokenService.basicAuthHeader, dockerApplicationName) :
          registryService.getCatalog(paginateSize, tokenService.basicAuthHeader, dockerApplicationName)
      }, { token ->
        path ? registryService.get(path, token, dockerApplicationName) :
          registryService.getCatalog(paginateSize, token, dockerApplicationName)
      }, "_catalog")
    } catch (Exception e) {
      log.warn("Error encountered during catalog of $path", e)
      return new DockerRegistryCatalog(repositories: [])
    }

    def nextPath = findNextLink(response?.headers)
    def catalog = (DockerRegistryCatalog) converter.fromBody(response.body, DockerRegistryCatalog)

    if (nextPath) {
      def nextCatalog = getCatalog(nextPath)
      catalog.repositories.addAll(nextCatalog.repositories)
    }

    return catalog
  }

  /*
   * This method will hit the /v2/ endpoint of the configured docker registry. If it this endpoint is up,
   * it will return silently. Otherwise, an exception is thrown detailing why the endpoint isn't available.
   */
  public void checkV2Availability() {
    request({
      registryService.checkVersion(tokenService.basicAuthHeader, dockerApplicationName)
    }, { token ->
      registryService.checkVersion(token, dockerApplicationName)
    }, "v2 version check")

    // Placate the linter (otherwise it expects to return the result of `request()`)
    null
  }

  /*
   * Implements token request flow described here https://docs.docker.com/registry/spec/auth/token/
   * The tokenService also caches tokens for us, so it will attempt to use an old token before retrying.
   */
  public Response request(Closure<Response> withoutToken, Closure<Response> withToken, String target) {
    DockerBearerToken dockerToken = tokenService.getToken(target)
    String token
    if (dockerToken) {
      token = "Bearer ${dockerToken.bearer_token ?: dockerToken.token}"
    }

    Response response
    try {
      if (token) {
        response = withToken(token)
      } else {
        response = withoutToken()
      }
    } catch (RetrofitError error) {
      if (error.response?.status == 401) {
        dockerToken = tokenService.getToken(target, error.response.headers)
        token = "Bearer ${dockerToken.bearer_token ?: dockerToken.token}"
        response = withToken(token)
      } else {
        throw error
      }
    }

    return response
  }
}
