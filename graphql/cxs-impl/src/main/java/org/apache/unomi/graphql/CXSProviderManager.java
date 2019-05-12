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
package org.apache.unomi.graphql;

import graphql.servlet.GraphQLMutationProvider;
import graphql.servlet.GraphQLQueryProvider;
import graphql.servlet.GraphQLTypesProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

import java.util.Map;

@Component(
        name="CXSProviderManager",
        immediate = true
)
public class CXSProviderManager {

    @Reference(name = "CXSGraphQLProvider")
    private CXSGraphQLProvider cxsGraphQLProvider;
    private ServiceRegistration<?> providerSR;
    private BundleContext bundleContext;

    @Activate
    void activate(
            ComponentContext componentContext,
            BundleContext bundleContext,
            Map<String,Object> config) {
        this.bundleContext = bundleContext;
    }

    @Deactivate
    void deactivate(
            ComponentContext componentContext,
            BundleContext bundleContext,
            Map<String,Object> config) {
    }

    void refreshProviders() {
        if (providerSR != null) {
            providerSR.unregister();
            providerSR = null;
            providerSR = bundleContext.registerService(new String[] {
                    GraphQLQueryProvider.class.getName(),
                    GraphQLTypesProvider.class.getName(),
                    GraphQLMutationProvider.class.getName()
            }, cxsGraphQLProvider, null);
        }
    }

}
