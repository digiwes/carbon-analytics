/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.analytics.spark.core;

import org.apache.spark.Dependency;
import org.apache.spark.InterruptibleIterator;
import org.apache.spark.Partition;
import org.apache.spark.SparkContext;
import org.apache.spark.TaskContext;
import org.apache.spark.rdd.RDD;
import org.apache.spark.sql.catalyst.expressions.Row;
import org.wso2.carbon.analytics.datasource.core.AnalyticsException;
import org.wso2.carbon.analytics.datasource.core.Record;
import org.wso2.carbon.analytics.datasource.core.RecordGroup;

import scala.collection.Seq;
import scala.reflect.ClassTag;

import java.io.Serializable;
import java.util.List;
import java.util.Iterator;
import java.util.Map;

import static scala.collection.JavaConversions.asScalaIterator;

/**
 * This class represents Spark analytics RDD implementation.
 */
public class AnalyticsRDD extends RDD<Row> implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private List<String> columns;
    
    private int tenantId;
    
    private String tableName;
    
    public AnalyticsRDD(int tenantId, String tableName, List<String> columns, 
            SparkContext sc, Seq<Dependency<?>> deps, ClassTag<Row> evidence) {
        super(sc, deps, evidence);
        this.tenantId = tenantId;
        this.tableName = tableName;
        this.columns = columns;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public scala.collection.Iterator<Row> compute(Partition split, TaskContext context) {
        AnalyticsPartition partition = (AnalyticsPartition) split;
        try {
            Iterator<Record> recordsItr = AnalyticsServiceHolder.getAnalyticsDataService().readRecords(partition.getRecordGroup());            
            return new InterruptibleIterator(context, asScalaIterator(new RowRecordIteratorAdaptor(recordsItr)));
        } catch (AnalyticsException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        
    }
    
    private class RowRecordIteratorAdaptor implements Iterator<Row> {

        private Iterator<Record> recordItr;
        
        public RowRecordIteratorAdaptor(Iterator<Record> recordItr) {
            this.recordItr = recordItr;
        }
        
        @Override
        public boolean hasNext() {
            return this.recordItr.hasNext();
        }

        @Override
        public Row next() {
            return this.recordToRow(this.recordItr.next());           
        }
        
        private Row recordToRow(Record record) {
            if (record == null) {
                return null;
            }
            Map<String, Object> recordVals = record.getValues();
            Object[] rowVals = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                rowVals[i] = recordVals.get(columns.get(i));
            }
            return org.apache.spark.sql.api.java.Row.create(rowVals).row();
        }

        @Override
        public void remove() {
            this.recordItr.remove();
        }
        
    }

    @Override
    public Partition[] getPartitions() {
        RecordGroup[] rgs;
        try {
            rgs = AnalyticsServiceHolder.getAnalyticsDataService().get(this.tenantId, this.tableName, 
                    this.columns, -1, -1, 0, Integer.MAX_VALUE);
        } catch (AnalyticsException e) {
            throw new RuntimeException(e.getMessage(), e);
        } 
        Partition[] result = new Partition[rgs.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = new AnalyticsPartition(rgs[i]);
        }
        return result;
    }

}
