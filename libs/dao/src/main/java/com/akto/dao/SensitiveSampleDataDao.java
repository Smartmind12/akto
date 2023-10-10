package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.SensitiveSampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import java.util.Arrays;
import org.bson.conversions.Bson;

public class SensitiveSampleDataDao extends AccountsContextDao<SensitiveSampleData>{

    public static final SensitiveSampleDataDao instance = new SensitiveSampleDataDao();
    @Override
    public String getCollName() {
        return "sensitive_sample_data";
    }

    @Override
    public Class<SensitiveSampleData> getClassT() {
        return SensitiveSampleData.class;
    }

    public static Bson getFilters(SingleTypeInfo singleTypeInfo) {
        return Filters.and(
                Filters.eq("_id.url", singleTypeInfo.getUrl()),
                Filters.eq("_id.method", singleTypeInfo.getMethod()),
                Filters.eq("_id.responseCode", singleTypeInfo.getResponseCode()),
                Filters.eq("_id.isHeader", singleTypeInfo.getIsHeader()),
                Filters.eq("_id.param", singleTypeInfo.getParam()),
                Filters.eq("_id.subType", singleTypeInfo.getSubType().getName()),
                Filters.in(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(singleTypeInfo.getApiCollectionId()))
        );
    }

    public void createIndicesIfAbsent() {

        String dbName = Context.accountId.get()+"";
        createCollectionIfAbsent(dbName, getCollName(), new CreateCollectionOptions());

        String[] keys = new String[]{"_id.url", SingleTypeInfo._COLLECTION_IDS, "_id.method"};
        Bson index = Indexes.ascending(keys);
        createIndexIfAbsent(dbName, getCollName(), index, new IndexOptions().name(createName(keys, 1)));
    }
}
