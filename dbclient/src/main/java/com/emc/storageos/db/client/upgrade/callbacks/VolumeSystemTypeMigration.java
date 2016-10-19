package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

public class VolumeSystemTypeMigration extends BaseCustomMigrationCallback {
    private static final Logger logger = LoggerFactory.getLogger(VolumeSystemTypeMigration.class);

    @Override
    public void process() throws MigrationCallbackException {
        DbClient dbClient = getDbClient();

        Map<URI, String> storageSystemTypeMap = new HashMap<URI, String>();

        int pageSize = 100;
        int totalBlockObjectCount = 0;
        int blockObjectUpdatedCount = 0;

        List<Class<? extends BlockObject>> classesToProcess = new ArrayList<Class<? extends BlockObject>>();
        classesToProcess.add(Volume.class);
        classesToProcess.add(BlockSnapshot.class);
        classesToProcess.add(BlockMirror.class);

        for (Class<? extends BlockObject> clazz : classesToProcess) {
            URI nextId = null;
            while (true) {
                List<URI> blockObjectUris = dbClient.queryByType(clazz, true, nextId, pageSize);

                if (blockObjectUris == null || blockObjectUris.isEmpty()) {
                    break;
                }

                List<BlockObject> blockObjectsToUpdate = new ArrayList<BlockObject>();
                List<? extends BlockObject> pageOfBlockObjects = dbClient.queryObject(clazz, blockObjectUris);
                logger.info("processing page of {} {} BlockObjects", pageOfBlockObjects.size(), clazz.getSimpleName());

                for (BlockObject blockObject : pageOfBlockObjects) {
                    if (blockObject.getSystemType() == null || blockObject.getSystemType().isEmpty()) {
                        String deviceSystemType = getDeviceSystemType(dbClient, storageSystemTypeMap, blockObject);
                        if (deviceSystemType != null) {
                            blockObject.setSystemType(deviceSystemType);
                            blockObjectsToUpdate.add(blockObject);
                            blockObjectUpdatedCount++;
                            logger.info("set storage system type to {} for BlockObject {}",
                                    deviceSystemType, blockObject.forDisplay());
                        } else {
                            logger.warn("could not determine storage system type for BlockObject {}",
                                    blockObject.forDisplay());
                        }
                    }
                }

                logger.info("updating system type on {} {} BlockObjects", blockObjectsToUpdate.size(), clazz.getSimpleName());
                dbClient.updateObject(blockObjectsToUpdate);
                nextId = blockObjectUris.get(blockObjectUris.size() - 1);
                totalBlockObjectCount += blockObjectUris.size();
            }
        }

        logger.info("Updated storage system type on {} of {} BlockObjects in the system",
                blockObjectUpdatedCount, totalBlockObjectCount);
    }

    /**
     * Get a storage device system type from the database or from the cache.
     * 
     * @param dbClient the database client
     * @param storageSystemTypeMap a map of storage system URIs to system types
     * @param blockObject the block object to check system type for
     * @return the storage device system type
     */
    private String getDeviceSystemType(DbClient dbClient, Map<URI, String> storageSystemTypeMap, BlockObject blockObject) {
        String deviceSystemType = null;
        URI storageSystemUri = blockObject.getStorageController();
        if (storageSystemTypeMap.containsKey(storageSystemUri)) {
            deviceSystemType = storageSystemTypeMap.get(storageSystemUri);
        } else {
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storageSystemUri);
            if (storageSystem != null) {
                deviceSystemType = storageSystem.checkIfVmax3() ? DiscoveredDataObject.Type.vmax3.name() : storageSystem.getSystemType();
                storageSystemTypeMap.put(storageSystemUri, deviceSystemType);
                logger.info("adding storage system type {} for storage system URI {}",
                        deviceSystemType, storageSystemUri);
            } else {
                logger.warn("could not find storage system by URI {} for BlockObject {}",
                        storageSystemUri, blockObject.forDisplay());
            }
        }
        return deviceSystemType;
    }

}
