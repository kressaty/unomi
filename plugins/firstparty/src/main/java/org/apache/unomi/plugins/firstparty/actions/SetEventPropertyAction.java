/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.plugins.firstparty.actions;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.persistence.spi.PropertyHelper;
import org.apache.unomi.persistence.spi.PersistenceService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.graalvm.polyglot.*;

public class SetEventPropertyAction implements ActionExecutor {

    private EventService eventService;
    private PersistenceService persistenceService;

    private boolean useEventToUpdateProfile = false;

    public void setUseEventToUpdateProfile(boolean useEventToUpdateProfile) {
        this.useEventToUpdateProfile = useEventToUpdateProfile;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public int execute(Action action, Event event) {
        boolean storeInSession = Boolean.TRUE.equals(action.getParameterValues().get("storeInSession"));
        boolean storeInEvent = Boolean.TRUE.equals(action.getParameterValues().get("storeInEvent"));

        String propertyName = (String) action.getParameterValues().get("setPropertyName");

        Object propertyValue = action.getParameterValues().get("setPropertyValue");
        if (propertyValue == null) {
            propertyValue = action.getParameterValues().get("setPropertyValueMultiple");
        }
        Object propertyValueInteger = action.getParameterValues().get("setPropertyValueInteger");
        Object setPropertyValueMultiple = action.getParameterValues().get("setPropertyValueMultiple");
        Object setPropertyValueBoolean = action.getParameterValues().get("setPropertyValueBoolean");

        if (propertyValue == null) {
            if (propertyValueInteger != null) {
                propertyValue = PropertyHelper.getInteger(propertyValueInteger);
            }
            if (setPropertyValueMultiple != null) {
                propertyValue = setPropertyValueMultiple;
            }
            if (setPropertyValueBoolean != null) {
                propertyValue = PropertyHelper.getBooleanValue(setPropertyValueBoolean);
            }
        }
        if (propertyValue != null && propertyValue.equals("now")) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            propertyValue = format.format(event.getTimeStamp());
        }

        Context polyglot = Context.create();
        Value array = polyglot.eval("js", "[1,2,42,4]");
        int result = array.getArrayElement(2).asInt();

        PropertyHelper.setProperty(event, propertyName, propertyValue, (String) action.getParameterValues().get("setPropertyStrategy"));
        if (event.isPersistent()) {
            persistenceService.update(event.getItemId(), event.getTimeStamp(), Event.class, "properties", event.getProperties());
        }

        return EventService.NO_CHANGE;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

}
