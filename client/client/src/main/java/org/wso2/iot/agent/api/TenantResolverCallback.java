/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.iot.agent.api;

import com.android.volley.VolleyError;

import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.beans.Tenant;

import java.util.List;

/**
 * Callback interface Handles call backs to tenant resolving requests.
 */
public interface TenantResolverCallback {
    /**
     * When the tenant resolving is completed, this can be called to notify the caller.
     * @param tenants List of tenants.
     */
    void onTenantResolved(List<Tenant> tenants);

    /**
     * When authentication success.
     */
    void onAuthenticationSuccess();

    /**
     * When authentication failed.
     */
    void onAuthenticationFail();

    /**
     * On failure.
     * @param exception Cause of the failure.
     */
    void onFailure(AndroidAgentException exception);
}
