/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

KylinApp.factory('JobService', ['$resource', function ($resource, config) {
  return $resource(Config.service.url + 'jobs/:jobId/:propName/:propValue/:action', {}, {
    list: {method: 'GET', params: {}, isArray: true},
    get: {method: 'GET', params: {}, isArray: false},
    overview: {method: 'GET', params: {propName: 'overview'}, isArray: false},
    stepOutput: {method: 'GET', params: {propName: 'steps', action: 'output'}, isArray: false},
    resume: {method: 'PUT', params: {action: 'resume'}, isArray: false},
    cancel: {method: 'PUT', params: {action: 'cancel'}, isArray: false},
    pause: {method: 'PUT', params: {action: 'pause'}, isArray: false},
    drop: {method: 'DELETE', params: {action: 'drop'}, isArray: false},
    resubmit: {method: 'PUT', params: {action: 'resubmit'}, isArray: false}
  });
}]);
