/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileSnapshotRestRep;
import com.emc.vipr.client.Task;

public class DeactivateFileSnapshot extends WaitForTask<FileSnapshotRestRep> {
    private URI snapshotId;

    public DeactivateFileSnapshot(String snapshotId) {
        this(uri(snapshotId));
    }

    public DeactivateFileSnapshot(URI snapshotId) {
        super();
        this.snapshotId = snapshotId;
        provideDetailArgs(snapshotId);
    }

    @Override
    protected Task<FileSnapshotRestRep> doExecute() throws Exception {
        return getClient().fileSnapshots().deactivate(snapshotId);
    }
}
