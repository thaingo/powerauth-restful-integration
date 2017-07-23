/*
 * PowerAuth integration libraries for RESTful API applications, examples and
 * related software components
 *
 * Copyright (C) 2017 Lime - HighTech Solutions s.r.o.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.getlime.security.powerauth.rest.api.base.provider;

import com.google.common.io.BaseEncoding;
import io.getlime.security.powerauth.crypto.lib.enums.PowerAuthSignatureTypes;
import io.getlime.security.powerauth.rest.api.base.authentication.PowerAuthApiAuthentication;
import io.getlime.security.powerauth.rest.api.base.exception.PowerAuthAuthenticationException;
import io.getlime.security.powerauth.rest.api.base.filter.PowerAuthRequestFilterBase;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract class for PowerAuth 2.0 authentication provider.
 *
 * @author Petr Dvorak, petr@lime-company.eu
 *
 */
public abstract class PowerAuthAuthenticationProviderBase {

    /**
     * Validate the signature from the PowerAuth 2.0 HTTP header against the provided HTTP method, request body and URI identifier.
     * Make sure to accept only allowed signatures.
     * @param httpMethod HTTP method (GET, POST, ...)
     * @param httpBody Body of the HTTP request.
     * @param requestUriIdentifier Request URI identifier.
     * @param httpAuthorizationHeader PowerAuth 2.0 HTTP authorization header.
     * @param allowedSignatureTypes Allowed types of the signature.
     * @return Instance of a PowerAuthApiAuthentication on successful authorization.
     * @throws PowerAuthAuthenticationException In case authorization fails, exception is raised.
     */
    public abstract PowerAuthApiAuthentication validateRequestSignature(
            String httpMethod,
            byte[] httpBody,
            String requestUriIdentifier,
            String httpAuthorizationHeader,
            List<PowerAuthSignatureTypes> allowedSignatureTypes
    ) throws PowerAuthAuthenticationException;

    /**
     * The same as {{@link #validateRequestSignature(String, byte[], String, String, List)} but uses default accepted signature type (2FA or 3FA).
     * @param httpMethod HTTP method (GET, POST, ...)
     * @param httpBody Request body
     * @param requestUriIdentifier Request URI identifier.
     * @param httpAuthorizationHeader PowerAuth 2.0 HTTP authorization header.
     * @return Instance of a PowerAuthApiAuthentication on successful authorization.
     * @throws PowerAuthAuthenticationException In case authorization fails, exception is raised.
     */
    public PowerAuthApiAuthentication validateRequestSignature(String httpMethod, byte[] httpBody, String requestUriIdentifier, String httpAuthorizationHeader) throws PowerAuthAuthenticationException {
        List<PowerAuthSignatureTypes> defaultAllowedSignatureTypes = new ArrayList<>();
        defaultAllowedSignatureTypes.add(PowerAuthSignatureTypes.POSSESSION_KNOWLEDGE);
        defaultAllowedSignatureTypes.add(PowerAuthSignatureTypes.POSSESSION_BIOMETRY);
        defaultAllowedSignatureTypes.add(PowerAuthSignatureTypes.POSSESSION_KNOWLEDGE_BIOMETRY);
        return this.validateRequestSignature(httpMethod, httpBody, requestUriIdentifier, httpAuthorizationHeader, defaultAllowedSignatureTypes);
    }

    /**
     * Validate a request signature, make sure only supported signature types are used.
     * @param servletRequest HTTPServletRequest with signed data.
     * @param requestUriIdentifier Request URI identifier.
     * @param httpAuthorizationHeader PowerAuth 2.0 HTTP authorization header.
     * @param allowedSignatureTypes Allowed types of signatures.
     * @return Instance of a PowerAuthApiAuthentication on successful authorization.
     * @throws PowerAuthAuthenticationException In case authorization fails, exception is raised.
     */
    public PowerAuthApiAuthentication validateRequestSignature(HttpServletRequest servletRequest, String requestUriIdentifier, String httpAuthorizationHeader, List<PowerAuthSignatureTypes> allowedSignatureTypes) throws PowerAuthAuthenticationException {
        // Get HTTP method and body bytes
        String requestMethod = servletRequest.getMethod().toUpperCase();
        String requestBodyString = ((String) servletRequest.getAttribute(PowerAuthRequestFilterBase.POWERAUTH_SIGNATURE_BASE_STRING));
        byte[] requestBodyBytes = requestBodyString == null ? null : BaseEncoding.base64().decode(requestBodyString);
        return this.validateRequestSignature(requestMethod, requestBodyBytes, requestUriIdentifier, httpAuthorizationHeader, allowedSignatureTypes);
    }

    /**
     * The same as {{@link #validateRequestSignature(HttpServletRequest, String, String, List)} but uses default accepted signature type (2FA or 3FA).
     * @param servletRequest HTTPServletRequest with signed data.
     * @param requestUriIdentifier Request URI identifier.
     * @param httpAuthorizationHeader PowerAuth 2.0 HTTP authorization header.
     * @return Instance of a PowerAuthApiAuthentication on successful authorization.
     * @throws PowerAuthAuthenticationException In case authorization fails, exception is raised.
     */
    public PowerAuthApiAuthentication validateRequestSignature(HttpServletRequest servletRequest, String requestUriIdentifier, String httpAuthorizationHeader) throws PowerAuthAuthenticationException {
        List<PowerAuthSignatureTypes> defaultAllowedSignatureTypes = new ArrayList<>();
        defaultAllowedSignatureTypes.add(PowerAuthSignatureTypes.POSSESSION_KNOWLEDGE);
        defaultAllowedSignatureTypes.add(PowerAuthSignatureTypes.POSSESSION_BIOMETRY);
        defaultAllowedSignatureTypes.add(PowerAuthSignatureTypes.POSSESSION_KNOWLEDGE_BIOMETRY);
        return this.validateRequestSignature(servletRequest, requestUriIdentifier, httpAuthorizationHeader, defaultAllowedSignatureTypes);
    }

}