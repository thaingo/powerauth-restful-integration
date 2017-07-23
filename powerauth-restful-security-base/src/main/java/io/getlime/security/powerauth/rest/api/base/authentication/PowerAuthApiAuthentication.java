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
package io.getlime.security.powerauth.rest.api.base.authentication;

/**
 * Inteface for PowerAuth API authentication object used between intermediate server
 * application (such as mobile banking API) and core systems (such as banking core).
 *
 * @author Petr Dvorak, petr@lime-company.eu
 *
 */
public interface PowerAuthApiAuthentication {

    /**
     * Get user ID
     * @return User ID
     */
    String getUserId();

    /**
     * Set user ID
     * @param userId User ID
     */
    void setUserId(String userId);

    /**
     * Get activation ID
     * @return Activation ID
     */
    String getActivationId();

    /**
     * Set activation ID
     * @param activationId Activation ID
     */
    void setActivationId(String activationId);

}