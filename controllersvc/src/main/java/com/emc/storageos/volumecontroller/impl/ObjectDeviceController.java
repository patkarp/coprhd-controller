package com.emc.storageos.volumecontroller.impl;

import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.object.BucketParam;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.FileStorageDevice;
import com.emc.storageos.volumecontroller.ObjectController;
import com.emc.storageos.volumecontroller.ObjectDeviceInputOutput;
import com.emc.storageos.volumecontroller.ObjectStorageDevice;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;

public class ObjectDeviceController implements ObjectController {

	private DbClient _dbClient;
	private Map<String, ObjectStorageDevice> _devices;
	private static final Logger _log = LoggerFactory.getLogger(ObjectDeviceController.class);
	
    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    public void setDevices(Map<String, ObjectStorageDevice> deviceInterfaces) {
        _devices = deviceInterfaces;
    }

    private ObjectStorageDevice getDevice(String deviceType) {
        return _devices.get(deviceType);
    }
    
	@Override
	public void connectStorage(URI storage) throws InternalException {
		// TODO Auto-generated method stub
		_log.info("ObjectDeviceController:connectStorage");

	}

	@Override
	public void disconnectStorage(URI storage) throws InternalException {
		// TODO Auto-generated method stub
		_log.info("ObjectDeviceController:disconnectStorage");

	}

	@Override
	public void discoverStorageSystem(AsyncTask[] tasks)
			throws InternalException {
		// TODO Auto-generated method stub
		_log.info("ObjectDeviceController:discoverStorageSystem");

	}

	@Override
	public void scanStorageProviders(AsyncTask[] tasks)
			throws InternalException {
		// TODO Auto-generated method stub
		_log.info("ObjectDeviceController:scanStorageProviders");

	}

	@Override
	public void startMonitoring(AsyncTask task, Type deviceType)
			throws InternalException {
		// TODO Auto-generated method stub
		_log.info("ObjectDeviceController:startMonitoring");

	}

	@Override
	public void createBucket(URI storage, URI uriPool, URI bkt, String label, String namespace, String retention,
			String hardQuota, String softQuota, String owner, String opId) throws ControllerException {
		// TODO Auto-generated method stub
		_log.info("ObjectDeviceController:createBucket start");
		StorageSystem storageObj = null;
		
	//	try {
			StoragePool stPool = _dbClient.queryObject(StoragePool.class, uriPool);
			Bucket bucketObj = _dbClient.queryObject(Bucket.class, bkt);
			ObjectDeviceInputOutput args = new ObjectDeviceInputOutput();
			storageObj = _dbClient.queryObject(StorageSystem.class, storage);

			args.setName(label);
			args.setNamespace(namespace);
			args.setRepGroup(stPool.getNativeId()); //recommended storeage pool
			args.setRetentionPeriod(retention);
			args.setBlkSizeHQ(hardQuota);
			args.setNotSizeSQ(softQuota);
			args.setOwner(owner);

			_log.info("ObjectDeviceController:createBucket URI and Type: " + storage.toString() + "   " +
					storageObj.getSystemType());
			BiosCommandResult result = getDevice(storageObj.getSystemType()).doCreateBucket(storageObj, args);
			_log.info("ObjectDeviceController:createBucket 1111");
			if (!result.getCommandPending()) {
				bucketObj.getOpStatus().updateTaskStatus(opId, result.toOperation());
			}

			//_dbClient.persistObject(bucketObj);
			_log.info("ObjectDeviceController:createBucket end");
		//} catch (Exception e) {
			//_log.error("Unable to create Bucket storage");
		//}
	}
	
}
