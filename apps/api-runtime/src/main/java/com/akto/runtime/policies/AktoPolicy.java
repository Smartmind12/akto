package com.akto.runtime.policies;

import com.akto.DaoInit;
import com.akto.dao.*;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.FilterSampleData;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.HttpResponseParams.Source;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.type.URLMethods;
import com.akto.types.CappedList;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class AktoPolicy {
    private List<RuntimeFilter> filters;
    private Map<ApiInfo.ApiInfoKey, ApiInfo> apiInfoMap;
    private List<ApiInfo.ApiInfoKey> apiInfoRemoveList ;
    private List<ApiInfo.ApiInfoKey> sampleDataRemoveList ;
    Map<ApiInfo.ApiInfoKey,Map<Integer, CappedList<String>>> sampleMessages = new HashMap<>();
    private boolean processCalledAtLeastOnce = false;
    ApiAccessTypePolicy apiAccessTypePolicy = new ApiAccessTypePolicy(null);

    private final int batchTimeThreshold = 60;
    private int timeSinceLastSync;
    private final int batchSizeThreshold = 1000;
    private int currentBatchSize = 0;

    private static final Logger logger = LoggerFactory.getLogger(AktoPolicy.class);

    public void fetchFilters() {
        this.filters = RuntimeFilterDao.instance.findAll(new BasicDBObject());
    }

    public AktoPolicy() {
        syncWithDb(true);
        this.timeSinceLastSync = Context.now();
    }

    public void syncWithDb(boolean initialising) {
        logger.info("Syncing with db");
        if (!initialising) {
            List<WriteModel<ApiInfo>> writesForApiInfo = AktoPolicy.getUpdates(apiInfoMap, apiInfoRemoveList);
            List<WriteModel<FilterSampleData>> writesForSampleData = getUpdatesForSampleData();
            logger.info("Writing to db: " + "writesForApiInfoSize="+writesForApiInfo.size() + " writesForSampleData="+ writesForSampleData.size());
            if (writesForApiInfo.size() > 0) ApiInfoDao.instance.getMCollection().bulkWrite(writesForApiInfo);
            if (writesForSampleData.size() > 0) FilterSampleDataDao.instance.getMCollection().bulkWrite(writesForSampleData);
        }

        fetchFilters();

        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(new BasicDBObject());
        if (accountSettings != null) {
            List<String> cidrList = accountSettings.getPrivateCidrList();
            if ( cidrList != null && !cidrList.isEmpty()) {
                logger.info("Found cidr from db");
                apiAccessTypePolicy.setPrivateCidrList(cidrList);
            }
        }

        apiInfoMap = new HashMap<>();

        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        apiInfoRemoveList = new ArrayList<>();
        for (ApiCollection apiCollection: apiCollections) {
            for (String u: apiCollection.getUrls()) {
                String[] v = u.split(" ");
                ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(apiCollection.getId(), v[0], URLMethods.Method.valueOf(v[1]));
                apiInfoMap.put(apiInfoKey, null);
            }
        }

        List<ApiInfo> apiInfoList =  ApiInfoDao.instance.findAll(new BasicDBObject());
        for (ApiInfo apiInfo: apiInfoList) {
            ApiInfo.ApiInfoKey apiInfoKey = apiInfo.getId();
            if (apiInfoMap.containsKey(apiInfoKey)) {
                apiInfoMap.put(apiInfoKey, apiInfo);
            } else {
                apiInfoRemoveList.add(apiInfoKey);
            }
        }

        sampleDataRemoveList = new ArrayList<>();
        List<ApiInfo.ApiInfoKey> filterSampleDataIdList = FilterSampleDataDao.instance.getIds();
        for (ApiInfo.ApiInfoKey apiInfoKey: filterSampleDataIdList) {
            if (apiInfoMap.containsKey(apiInfoKey)) {
                sampleMessages.put(apiInfoKey, new HashMap<>());
            } else {
                sampleDataRemoveList.add(apiInfoKey);
            }
        }

    }

    public void main(List<HttpResponseParams> httpResponseParamsList) throws Exception {
        boolean syncImmediately = false;

        for (HttpResponseParams httpResponseParams: httpResponseParamsList) {
            process(httpResponseParams);
            if (httpResponseParams.getSource().equals(Source.HAR) || httpResponseParams.getSource().equals(Source.PCAP)) {
                syncImmediately = true;
            }
            currentBatchSize += 1;
        }

        if (syncImmediately || currentBatchSize >= batchSizeThreshold || (Context.now() -  timeSinceLastSync) >= batchTimeThreshold) {
            syncWithDb(false);
            this.currentBatchSize = 0;
            this.timeSinceLastSync = Context.now();
        }
    }

    public static void main(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://172.18.0.2:27017/admini"));
        Context.accountId.set(1_000_000);
        RuntimeFilterDao.instance.initialiseFilters();
//        RuntimeFilterDao.instance.initialiseFilters();
//        List<CustomFilter> customFilterList = new ArrayList<>();
//        customFilterList.add(new ResponseCodeRuntimeFilter(200,299));
//        customFilterList.add(new FieldExistsFilter("labelId"));
//        RuntimeFilter runtimeFilter = new RuntimeFilter(Context.now(),RuntimeFilter.UseCase.SET_CUSTOM_FIELD, "Check labelId", customFilterList, RuntimeFilter.Operator.AND, "check_label_id");
//        RuntimeFilterDao.instance.insertOne(runtimeFilter);
    }

    public void process(HttpResponseParams httpResponseParams) throws Exception {
        if (!this.processCalledAtLeastOnce) {
            syncWithDb(true);
            this.processCalledAtLeastOnce = true;
        }

        ApiInfo.ApiInfoKey key = getApiInfoMapKey(httpResponseParams);
        if (key == null) return;
        ApiInfo apiInfo = apiInfoMap.get(key);
        if (apiInfo == null) {
            apiInfo = new ApiInfo(httpResponseParams);
            apiInfo.setId(key);
        }

        for (RuntimeFilter filter: filters) {
            boolean filterResult = filter.process(httpResponseParams);
            if (!filterResult) continue;

            RuntimeFilter.UseCase useCase = filter.getUseCase();
            boolean saveSample = false;
            switch (useCase) {
                case AUTH_TYPE:
                    saveSample = AuthPolicy.findAuthType(httpResponseParams, apiInfo, filter);
                    break;
                case SET_CUSTOM_FIELD:
                    saveSample = SetFieldPolicy.setField(httpResponseParams, apiInfo, filter);
                    break;
                case DETERMINE_API_ACCESS_TYPE:
                    saveSample = apiAccessTypePolicy.findApiAccessType(httpResponseParams, apiInfo, filter);
                    break;
                default:
                    throw new Exception("Function for use case not defined");
            }

            // add sample data
            if (saveSample) {
                Map<Integer, CappedList<String>> sampleData = sampleMessages.get(key);
                if (sampleData == null) {
                    sampleData = new HashMap<>();
                }
                CappedList<String> d = sampleData.getOrDefault(filter.getId(),new CappedList<String>(FilterSampleData.cap, true));
                d.add(httpResponseParams.getOrig());
                sampleData.put(filter.getId(), d);
                sampleMessages.put(key, sampleData);
            }
        }

        apiInfo.setLastSeen(Context.now());
        apiInfoMap.put(key, apiInfo);
    }

    public static ApiInfo.ApiInfoKey getApiInfoMapKey(ApiInfo.ApiInfoKey apiInfoKey, Set<ApiInfo.ApiInfoKey> apiInfoKeySet)  {
        // strict check
        if (apiInfoKeySet.contains(apiInfoKey)) {
            return apiInfoKey;
        }
        System.out.println("strict check failed for " + apiInfoKey.getUrl());

        // template check
        for (ApiInfo.ApiInfoKey key: apiInfoKeySet) {
            // 1. match collection id
            if (key.getApiCollectionId() != apiInfoKey.getApiCollectionId()) continue;
            // 2. match method
            if (!key.getMethod().equals(apiInfoKey.getMethod())) continue;
            // 3. match url
            String keyUrl = key.getUrl();
            // a. check if template url
            if (! (keyUrl.contains("STRING") || keyUrl.contains("INTEGER"))) continue;
            // b. check if template url matches
            System.out.println("checking template url");
            String[] a = keyUrl.split("/");
            String[] b = apiInfoKey.getUrl().split("/");
            if (a.length != b.length) continue;
            boolean flag = true;
            for (int i =0; i < a.length; i++) {
                if (!Objects.equals(a[i], b[i])) {
                    if (!(Objects.equals(a[i], "STRING") || Objects.equals(a[i], "INTEGER"))) {
                        flag = false;
                    }
                }
            }

            // TODO: case when empty list
            if (flag) {
                System.out.println("SUCCESS: " + key.getUrl());
                return key;
            }

        }

        // else discard with log
        System.out.println("FAILED");
        return null;

    }

    public ApiInfo.ApiInfoKey getApiInfoMapKey(HttpResponseParams httpResponseParams)  {
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(
                httpResponseParams.getRequestParams().getApiCollectionId(),
                httpResponseParams.getRequestParams().getURL(),
                URLMethods.Method.valueOf(httpResponseParams.getRequestParams().getMethod())
        );

        return getApiInfoMapKey(apiInfoKey, this.apiInfoMap.keySet());
    }

    public static List<WriteModel<ApiInfo>> getUpdates(Map<ApiInfo.ApiInfoKey, ApiInfo> apiInfoMap , List<ApiInfo.ApiInfoKey> apiInfoRemoveList) {
        List<ApiInfo> apiInfoList =  ApiInfoDao.instance.findAll(new BasicDBObject());
        Map<ApiInfo.ApiInfoKey, ApiInfo> dbApiInfoMap = new HashMap<>();
        for (ApiInfo apiInfo: apiInfoList) {
            dbApiInfoMap.put(apiInfo.getId(), apiInfo);
        }

        List<WriteModel<ApiInfo>> updates = new ArrayList<>();

        for (ApiInfo.ApiInfoKey key: apiInfoMap.keySet()) {
            ApiInfo apiInfo = apiInfoMap.get(key);
            if (apiInfo == null) {
                apiInfo = new ApiInfo(key);
            }
            ApiInfo originalApiInfo = dbApiInfoMap.get(key);
            if (originalApiInfo == null) {
                originalApiInfo = new ApiInfo(key);
            }

            List<Bson> subUpdates = new ArrayList<>();

            // allAuthTypesFound
            Set<Set<ApiInfo.AuthType>> allAuthTypesFound = apiInfo.getAllAuthTypesFound();
            if (allAuthTypesFound.isEmpty()) {
                subUpdates.add(Updates.set(ApiInfo.ALL_AUTH_TYPES_FOUND, new HashSet<>()));
            }
            for (Set<ApiInfo.AuthType> authTypes: allAuthTypesFound) {
                Set<Set<ApiInfo.AuthType>> originalAllAuthTypesFound = originalApiInfo.getAllAuthTypesFound();
                if (!originalAllAuthTypesFound.contains(authTypes)) {
                    subUpdates.add(Updates.addToSet(ApiInfo.ALL_AUTH_TYPES_FOUND, authTypes));
                }
            }

            // apiAccessType
            Set<ApiInfo.ApiAccessType> apiAccessTypes = apiInfo.getApiAccessTypes();
            if (apiAccessTypes.isEmpty()) {
                subUpdates.add(Updates.set(ApiInfo.API_ACCESS_TYPES, new HashSet<>()));
            }
            for (ApiInfo.ApiAccessType apiAccessType: apiAccessTypes) {
                if (!(originalApiInfo.getApiAccessTypes().contains(apiAccessType))) {
                    subUpdates.add(Updates.addToSet(ApiInfo.API_ACCESS_TYPES, apiAccessType));
                }
            }

            // violations
            Map<String,Integer> violationsMap = apiInfo.getViolations();
            if (violationsMap.isEmpty()) {
                subUpdates.add(Updates.set(ApiInfo.VIOLATIONS, new HashMap<>()));
            }
            for (String customKey: violationsMap.keySet()) {
                subUpdates.add(Updates.set(ApiInfo.VIOLATIONS + "." + customKey, violationsMap.get(customKey)));
            }

            // last seen
            subUpdates.add(Updates.set(ApiInfo.LAST_SEEN, apiInfo.getLastSeen()));

            updates.add(
                    new UpdateOneModel<>(
                            ApiInfoDao.getFilter(apiInfo.getId().url, apiInfo.getId().method+"", apiInfo.getId().getApiCollectionId()),
                            Updates.combine(subUpdates),
                            new UpdateOptions().upsert(true)
                    )
            );
        }


        for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoRemoveList) {
            updates.add(
                    new DeleteOneModel<>(
                            ApiInfoDao.getFilter(apiInfoKey.getUrl(), apiInfoKey.getMethod() +"", apiInfoKey.getApiCollectionId())
                    )
            );
        }

        return updates;
    }

    public List<WriteModel<FilterSampleData>> getUpdatesForSampleData() {
        ArrayList<WriteModel<FilterSampleData>> bulkUpdates = new ArrayList<>();

        for (ApiInfo.ApiInfoKey apiInfoKey: sampleMessages.keySet()) {
            Map<Integer, CappedList<String>> filterSampleDataMap = sampleMessages.get(apiInfoKey);
            for (Integer filterId: filterSampleDataMap.keySet()) {
                List<String> sampleData = filterSampleDataMap.get(filterId).get();
                Bson bson = Updates.pushEach(FilterSampleData.SAMPLES, sampleData, new PushOptions().slice(-1 * FilterSampleData.cap));
                bulkUpdates.add(
                        new UpdateOneModel<>(
                                Filters.and(
                                        Filters.eq(FilterSampleData.FILTER_ID,  filterId),
                                        ApiInfoDao.getFilter(apiInfoKey)
                                ),
                                bson,
                                new UpdateOptions().upsert(true)
                        )
                );
            }
        }

        for (ApiInfo.ApiInfoKey apiInfoKey: sampleDataRemoveList) {
            bulkUpdates.add(
                    new DeleteOneModel<>(
                            ApiInfoDao.getFilter(apiInfoKey.getUrl(), apiInfoKey.getMethod() +"", apiInfoKey.getApiCollectionId())
                    )
            );
        }

        return bulkUpdates;
    }

    public List<RuntimeFilter> getFilters() {
        return filters;
    }

    public void setFilters(List<RuntimeFilter> filters) {
        this.filters = filters;
    }

    public Map<ApiInfo.ApiInfoKey, ApiInfo> getApiInfoMap() {
        return apiInfoMap;
    }

    public void setApiInfoMap(Map<ApiInfo.ApiInfoKey, ApiInfo> apiInfoMap) {
        this.apiInfoMap = apiInfoMap;
    }

    public List<ApiInfo.ApiInfoKey> getApiInfoRemoveList() {
        return apiInfoRemoveList;
    }

    public void setApiInfoRemoveList(List<ApiInfo.ApiInfoKey> apiInfoRemoveList) {
        this.apiInfoRemoveList = apiInfoRemoveList;
    }

    public Map<ApiInfo.ApiInfoKey, Map<Integer, CappedList<String>>> getSampleMessages() {
        return sampleMessages;
    }

    public void setSampleMessages(Map<ApiInfo.ApiInfoKey, Map<Integer, CappedList<String>>> sampleMessages) {
        this.sampleMessages = sampleMessages;
    }

    public boolean isProcessCalledAtLeastOnce() {
        return processCalledAtLeastOnce;
    }

    public void setProcessCalledAtLeastOnce(boolean processCalledAtLeastOnce) {
        this.processCalledAtLeastOnce = processCalledAtLeastOnce;
    }

    public ApiAccessTypePolicy getApiAccessTypePolicy() {
        return apiAccessTypePolicy;
    }

    public void setApiAccessTypePolicy(ApiAccessTypePolicy apiAccessTypePolicy) {
        this.apiAccessTypePolicy = apiAccessTypePolicy;
    }

    public List<ApiInfo.ApiInfoKey> getSampleDataRemoveList() {
        return sampleDataRemoveList;
    }

    public void setSampleDataRemoveList(List<ApiInfo.ApiInfoKey> sampleDataRemoveList) {
        this.sampleDataRemoveList = sampleDataRemoveList;
    }
}
