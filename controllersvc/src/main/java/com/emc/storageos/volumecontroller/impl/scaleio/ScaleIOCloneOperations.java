/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.scaleio;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.scaleio.api.restapi.ScaleIORestClient;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOSnapshotVolumeResponse;
import com.emc.storageos.scaleio.api.restapi.response.ScaleIOVolume;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.CloneOperations;
import com.emc.storageos.volumecontroller.TaskCompleter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

public class ScaleIOCloneOperations implements CloneOperations {

    private static Logger log = LoggerFactory.getLogger(ScaleIOCloneOperations.class);
    private DbClient dbClient;
    private ScaleIOHandleFactory scaleIOHandleFactory;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setScaleIOHandleFactory(ScaleIOHandleFactory scaleIOHandleFactory) {
        this.scaleIOHandleFactory = scaleIOHandleFactory;
    }

    @Override
    public void createSingleClone(StorageSystem storageSystem, URI sourceVolume, URI cloneVolume, Boolean createInactive,
            TaskCompleter taskCompleter) {
        try {
            ScaleIORestClient scaleIOHandle = scaleIOHandleFactory.using(dbClient).getClientHandle(storageSystem);

            Volume cloneObj = dbClient.queryObject(Volume.class, cloneVolume);
            BlockObject parent = BlockObject.fetch(dbClient, sourceVolume);

            String systemId = scaleIOHandle.getSystemId();
            // Note: ScaleIO snapshots can be treated as full copies, hence re-use of #snapshotVolume here.
            ScaleIOSnapshotVolumeResponse result = scaleIOHandle.snapshotVolume(parent.getNativeId(), cloneObj.getLabel(), systemId);
            String nativeId = result.getVolumeIdList().get(0);
            ScaleIOHelper.updateSnapshotWithSnapshotVolumeResult(dbClient, cloneObj, systemId, nativeId);
            // Snapshots result does not provide capacity info, so we need to perform a --query_all_volumes
            updateCloneFromQueryAllVolumes(scaleIOHandle, cloneObj);
            dbClient.persistObject(cloneObj);
            ScaleIOHelper.updateStoragePoolCapacity(dbClient, scaleIOHandle, cloneObj);
            taskCompleter.ready(dbClient);
        } catch (Exception e) {
            Volume clone = dbClient.queryObject(Volume.class, cloneVolume);
            if (clone != null) {
                clone.setInactive(true);
                dbClient.persistObject(clone);
            }
            log.error("Encountered an exception", e);
            ServiceCoded code =
                    DeviceControllerErrors.scaleio.
                            encounteredAnExceptionFromScaleIOOperation("createSingleClone", e.getMessage());
            taskCompleter.error(dbClient, code);
        }
    }

    @Override
    public void detachSingleClone(StorageSystem storageSystem, URI cloneVolume, TaskCompleter taskCompleter) {
        log.info("START detachSingleClone operation");
        // no operation, set to ready
        Volume clone = dbClient.queryObject(Volume.class, cloneVolume);
        clone.setAssociatedSourceVolume(NullColumnValueGetter.getNullURI());
        clone.setReplicaState(ReplicationState.DETACHED.name());
        dbClient.persistObject(clone);
        taskCompleter.ready(dbClient);
    }

    @Override
    public void activateSingleClone(StorageSystem storageSystem, URI fullCopy, TaskCompleter completer) {
        // Not supported
    }

    private void updateCloneFromQueryAllVolumes(ScaleIORestClient scaleIOHandle, Volume cloneObj) throws Exception {

        try {
            ScaleIOVolume vol = scaleIOHandle.queryVolume(cloneObj.getNativeId());
            long size = Long.parseLong(vol.getSizeInKb()) * 1024L;
            cloneObj.setAllocatedCapacity(size);
            cloneObj.setProvisionedCapacity(size);
        } catch (Exception e) {
            log.warn("Failed to update full copy {} with size information: {}", cloneObj.getId(),
                    e.getMessage());
            throw e;
        }
    }

    @Override
    public void restoreFromSingleClone(StorageSystem storageSystem, URI clone, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");

    }

    @Override
    public void fractureSingleClone(StorageSystem storageSystem, URI sourceVolume,
            URI clone, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void resyncSingleClone(StorageSystem storageSystem, URI clone, TaskCompleter completer) {
        // no support
    }

    @Override
    public void createGroupClone(StorageSystem storage, List<URI> cloneList,
            Boolean createInactive, TaskCompleter taskCompleter) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void activateGroupClones(StorageSystem storage, List<URI> clone, TaskCompleter taskCompleter) {
        throw new UnsupportedOperationException("Not yet implemented");

    }

    @Override
    public void restoreGroupClones(StorageSystem storageSystem, List<URI> clones, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");

    }

    @Override
    public void fractureGroupClones(StorageSystem storageSystem, List<URI> clones, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");

    }

    @Override
    public void resyncGroupClones(StorageSystem storageSystem, List<URI> clones, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");

    }

    @Override
    public void detachGroupClones(StorageSystem storageSystem, List<URI> clones, TaskCompleter completer) {
        throw new UnsupportedOperationException("Not yet implemented");

    }

}
