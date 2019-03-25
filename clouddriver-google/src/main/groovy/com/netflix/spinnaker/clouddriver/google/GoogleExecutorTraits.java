package com.netflix.spinnaker.clouddriver.google;

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.batch.GoogleBatchRequest;
import com.netflix.spinnaker.clouddriver.google.security.AccountForClient;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * This class is syntactic sugar atop the static GoogleExecutor.
 * By making it a trait, we can wrap the calls with less in-line syntax.
 */
public interface GoogleExecutorTraits {
  Registry getRegistry();

  default <T> T timeExecuteBatch(GoogleBatchRequest googleBatchRequest, String batchContext, String... tags) throws IOException {
    return GoogleExecutor.timeExecuteBatch(getRegistry(), googleBatchRequest, batchContext, tags);
  }

  default <T> T timeExecute(AbstractGoogleClientRequest<T> request, String api, String... tags) throws IOException {
    String account = AccountForClient.getAccount(request.getAbstractGoogleClient());

    List<String> decoratedTags = Arrays.asList(tags);
    decoratedTags.add("account");
    decoratedTags.add(account);

    return GoogleExecutor.timeExecute(getRegistry(), request, "google.api", api, decoratedTags.toArray(new String[0]));
  }

  String TAG_BATCH_CONTEXT = GoogleExecutor.getTAG_BATCH_CONTEXT();
  String TAG_REGION = GoogleExecutor.getTAG_REGION();
  String TAG_SCOPE = GoogleExecutor.getTAG_SCOPE();
  String TAG_ZONE = GoogleExecutor.getTAG_ZONE();
  String SCOPE_GLOBAL = GoogleExecutor.getSCOPE_GLOBAL();
  String SCOPE_REGIONAL = GoogleExecutor.getSCOPE_REGIONAL();
  String SCOPE_ZONAL = GoogleExecutor.getSCOPE_ZONAL();
}
