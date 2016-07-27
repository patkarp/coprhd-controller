/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

/**
 * CompresstionMatcher is responsible to match all the pools matching the compression enabled attribute on VMAX3 All Flash array
 * given VirtualPool.
 * 
 */
public class CompressionMatcher extends AttributeMatcher {

    private static final Logger _logger = LoggerFactory.getLogger(CompressionMatcher.class);

    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap,
            StringBuffer errorMessage) {
        String compressionEnabled = attributeMap.get(Attributes.compression_enabled.toString()).toString();

        _logger.info("Pools Matching Compression attribute {} Started:{}", compressionEnabled, Joiner
                .on("\t").join(getNativeGuidFromPools(pools)));
        // defensive copy
        List<StoragePool> filteredPoolList = new ArrayList<StoragePool>();
        StringSet deviceTypes = (StringSet) attributeMap.get(Attributes.system_type.toString());

        //TODO: Bharath - add comments
        if (deviceTypes.contains(VirtualPool.SystemType.vmax.toString())) {
            Iterator<StoragePool> poolIterator = pools.iterator();
            while (poolIterator.hasNext()) {
                StoragePool pool = poolIterator.next();
                // check whether pool matching with vpool or not.
                // if it doesn't match remove it from all pools.
                if (pool.getCompressionEnabled()) {
                    filteredPoolList.add(pool);
                } else {
                    _logger.info("Ignoring pool {} as it doesn't support compression", pool.getNativeGuid());
                }
            }
        } else {
            filteredPoolList = new ArrayList<StoragePool> (); //getPoolsWithAutoTieringEnabled(pools);
        }
        _logger.info("Pools Matching Compression attribute Ended:{}",
                Joiner.on("\t").join(getNativeGuidFromPools(filteredPoolList)));
        if (CollectionUtils.isEmpty(filteredPoolList)) {
            errorMessage.append(String.format("No matching storage pool found with Compression  %s. ", compressionEnabled));
            _logger.error(errorMessage.toString());
        }

        return filteredPoolList;
    }

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        boolean status = false;       
        if (null != attributeMap && 
        	attributeMap.containsKey(Attributes.compression_enabled.toString()) && 
        	(boolean) attributeMap.get(Attributes.compression_enabled.toString())) {        
            StringSet deviceTypes = (StringSet) attributeMap.get(Attributes.system_type.toString());
            if (deviceTypes.contains(VirtualPool.SystemType.vmax.toString())) {
                status = true;
             }
        }
        return status;
    }

 
    private boolean isCompressionEnabledOnStorageSystem(URI storageSystemURI) {
        StorageSystem system = _objectCache.queryObject(StorageSystem.class,
                storageSystemURI);
        // if fast is disabled then skip it too.
        if (null != system && system.checkIfVmax3()) {
            return true;
        }
        return false;
    }

}
