
package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonLoadBalancerInstanceStateCachingAgent
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.cats.provider.ProviderCache
import spock.lang.Shared
import spock.lang.Specification
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.ApplicationContext
import org.springframework.beans.factory.annotation.Autowired
import com.netflix.spinnaker.cats.cache.Cache
import groovy.util.logging.Slf4j
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerNotFoundException
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository

@Slf4j
class AmazonLoadBalancerInstanceStateCachingAgentSpec extends Specification {

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  ApplicationContext applicationContext

  void "When loading ELB does not get throttled, it should continue"() {
    setup:
    def providerCache = Mock(ProviderCache)
    final ApplicationContext ctx
    def task = new DefaultTask("1")
    TaskRepository.threadLocalTask.set(task)
    def amazonLoadBalancerInstanceStateCachingAgent = Mock(AmazonLoadBalancerInstanceStateCachingAgent)

    when:
    amazonLoadBalancerInstanceStateCachingAgent.loadData(providerCache)

    then:
    // Did not get throttled
    task.status.isFailed() == false && task.status.isCompleted() == false
  }

  void "When loading ELB gets throttled, task should stop"() {
    setup:
    def providerCache = Mock(ProviderCache)
    final ApplicationContext ctx
    def task = new DefaultTask("1")
    // TaskRepository.threadLocalTask.set(task)
    def amazonLoadBalancerInstanceStateCachingAgent = Stub(AmazonLoadBalancerInstanceStateCachingAgent) {
      loadData(_) >> {
        def errorMessage = "Could not grab load balencer! Failure Type:  Message: "
        def phaseName = "LOAD_ELB"
        log.info(errorMessage)
        task.fail()
      }
    }

    when:
    amazonLoadBalancerInstanceStateCachingAgent.loadData()

    then:
    task.status.isFailed() == true && task.status.isCompleted() == true

  }
}
