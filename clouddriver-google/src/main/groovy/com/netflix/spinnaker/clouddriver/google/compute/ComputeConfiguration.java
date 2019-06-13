package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ComputeConfiguration {

  public static final String BATCH_REQUEST_EXECUTOR = "batchRequestExecutor";

  @Bean
  @Qualifier(BATCH_REQUEST_EXECUTOR)
  public ListeningExecutorService batchRequestExecutor() {
    return MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
  }
}
