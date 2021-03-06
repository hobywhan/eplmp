/*******************************************************************************
  * Copyright (c) 2017 DocDoku.
  * All rights reserved. This program and the accompanying materials
  * are made available under the terms of the Eclipse Public License v1.0
  * which accompanies this distribution, and is available at
  * http://www.eclipse.org/legal/epl-v10.html
  *
  * Contributors:
  *    DocDoku - initial API and implementation
  *******************************************************************************/

package org.polarsys.eplmp.server.auth.modules;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.AuthStatus;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.callback.CallerPrincipalCallback;
import javax.security.auth.message.callback.GroupPrincipalCallback;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of session authentication module
 *
 * @author Morgan Guimard
 */
public class SessionSAM extends CustomSAM {

    private static final Logger LOGGER = Logger.getLogger(SessionSAM.class.getName());

    public SessionSAM() {
    }

    @Override
    public AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject, Subject serviceSubject) throws AuthException {

        HttpServletRequest request = (HttpServletRequest) messageInfo.getRequestMessage();
        LOGGER.log(Level.FINE, "Validating request @" + request.getMethod() + " " + request.getRequestURI());

        String login = (String) request.getSession().getAttribute("login");
        String groups = (String) request.getSession().getAttribute("groups");

        CallerPrincipalCallback callerPrincipalCallback = new CallerPrincipalCallback(clientSubject, login);
        GroupPrincipalCallback groupPrincipalCallback = new GroupPrincipalCallback(clientSubject, new String[]{groups});
        Callback[] callbacks = new Callback[]{callerPrincipalCallback, groupPrincipalCallback};

        try {
            callbackHandler.handle(callbacks);
        } catch (IOException | UnsupportedCallbackException e) {
            throw new AuthException(e.getMessage());
        }

        return AuthStatus.SUCCESS;
    }

    @Override
    public boolean canHandle(MessageInfo messageInfo) {
        HttpServletRequest request = (HttpServletRequest) messageInfo.getRequestMessage();
        HttpSession session = request.getSession(false);

        if(session == null){
            return false;
        }

        String login = (String) session.getAttribute("login");
        String groups = (String) session.getAttribute("groups");
        return login != null && !login.isEmpty() && groups != null && !groups.isEmpty();
    }
}
