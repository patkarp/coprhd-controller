/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.DELETION_TYPE;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;

@Service("RemoveBlockStorage")
public class RemoveBlockStorageService extends ViPRService {
    @Param(VOLUMES)
    protected List<String> volumeIds;

    @Param(DELETION_TYPE)
    protected VolumeDeleteTypeEnum deletionType;

    @Override
    public void precheck() {
        BlockStorageUtils.getBlockResources(uris(volumeIds));
    }

    @Override
    public void execute() {
        BlockStorageUtils.removeBlockResources(uris(volumeIds), deletionType);
    }
}
