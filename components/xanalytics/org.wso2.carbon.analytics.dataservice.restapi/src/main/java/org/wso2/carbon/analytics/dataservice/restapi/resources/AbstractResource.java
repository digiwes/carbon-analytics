/**
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.analytics.dataservice.restapi.resources;

import org.wso2.carbon.analytics.dataservice.restapi.Constants;
import org.wso2.carbon.analytics.dataservice.restapi.beans.ResponseBean;

import javax.ws.rs.core.Response;

/**
 * The Abstract Class AbstractResource.
 */
public abstract class AbstractResource {

	/**
	 * Handle response.
	 * @param responseStatus
	 *            the response status
	 * @param message
	 *            the message
	 * @return the response
	 */
	public Response handleResponse(ResponseStatus responseStatus, String message) {
		Response response;
		switch (responseStatus) {
			case SUCCESS:
				ResponseBean success = getResponseMessage(Constants.Status.SUCCESS, message);
				response = Response.ok().entity(success).build();
				break;
			case FAILED:
				ResponseBean failed = getResponseMessage(Constants.Status.FAILED, message);
				response = Response.serverError().entity(failed).build();
				break;
			case INVALID:
				ResponseBean invalid = getResponseMessage(Constants.Status.FAILED, message);
				response = Response.status(400).entity(invalid).build();
				break;
			case FORBIDDEN:
				ResponseBean forbidden = getResponseMessage(Constants.Status.FAILED, message);
				response = Response.status(403).entity(forbidden).build();
				break;
			case NON_EXISTENT:
				ResponseBean nonExistent =
				                           getResponseMessage(Constants.Status.NON_EXISTENT,
				                                              message);
				response = Response.status(404).entity(nonExistent).build();
				break;
			default:
				response = Response.noContent().build();
				break;
		}
		return response;
	}

	/**
	 * Gets the response message.
	 * @param status
	 *            the status
	 * @param message
	 *            the message
	 * @return the response message
	 */
	private ResponseBean getResponseMessage(String status, String message) {
		ResponseBean standardResponse = new ResponseBean(status);
		if (message != null) {
			standardResponse.setMessage(message);
		}
		return standardResponse;
	}

	/**
	 * The Enum ResponseStatus.
	 */
	public enum ResponseStatus {

		/** The success. */
		SUCCESS,
		/** The failed. */
		FAILED,
		/** The invalid. */
		INVALID,
		/** The forbidden. */
		FORBIDDEN,
		/** The non existent. */
		NON_EXISTENT
	}

}
