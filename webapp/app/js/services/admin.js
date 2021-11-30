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

KylinApp.factory('AdminService', ['$resource', function ($resource, config) {
  return $resource(Config.service.url + 'admin/:action', {}, {
    env: {method: 'GET', params: {action: 'env'}, isArray: false},
    config: {method: 'GET', params: {action: 'config'}, isArray: false},
    publicConfig: {method: 'GET', params: {action: 'public_config'}, isArray: false},
    cleanStorage: {method: 'DELETE', params: {action: 'storage'}, isArray: false},
    updateConfig: {method: 'PUT', params: {action: 'config'}, isArray: false},
    getVersionInfo: {method: 'GET', params: {action: 'version'}, isArray: false},
    openSparderUrl: {method: 'GET', params: {action: 'sparder_url'}, isArray: false},
  });
}]);
