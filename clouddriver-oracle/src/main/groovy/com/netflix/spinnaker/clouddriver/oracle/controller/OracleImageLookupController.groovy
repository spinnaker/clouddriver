/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oracle.controller

import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.clouddriver.oracle.model.OracleImage
import com.netflix.spinnaker.clouddriver.oracle.provider.view.OracleImageProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

import java.util.regex.Pattern
import java.util.stream.Collectors

@Slf4j
@RestController
@RequestMapping("/oracle/images")
class OracleImageLookupController {

  final OracleImageProvider imageProvider

  @Autowired
  OracleImageLookupController(final OracleImageProvider imageProvider) {
    this.imageProvider = imageProvider
  }

  @RequestMapping(value = '/{account}/{region}/{imageId:.+}', method = RequestMethod.GET)
  List<OracleImage> getById(
    @PathVariable String account, @PathVariable String region, @PathVariable String imageId) {
    def images = imageProvider.getByAccountAndRegion(account, region)
    if (images) {
      return [images.find { it.id == imageId }] as List
    }
    return []
  }

  @RequestMapping(value = '/find', method = RequestMethod.GET)
  Set<OracleImage> find(@RequestParam(required = false) String account, @RequestParam(required = false) String q) {
    Set<OracleImage> results
    Set<OracleImage> images = imageProvider.getAll()
    if (!images) {
      results = Collections.emptySet()
    } else {
      results = images.stream()
        .collect(Collectors.toSet())
        .sort { OracleImage a, OracleImage b -> a.name <=> b.name }

      Pattern pattern = resolveQueryToPattern(q)
      log.info('filtering images using pattern {}', pattern)
      results = results.findAll { pattern.matcher(it.name).matches() }
    }

    return results
  }

  Pattern resolveQueryToPattern(String query) {
    String glob = query?.trim() ?: '*'
    // Wrap in '*' if there are no glob-style characters in the query string.
    if (!glob.contains('*') && !glob.contains('?') && !glob.contains('[') && !glob.contains('\\')) {
      glob = "*${glob}*"
    }
    return new InMemoryCache.Glob(glob).toPattern()
  }
}
