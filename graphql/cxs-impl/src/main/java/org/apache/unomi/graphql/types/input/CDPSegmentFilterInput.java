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

import java.util.List;

@GraphQLName("CDP_SegmentFilter")
public class CDPSegmentFilterInput {

    @GraphQLField
    @GraphQLName("and")
    public List<CDPSegmentFilterInput> andFilters;

    @GraphQLField
    @GraphQLName("or")
    public List<CDPSegmentFilterInput> orFilters;

    @GraphQLField
    @GraphQLName("view_equals")
    public String viewEquals;

    @GraphQLField
    @GraphQLName("view_regexp")
    public String viewRegexp;

    @GraphQLField
    @GraphQLName("name_equals")
    public String nameEquals;

    @GraphQLField
    @GraphQLName("name_regexp")
    public String nameRegexp;

}
