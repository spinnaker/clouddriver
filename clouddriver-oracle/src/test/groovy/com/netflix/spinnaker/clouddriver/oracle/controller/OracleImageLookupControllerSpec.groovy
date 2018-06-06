/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.controller

import com.netflix.spinnaker.clouddriver.oracle.model.OracleImage
import com.netflix.spinnaker.clouddriver.oracle.provider.view.OracleImageProvider
import spock.lang.Specification

class OracleImageLookupControllerSpec extends Specification {

  def "test get image details by Id"() {
    setup:
    def imageProvider = Mock(OracleImageProvider)
    def imageLookupController = new OracleImageLookupController(imageProvider)
    def images = [
      new OracleImage(id: "ocid.image.123",
        name: "My Image",
        compatibleShapes: ["small"]),
      new OracleImage(id: "ocid.image.234",
        name: "My Other Image",
        compatibleShapes: ["small"])
    ]

    when:
    def results = imageLookupController.getById("DEFAULT", "AD1", "ocid.image.123")

    then:
    1 * imageProvider.getByAccountAndRegion("DEFAULT", "AD1") >> images
    results.size() == 1
    results.first().name == "My Image"
  }

  def "test find image by query"() {
    setup:
    def imageProvider = Mock(OracleImageProvider)
    def imageLookupController = new OracleImageLookupController(imageProvider)
    def images = [
      new OracleImage(id: "ocid.image.123",
        name: "foo 1",
        compatibleShapes: ["small"]),
      new OracleImage(id: "ocid.image.234",
        name: "foo 2",
        compatibleShapes: ["small"]),
      new OracleImage(id: "ocid.image.345",
        name: "bar 1",
        compatibleShapes: ["small"])
    ]

    when:
    def results = imageLookupController.find("DEFAULT", "foo")

    then:
    1 * imageProvider.getAll() >> images
    results.size() == 2
    results.contains(images[0])
    results.contains(images[1])
  }
}
