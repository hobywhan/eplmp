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

package org.polarsys.eplmp.core.exceptions;

import java.util.Locale;

/**
 * Created by fredericmaury on 23/11/2016.
 */
public class UpdateException extends ApplicationException {

    public UpdateException(String pMessage) {
        super(pMessage);
    }

    public UpdateException(Locale pLocale) {
        this(pLocale, null);
    }

    public UpdateException(Locale pLocale, Throwable pCause) {
        super(pLocale, pCause);
    }

    @Override
    public String getLocalizedMessage() {
        return getBundleDefaultMessage();
    }
}
