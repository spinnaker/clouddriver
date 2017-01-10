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
package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.netflix.spinnaker.clouddriver.google.GoogleExecutorTraits

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation

import org.springframework.beans.factory.annotation.Autowired
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public abstract class GoogleAtomicOperation<ResultType> implements AtomicOperation<ResultType>, GoogleExecutorTraits {
}
