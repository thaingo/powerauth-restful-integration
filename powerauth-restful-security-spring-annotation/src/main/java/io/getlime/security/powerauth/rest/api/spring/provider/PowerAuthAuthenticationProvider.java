/*
 * PowerAuth integration libraries for RESTful API applications, examples and
 * related software components
 *
 * Copyright (C) 2018 Wultra s.r.o.
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
package io.getlime.security.powerauth.rest.api.spring.provider;

import com.google.common.io.BaseEncoding;
import io.getlime.powerauth.soap.v3.*;
import io.getlime.security.powerauth.crypto.lib.enums.PowerAuthSignatureTypes;
import io.getlime.security.powerauth.http.PowerAuthHttpBody;
import io.getlime.security.powerauth.http.PowerAuthHttpHeader;
import io.getlime.security.powerauth.http.PowerAuthSignatureHttpHeader;
import io.getlime.security.powerauth.http.PowerAuthTokenHttpHeader;
import io.getlime.security.powerauth.http.validator.InvalidPowerAuthHttpHeaderException;
import io.getlime.security.powerauth.http.validator.PowerAuthSignatureHttpHeaderValidator;
import io.getlime.security.powerauth.http.validator.PowerAuthTokenHttpHeaderValidator;
import io.getlime.security.powerauth.rest.api.base.application.PowerAuthApplicationConfiguration;
import io.getlime.security.powerauth.rest.api.base.authentication.PowerAuthApiAuthentication;
import io.getlime.security.powerauth.rest.api.base.exception.PowerAuthAuthenticationException;
import io.getlime.security.powerauth.rest.api.base.provider.PowerAuthAuthenticationProviderBase;
import io.getlime.security.powerauth.rest.api.spring.authentication.PowerAuthApiAuthenticationImpl;
import io.getlime.security.powerauth.rest.api.spring.authentication.PowerAuthSignatureAuthenticationImpl;
import io.getlime.security.powerauth.rest.api.spring.authentication.PowerAuthTokenAuthenticationImpl;
import io.getlime.security.powerauth.rest.api.spring.converter.v3.SignatureTypeConverter;
import io.getlime.security.powerauth.soap.spring.client.PowerAuthServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Implementation of PowerAuth authentication provider.
 *
 * @author Petr Dvorak, petr@wultra.com
 *
 */
@Component
public class PowerAuthAuthenticationProvider extends PowerAuthAuthenticationProviderBase {

    private static final Logger logger = LoggerFactory.getLogger(PowerAuthAuthenticationProvider.class);

    private PowerAuthServiceClient powerAuthClient;

    private PowerAuthApplicationConfiguration applicationConfiguration;

    @Autowired
    public void setPowerAuthClient(PowerAuthServiceClient powerAuthClient) {
        this.powerAuthClient = powerAuthClient;
    }

    @Autowired(required=false)
    public void setApplicationConfiguration(PowerAuthApplicationConfiguration applicationConfiguration) {
        this.applicationConfiguration = applicationConfiguration;
    }

    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        // Handle signature based authentications
        if (authentication instanceof PowerAuthSignatureAuthenticationImpl) {
            return validateSignatureAuthentication((PowerAuthSignatureAuthenticationImpl) authentication);
        }
        // Handle basic token-based authentications
        else if (authentication instanceof PowerAuthTokenAuthenticationImpl) {
            return validateTokenAuthentication((PowerAuthTokenAuthenticationImpl) authentication);
        }
        // Return null in case unknown authentication type is provided
        return null;
    }

    /**
     * Validate signature based authentication.
     *
     * @param authentication Signature based authentication object.
     * @return API authentication object in case of successful authentication, null otherwise.
     */
    private PowerAuthApiAuthenticationImpl validateSignatureAuthentication(PowerAuthSignatureAuthenticationImpl authentication) {

        if (authentication.getSignatureType() != null) {

            SignatureTypeConverter converter = new SignatureTypeConverter();
            final SignatureType signatureType = converter.convertFrom(authentication.getSignatureType());

            VerifySignatureRequest soapRequest = new VerifySignatureRequest();
            soapRequest.setActivationId(authentication.getActivationId());
            soapRequest.setApplicationKey(authentication.getApplicationKey());
            soapRequest.setSignature(authentication.getSignature());
            soapRequest.setSignatureType(signatureType);
            soapRequest.setSignatureVersion(authentication.getVersion());
            soapRequest.setData(PowerAuthHttpBody.getSignatureBaseString(
                    authentication.getHttpMethod(),
                    authentication.getRequestUri(),
                    authentication.getNonce(),
                    authentication.getData()
            ));

            // In case forced signature version is specified, use it in the SOAP request.
            // This occurs when verifying signature during upgrade before upgrade is committed.
            if (authentication.getForcedSignatureVersion() != null) {
                soapRequest.setForcedSignatureVersion(authentication.getForcedSignatureVersion().longValue());
            }

            VerifySignatureResponse soapResponse = powerAuthClient.verifySignature(soapRequest);

            if (soapResponse.isSignatureValid()) {
                return copyAuthenticationAttributes(soapResponse.getActivationId(), soapResponse.getUserId(),
                        soapResponse.getApplicationId(), PowerAuthSignatureTypes.getEnumFromString(soapResponse.getSignatureType().value()),
                        authentication.getVersion(), authentication.getHttpHeader());
            } else {
                return null;
            }

        } else {
            return null;
        }
    }

    /**
     * Validate basic token-based authentication.
     *
     * @param authentication Token based authentication object.
     * @return API authentication object in case of successful authentication, null otherwise.
     */
    private PowerAuthApiAuthenticationImpl validateTokenAuthentication(PowerAuthTokenAuthenticationImpl authentication) {

        ValidateTokenRequest soapRequest = new ValidateTokenRequest();
        soapRequest.setTokenId(authentication.getTokenId());
        soapRequest.setTokenDigest(authentication.getTokenDigest());
        soapRequest.setNonce(authentication.getNonce());
        soapRequest.setTimestamp(Long.valueOf(authentication.getTimestamp()));

        try {
            final ValidateTokenResponse soapResponse = powerAuthClient.validateToken(soapRequest);
            if (soapResponse.isTokenValid()) {
                return copyAuthenticationAttributes(soapResponse.getActivationId(), soapResponse.getUserId(),
                        soapResponse.getApplicationId(), PowerAuthSignatureTypes.getEnumFromString(soapResponse.getSignatureType().value()),
                        authentication.getVersion(), authentication.getHttpHeader());
            } else {
                return null;
            }
        } catch (Exception e) {
            logger.warn("Token validation failed", e);
            return null;
        }
    }

    /**
     * Prepare API initialized authentication object with provided authentication attributes.
     * @param activationId Activation ID.
     * @param userId User ID.
     * @param applicationId Application ID.
     * @param signatureType Signature Type.
     * @return Initialized instance of API authentication.
     */
    private PowerAuthApiAuthenticationImpl copyAuthenticationAttributes(String activationId, String userId, Long applicationId, PowerAuthSignatureTypes signatureType, String version, PowerAuthHttpHeader httpHeader) {
        PowerAuthApiAuthenticationImpl apiAuthentication = new PowerAuthApiAuthenticationImpl();
        apiAuthentication.setActivationId(activationId);
        apiAuthentication.setUserId(userId);
        apiAuthentication.setApplicationId(applicationId);
        apiAuthentication.setSignatureFactors(signatureType);
        apiAuthentication.setAuthenticated(true);
        apiAuthentication.setVersion(version);
        apiAuthentication.setHttpHeader(httpHeader);
        return apiAuthentication;
    }

    /**
     * Validate the signature from the PowerAuth HTTP header against the provided HTTP method, request body and URI identifier.
     * Make sure to accept only allowed signatures.
     * @param httpMethod HTTP method (GET, POST, ...)
     * @param httpBody Body of the HTTP request.
     * @param requestUriIdentifier Request URI identifier.
     * @param httpAuthorizationHeader PowerAuth HTTP authorization header.
     * @param allowedSignatureTypes Allowed types of the signature.
     * @param forcedSignatureVersion Forced signature version, optional parameter used during upgrade.
     * @return Instance of a PowerAuthApiAuthenticationImpl on successful authorization.
     * @throws PowerAuthAuthenticationException In case authorization fails, exception is raised.
     */
    public PowerAuthApiAuthentication validateRequestSignature(
            String httpMethod,
            byte[] httpBody,
            String requestUriIdentifier,
            String httpAuthorizationHeader,
            List<PowerAuthSignatureTypes> allowedSignatureTypes,
            @Nullable Integer forcedSignatureVersion
    ) throws PowerAuthAuthenticationException {

        // Check for HTTP PowerAuth signature header
        if (httpAuthorizationHeader == null || httpAuthorizationHeader.equals("undefined")) {
            throw new PowerAuthAuthenticationException("POWER_AUTH_SIGNATURE_INVALID_EMPTY");
        }

        // Parse HTTP header
        PowerAuthSignatureHttpHeader header = new PowerAuthSignatureHttpHeader().fromValue(httpAuthorizationHeader);

        // Validate the header
        try {
            PowerAuthSignatureHttpHeaderValidator.validate(header);
        } catch (InvalidPowerAuthHttpHeaderException e) {
            logger.error(e.getMessage(), e);
            throw new PowerAuthAuthenticationException(e.getMessage());
        }

        // Check if the signature type is allowed
        PowerAuthSignatureTypes expectedSignatureType = PowerAuthSignatureTypes.getEnumFromString(header.getSignatureType());
        if (!allowedSignatureTypes.contains(expectedSignatureType)) {
            throw new PowerAuthAuthenticationException("POWER_AUTH_SIGNATURE_TYPE_INVALID");
        }

        // Configure PowerAuth authentication object
        PowerAuthSignatureAuthenticationImpl powerAuthAuthentication = new PowerAuthSignatureAuthenticationImpl();
        powerAuthAuthentication.setActivationId(header.getActivationId());
        powerAuthAuthentication.setApplicationKey(header.getApplicationKey());
        powerAuthAuthentication.setNonce(BaseEncoding.base64().decode(header.getNonce()));
        powerAuthAuthentication.setSignatureType(header.getSignatureType());
        powerAuthAuthentication.setSignature(header.getSignature());
        powerAuthAuthentication.setHttpMethod(httpMethod);
        powerAuthAuthentication.setRequestUri(requestUriIdentifier);
        powerAuthAuthentication.setData(httpBody);
        powerAuthAuthentication.setVersion(header.getVersion());
        powerAuthAuthentication.setHttpHeader(header);
        powerAuthAuthentication.setForcedSignatureVersion(forcedSignatureVersion);

        // Call the authentication based on signature authentication object
        PowerAuthApiAuthentication auth = (PowerAuthApiAuthentication) this.authenticate(powerAuthAuthentication);

        // In case authentication is null, throw PowerAuth exception
        if (auth == null) {
            throw new PowerAuthAuthenticationException("POWER_AUTH_SIGNATURE_INVALID_VALUE");
        }

        return auth;
    }

    public PowerAuthApiAuthentication validateToken(String tokenHeader, List<PowerAuthSignatureTypes> allowedSignatureTypes) throws PowerAuthAuthenticationException {

        // Check for HTTP PowerAuth signature header
        if (tokenHeader == null || tokenHeader.equals("undefined")) {
            throw new PowerAuthAuthenticationException("POWER_AUTH_TOKEN_INVALID_EMPTY");
        }

        // Parse HTTP header
        PowerAuthTokenHttpHeader header = new PowerAuthTokenHttpHeader().fromValue(tokenHeader);

        // Validate the header
        try {
            PowerAuthTokenHttpHeaderValidator.validate(header);
        } catch (InvalidPowerAuthHttpHeaderException e) {
            logger.warn(e.getMessage(), e);
            throw new PowerAuthAuthenticationException(e.getMessage());
        }

        // Prepare authentication object
        PowerAuthTokenAuthenticationImpl powerAuthTokenAuthentication = new PowerAuthTokenAuthenticationImpl();
        powerAuthTokenAuthentication.setTokenId(header.getTokenId());
        powerAuthTokenAuthentication.setTokenDigest(header.getTokenDigest());
        powerAuthTokenAuthentication.setNonce(header.getNonce());
        powerAuthTokenAuthentication.setTimestamp(header.getTimestamp());
        powerAuthTokenAuthentication.setVersion(header.getVersion());
        powerAuthTokenAuthentication.setHttpHeader(header);

        // Call the authentication based on token authentication object
        final PowerAuthApiAuthentication auth = (PowerAuthApiAuthentication) this.authenticate(powerAuthTokenAuthentication);

        // In case authentication is null, throw PowerAuth exception
        if (auth == null) {
            throw new PowerAuthAuthenticationException("POWER_AUTH_TOKEN_INVALID_VALUE");
        }

        // Check if the signature type is allowed
        PowerAuthSignatureTypes expectedSignatureType = auth.getSignatureFactors();
        if (!allowedSignatureTypes.contains(expectedSignatureType)) {
            throw new PowerAuthAuthenticationException("POWER_AUTH_TOKEN_SIGNATURE_TYPE_INVALID");
        }

        return auth;

    }

}
