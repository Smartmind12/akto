package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SampleDataDao extends AccountsContextDao<SampleData> {

    public static final SampleDataDao instance = new SampleDataDao();

    @Override
    public String getCollName() {
        return "sample_data";
    }

    @Override
    public Class<SampleData> getClassT() {
        return SampleData.class;
    }

    public void createIndicesIfAbsent() {

        String dbName = Context.accountId.get()+"";
        createCollectionIfAbsent(dbName, getCollName(), new CreateCollectionOptions());

        List<String[]> ascIndices = Arrays.asList(
                new String[]{SingleTypeInfo._COLLECTION_IDS, "_id.url", "_id.method"},
                new String[]{SingleTypeInfo._COLLECTION_IDS}
        );

        for(String [] keys: ascIndices) {
            Bson index = Indexes.ascending(keys);
            createIndexIfAbsent(dbName, getCollName(), index, new IndexOptions().name(createName(keys, 1)));
        }

    }

    public List<SampleData> fetchSampleDataPaginated(int apiCollectionId, String lastFetchedUrl,
                                                     String lastFetchedMethod, int limit, int sliceLimit) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.in(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(apiCollectionId)));

        if (lastFetchedUrl != null && lastFetchedMethod != null) {
            Bson f1 = Filters.gt("_id.url", lastFetchedUrl);
            Bson f2 = Filters.and(
                    Filters.eq("_id.url", lastFetchedUrl),
                    Filters.gt("_id.method", lastFetchedMethod)
            );

            filters.add(
                    Filters.or(f1, f2)
            );
        }

        Bson sort = Sorts.ascending("_id.url", "_id.method");

        MongoCursor<SampleData> cursor = SampleDataDao.instance.getMCollection()
                .find(Filters.and(filters))
                .projection(Projections.slice("samples", sliceLimit))
                .skip(0)
                .limit(limit)
                .sort(sort)
                .cursor();

        List<SampleData> sampleDataList = new ArrayList<>();

        while (cursor.hasNext()) {
            SampleData sampleData = cursor.next();
            sampleDataList.add(sampleData);
        }

        cursor.close();

        return sampleDataList;
    }


}
