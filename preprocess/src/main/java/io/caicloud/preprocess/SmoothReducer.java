package io.caicloud.preprocess;

import com.aliyun.odps.data.Record;
import com.aliyun.odps.mapred.ReducerBase;
import io.caicloud.util.MyDateUtil;
import io.caicloud.util.MyUtil;

import java.io.IOException;
import java.util.*;

/**
 * SmoothReducer.java
 *
 * @author hehuihui@meituan.com
 * @date 2016-05-20
 * @brief
 */


public class SmoothReducer extends ReducerBase {
    private Record result;
    private MyDateUtil myDateUtil = new MyDateUtil();

    public void setup(TaskContext context) throws IOException {
        result = context.createOutputRecord();
    }

    @Override
    public void reduce(Record key, Iterator<Record> values, TaskContext context) throws IOException {
        // 记录当前item的销售起始日期
        long startDay = myDateUtil.END_DAY;
        Map<Long, Object[]> map = new HashMap<Long, Object[]>();
        while (values.hasNext()) {
            Record val = values.next();
            Long day = val.getBigint(0);
            if (day < startDay) {
                startDay = day;
            }
            map.put(day, val.toArray());
        }

        // 缺失值填充
//        int startIndex = myDateUtil.getDayMap().get(startDay);
//        Object[] firstRecord = map.get(startDay);
//        for (int i = startIndex; i < myDateUtil.getDayList().size(); i++) {
//            Long day = myDateUtil.getDayList().get(i);
//            if (!map.containsKey(day)) {
//                Object[] emptyRecords = new Object[32];
//                Arrays.fill(emptyRecords, 0);
//                emptyRecords[0] = day;
//                for (int j = 1; j < 7; j++) {
//                    emptyRecords[j] = firstRecord[j];
//                }
//                emptyRecords[13] = 0d;  //14:amt_gmv
//                emptyRecords[16] = 0d;  //17:amt_alipay
//                emptyRecords[29] = 0d;  //30:amt_alipay_njhs
//                map.put(day, emptyRecords);
//            }
//        }


        for (Map.Entry<Long, Object[]> entry : map.entrySet()) {
            Long day = entry.getKey();
            Object[] recordValue = entry.getValue();

            if (!myDateUtil.getPromotionDayMap().containsKey(day)) {
                result.set(recordValue);
                context.write(result);
                continue;
            }

            int index = -1;
            for (int i = 0; i < 7; i++) {
                result.set(++index, recordValue[i]);
            }

            long saleSum = 0;   // 记录销量之和
            int N = 0;          // 记录有效的日期数量

            // 获取前后一周的销量之和（不包含当天）
            for (int i = -7; i <= 7; i++) {
                if (i == 0) {
                    continue;
                }
                Long nearDay = MyUtil.getNearDay(day, i);
                Object[] nearRecord = map.get(nearDay);
                if (nearRecord != null) {
                    saleSum += MyUtil.parseLong(nearRecord[30]);
                    N++;
                }
            }

            // 计算销量均值
            double saleAvg = saleSum * 1.0 / Math.max(N, 1);

            long storeCode = MyUtil.parseLong(recordValue[2]);
            long sale = MyUtil.parseLong(recordValue[30]);

            // 如果当天销量 > 均值的2.5倍，而且当天销量>60(全国)15(地区)，则需进行平滑
            if (sale > saleAvg * 2.5) {
                if ((storeCode == 0 && sale > 60)
                        || (storeCode != 0 && sale > 15))
                {
                    double[] sumRecord = new double[32];
                    Arrays.fill(sumRecord, 0d);
                    int M = 0;
                    for (int i = -7; i <= 7; i++) {
                        if (i == 0) {
                            continue;
                        }
                        Long nearDay = MyUtil.getNearDay(day, -i);
                        Object[] nearRecord = map.get(nearDay);
                        if (nearRecord != null) {
                            for (int j = 7; j < 32; j++) {
                                sumRecord[j] += MyUtil.parseDouble(nearRecord[j]);
                            }
                            M++;
                        }
                    }

                    for (int j = 7; j < 32; j++) {
                        double avg = MyUtil.round(sumRecord[j] / Math.max(M, 1));
                        result.set(++index, avg);
                    }
                    context.write(result);
                    continue;
                }
            }

            // 获取各个时间段前N天的feature（包含当天）
            for (int j = 7; j < 32; j++) {
                result.set(++index, recordValue[j]);
            }
            context.write(result);
        }
    }

}
