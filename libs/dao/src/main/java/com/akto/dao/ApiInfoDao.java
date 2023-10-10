package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ApiInfoDao extends AccountsContextDao<ApiInfo>{

    public static ApiInfoDao instance = new ApiInfoDao();

    public void createIndicesIfAbsent() {

        String dbName = Context.accountId.get()+"";
        createCollectionIfAbsent(dbName, getCollName(), new CreateCollectionOptions());

        List<String[]> ascIndices = Arrays.asList(
                new String[]{SingleTypeInfo._COLLECTION_IDS},
                new String[]{"_id.url"},
                new String[]{SingleTypeInfo._COLLECTION_IDS, "_id.url"}
        );

        for(String [] keys: ascIndices) {
            Bson index = Indexes.ascending(keys);
            createIndexIfAbsent(dbName, getCollName(), index, new IndexOptions().name(createName(keys, 1)));
        }
    }

    @Override
    public String getCollName() {
        return "api_info";
    }

    @Override
    public Class<ApiInfo> getClassT() {
        return ApiInfo.class;
    }

    public static Bson getFilter(ApiInfo.ApiInfoKey apiInfoKey) {
        return getFilter(apiInfoKey.getUrl(), apiInfoKey.getMethod().name(), apiInfoKey.getApiCollectionId());
    }

    public static Bson getFilter(String url, String method, int apiCollectionId) {
        return Filters.and(
                Filters.eq("_id.url", url),
                Filters.eq("_id.method", method),
                Filters.in(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(apiCollectionId))
        );
    }

}
