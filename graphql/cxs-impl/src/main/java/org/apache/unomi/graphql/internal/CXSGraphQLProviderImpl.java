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
package org.apache.unomi.graphql.internal;

import graphql.annotations.processor.GraphQLAnnotations;
import graphql.annotations.processor.retrievers.GraphQLFieldRetriever;
import graphql.annotations.processor.retrievers.GraphQLObjectInfoRetriever;
import graphql.annotations.processor.searchAlgorithms.BreadthFirstSearch;
import graphql.annotations.processor.searchAlgorithms.ParentalSearch;
import graphql.annotations.processor.typeBuilders.InputObjectBuilder;
import graphql.schema.*;
import graphql.servlet.GraphQLMutationProvider;
import graphql.servlet.GraphQLQueryProvider;
import graphql.servlet.GraphQLTypesProvider;
import org.apache.unomi.graphql.*;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

import java.util.*;

import static graphql.Scalars.*;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

@Component(
        name = "CXSGraphQLProvider",
        immediate = true
)
public class CXSGraphQLProviderImpl implements CXSGraphQLProvider, GraphQLQueryProvider, GraphQLTypesProvider, GraphQLMutationProvider {

    private Map<String,GraphQLOutputType> registeredOutputTypes = new TreeMap<>();
    private Map<String,GraphQLInputType> registeredInputTypes = new TreeMap<>();

    @Activate
    void activate(
            ComponentContext cc,
            BundleContext bc,
            Map<String,Object> config) {

        registeredOutputTypes.put(CXSGeoPoint.class.getName(), GraphQLAnnotations.object(CXSGeoPoint.class));
        registeredOutputTypes.put(CXSProperties.class.getName(), GraphQLAnnotations.object(CXSProperties.class));
        registeredOutputTypes.put(CXSEventType.class.getName(), GraphQLAnnotations.object(CXSEventType.class));

        GraphQLObjectInfoRetriever graphQLObjectInfoRetriever = new GraphQLObjectInfoRetriever();
        GraphQLInputObjectType cxsEventTypeInput = new InputObjectBuilder(graphQLObjectInfoRetriever, new ParentalSearch(graphQLObjectInfoRetriever),
                new BreadthFirstSearch(graphQLObjectInfoRetriever), new GraphQLFieldRetriever()).
                getInputObjectBuilder(CXSEventTypeInput.class, GraphQLAnnotations.getInstance().getContainer()).build();
        registeredInputTypes.put(CXSEventTypeInput.class.getName(), cxsEventTypeInput);

        registeredOutputTypes.put("CXS_Event", buildCXSEventOutputType());
        registeredOutputTypes.put("CXS_Query", buildCXSQueryOutputType());
        registeredOutputTypes.put("CXS_Mutation", buildCXSMutationOutputType());
    }

    @Deactivate
    void deactivate(
            ComponentContext cc,
            BundleContext bc,
            Map<String,Object> config) {

        registeredOutputTypes.clear();
    }

    @Override
    public Collection<GraphQLFieldDefinition> getQueries() {
        List<GraphQLFieldDefinition> fieldDefinitions = new ArrayList<GraphQLFieldDefinition>();
        fieldDefinitions.add(newFieldDefinition()
                .type(registeredOutputTypes.get("CXS_Query"))
                .name("cxs")
                .description("Root field for all CXS queries")
                .dataFetcher(new DataFetcher() {
                    public Object get(DataFetchingEnvironment environment) {
                        Map<String,Object> map = environment.getContext();
                        return map.keySet();
                    }
                }).build());
        return fieldDefinitions;
    }

    @Override
    public Collection<GraphQLType> getTypes() {
        return new ArrayList<>();
    }

    @Override
    public Collection<GraphQLFieldDefinition> getMutations() {
        List<GraphQLFieldDefinition> fieldDefinitions = new ArrayList<GraphQLFieldDefinition>();
        fieldDefinitions.add(newFieldDefinition()
                .type(registeredOutputTypes.get("CXS_Mutation"))
                .name("cxs")
                .description("Root field for all CXS mutation")
                .dataFetcher(new DataFetcher() {
                    public Object get(DataFetchingEnvironment environment) {
                        Map<String,Object> map = environment.getContext();
                        return map.keySet();
                    }
                }).build());
        return fieldDefinitions;
    }

    private GraphQLOutputType buildCXSQueryOutputType() {
        return newObject()
                .name("CXS_Query")
                .description("Root CXS query type")
                .field(newFieldDefinition()
                        .type(new GraphQLList(registeredOutputTypes.get(CXSEventType.class.getName())))
                        .name("getEventTypes")
                        .description("Retrieves the list of all the declared CXS event types in the Apache Unomi server")
                )
                .build();
    }

    private GraphQLOutputType buildCXSMutationOutputType() {
        return newObject()
                .name("CXS_Mutation")
                .description("Root CXS mutation type")
                .field(newFieldDefinition()
                        .type(registeredOutputTypes.get(CXSEventType.class.getName()))
                        .name("createOrUpdateEventType")
                        .argument(newArgument()
                                .name("eventType")
                                .type(registeredInputTypes.get(CXSEventTypeInput.class.getName()))
                        )
                        .description("Create or updates a CXS event type in the Apache Unomi server")
                )
                .build();
    }


    private GraphQLOutputType buildCXSEventOutputType() {
        return newObject()
                .name("CXS_Event")
                .description("An event is generated by user interacting with the Context Server")
                .field(newFieldDefinition()
                        .type(GraphQLID)
                        .name("id")
                        .description("A unique identifier for the event")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CXSEvent CXSEvent = environment.getSource();
                                return CXSEvent.getId();
                            }
                        })
                )
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("eventType")
                        .description("An identifier for the event type")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CXSEvent CXSEvent = environment.getSource();
                                return CXSEvent.getEventType();
                            }
                        })
                )
                .field(newFieldDefinition()
                        .type(GraphQLLong)
                        .name("timestamp")
                        .description("The difference, measured in milliseconds, between the current time and midnight, January 1, 1970 UTC.")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CXSEvent CXSEvent = environment.getSource();
                                return CXSEvent.getTimeStamp();
                            }
                        }))
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("subject")
                        .description("The entity that has fired the event (using the profile)")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CXSEvent CXSEvent = environment.getSource();
                                return CXSEvent.getSubject();
                            }
                        }))
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("object")
                        .description("The object on which the event was fired.")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CXSEvent CXSEvent = environment.getSource();
                                return CXSEvent.getObject();
                            }
                        })
                )
                .field(newFieldDefinition()
                        .type(registeredOutputTypes.get(CXSGeoPoint.class.getName()))
                        .name("location")
                        .description("The geo-point location where the event was fired.")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CXSEvent CXSEvent = environment.getSource();
                                return CXSEvent.getLocation();
                            }
                        })
                )
                .field(newFieldDefinition()
                        .type(new GraphQLList(registeredOutputTypes.get(CXSProperties.class.getName())))
                        .name("properties")
                        .description("Generic properties for the event")
                        .dataFetcher(new DataFetcher() {
                            public Object get(DataFetchingEnvironment environment) {
                                CXSEvent CXSEvent = environment.getSource();
                                return new ArrayList<Map.Entry<Object,Object>>(CXSEvent.getProperties().entrySet());
                            }
                        })
                )
                .build();
    }
}
