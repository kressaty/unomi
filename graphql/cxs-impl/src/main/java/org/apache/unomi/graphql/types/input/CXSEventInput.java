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
package org.apache.unomi.graphql.types.input;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;

import java.util.LinkedHashMap;
import java.util.Map;

@GraphQLName("CXS_Event")
public class CXSEventInput {
    private String id;
    private String eventType;
    private long timeStamp;
    private String subject;
    private String object;
    private Map<Object,Object> properties = new LinkedHashMap<>();
    private CXSGeoPointInput location;

    @GraphQLField
    public String getId() {
        return id;
    }

    @GraphQLField
    public String getEventType() {
        return eventType;
    }

    @GraphQLField
    public long getTimeStamp() {
        return timeStamp;
    }

    @GraphQLField
    public String getSubject() {
        return subject;
    }

    @GraphQLField
    public String getObject() {
        return object;
    }

    public Map<Object, Object> getProperties() {
        return properties;
    }

    @GraphQLField
    public CXSGeoPointInput getLocation() {
        return location;
    }

}
