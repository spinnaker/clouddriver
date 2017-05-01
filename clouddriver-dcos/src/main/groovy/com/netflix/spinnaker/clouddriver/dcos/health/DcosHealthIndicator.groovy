package com.netflix.spinnaker.clouddriver.dcos.health

import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.transform.InheritConstructors
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.bind.annotation.ResponseStatus

import java.util.concurrent.atomic.AtomicReference

class DcosHealthIndicator implements HealthIndicator {
  private final AccountCredentialsProvider accountCredentialsProvider
  private final DcosClientProvider dcosClientProvider
  private final AtomicReference<Exception> lastException = new AtomicReference<>(null)

  DcosHealthIndicator(AccountCredentialsProvider accountCredentialsProvider,
                       DcosClientProvider dcosClientProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider
    this.dcosClientProvider = dcosClientProvider
  }

  @Override
  Health health() {
    def ex = lastException.get()

    if (ex) {
      new Health.Builder().down().build()
    }

    new Health.Builder().up().build()
  }

  @Scheduled(fixedDelay = 300000L)
  void checkHealth() {
    try {
      Set<DcosCredentials> dcosCredentialsSet = accountCredentialsProvider.all.findAll {
        it instanceof DcosCredentials
      } as Set<DcosCredentials>

      for (DcosCredentials accountCredentials in dcosCredentialsSet) {
        String pong = dcosClientProvider.getDcosClient(accountCredentials).ping

        if ("pong" != pong) {
          throw new DcosIOException()
        }
      }

      lastException.set(null)
    } catch (Exception ex) {
      lastException.set(ex)
    }
  }

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Problem communicating with DCOS.")
  @InheritConstructors
  static class DcosIOException extends RuntimeException {}
}

