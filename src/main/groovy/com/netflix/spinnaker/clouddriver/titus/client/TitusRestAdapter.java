/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.client;

import com.netflix.spinnaker.clouddriver.titus.client.model.*;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;
import java.util.Map;

public interface TitusRestAdapter {

    @GET("/api/v2/jobs/{jobId}")
    Call<Job> getJob(@Path("jobId") String jobId);

    @GET("/api/v2/jobs/{jobId}")
    Call<Object> getJobJson(@Path("jobId") String jobId);

    @POST("/api/v2/jobs")
    Call<SubmitJobResponse> submitJob(@Body JobDescription jobDescription);

    @PATCH("/api/v2/jobs/{jobId}")
    Call<Void> updateJob(@Path("jobId") String jobId, @Body Map<String, Object>jobAttributes);

    @POST("/api/v2/jobs/kill")
    Call<Void> killJob(@Body TerminateJobRequest terminateJobRequest);

    @GET("/api/v2/tasks/{taskId}")
    Call<Task> getTask(@Path("taskId") String taskId);

    @GET("/api/v2/tasks/{taskId}")
    Call<Object> getTaskJson(@Path("taskId") String taskId);

    @GET("/api/v2/jobs")
    Call<List<Job>> getJobsByType(@Query("type") String type);

    @GET("/api/v2/jobs")
    Call<List<Job>> getJobsByLabel(@Query(value="labels", encoded=true) String labels);

    @GET("/api/v2/jobs")
    Call<List<Job>> getJobsByApplication(@Query("appName") String application);

    @POST("/api/v2/tasks/kill")
    Call<Void> terminateTasksAndShrink(@Body TerminateTasksAndShrinkJobRequest terminateTasksAndShrinkJobRequest);

    @GET("/api/v2/logs/download/{taskId}")
    Call<Map> logsDownload(@Path("taskId") String taskId);

    @POST("/api/v2/jobs/setinstancecounts")
    Call<Void> resizeJob(@Body ResizeJobRequest resizeJob);

    @POST("/api/v2/jobs/setinservice")
    Call<Void> activateJob(@Body ActivateJobRequest activateJob);
}
