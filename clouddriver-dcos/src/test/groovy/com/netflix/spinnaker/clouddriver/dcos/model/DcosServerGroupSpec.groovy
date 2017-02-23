package com.netflix.spinnaker.clouddriver.dcos.model

import mesosphere.marathon.client.model.v2.App
import spock.lang.Specification
import spock.lang.Subject


class DcosServerGroupSpec extends Specification {
  private static final String ACCOUNT = "testAccount"
  static final private String APP = "testApp"
  static final private String SERVER_GROUP_NAME = "${APP}-v000"
  static final private String REGION = "region"
  static final private String MARATHON_APP = "/${ACCOUNT}/${REGION}/${SERVER_GROUP_NAME}"

  def setup() {
  }

  void "load balancers associated to the server group that don't have the same account won't be populated"() {
    setup:
    def lbApp = createApp(MARATHON_APP)
    def goodLb = "${APP}-goodlb"
    def badLb = "${APP}-badlb"
    lbApp.getLabels() >> ["HAPROXY_GROUP": "${ACCOUNT}_${goodLb},wrongAccount_${badLb}"]

    when:
    @Subject def serverGroup = new DcosServerGroup(ACCOUNT, lbApp)

    then:
    serverGroup.loadBalancers.size() == 1
    serverGroup.loadBalancers.first() == goodLb.toString()
  }

  // TODO build info

  def createApp(id) {
    Stub(App) {
      getId() >> id
    }
  }
}