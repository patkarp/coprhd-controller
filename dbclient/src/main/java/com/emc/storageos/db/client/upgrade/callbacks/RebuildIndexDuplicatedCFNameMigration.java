/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.CompositeColumnName;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.RowMutator;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.InternalDbClient;
import com.emc.storageos.db.client.upgrade.callbacks.FieldValueTimeUUIDPair.DuplciatedIndexDataObject;
import com.emc.storageos.db.common.schema.FieldInfo;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;
import com.google.common.collect.Sets;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.query.ColumnFamilyQuery;
import com.netflix.astyanax.util.TimeUUIDUtils;

public class RebuildIndexDuplicatedCFNameMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(RebuildIndexDuplicatedCFNameMigration.class);
    private int totalProcessedDataObjectCount = 0;
    private Set<Class<? extends DataObject>> scanClasses = Sets.newHashSet(FileShare.class, Snapshot.class);
    private DuplciatedIndexDataObject duplciatedIndexDataObject = new DuplciatedIndexDataObject();
    private Set<String> fieldNames = Sets.newHashSet("path", "mountPath");

    @Override
    public void process() throws MigrationCallbackException {
        log.info("Begin to run migration handler RebuildIndexDuplicatedCFName");
        long beginTime = System.currentTimeMillis();
        
        for (Class<? extends DataObject> clazz : scanClasses) {
            try {
                handleDataObjectClass(clazz);
            } catch (Exception e) {
                log.error("Failed to detect/rebuild duplicated index for {}", clazz);
            }
        }

        log.info("Total Data Object: {}", totalProcessedDataObjectCount);
        
        log.info("Finish run migration handler RebuildIndexDuplicatedCFName");
        log.info("Total seconds: {}", (System.currentTimeMillis() - beginTime)/1000);
    }

    public int handleDataObjectClass(Class<? extends DataObject> clazz) throws Exception {
        log.info("proccess model class {}", clazz);

        InternalDbClient dbClient = (InternalDbClient) getDbClient();
        DataObjectType doType = TypeMap.getDoType(clazz);
        
        int totalProcessedIndex = 0;
        Keyspace keyspace = dbClient.getLocalContext().getKeyspace();

        ColumnFamilyQuery<String, CompositeColumnName> query = keyspace.prepareQuery(doType.getCF());
        OperationResult<Rows<String, CompositeColumnName>> result = query.getAllRows().setRowLimit(100).execute();
        int totalCount = 0;
        for (Row<String, CompositeColumnName> objRow : result.getResult()) {
            boolean inactiveObject = false;
            totalProcessedDataObjectCount++;
            totalCount++;

            for (Column<CompositeColumnName> column : objRow.getColumns()) {
                if (DataObject.INACTIVE_FIELD_NAME.equals(column.getName().getOne()) && column.getBooleanValue()) {
                    inactiveObject = true;
                    break;
                }
            }

            // skip inactive data object
            if (inactiveObject) {
                continue;
            }

            totalProcessedIndex += proccessDataObjectTypeByIndexCF(doType, objRow, keyspace);
        }
        
        log.info("Total data object count is {} for model {}", totalCount, clazz.getName());
        return totalProcessedIndex;
    }

    public int proccessDataObjectTypeByIndexCF(DataObjectType doType, Row<String, CompositeColumnName> objRow, Keyspace keyspace) throws InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException {
        Map<FieldValueTimeUUIDPair, CompositeColumnName> valueColumnMap = new HashMap<FieldValueTimeUUIDPair, CompositeColumnName>();
        int totalCleanupCount = 0;

        DataObject dataObject = DataObject.createInstance(doType.getDataObjectClass(), URI.create(objRow.getKey()));
        for (Column<CompositeColumnName> column : objRow.getColumns()) {
            if (!fieldNames.contains(column.getName().getOne())) {
                continue;
            }

            ColumnField columnField = doType.getColumnField(column.getName().getOne());
            columnField.deserialize(column, dataObject);
            Object valueObject = columnField.getPropertyDescriptor().getReadMethod().invoke(dataObject, null);

            if (valueObject == null || column.getName().getTimeUUID() == null) {
                continue;
            }
            
            FieldValueTimeUUIDPair key = null;
            key = new FieldValueTimeUUIDPair(valueObject, column.getName().getTimeUUID());
            
            
            if (valueColumnMap.containsKey(key)) {
                totalCleanupCount++;
                /*log.info("Found duplicated index value: {}", key);
                log.info("Column1: {}", column.getName());
                log.info("Column2: {}", valueColumnMap.get(key));*/
                rebuildIndex(doType, columnField, valueObject, objRow.getKey(), column, keyspace);
            } else {
                valueColumnMap.put(key, column.getName());
            }
        }

        if (totalCleanupCount > 0) {
            log.info("Total cleanup {} for {}", totalCleanupCount, objRow.getKey());
        }
        return totalCleanupCount;
    }

    private void rebuildIndex(DataObjectType doType, ColumnField columnField, Object value, String rowKey,
            Column<CompositeColumnName> column, Keyspace keyspace) throws InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException {
        RowMutator rowMutator = new RowMutator(keyspace, false, TimeUUIDUtils.getMicrosTimeFromUUID(column.getName().getTimeUUID()));
        rowMutator.getRecordColumnList(doType.getCF(), rowKey).deleteColumn(column.getName());

        DataObject dataObject = DataObject.createInstance(doType.getDataObjectClass(), URI.create(rowKey));
        dataObject.trackChanges();
        if (columnField.getType() == ColumnField.ColumnType.TrackingSet) {
            StringSet stringSet = new StringSet();
            stringSet.add(column.getStringValue());
            value = stringSet;
        }
        columnField.getPropertyDescriptor().getWriteMethod().invoke(dataObject, value);
        columnField.serialize(dataObject, rowMutator);
        
        rowMutator.execute();
    } 
}

class FieldValueTimeUUIDPair {
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("FieldValueTimeUUIDPair [fieldValue=");
        builder.append(fieldValue);
        builder.append(", timeUUID=");
        builder.append(timeUUID);
        builder.append("]");
        return builder.toString();
    }

    private Object fieldValue;
    private UUID timeUUID;

    public FieldValueTimeUUIDPair(Object fieldValue, UUID timeUUID) {
        super();
        this.fieldValue = fieldValue;
        this.timeUUID = timeUUID;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((fieldValue == null) ? 0 : fieldValue.hashCode());
        result = prime * result + ((timeUUID == null) ? 0 : timeUUID.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FieldValueTimeUUIDPair other = (FieldValueTimeUUIDPair) obj;
        if (fieldValue == null) {
            if (other.fieldValue != null)
                return false;
        } else if (!fieldValue.equals(other.fieldValue))
            return false;
        if (timeUUID == null) {
            if (other.timeUUID != null)
                return false;
        } else if (!timeUUID.equals(other.timeUUID))
            return false;
        return true;
    }
    
    public static class DuplciatedIndexDataObject {
        private String className;
        private String dataCFName;
        private Map<IndexCFKey, List<FieldInfo>> indexFieldsMap = new HashMap<IndexCFKey, List<FieldInfo>>();

        public String getDataCFName() {
            return dataCFName;
        }

        public void setDataCFName(String dataCFName) {
            this.dataCFName = dataCFName;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public Map<IndexCFKey, List<FieldInfo>> getIndexFieldsMap() {
            return indexFieldsMap;
        }

        public void setIndexFieldsMap(Map<IndexCFKey, List<FieldInfo>> indexFieldsMap) {
            this.indexFieldsMap = indexFieldsMap;
        }
    }
    
    public static class IndexCFKey {
        private String index;
        private String cf;
        private String fieldTypeClass;
        
        public IndexCFKey(String index, String cf, String fieldTypeClass) {
            super();
            this.index = index;
            this.cf = cf;
            this.fieldTypeClass = fieldTypeClass;
        }

        public String getIndex() {
            return index;
        }

        public String getCf() {
            return cf;
        }

        public String getFieldTypeClass() {
            return fieldTypeClass;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((cf == null) ? 0 : cf.hashCode());
            result = prime * result + ((fieldTypeClass == null) ? 0 : fieldTypeClass.hashCode());
            result = prime * result + ((index == null) ? 0 : index.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            IndexCFKey other = (IndexCFKey) obj;
            if (cf == null) {
                if (other.cf != null)
                    return false;
            } else if (!cf.equals(other.cf))
                return false;
            if (fieldTypeClass == null) {
                if (other.fieldTypeClass != null)
                    return false;
            } else if (!fieldTypeClass.equals(other.fieldTypeClass))
                return false;
            if (index == null) {
                if (other.index != null)
                    return false;
            } else if (!index.equals(other.index))
                return false;
            return true;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("IndexCFKey [index=");
            builder.append(index);
            builder.append(", cf=");
            builder.append(cf);
            builder.append(", fieldTypeClass=");
            builder.append(fieldTypeClass);
            builder.append("]");
            return builder.toString();
        }
    }
}