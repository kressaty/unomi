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

import graphql.annotations.processor.GraphQLAnnotationsComponent;
import graphql.annotations.processor.ProcessingElementsContainer;
import graphql.schema.*;
import graphql.servlet.GraphQLMutationProvider;
import graphql.servlet.GraphQLQueryProvider;
import graphql.servlet.GraphQLTypesProvider;
import org.apache.unomi.graphql.*;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static graphql.Scalars.*;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLInputObjectField.newInputObjectField;
import static graphql.schema.GraphQLInputObjectType.newInputObject;
import static graphql.schema.GraphQLObjectType.newObject;

public class CXSGraphQLProviderImpl implements CXSGraphQLProvider, GraphQLQueryProvider, GraphQLTypesProvider, GraphQLMutationProvider {

    private static final Logger logger = LoggerFactory.getLogger(CXSGraphQLProviderImpl.class.getName());

    private Map<String,GraphQLOutputType> registeredOutputTypes = new TreeMap<>();
    private Map<String,GraphQLInputType> registeredInputTypes = new TreeMap<>();
    private CXSProviderManager cxsProviderManager;
    private GraphQLAnnotationsComponent annotationsComponent;
    private ProcessingElementsContainer container;

    private Map<String,CXSEventType> eventTypes = new TreeMap<>();

    public CXSGraphQLProviderImpl(GraphQLAnnotationsComponent annotationsComponent) {
        this.annotationsComponent = annotationsComponent;
        container = annotationsComponent.createContainer();
        updateGraphQLTypes();
    }

    private void updateGraphQLTypes() {

        registeredOutputTypes.put(PageInfo.class.getName(), annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(PageInfo.class, container));

        registeredOutputTypes.put(CXSGeoPoint.class.getName(), annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(CXSGeoPoint.class, container));
        registeredOutputTypes.put(CXSSetPropertyType.class.getName(),annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(CXSSetPropertyType.class, container));
        registeredOutputTypes.put(CXSEventType.class.getName(), annotationsComponent.getOutputTypeProcessor().getOutputTypeOrRef(CXSEventType.class, container));

        registeredInputTypes.put(CXSGeoDistanceInput.class.getName(), annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CXSGeoDistanceInput.class, container));
        registeredInputTypes.put(CXSDateFilterInput.class.getName(), annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CXSDateFilterInput.class, container));
        registeredInputTypes.put(CXSEventTypeInput.class.getName(), annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CXSEventTypeInput.class, container));
        registeredInputTypes.put(CXSOrderByInput.class.getName(), annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CXSOrderByInput.class, container));
        registeredInputTypes.put("CXS_EventInput", buildCXSEventInputType());
        registeredInputTypes.put(CXSEventOccurrenceFilterInput.class.getName(), annotationsComponent.getInputTypeProcessor().getInputTypeOrRef(CXSEventOccurrenceFilterInput.class, container));
        registeredInputTypes.put("CXS_EventPropertiesFilterInput", buildCXSEventPropertiesFilterInput());
        registeredInputTypes.put("CXS_EventFilterInput", buildCXSEventFilterInputType());

        registeredOutputTypes.put("CXS_EventProperties", buildCXSEventPropertiesOutputType());

        registeredOutputTypes.put("CXS_Event", buildCXSEventOutputType());
        registeredOutputTypes.put("CXS_EventEdge", buildCXSEventEdgeOutputType());
        registeredOutputTypes.put("CXS_EventConnection", buildCXSEventConnectionOutputType());
        registeredOutputTypes.put("CXS_Query", buildCXSQueryOutputType());
        registeredOutputTypes.put("CXS_Mutation", buildCXSMutationOutputType());
    }

    private GraphQLOutputType buildCXSEventEdgeOutputType() {
        return newObject()
                .name("CXS_EventEdge")
                .description("The Relay edge type for the CXS_Event output type")
                .field(newFieldDefinition()
                        .name("node")
                        .type(registeredOutputTypes.get("CXS_Event"))
                )
                .field(newFieldDefinition()
                        .name("cursor")
                        .type(GraphQLString)
                )
                .build();
    }

    private GraphQLOutputType buildCXSEventConnectionOutputType() {
        return newObject()
                .name("CXS_EventConnection")
                .description("The Relay connection type for the CXS_Event output type")
                .field(newFieldDefinition()
                        .name("edges")
                        .type(new GraphQLList(registeredOutputTypes.get("CXS_EventEdge")))
                )
                .field(newFieldDefinition()
                        .name("pageInfo")
                        .type(new GraphQLList(registeredOutputTypes.get(PageInfo.class.getName())))
                )
                .build();
    }

    private GraphQLInputType buildCXSEventPropertiesFilterInput() {
        GraphQLInputObjectType.Builder cxsEventPropertiesFilterInput = newInputObject()
                .name("CXS_EventPropertiesFilterInput")
                .description("Filter conditions for each event types and built-in properties");

        generateEventPropertiesFilters(cxsEventPropertiesFilterInput);
        generateEventTypesFilters(cxsEventPropertiesFilterInput);

        return cxsEventPropertiesFilterInput.build();
    }


    private void generateEventPropertiesFilters(GraphQLInputObjectType.Builder cxsEventPropertiesFilterInput) {
        addIdentityFilters("id", cxsEventPropertiesFilterInput);
        addIdentityFilters("sourceId", cxsEventPropertiesFilterInput);
        addIdentityFilters("clientId", cxsEventPropertiesFilterInput);
        addIdentityFilters("profileId", cxsEventPropertiesFilterInput);
        addDistanceFilters("location", cxsEventPropertiesFilterInput);
        addDateFilters("timestamp", cxsEventPropertiesFilterInput);
    }

    private void generateEventTypesFilters(GraphQLInputObjectType.Builder cxsEventPropertiesFilterInput) {
        for (Map.Entry<String,CXSEventType> eventTypeEntry : eventTypes.entrySet()) {
            addSetFilters(eventTypeEntry.getKey(), eventTypeEntry.getValue().properties, cxsEventPropertiesFilterInput);
        }
    }

    private void addSetFilters(String eventTypeName, List<CXSPropertyType> properties, GraphQLInputObjectType.Builder inputTypeBuilder) {
        GraphQLInputObjectType.Builder eventTypeFilterInput = newInputObject()
                .name(eventTypeName + "FilterInput")
                .description("Auto-generated filter input type for event type " + eventTypeName);

        for (CXSPropertyType cxsPropertyType : properties) {
            if (cxsPropertyType instanceof CXSIdentifierPropertyType) {
                addIdentityFilters(cxsPropertyType.name, eventTypeFilterInput);
            } else if (cxsPropertyType instanceof CXSStringPropertyType) {
                addStringFilters(cxsPropertyType.name, eventTypeFilterInput);
            } else if (cxsPropertyType instanceof CXSBooleanPropertyType) {
                addBooleanFilters(cxsPropertyType.name, eventTypeFilterInput);
            } else if (cxsPropertyType instanceof CXSIntPropertyType) {
                addIntegerFilters(cxsPropertyType.name, eventTypeFilterInput);
            } else if (cxsPropertyType instanceof CXSFloatPropertyType) {
                addFloatFilters(cxsPropertyType.name, eventTypeFilterInput);
            } else if (cxsPropertyType instanceof CXSGeoPointPropertyType) {
                addDistanceFilters(cxsPropertyType.name, eventTypeFilterInput);
            } else if (cxsPropertyType instanceof CXSDatePropertyType) {
                addDateFilters(cxsPropertyType.name, eventTypeFilterInput);
            } else if (cxsPropertyType instanceof CXSSetPropertyType) {
                addSetFilters(cxsPropertyType.name, ((CXSSetPropertyType) cxsPropertyType).properties, eventTypeFilterInput);
            }
        }

        registeredInputTypes.put(eventTypeName + "FilterInput", eventTypeFilterInput.build());

        inputTypeBuilder.field(newInputObjectField()
                .name(eventTypeName)
                .type(registeredInputTypes.get(eventTypeName + "FilterInput"))
        );

    }

    private void addIdentityFilters(String propertyName, GraphQLInputObjectType.Builder inputTypeBuilder) {
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_equals")
                .type(GraphQLString)
        );
    }

    private void addStringFilters(String propertyName, GraphQLInputObjectType.Builder inputTypeBuilder) {
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_equals")
                .type(GraphQLString)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_regexp")
                .type(GraphQLString)
        );
    }

    private void addBooleanFilters(String propertyName, GraphQLInputObjectType.Builder inputTypeBuilder) {
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_equals")
                .type(GraphQLBoolean)
        );
    }

    private void addIntegerFilters(String propertyName, GraphQLInputObjectType.Builder inputTypeBuilder) {
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_equals")
                .type(GraphQLInt)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_gt")
                .type(GraphQLInt)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_gte")
                .type(GraphQLInt)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_lt")
                .type(GraphQLInt)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_lte")
                .type(GraphQLInt)
        );
    }

    private void addFloatFilters(String propertyName, GraphQLInputObjectType.Builder inputTypeBuilder) {
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_equals")
                .type(GraphQLFloat)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_gt")
                .type(GraphQLFloat)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_gte")
                .type(GraphQLFloat)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_lt")
                .type(GraphQLFloat)
        );
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_lte")
                .type(GraphQLFloat)
        );
    }

    private void addDistanceFilters(String propertyName, GraphQLInputObjectType.Builder inputTypeBuilder) {
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_distance")
                .type(registeredInputTypes.get(CXSGeoDistanceInput.class.getName()))
        );
    }

    private void addDateFilters(String propertyName, GraphQLInputObjectType.Builder inputTypeBuilder) {
        inputTypeBuilder.field(newInputObjectField()
                .name(propertyName + "_between")
                .type(registeredInputTypes.get(CXSDateFilterInput.class.getName()))
        );
    }

    private GraphQLInputType buildCXSEventFilterInputType() {
        GraphQLInputObjectType.Builder cxsEventFilterInputType = newInputObject()
                .name("CXS_EventFilterInput")
                .description("Filter conditions for each event types and built-in properties")
                .field(newInputObjectField()
                        .name("and")
                        .type(new GraphQLList(new GraphQLTypeReference("CXS_EventFilterInput")))
                )
                .field(newInputObjectField()
                        .name("or")
                        .type(new GraphQLList(new GraphQLTypeReference("CXS_EventFilterInput")))
                )
                .field(newInputObjectField()
                        .name("properties")
                        .type(registeredInputTypes.get("CXS_EventPropertiesFilterInput"))
                )
                .field(newInputObjectField()
                        .name("eventOccurrence")
                        .type(registeredInputTypes.get(CXSEventOccurrenceFilterInput.class.getName()))
                );
        return cxsEventFilterInputType.build();
    }

    private GraphQLInputType buildCXSEventInputType() {
        GraphQLInputObjectType.Builder cxsEventInputType = newInputObject()
                .name("CXS_EventInput")
                .description("The event input object to send events to the Context Server")
                .field(newInputObjectField()
                        .name("id")
                        .type(GraphQLID)
                );

        for (Map.Entry<String,CXSEventType> cxsEventTypeEntry : eventTypes.entrySet()) {
            CXSEventType cxsEventType = cxsEventTypeEntry.getValue();
            cxsEventInputType.field(newInputObjectField()
                    .name(cxsEventTypeEntry.getKey())
                    .type(buildCXSEventTypeInputProperty(cxsEventType.typeName, cxsEventType.properties))
            );
        }

        return cxsEventInputType.build();

    }

    private GraphQLInputType buildCXSEventTypeInputProperty(String typeName, List<CXSPropertyType> propertyTypes) {
        String eventTypeName = typeName.substring(0, 1).toUpperCase() + typeName.substring(1) + "EventTypeInput";
        GraphQLInputObjectType.Builder eventInputType = newInputObject()
                .name(eventTypeName)
                .description("Event type object for event type " + typeName);

        for (CXSPropertyType cxsEventPropertyType : propertyTypes) {
            GraphQLInputType eventPropertyInputType = null;
            if (cxsEventPropertyType instanceof CXSIdentifierPropertyType) {
                eventPropertyInputType = GraphQLID;
            } else if (cxsEventPropertyType instanceof CXSStringPropertyType) {
                eventPropertyInputType = GraphQLString;
            } else if (cxsEventPropertyType instanceof CXSIntPropertyType) {
                eventPropertyInputType = GraphQLInt;
            } else if (cxsEventPropertyType instanceof CXSFloatPropertyType) {
                eventPropertyInputType = GraphQLFloat;
            } else if (cxsEventPropertyType instanceof CXSBooleanPropertyType) {
                eventPropertyInputType = GraphQLBoolean;
            } else if (cxsEventPropertyType instanceof CXSDatePropertyType) {
                eventPropertyInputType = GraphQLString;
            } else if (cxsEventPropertyType instanceof CXSGeoPointPropertyType) {
                eventPropertyInputType = registeredInputTypes.get(CXSGeoPoint.class.getName());
            } else if (cxsEventPropertyType instanceof CXSSetPropertyType) {
                eventPropertyInputType = buildCXSEventTypeInputProperty(cxsEventPropertyType.name, ((CXSSetPropertyType)cxsEventPropertyType).properties);
            }
            eventInputType
                    .field(newInputObjectField()
                            .type(eventPropertyInputType)
                            .name(cxsEventPropertyType.name)
                    );
        }

        return eventInputType.build();
    }

    @Deactivate
    void deactivate(
            ComponentContext cc,
            BundleContext bc,
            Map<String,Object> config) {

        registeredOutputTypes.clear();
    }

    public void setCxsProviderManager(CXSProviderManager cxsProviderManager) {
        this.cxsProviderManager = cxsProviderManager;
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
                .dataFetcher(new DataFetcher<Object>() {
                    @Override
                    public Object get(DataFetchingEnvironment environment) {
                        Object contextObject = environment.getContext();
                        return contextObject;
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
                .field(newFieldDefinition()
                        .type(new GraphQLList(registeredOutputTypes.get("CXS_Event")))
                        .name("getEvent")
                        .description("Retrieves a specific event")
                )
                .field(newFieldDefinition()
                        .type(new GraphQLList(registeredOutputTypes.get("CXS_EventConnection")))
                        .name("findEvents")
                        .argument(newArgument()
                                .name("filter")
                                .type(registeredInputTypes.get("CXS_EventFilterInput"))
                        )
                        .argument(newArgument()
                                .name("orderBy")
                                .type(registeredInputTypes.get(CXSOrderByInput.class.getName()))
                        )
                        .argument(newArgument()
                                .name("first")
                                .type(GraphQLInt)
                                .description("Number of objects to retrieve starting at the after cursor position")
                        )
                        .argument(newArgument()
                                .name("after")
                                .type(GraphQLString)
                                .description("Starting cursor location to retrieve the object from")
                        )
                        .argument(newArgument()
                                .name("last")
                                .type(GraphQLInt)
                                .description("Number of objects to retrieve end at the before cursor position")
                        )
                        .argument(newArgument()
                                .name("before")
                                .type(GraphQLString)
                                .description("End cursor location to retrieve the object from")
                        )
                        .description("Retrieves the events that match the specified filters")
                )
                /*
                .field(newFieldDefinition()
                        .type(new GraphQLList(registeredOutputTypes.get("CXS_ProfileConnection")))
                        .name("findProfiles")
                        .argument(newArgument()
                                .name("filter")
                                .type(registeredInputTypes.get("CXS_ProfileFilterInput"))
                        )
                        .argument(newArgument()
                                .name("orderBy")
                                .type(registeredInputTypes.get(CXSOrderByInput.class.getName()))
                        )
                        .argument(newArgument()
                                .name("first")
                                .type(GraphQLInt)
                                .description("Number of objects to retrieve starting at the after cursor position")
                        )
                        .argument(newArgument()
                                .name("after")
                                .type(GraphQLString)
                                .description("Starting cursor location to retrieve the object from")
                        )
                        .argument(newArgument()
                                .name("last")
                                .type(GraphQLInt)
                                .description("Number of objects to retrieve end at the before cursor position")
                        )
                        .argument(newArgument()
                                .name("before")
                                .type(GraphQLString)
                                .description("End cursor location to retrieve the object from")
                        )
                        .description("Retrieves the profiles that match the specified profiles")
                )
                */
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
                        .dataFetcher(new DataFetcher<CXSEventType>() {
                            @Override
                            public CXSEventType get(DataFetchingEnvironment environment) {
                                Map<String,Object> arguments = environment.getArguments();
                                CXSEventType cxsEventType = new CXSEventType();
                                if (arguments.containsKey("eventType")) {
                                    Map<String,Object> eventTypeArguments = (Map<String,Object>) arguments.get("eventType");
                                    if (eventTypeArguments.containsKey("typeName")) {
                                        cxsEventType.id = (String) eventTypeArguments.get("typeName");
                                        cxsEventType.typeName = (String) eventTypeArguments.get("typeName");
                                    }
                                    cxsEventType.properties = new ArrayList<>();
                                    if (eventTypeArguments.containsKey("properties")) {
                                        List<Map<String, Object>> properties = (List<Map<String, Object>>) eventTypeArguments.get("properties");
                                        for (Map<String, Object> propertyTypeMap : properties) {
                                            CXSPropertyType cxsPropertyType = getPropertyType(propertyTypeMap);
                                            if (cxsPropertyType != null) {
                                                cxsEventType.properties.add(cxsPropertyType);
                                            }
                                        }
                                    }
                                }
                                eventTypes.put(cxsEventType.typeName, cxsEventType);
                                updateGraphQLTypes();
                                if (cxsProviderManager != null) {
                                    cxsProviderManager.refreshProviders();
                                }
                                return cxsEventType;
                            }
                        })
                )
                .field(newFieldDefinition()
                        .name("processEvents")
                        .description("Processes events sent to the Context Server")
                        .argument(newArgument()
                                .name("events")
                                .type(new GraphQLList(registeredInputTypes.get("CXS_EventInput"))))
                        .type(GraphQLInt)
                )
                .build();
    }

    private CXSPropertyType getPropertyType(Map<String, Object> propertyTypeMap) {
        if (propertyTypeMap.size() > 1) {
            logger.error("Only one property type is allowed for each property !");
            return null;
        }
        CXSPropertyType propertyType = null;
        if (propertyTypeMap.containsKey("identifier")) {
            propertyType = getIdentifierPropertyType(propertyTypeMap);
        } else if (propertyTypeMap.containsKey("string")) {
            propertyType = getStringPropertyType(propertyTypeMap);
        } else if (propertyTypeMap.containsKey("set")) {
            propertyType = getSetPropertyType(propertyTypeMap);
        }
        return propertyType;
    }

    private CXSPropertyType getSetPropertyType(Map<String, Object> propertyTypeMap) {
        CXSSetPropertyType cxsSetPropertyType = new CXSSetPropertyType();
        Map<String,Object> setPropertyTypeMap = (Map<String,Object>) propertyTypeMap.get("set");
        populateCommonProperties(setPropertyTypeMap, cxsSetPropertyType);
        if (setPropertyTypeMap.containsKey("properties")) {
            List<Map<String,Object>> propertyList = (List<Map<String,Object>>) setPropertyTypeMap.get("properties");
            List<CXSPropertyType> setProperties = new ArrayList<>();
            for (Map<String,Object> setProperty : propertyList) {
                CXSPropertyType subPropertyType = getPropertyType(setProperty);
                if (subPropertyType != null) {
                    setProperties.add(subPropertyType);
                }
            }
            cxsSetPropertyType.properties = setProperties;
        }
        return cxsSetPropertyType;
    }

    private CXSPropertyType getStringPropertyType(Map<String, Object> propertyTypeMap) {
        CXSStringPropertyType cxsStringPropertyType = new CXSStringPropertyType();
        Map<String,Object> stringPropertyTypeMap = (Map<String,Object>) propertyTypeMap.get("string");
        populateCommonProperties(stringPropertyTypeMap, cxsStringPropertyType);
        return cxsStringPropertyType;
    }

    private CXSPropertyType getIdentifierPropertyType(Map<String, Object> propertyTypeMap) {
        CXSIdentifierPropertyType cxsIdentifierPropertyType = new CXSIdentifierPropertyType();
        Map<String,Object> identifierPropertyTypeMap = (Map<String,Object>) propertyTypeMap.get("identifier");
        populateCommonProperties(identifierPropertyTypeMap, cxsIdentifierPropertyType);
        return cxsIdentifierPropertyType;
    }

    private void populateCommonProperties(Map<String, Object> propertyTypeMap, CXSPropertyType cxsPropertyType) {
        if (propertyTypeMap == null || propertyTypeMap.size() == 0) {
            return;
        }
        if (propertyTypeMap.containsKey("id")) {
            cxsPropertyType.id = (String) propertyTypeMap.get("id");
        }
        if (propertyTypeMap.containsKey("name")) {
            cxsPropertyType.name = (String) propertyTypeMap.get("name");
        }
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
                        .type(new GraphQLList(registeredOutputTypes.get("CXS_EventProperties")))
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

    private GraphQLOutputType buildCXSEventPropertiesOutputType() {
        GraphQLObjectType.Builder eventPropertiesOutputType = newObject()
                .name("CXS_EventProperties")
                .description("All possible properties of an event");

        // we create a dummy field because GraphQL requires at least one
        eventPropertiesOutputType.field(newFieldDefinition()
                .type(GraphQLInt)
                .name("typeCount")
                .description("Total count of different field types")
        );

        for (Map.Entry<String,CXSEventType> cxsEventTypeEntry : eventTypes.entrySet()) {
            CXSEventType cxsEventType = cxsEventTypeEntry.getValue();
            eventPropertiesOutputType
                    .field(newFieldDefinition()
                            .type(buildEventOutputType(cxsEventType.typeName, cxsEventType.properties))
                            .name(cxsEventTypeEntry.getKey())
                    );
        }

        return eventPropertiesOutputType.build();
    }

    private GraphQLOutputType buildEventOutputType(String typeName, List<CXSPropertyType> propertyTypes) {
        String eventTypeName = typeName.substring(0, 1).toUpperCase() + typeName.substring(1) + "EventType";
        GraphQLObjectType.Builder eventOutputType = newObject()
                .name(eventTypeName)
                .description("Event type object for event type " + typeName);

        for (CXSPropertyType cxsEventPropertyType : propertyTypes) {
            GraphQLOutputType eventPropertyOutputType = null;
            if (cxsEventPropertyType instanceof CXSIdentifierPropertyType) {
                eventPropertyOutputType = GraphQLID;
            } else if (cxsEventPropertyType instanceof CXSStringPropertyType) {
                eventPropertyOutputType = GraphQLString;
            } else if (cxsEventPropertyType instanceof CXSIntPropertyType) {
                eventPropertyOutputType = GraphQLInt;
            } else if (cxsEventPropertyType instanceof CXSFloatPropertyType) {
                eventPropertyOutputType = GraphQLFloat;
            } else if (cxsEventPropertyType instanceof CXSBooleanPropertyType) {
                eventPropertyOutputType = GraphQLBoolean;
            } else if (cxsEventPropertyType instanceof CXSDatePropertyType) {
                eventPropertyOutputType = GraphQLString;
            } else if (cxsEventPropertyType instanceof CXSGeoPointPropertyType) {
                eventPropertyOutputType = registeredOutputTypes.get(CXSGeoPoint.class.getName());
            } else if (cxsEventPropertyType instanceof CXSSetPropertyType) {
                eventPropertyOutputType = buildEventOutputType(cxsEventPropertyType.name, ((CXSSetPropertyType)cxsEventPropertyType).properties);
            }
            eventOutputType
                    .field(newFieldDefinition()
                            .type(eventPropertyOutputType)
                            .name(cxsEventPropertyType.name)
                    );
        }


        return eventOutputType.build();
    }

}
