/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.PasswordHistory;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;
import com.netflix.astyanax.model.Column;
import com.netflix.astyanax.model.ColumnFamily;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.model.CqlResult;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.query.ColumnFamilyQuery;
import com.netflix.astyanax.serializers.CompositeRangeBuilder;
import com.netflix.astyanax.serializers.StringSerializer;
import com.netflix.astyanax.util.RangeBuilder;
import com.netflix.astyanax.util.TimeUUIDUtils;

public class DbConsistencyCheckerHelper {
    private static final Logger _log = LoggerFactory.getLogger(DbConsistencyCheckerHelper.class);
    private static final int INDEX_OBJECTS_BATCH_SIZE = 1000;

    private static final String DELETE_INDEX_CQL = "delete from \"%s\" where key='%s' and column1='%s' and column2='%s' and column3='%s' and column4='%s' and column5=%s;";
    private static final String DELETE_INDEX_CQL_WITHOUT_UUID = "delete from \"%s\" where key='%s' and column1='%s' and column2='%s' and column3='%s' and column4='%s';";
    private static final String CQL_QUERY_SCHEMA_VERSION_TIMESTAMP = "SELECT key, writetime(value) FROM \"SchemaRecord\";";

    private DbClientImpl dbClient;
    private Set<Class<? extends DataObject>> excludeClasses = new HashSet<Class<? extends DataObject>>(Arrays.asList(PasswordHistory.class));
    private Map<Long, String> schemaVersionsTime;
    
    public DbConsistencyCheckerHelper() {
    }

    public DbConsistencyCheckerHelper(DbClientImpl dbClient) {
        this.dbClient = dbClient;
        schemaVersionsTime = querySchemaVersions();
    }

    /**
     * Find out all rows in DataObject CFs that can't be deserialized,
     * such as such as object id cannot be converted to URI.
     *
     * @return number of corrupted rows
     */
    public int checkDataObject(DataObjectType doType, boolean toConsole) {
        int dirtyCount = 0;
        Class<? extends DataObject> dataObjectClass = doType.getDataObjectClass();
        
		_log.info("Check CF {}", dataObjectClass.getName());
        
        if (excludeClasses.contains(dataObjectClass)) {
        	_log.info("Skip CF {} since its URI is special", dataObjectClass);
        	return 0;
        }
        
        try {
            OperationResult<Rows<String, CompositeColumnName>> result = dbClient.getKeyspace(
                    dataObjectClass).prepareQuery(doType.getCF())
                    .getAllRows().setRowLimit(dbClient.DEFAULT_PAGE_SIZE)
                    .withColumnRange(new RangeBuilder().setLimit(1).build())
                    .execute();
            for (Row<String, CompositeColumnName> row : result.getResult()) {
            	try {
	            	if (!isValidDataObjectKey(URI.create(row.getKey()), dataObjectClass)) {
	            		dirtyCount++;
	    		        logMessage(String.format("Inconsistency found: Row key '%s' failed to convert to URI in CF %s",
	    		                row.getKey(), dataObjectClass.getName()), true, toConsole);
	            	}
            	} catch (Exception ex) {
            		dirtyCount++;
            		logMessage(String.format("Inconsistency found: Row key '%s' failed to convert to URI in CF %s with exception %s",
                            row.getKey(), dataObjectClass.getName(),
                            ex.getMessage()), true, toConsole);
            	}
            }

        } catch (ConnectionException e) {
            throw DatabaseException.retryables.connectionFailed(e);
        }

        return dirtyCount;
    }

    /**
     * Scan all the data object records, to find out the object record is existing but the related index is missing.
     *
     * @param doType
     * @param toConsole whether print out in the console
     * @return the number of corrupted data
     * @throws ConnectionException
     */
    public void checkCFIndices(DataObjectType doType, boolean toConsole, CheckResult checkResult) throws ConnectionException {
        Class objClass = doType.getDataObjectClass();
        _log.info("Check Data Object CF {}", objClass);

        Map<String, ColumnField> indexedFields = new HashMap<String, ColumnField>();
        for (ColumnField field : doType.getColumnFields()) {
            if (field.getIndex() != null) {
            	indexedFields.put(field.getName(), field);
            }
        }

        if (indexedFields.isEmpty()) {
            return;
        }

        Keyspace keyspace = dbClient.getKeyspace(objClass);
        ColumnFamilyQuery<String, CompositeColumnName> query = keyspace.prepareQuery(doType.getCF());
        OperationResult<Rows<String, CompositeColumnName>> result = query.getAllRows().setRowLimit(dbClient.DEFAULT_PAGE_SIZE).execute();

        for (Row<String, CompositeColumnName> objRow : result.getResult()) {
            boolean inactiveObject = false;
            
            for (Column<CompositeColumnName> column : objRow.getColumns()) {
                if (column.getName().getOne().equals(DataObject.INACTIVE_FIELD_NAME) && column.getBooleanValue()) {
                	inactiveObject = true;
                	break;
                }
            }
            
            if (inactiveObject) {
            	continue;
            }

            for (Column<CompositeColumnName> column : objRow.getColumns()) {
            	if (!indexedFields.containsKey(column.getName().getOne())) {
            		continue;
            	}
            	
            	// we don't build index if the value is null, refer to ColumnField.
                if (!column.hasValue()) {
                    continue;
                }
            	
            	ColumnField indexedField = indexedFields.get(column.getName().getOne());
            	String indexKey = getIndexKey(indexedField, column);
            	
                if (indexKey == null) {
                    continue;
                }
                
                boolean isColumnInIndex = isColumnInIndex(keyspace, indexedField.getIndexCF(), indexKey,
                        getIndexColumns(indexedField, column, objRow.getKey()));
                
                if (!isColumnInIndex) {
                    String dbVersion = findDataCreatedInWhichDBVersion(column.getName().getTimeUUID());
                    checkResult.increaseByVersion(dbVersion);
                    logMessage(String.format(
                            "Inconsistency found Object(%s, id: %s, field: %s) is existing, but the related Index(%s, type: %s, id: %s) is missing. This entry is updated by version %s",
                            indexedField.getDataObjectType().getSimpleName(), objRow.getKey(), indexedField.getName(),
                            indexedField.getIndexCF().getName(), indexedField.getIndex().getClass().getSimpleName(), indexKey, dbVersion),
                            true, toConsole);
                    DbCheckerFileWriter.writeTo(DbCheckerFileWriter.WRITER_REBUILD_INDEX,
                            String.format("id:%s, cfName:%s", objRow.getKey(),
                                    indexedField.getDataObjectType().getSimpleName()));
                }
            }
        }
    }

    /**
     * Scan all the indices and related data object records, to find out
     * the index record is existing but the related data object records is missing.
     *
     * @return number of the corrupted rows in this index CF
     * @throws ConnectionException
     */
    public void checkIndexingCF(IndexAndCf indexAndCf, boolean toConsole, CheckResult checkResult) throws ConnectionException {
        String indexCFName = indexAndCf.cf.getName();
        Map<String, ColumnFamily<String, CompositeColumnName>> objCfs = getDataObjectCFs();
        _log.info("Start checking the index CF {}", indexCFName);

        Map<ColumnFamily<String, CompositeColumnName>, Map<String, List<IndexEntry>>> objsToCheck = new HashMap<>();

        ColumnFamilyQuery<String, IndexColumnName> query = indexAndCf.keyspace
                .prepareQuery(indexAndCf.cf);

        OperationResult<Rows<String, IndexColumnName>> result = query.getAllRows()
                .setRowLimit(dbClient.DEFAULT_PAGE_SIZE)
                .withColumnRange(new RangeBuilder().setLimit(dbClient.DEFAULT_PAGE_SIZE).build())
                .execute();

        for (Row<String, IndexColumnName> row : result.getResult()) {
            ColumnList<IndexColumnName> columns = row.getColumns();
            
            for (Column<IndexColumnName> column : columns) {
                ObjectEntry objEntry = extractObjectEntryFromIndex(row.getKey(),
                        column.getName(), indexAndCf.indexType, toConsole);
                if (objEntry == null) {
                    continue;
                }
                ColumnFamily<String, CompositeColumnName> objCf = objCfs
                        .get(objEntry.getClassName());

                if (objCf == null) {
                    logMessage(String.format("DataObject does not exist for %s", row.getKey()), true, toConsole);
                    continue;
                }

                Map<String, List<IndexEntry>> objKeysIdxEntryMap = objsToCheck.get(objCf);
                if (objKeysIdxEntryMap == null) {
                    objKeysIdxEntryMap = new HashMap<>();
                    objsToCheck.put(objCf, objKeysIdxEntryMap);
                }
                List<IndexEntry> idxEntries = objKeysIdxEntryMap.get(objEntry.getObjectId());
                if (idxEntries == null) {
                    idxEntries = new ArrayList<>();
                    objKeysIdxEntryMap.put(objEntry.getObjectId(), idxEntries);
                }
                idxEntries.add(new IndexEntry(row.getKey(), column.getName()));
            }
            
            if (getObjsSize(objsToCheck) >= INDEX_OBJECTS_BATCH_SIZE ) {
                processBatchIndexObjects(indexAndCf, toConsole, objsToCheck, checkResult);
            }
            
        }

        // Detect whether the DataObject CFs have the records
        processBatchIndexObjects(indexAndCf, toConsole, objsToCheck, checkResult);
    }

    private int getObjsSize(Map<ColumnFamily<String, CompositeColumnName>, Map<String, List<IndexEntry>>> objsToCheck) {
        int size = 0;
        for (Map<String, List<IndexEntry>> objMap : objsToCheck.values()) {
            for (List<IndexEntry> objs : objMap.values()) {
                size += objs.size();
            }
        }
        return size;
    }

    /*
     * We need to process index objects in batch to avoid occupy too many memory
     * */
    private void processBatchIndexObjects(IndexAndCf indexAndCf, boolean toConsole,
            Map<ColumnFamily<String, CompositeColumnName>, Map<String, List<IndexEntry>>> objsToCheck, CheckResult checkResult) throws ConnectionException {
        for (ColumnFamily<String, CompositeColumnName> objCf : objsToCheck.keySet()) {
            Map<String, List<IndexEntry>> objKeysIdxEntryMap = objsToCheck.get(objCf);

            OperationResult<Rows<String, CompositeColumnName>> objResult = indexAndCf.keyspace
                    .prepareQuery(objCf).getRowSlice(objKeysIdxEntryMap.keySet())
                    .execute();
            for (Row<String, CompositeColumnName> row : objResult.getResult()) {
                Set<UUID> existingDataColumnUUIDSet = new HashSet<>();
                for (Column<CompositeColumnName> column : row.getColumns()) {
                    if (column.getName().getTimeUUID() != null) {
                        existingDataColumnUUIDSet.add(column.getName().getTimeUUID());
                    }
                }
                
                List<IndexEntry> idxEntries = objKeysIdxEntryMap.get(row.getKey());
                for (IndexEntry idxEntry : idxEntries) {
                    if (row.getColumns().isEmpty()
                            || (idxEntry.getColumnName().getTimeUUID() != null && !existingDataColumnUUIDSet.contains(idxEntry
                                    .getColumnName().getTimeUUID()))) {
                        String dbVersion = findDataCreatedInWhichDBVersion(idxEntry.getColumnName().getTimeUUID());
                        checkResult.increaseByVersion(dbVersion);
                        if (row.getColumns().isEmpty()) {
                            logMessage(String.format("Inconsistency found: Index(%s, type: %s, id: %s, column: %s) is existing "
                                + "but the related object record(%s, id: %s) is missing. This entry is updated by version %s",
                                indexAndCf.cf.getName(), indexAndCf.indexType.getSimpleName(),
                                idxEntry.getIndexKey(), idxEntry.getColumnName(),
                                objCf.getName(), row.getKey(), dbVersion), true, toConsole);
                        } else {
                            logMessage(String.format("Inconsistency found: Index(%s, type: %s, id: %s, column: %s) is existing, "
                                    + "but the related object record(%s, id: %s) has not data column can match this index. This entry is updated by version %s",
                                    indexAndCf.cf.getName(), indexAndCf.indexType.getSimpleName(),
                                    idxEntry.getIndexKey(), idxEntry.getColumnName(),
                                    objCf.getName(), row.getKey(), dbVersion), true, toConsole);
                        }
                        UUID timeUUID = idxEntry.getColumnName().getTimeUUID();
                        DbCheckerFileWriter.writeTo(indexAndCf.keyspace.getKeyspaceName(),
                                String.format(timeUUID != null ? DELETE_INDEX_CQL : DELETE_INDEX_CQL_WITHOUT_UUID,
                                        indexAndCf.cf.getName(), idxEntry.getIndexKey(), idxEntry.getColumnName().getOne(),
                                        idxEntry.getColumnName().getTwo(),
                                        handleNullValue(idxEntry.getColumnName().getThree()),
                                        handleNullValue(idxEntry.getColumnName().getFour()),
                                        timeUUID));
                    }
                }
            }
        }
        objsToCheck.clear();
    }

    public Map<String, IndexAndCf> getAllIndices() {
        // Map<Index_CF_Name, <DbIndex, ColumnFamily, Map<Class_Name, object-CF_Name>>>
        Map<String, IndexAndCf> allIdxCfs = new TreeMap<>();
        for (DataObjectType objType : TypeMap.getAllDoTypes()) {
            Map<String, IndexAndCf> idxCfs = getIndicesOfCF(objType);
            allIdxCfs.putAll(idxCfs);
        }

        return allIdxCfs;
    }

    public Map<String, IndexAndCf> getIndicesOfCF(DataObjectType objType) {
        Map<String, IndexAndCf> idxCfs = new TreeMap<>();
        Keyspace keyspace = dbClient.getKeyspace(objType.getDataObjectClass());
        for (ColumnField field : objType.getColumnFields()) {
            DbIndex index = field.getIndex();
            if (index == null) {
                continue;
            }

            IndexAndCf indexAndCf = new IndexAndCf(index.getClass(), field.getIndexCF(), keyspace);
            String key = indexAndCf.generateKey();
            IndexAndCf idxAndCf = idxCfs.get(key);
            if (idxAndCf == null) {
                idxAndCf = new IndexAndCf(index.getClass(), field.getIndexCF(), keyspace);
                idxCfs.put(key, idxAndCf);
            }
        }
        return idxCfs;
    }

    public Map<String, ColumnFamily<String, CompositeColumnName>> getDataObjectCFs() {
        Map<String, ColumnFamily<String, CompositeColumnName>> objCfs = new TreeMap<>();
        for (DataObjectType objType : TypeMap.getAllDoTypes()) {
            String simpleClassName = objType.getDataObjectClass().getSimpleName();
            ColumnFamily<String, CompositeColumnName> objCf = objCfs.get(simpleClassName);
            if (objCf == null) {
                objCfs.put(simpleClassName, objType.getCF());
            }
        }

        return objCfs;
    }

    void logMessage(String msg, boolean isError, boolean toConsole) {
        if (isError) {
            _log.error(msg);
            if (toConsole) {
                System.err.println(msg);
            }
            return;
        }

        _log.info(msg);
        if (toConsole) {
            System.out.println(msg);
        }
    }

    /*
     * This class records the Index Data's ColumnFamily and
     * the related DbIndex type and it belongs to which Keyspace.
     */
    public static class IndexAndCf implements Comparable {
        private ColumnFamily<String, IndexColumnName> cf;
        private Class<? extends DbIndex> indexType;
        private Keyspace keyspace;

        public IndexAndCf(Class<? extends DbIndex> indexType,
                ColumnFamily<String, IndexColumnName> cf, Keyspace keyspace) {
            this.indexType = indexType;
            this.cf = cf;
            this.keyspace = keyspace;
        }

        @Override
        public String toString() {
            return generateKey();
        }

        String generateKey() {
            StringBuffer buffer = new StringBuffer();
            buffer.append(keyspace.getKeyspaceName()).append("/")
                    .append(indexType.getSimpleName()).append("/")
                    .append(cf.getName());
            return buffer.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof IndexAndCf)) {
                return false;
            }

            if (this == obj) {
                return true;
            }

            IndexAndCf that = (IndexAndCf) obj;
            if (cf != null ? !cf.equals(that.cf) : that.cf != null) {
                return false;
            }
            if (indexType != null ? !indexType.equals(that.indexType)
                    : that.indexType != null) {
                return false;
            }
            if (keyspace != null ? !keyspace.equals(that.keyspace)
                    : that.keyspace != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = cf != null ? cf.hashCode() : 0;
            result = 31 * result + (indexType != null ? indexType.hashCode() : 0);
            result = 31 * result + (keyspace != null ? keyspace.hashCode() : 0);
            return result;
        }

        @Override
        public int compareTo(Object object) {
            IndexAndCf other = (IndexAndCf) object;
            return this.generateKey().compareTo(other.generateKey());
        }
    }

    class IndexEntry {
        private String indexKey;
        private IndexColumnName columnName;

        public IndexEntry(String indexKey, IndexColumnName columnName) {
            this.indexKey = indexKey;
            this.columnName = columnName;
        }

        public String getIndexKey() {
            return indexKey;
        }

        public IndexColumnName getColumnName() {
            return columnName;
        }

    }

    private ObjectEntry extractObjectEntryFromIndex(String indexKey,
            IndexColumnName name, Class<? extends DbIndex> type, boolean toConsole) {
        // The className of a data object CF in a index record
        String className;
        // The id of the data object record in a index record
        String objectId;
        if (type.equals(AltIdDbIndex.class)) {
            objectId = name.getTwo();
            className = name.getOne();
        } else if (type.equals(RelationDbIndex.class)) {
            objectId = name.getTwo();
            className = name.getOne();
        } else if (type.equals(NamedRelationDbIndex.class)) {
            objectId = name.getFour();
            className = name.getOne();
        } else if (type.equals(DecommissionedDbIndex.class)) {
            objectId = name.getTwo();
            className = indexKey;
        } else if (type.equals(PermissionsDbIndex.class)) {
            objectId = name.getTwo();
            className = name.getOne();
        } else if (type.equals(PrefixDbIndex.class)) {
            objectId = name.getFour();
            className = name.getOne();
        } else if (type.equals(ScopedLabelDbIndex.class)) {
            objectId = name.getFour();
            className = name.getOne();
        } else if (type.equals(AggregateDbIndex.class)) {
            objectId = name.getTwo();
            int firstColon = indexKey.indexOf(':');
            className = firstColon == -1 ? indexKey : indexKey.substring(0, firstColon);
        } else {
            String msg = String.format("Unsupported index type %s.", type);
            logMessage(msg, false, toConsole);
            return null;
        }

        return new ObjectEntry(className, objectId);
    }

    public static class ObjectEntry {
        private String className;
        private String objectId;

        public ObjectEntry(String className, String objectId) {
            this.className = className;
            this.objectId = objectId;
        }

        public String getClassName() {
            return className;
        }

        public String getObjectId() {
            return objectId;
        }

        @Override
        public String toString() {
            StringBuffer buffer = new StringBuffer();
            buffer.append("ObjectEntry ClassName: ").append(className).append(" ObjectId: ").append(objectId);
            return buffer.toString();
        }
    }

    private boolean isColumnInIndex(Keyspace ks, ColumnFamily<String, IndexColumnName> indexCf, String indexKey, String[] indexColumns)
            throws ConnectionException {
        CompositeRangeBuilder builder = IndexColumnNameSerializer.get().buildRange();
        for (int i = 0; i < indexColumns.length; i++) {
            if (i == (indexColumns.length - 1)) {
                builder.greaterThanEquals(indexColumns[i]).lessThanEquals(indexColumns[i]).limit(1);
                break;
            }
            builder.withPrefix(indexColumns[i]);
        }

        ColumnList<IndexColumnName> result = ks.prepareQuery(indexCf).getKey(indexKey)
                .withColumnRange(builder)
                .execute().getResult();
        for (Column<IndexColumnName> indexColumn : result) {
            return true;
        }
        return false;
    }

    public static String getIndexKey(ColumnField field, Column<CompositeColumnName> column) {
        String indexKey = null;
        DbIndex dbIndex = field.getIndex();
        boolean indexByKey = field.isIndexByKey();
        if (dbIndex instanceof AltIdDbIndex) {
            indexKey = indexByKey ? column.getName().getTwo() : column.getStringValue();
        } else if (dbIndex instanceof RelationDbIndex) {
            indexKey = indexByKey ? column.getName().getTwo() : column.getStringValue();
        } else if (dbIndex instanceof NamedRelationDbIndex) {
            indexKey = NamedURI.fromString(column.getStringValue()).getURI().toString();
        } else if (dbIndex instanceof DecommissionedDbIndex) {
            indexKey = field.getDataObjectType().getSimpleName();
        } else if (dbIndex instanceof PermissionsDbIndex) {
            indexKey = column.getName().getTwo();
        } else if (dbIndex instanceof PrefixDbIndex) {
            indexKey = field.getPrefixIndexRowKey(column.getStringValue());
        } else if (dbIndex instanceof ScopedLabelDbIndex) {
            indexKey = field.getPrefixIndexRowKey(ScopedLabel.fromString(column.getStringValue()));
        } else if (dbIndex instanceof AggregateDbIndex) {
            // Not support this index type yet.
        } else {
            String msg = String.format("Unsupported index type %s.", dbIndex.getClass());
            _log.warn(msg);
        }

        return indexKey;
    }

    public static String[] getIndexColumns(ColumnField field, Column<CompositeColumnName> column, String rowKey) {
        String[] indexColumns = null;
        DbIndex dbIndex = field.getIndex();

        if (dbIndex instanceof AggregateDbIndex) {
            // Not support this index type yet.
            return indexColumns;
        }

        if (dbIndex instanceof NamedRelationDbIndex) {
            indexColumns = new String[4];
            indexColumns[0] = field.getDataObjectType().getSimpleName();
            NamedURI namedURI = NamedURI.fromString(column.getStringValue());
            String name = namedURI.getName();
            indexColumns[1] = name.toLowerCase();
            indexColumns[2] = name;
            indexColumns[3] = rowKey;

        } else if (dbIndex instanceof PrefixDbIndex) {
            indexColumns = new String[4];
            indexColumns[0] = field.getDataObjectType().getSimpleName();
            indexColumns[1] = column.getStringValue().toLowerCase();
            indexColumns[2] = column.getStringValue();
            indexColumns[3] = rowKey;

        } else if (dbIndex instanceof ScopedLabelDbIndex) {
            indexColumns = new String[4];
            indexColumns[0] = field.getDataObjectType().getSimpleName();
            ScopedLabel label = ScopedLabel.fromString(column.getStringValue());
            indexColumns[1] = label.getLabel().toLowerCase();
            indexColumns[2] = label.getLabel();
            indexColumns[3] = rowKey;

        } else if (dbIndex instanceof DecommissionedDbIndex) {
            indexColumns = new String[2];
            Boolean val = column.getBooleanValue();
            indexColumns[0] = val.toString();
            indexColumns[1] = rowKey;
        } else {
            // For AltIdDbIndex, RelationDbIndex, PermissionsDbIndex
            indexColumns = new String[2];
            indexColumns[0] = field.getDataObjectType().getSimpleName();
            indexColumns[1] = rowKey;
        }
        return indexColumns;
    }

    private String handleNullValue(String columnValue) {
        return columnValue == null ? "" : columnValue;
    }

    public DbClientImpl getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClientImpl dbClient) {
        this.dbClient = dbClient;
    }
    
    private boolean isValidDataObjectKey(URI uri, final Class<? extends DataObject> type) {
    	return uri != null && URIUtil.isValid(uri) && URIUtil.isType(uri, type);
    }

    protected Map<Long, String> querySchemaVersions() {
        Map<Long, String> result = new TreeMap<Long, String>();
        ColumnFamily<String, String> CF_STANDARD1 =
                new ColumnFamily<String, String>("SchemaRecord",
                        StringSerializer.get(), StringSerializer.get(), StringSerializer.get());
        try {
            OperationResult<CqlResult<String, String>> queryResult = dbClient.getLocalContext().getKeyspace().prepareQuery(CF_STANDARD1)
                    .withCql(CQL_QUERY_SCHEMA_VERSION_TIMESTAMP)
                    .execute();
            for (Row<String, String> row : queryResult.getResult().getRows()) {
                result.put(row.getColumns().getColumnByIndex(1).getLongValue(), row.getColumns().getColumnByIndex(0).getStringValue());
            }
        } catch (ConnectionException e) {
            _log.error("Failed to query schema versions", e);
        }
        
        return result;
    }
    
    public String findDataCreatedInWhichDBVersion(UUID timeUUID) {
        
        long createTime = 0;
        try {
            createTime = TimeUUIDUtils.getMicrosTimeFromUUID(timeUUID);
        } catch (Exception e) {
            //ignore
        }
        
        return findDataCreatedInWhichDBVersion(createTime);
    }
    
    public String findDataCreatedInWhichDBVersion(long createTime) {
        //small data set, no need to binary search
        long selectKey = 0;
        for (Entry<Long, String> entry : schemaVersionsTime.entrySet()) {
            if (createTime >= entry.getKey()) {
                selectKey = entry.getKey();
            }
        }
        
        return selectKey == 0 ? "Unknown" : schemaVersionsTime.get(selectKey);
    }
    
    public static class CheckResult {
        private int total;
        private Map<String, Integer> countOfVersion = new TreeMap<String, Integer>();
        
        public int getTotal() {
            return total;
        }

        public Map<String, Integer> getCountOfVersion() {
            return countOfVersion;
        }

        public void increaseByVersion(String version) {
            if (!countOfVersion.containsKey(version)) {
                countOfVersion.put(version, 0);
            }
            
            countOfVersion.put(version, countOfVersion.get(version) + 1);
            this.total++;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("\nCorrupted rows by version: ");
            int index = 1;
            int max = countOfVersion.size();
            for (Entry<String, Integer> entry : countOfVersion.entrySet()) {
                builder.append(entry.getKey()).append("(").append(entry.getValue()).append(")");
                if (index++ < max) {
                    builder.append(", ");
                }
            }
            return builder.toString();
        }
    }
}