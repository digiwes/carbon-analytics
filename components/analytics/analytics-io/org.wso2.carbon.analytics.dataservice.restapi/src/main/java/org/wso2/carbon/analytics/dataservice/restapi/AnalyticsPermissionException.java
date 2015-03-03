package org.wso2.carbon.analytics.dataservice.restapi;

/*
* Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

/**
 * This exception will be thrown during all permission related issues.
 */

public class AnalyticsPermissionException extends Exception {

    private static final long serialVersionUID = 1725014737056463576L;

    public AnalyticsPermissionException(String message) {
        super(message);
    }

    public AnalyticsPermissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
