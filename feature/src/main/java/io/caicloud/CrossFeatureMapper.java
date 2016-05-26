package io.caicloud;

import com.aliyun.odps.data.Record;
import com.aliyun.odps.mapred.MapperBase;

import java.io.IOException;

/**
 * CrossFeatureMapper.java
 *
 * @author hehuihui@meituan.com
 * @date 2016-05-20
 * @brief
 */


public class CrossFeatureMapper extends MapperBase {
    Record keyRecord;
    Record valueRecord;

    @Override
    public void setup(TaskContext context) throws IOException {
        keyRecord = context.createMapOutputKeyRecord();
        valueRecord = context.createMapOutputValueRecord();
    }

    @Override
    public void map(long key, Record record, TaskContext context) throws IOException {
        keyRecord.set(0, record.getBigint(2));  // item_id
        keyRecord.set(1, record.getBigint(3));  // store_code

        for (int i = 0; i < record.getColumnCount(); i++) {
            valueRecord.set(i, record.get(i));
        }
        context.write(keyRecord, valueRecord);
    }
}
