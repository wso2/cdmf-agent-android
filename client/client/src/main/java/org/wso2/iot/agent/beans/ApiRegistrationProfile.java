/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 *
 */

package org.wso2.iot.agent.beans;


import org.wso2.iot.agent.AndroidAgentException;
import org.wso2.iot.agent.utils.CommonUtils;

/**
 * This class represents the data that are required to register
 * the oauth application.
 */
public class ApiRegistrationProfile {

    public String applicationName;
    public String tags[];
    public boolean isAllowedToAllDomains;
    public String consumerKey;
    public String consumerSecret;
    public boolean isMappingAnExistingOAuthApp;

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public boolean isAllowedToAllDomains() {
        return isAllowedToAllDomains;
    }

    public void setIsAllowedToAllDomains(boolean isAllowedToAllDomains) {
        this.isAllowedToAllDomains = isAllowedToAllDomains;
    }

    public boolean isMappingAnExistingOAuthApp() {
        return isMappingAnExistingOAuthApp;
    }

    public void setIsMappingAnExistingOAuthApp(boolean isMappingAnExistingOAuthApp) {
        this.isMappingAnExistingOAuthApp = isMappingAnExistingOAuthApp;
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    public String getConsumerSecret() {
        return consumerSecret;
    }

    public void setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
    }

    public String toJSON() throws AndroidAgentException {
        return CommonUtils.toJSON(this);
    }
}