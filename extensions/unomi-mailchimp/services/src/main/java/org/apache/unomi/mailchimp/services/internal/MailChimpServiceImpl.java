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

package org.apache.unomi.mailchimp.services.internal;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.mailchimp.services.HttpUtils;
import org.apache.unomi.mailchimp.services.MailChimpResult;
import org.apache.unomi.mailchimp.services.MailChimpService;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MailChimpServiceImpl implements MailChimpService {
    private static final String LISTS = "lists";
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String MERGE_FIELDS = "merge_fields";
    private static final String EMAIL_TYPE = "email_type";
    private static final String EMAIL_ADDRESS = "email_address";
    private static final String EMAIL = "email";
    private static final String ERRORS = "errors";
    private static final String FIRST_NAME = "firstName";
    private static final String LAST_NAME = "lastName";
    private static final String MEMBERS = "members";
    private static final String LIST_IDENTIFIER = "listIdentifier";
    private static final String STATUS = "status";
    private static final String SUBSCRIBED = "subscribed";
    private static final String UNSUBSCRIBED = "unsubscribed";
    private static final String TAG = "tag";
    private static final String TYPE = "type";
    private static final String OPTIONS = "options";
    private static final String DATE_FORMAT = "date_format";
    private static final String UNOMI_ID = "unomiId";
    private static final String MC_SUB_TAG_NAME = "mcSubTagName";
    private static Logger logger = LoggerFactory.getLogger(MailChimpServiceImpl.class);
    private String apiKey;
    private String urlSubDomain;
    private Map<String, List<Map<String, String>>> listMergeFieldMapping;
    private Boolean isMergeFieldsActivate;
    private CloseableHttpClient httpClient;


    @Override
    public List<HashMap<String, String>> getAllLists() {
        List<HashMap<String, String>> mcLists = new ArrayList<>();
        if (isMailChimpConnectorConfigured()) {
            JsonNode response = HttpUtils.executeGetRequest(httpClient, getBaseUrl() + "/lists", getHeaders());
            if (response != null) {
                if (response.has(LISTS) && response.get(LISTS).size() > 0) {
                    for (JsonNode list : response.get(LISTS)) {
                        if (list.has(ID) && list.has(NAME)) {
                            HashMap<String, String> mcListInfo = new HashMap<>();
                            mcListInfo.put(ID, list.get(ID).asText());
                            mcListInfo.put(NAME, list.get(NAME).asText());
                            mcLists.add(mcListInfo);
                        } else {
                            logger.warn("Missing mandatory information for list, {}", list.asText());
                        }
                    }
                } else {
                    logger.info("No list to return, response was {}", response.asText());
                }
            }
        }
        return mcLists;
    }

    @Override
    public MailChimpResult addToMCList(Profile profile, Action action) {
        if (!isMailChimpConnectorConfigured() || !visitorHasMandatoryProperties(profile)) {
            return MailChimpResult.ERROR;
        }

        String listIdentifier = (String) action.getParameterValues().get(LIST_IDENTIFIER);
        JsonNode currentMember = isMemberOfMailChimpList(profile, listIdentifier);
        JSONObject mergeFields = new JSONObject();

        if (currentMember != null && currentMember.has(STATUS)) {
            JSONObject body = new JSONObject();
            if (currentMember.get(STATUS).asText().equals(UNSUBSCRIBED)) {
                logger.info("The visitor is already in the MailChimp list, his status is unsubscribed");
                body.put(STATUS, SUBSCRIBED);
            }

            if (isMergeFieldsActivate && addProfilePropertiesToMergeFieldsObject(profile, listIdentifier, mergeFields) == MailChimpResult.SUCCESS) {
                body.put(MERGE_FIELDS, mergeFields);
            }
            return updateSubscription(listIdentifier, body.toString(), currentMember, true);
        }

        JSONObject userData = new JSONObject();
        userData.put(EMAIL_TYPE, "html");
        userData.put(EMAIL_ADDRESS, profile.getProperty(EMAIL).toString());
        userData.put(STATUS, SUBSCRIBED);

        if (isMergeFieldsActivate) {
            addProfilePropertiesToMergeFieldsObject(profile, listIdentifier, mergeFields);
        }
        userData.put(MERGE_FIELDS, mergeFields);

        JsonNode response = HttpUtils.executePostRequest(httpClient, getBaseUrl() + "/lists/" + listIdentifier + "/members", getHeaders(), userData.toString());
        if (response == null || (response.has(ERRORS) && response.get(ERRORS).size() > 0)) {
            logger.error("Error when adding user to MailChimp list, list identifier was {} and response was {}", listIdentifier, response);
            return MailChimpResult.ERROR;
        }

        return MailChimpResult.UPDATED;
    }

    @Override
    public MailChimpResult removeFromMCList(Profile profile, Action action) {
        if (!isMailChimpConnectorConfigured() || !visitorHasMandatoryProperties(profile)) {
            return MailChimpResult.ERROR;
        }

        String listIdentifier = (String) action.getParameterValues().get(LIST_IDENTIFIER);
        if (StringUtils.isBlank(listIdentifier)) {
            logger.warn("Couldn't get the list identifier from Unomi");
            return MailChimpResult.ERROR;
        }

        JsonNode currentMember = isMemberOfMailChimpList(profile, listIdentifier);
        if (currentMember == null) {
            return MailChimpResult.NO_CHANGE;
        }

        JsonNode response = HttpUtils.executeDeleteRequest(httpClient, getBaseUrl() + "/lists/" + listIdentifier + "/members/" + currentMember.get(ID).asText(), getHeaders());
        if (response == null || (response.has(ERRORS) && response.get(ERRORS).size() > 0)) {
            logger.error("Couldn't remove the visitor from the MailChimp list, list identifier was {} and response was {}", listIdentifier, response);
            return MailChimpResult.ERROR;
        }

        return MailChimpResult.REMOVED;
    }

    @Override
    public MailChimpResult unsubscribeFromMCList(Profile profile, Action action) {
        if (!isMailChimpConnectorConfigured() || !visitorHasMandatoryProperties(profile)) {
            return MailChimpResult.ERROR;
        }

        String listIdentifier = (String) action.getParameterValues().get(LIST_IDENTIFIER);
        if (StringUtils.isBlank(listIdentifier)) {
            logger.warn("Couldn't get the list identifier from Unomi");
            return MailChimpResult.ERROR;
        }

        JsonNode currentMember = isMemberOfMailChimpList(profile, listIdentifier);
        if (currentMember == null) {
            return MailChimpResult.REMOVED;
        }
        if (currentMember.get(STATUS).asText().equals(UNSUBSCRIBED)) {
            return MailChimpResult.NO_CHANGE;
        }

        JSONObject body = new JSONObject();
        body.put(STATUS, UNSUBSCRIBED);
        return updateSubscription(listIdentifier, body.toString(), currentMember, false);
    }


    @Override
    public MailChimpResult updateMCProfileProperties(Profile profile, Action action) {
        if (!isMailChimpConnectorConfigured() || !visitorHasMandatoryProperties(profile)) {
            return MailChimpResult.ERROR;
        }

        String listIdentifier = (String) action.getParameterValues().get(LIST_IDENTIFIER);
        if (StringUtils.isBlank(listIdentifier)) {
            logger.warn("MailChimp list identifier not found");
            return MailChimpResult.ERROR;
        }

        JsonNode currentMember = isMemberOfMailChimpList(profile, listIdentifier);
        if (currentMember == null) {
            logger.warn("The visitor was not part of the list");
            return MailChimpResult.NO_CHANGE;
        }


        JSONObject mergeFields = new JSONObject();
        MailChimpResult result = addProfilePropertiesToMergeFieldsObject(profile, listIdentifier, mergeFields);
        if (result != MailChimpResult.SUCCESS) {
            return result;
        }

        JSONObject body = new JSONObject();
        body.put(MERGE_FIELDS, mergeFields);

        JsonNode response = HttpUtils.executePatchRequest(httpClient, getBaseUrl() + "/lists/" + listIdentifier + "/members/" + currentMember.get(ID).asText(), getHeaders(), body.toString());
        if (response == null || (response.has(ERRORS) && response.get(ERRORS).size() > 0)) {
            logger.error("Error when updating visitor properties to MailChimp list, list identifier was {} and response was {}", listIdentifier, response);
            return MailChimpResult.ERROR;
        }

        return MailChimpResult.UPDATED;
    }

    private MailChimpResult addProfilePropertiesToMergeFieldsObject(Profile profile, String listIdentifier, JSONObject mergeFields) {
        if (listMergeFieldMapping.isEmpty()) {
            logger.error("List of merge fields is not correctly configured");
            return MailChimpResult.ERROR;
        }

        JsonNode mergeFieldsDefinitions = getMCListProperties(listIdentifier);
        if (mergeFieldsDefinitions == null) {
            logger.error("Could not get MailChimp list's merge fields");
            return MailChimpResult.ERROR;
        }

        for (JsonNode mergeFieldDefinition : mergeFieldsDefinitions.get(MERGE_FIELDS)) {
            if (mergeFieldDefinition.has(TAG) && mergeFieldDefinition.has(TYPE)) {
                String mcTagName = mergeFieldDefinition.get(TAG).asText();
                if (listMergeFieldMapping.containsKey(mcTagName)) {
                    List<Map<String, String>> fields = listMergeFieldMapping.get(mcTagName);
                    for (Map<String, String> fieldInfo : fields) {
                        String unomiId = fieldInfo.get(UNOMI_ID);
                        if (profile.getProperty(unomiId) != null) {
                            switch (mergeFieldDefinition.get(TYPE).asText()) {
                                case "address":
                                    if (mergeFields.has(mcTagName)) {
                                        mergeFields.getJSONObject(mcTagName).put(fieldInfo.get(MC_SUB_TAG_NAME), profile.getProperty(unomiId));
                                    } else {
                                        JSONObject address = new JSONObject();
                                        address.put("addr1", "");
                                        address.put("addr2", "");
                                        address.put("city", "");
                                        address.put("country", "");
                                        address.put("state", "");
                                        address.put("zip", "");
                                        address.put(fieldInfo.get(MC_SUB_TAG_NAME), profile.getProperty(unomiId));
                                        mergeFields.put(mcTagName, address);
                                    }
                                    break;
                                case "date":
                                    if (mergeFieldDefinition.has(OPTIONS) && mergeFieldDefinition.get(OPTIONS).has(DATE_FORMAT)) {
                                        String mcDateFormat = mergeFieldDefinition.get(OPTIONS).get(DATE_FORMAT).asText();
                                        DateTime dateTime = new DateTime(profile.getProperty(unomiId));
                                        DateTimeFormatter dateTimeFormatter;
                                        if (mcDateFormat.equals("MM/DD/YYYY")) {
                                            dateTimeFormatter = DateTimeFormat.forPattern("MM/dd/yyyy");
                                        } else {
                                            dateTimeFormatter = DateTimeFormat.forPattern("dd/MM/yyyy");
                                        }
                                        mergeFields.put(mcTagName, dateTimeFormatter.print(dateTime));
                                    }
                                    break;
                                case "birthday":
                                    if (mergeFieldDefinition.has(OPTIONS) && mergeFieldDefinition.get(OPTIONS).has(DATE_FORMAT)) {
                                        String mcDateFormat = mergeFieldDefinition.get(OPTIONS).get(DATE_FORMAT).asText();
                                        DateTime dateTime = new DateTime(profile.getProperty(unomiId));
                                        DateTimeFormatter dateTimeFormatter;
                                        if (mcDateFormat.equals("MM/DD")) {
                                            dateTimeFormatter = DateTimeFormat.forPattern("MM/dd");
                                        } else {
                                            dateTimeFormatter = DateTimeFormat.forPattern("dd/MM");
                                        }
                                        mergeFields.put(mcTagName, dateTimeFormatter.print(dateTime));
                                    }
                                    break;
                                default:
                                    mergeFields.put(mcTagName, profile.getProperty(unomiId));
                                    break;
                            }
                        }
                    }
                } else {
                    logger.warn("Found property {} in MC list, if you need this property please update mapping or add the property to your MC list", mcTagName);
                }
            }
        }

        return MailChimpResult.SUCCESS;
    }

    private JsonNode getMCListProperties(String listIdentifier) {
        JsonNode currentMergeFields = HttpUtils.executeGetRequest(httpClient, getBaseUrl() + "/lists/" + listIdentifier + "/merge-fields", getHeaders());
        if (currentMergeFields == null || !currentMergeFields.has(MERGE_FIELDS)) {
            logger.error("Can't find merge_fields from the response, the response was {}", currentMergeFields);
            return null;
        }

        return currentMergeFields;
    }


    private void initHttpClient() {
        if (httpClient == null) {
            httpClient = HttpUtils.initHttpClient();
        }
    }

    private boolean isMailChimpConnectorConfigured() {
        if (StringUtils.isNotBlank(apiKey) && StringUtils.isNotBlank(urlSubDomain)) {
            initHttpClient();
            return true;
        }
        logger.error("MailChimp extension isn't correctly configured, please check cfg file.");
        return false;
    }

    private boolean visitorHasMandatoryProperties(Profile profile) {
        if (profile.getProperty(FIRST_NAME) == null
                || profile.getProperty(LAST_NAME) == null
                || profile.getProperty(EMAIL) == null) {
            logger.warn("Visitor mandatory properties are missing");
            return false;
        }
        return true;
    }

    private JsonNode isMemberOfMailChimpList(Profile profile, String listIdentifier) {

        String email = profile.getProperty(EMAIL).toString().toLowerCase();
        JsonNode response = HttpUtils.executeGetRequest(httpClient, getBaseUrl() + "/lists/" + listIdentifier + "/members/", getHeaders());
        if (response != null) {
            if (response.has(MEMBERS)) {
                for (JsonNode member : response.get(MEMBERS)) {
                    if (member.get(EMAIL_ADDRESS).asText().toLowerCase().equals(email)) {
                        return member;
                    }
                }
            }
        }
        return null;
    }

    private MailChimpResult updateSubscription(String listIdentifier, String jsonData, JsonNode member, boolean toSubscribe) {
        JsonNode response = HttpUtils.executePatchRequest(httpClient, getBaseUrl() + "/lists/" + listIdentifier + "/members/" + member.get(ID).asText(), getHeaders(), jsonData);
        if (response != null) {
            if (response.has(STATUS)) {
                String responseStatus = response.get(STATUS).asText();
                if ((toSubscribe && responseStatus.equals(SUBSCRIBED)) || (!toSubscribe && responseStatus.equals(UNSUBSCRIBED))) {
                    return MailChimpResult.UPDATED;
                } else {
                    return MailChimpResult.NO_CHANGE;
                }
            }
        }
        logger.error("Couldn't update the subscription of the visitor");
        return MailChimpResult.ERROR;
    }

    private String getBaseUrl() {
        return "https://" + urlSubDomain + ".api.mailchimp.com/3.0";
    }

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "apikey " + apiKey);
        return headers;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setUrlSubDomain(String urlSubDomain) {
        this.urlSubDomain = urlSubDomain;
    }

    public void setListMergeFieldMapping(String listMergeFields) {
        this.listMergeFieldMapping = new HashMap<>();
        if (StringUtils.isNotBlank(listMergeFields)) {
            String mergeFields[] = StringUtils.split(listMergeFields, ",");
            if (mergeFields.length > 0) {
                for (String mergeField : mergeFields) {
                    if (StringUtils.isNotBlank(mergeField)) {
                        String mergeFieldInfo[] = StringUtils.split(mergeField, ":");
                        if (mergeFieldInfo.length > 0) {
                            Map<String, String> fieldInfo = new HashMap<>();
                            fieldInfo.put(UNOMI_ID, mergeFieldInfo[0]);
                            if (mergeFieldInfo.length == 3) {
                                fieldInfo.put(MC_SUB_TAG_NAME, mergeFieldInfo[2]);
                            }

                            String mcTagName = mergeFieldInfo[1];
                            if (listMergeFieldMapping.containsKey(mcTagName)) {
                                listMergeFieldMapping.get(mcTagName).add(fieldInfo);
                            } else {
                                List<Map<String, String>> fields = new ArrayList<>();
                                fields.add(fieldInfo);
                                listMergeFieldMapping.put(mcTagName, fields);
                            }
                        }
                    }
                }
            }
        }
    }

    public void setIsMergeFieldsActivate(Boolean isMergeFieldsActivate) {
        this.isMergeFieldsActivate = isMergeFieldsActivate;
    }
}
