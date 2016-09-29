/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.storagedriver;


import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationArgument;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

import java.util.List;

/**
 * This class defines driver interface methods for remote replication.
 */
public interface RemoteReplicationDriver {

    /**
     * Create empty remote replication group.
     * @param replicationGroup specifies properties of remote replication group to create.
     * @param capabilities storage capabilities for the group
     * @return driver task
     */
    public DriverTask createRemoteReplicationGroup(RemoteReplicationGroup replicationGroup, StorageCapabilities capabilities);

    /**
     * Create replication pairs in existing replication group container.
     * At the completion of this operation all remote replication pairs should be associated to their group and should
     * be in the following state:
     *  a) createActive is true:
     *     Pairs state: ACTIVE;
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read enabled/write disabled;
     *     data on R2 elements is synchronized with R1 data copied to R2;
     *     replication links are set to ready state;
     *
     *  b) createActive is false:
     *     Pairs state: SPLIT;
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read/write enabled;
     *     replication links are set to not ready state;
     *     The state of replication pairs is same as after 'split' operation.
     *
     * @param replicationPairs list of replication pairs to create
     * @param createActive true, if pairs should start replication link automatically after creation (link in ready state), false otherwise
     * @param capabilities storage capabilities for the pairs
     * @return driver task
     */
    public DriverTask createGroupReplicationPairs(List<RemoteReplicationPair> replicationPairs, boolean createActive, StorageCapabilities capabilities);

    /**
     * Create replication pairs in existing replication set. Pairs are created outside of group container.
     * At the completion of this operation all remote replication pairs should be associated to their set and should
     * be in the following state:
     *  a) createActive is true:
     *     Pairs state: ACTIVE;
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read enabled/write disabled;
     *     data on R2 elements is synchronized with R1 data copied to R2;
     *     replication links are set to ready state;
     *
     *  b) createActive is false:
     *     Pairs state: SPLIT;
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read/write enabled;
     *     replication links are set to not ready state;
     *     The state of replication pairs is same as after 'split' operation.
     *
     * @param replicationPairs list of replication pairs to create
     * @param createActive true, if pairs should start replication link automatically after creation (link in ready state), false otherwise
     * @param capabilities storage capabilities for the pairs
     * @return driver task
     */
    public DriverTask createSetReplicationPairs(List<RemoteReplicationPair> replicationPairs, boolean createActive, StorageCapabilities capabilities);

    /**
     * Delete remote replication pairs. Should not delete backend volumes.
     * Only should affect remote replication configuration on array.
     * At the completion of this operation all remote replication elements from the pairs
     * should be in the following state:
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read/write enabled;
     *     replication links are not ready;
     *     R1 and R2 elements are disassociated from their remote replication containers and
     *     become independent storage elements;
     *
     * @param replicationPairs replication pairs to delete
     * @return  driver task
     */
    public DriverTask deleteReplicationPairs(List<RemoteReplicationPair> replicationPairs);


    // replication link operations

    /**
     * Suspend remote replication link for remote replication argument.
     * At the completion of this operation all remote replication pairs which belong to the replication argument should
     * be in the following state:
     *     Pairs state: SUSPENDED;
     *     No change in remote replication container (group/set) for the pairs;
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read enabled/write disabled;
     *     replication links should be in not ready state;
     *
     * @param replicationArgument: set/group/pair
     * @return driver task
     */
    public DriverTask suspend(RemoteReplicationArgument replicationArgument);

    /**
     * Resume remote replication link for remote replication argument.
     * At the completion of this operation all remote replication pairs which belong to the replication argument should
     * be in the following state:
     *     Pairs state: ACTIVE;
     *     No change in remote replication container (group/set) for the pairs;
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read enabled/write disabled;
     *     data on R2 elements is synchronized with R1 data copied to R2 elements;
     *     replication links should be in ready state;
     *
     * @param replicationArgument: set/group/pair
     * @return driver task
     */
    public DriverTask resume(RemoteReplicationArgument replicationArgument);

    /**
     * Split remote replication link for remote replication argument.
     * At the completion of this operation all remote replication pairs which belong to the replication argument should
     * be in the following state:
     *     Pairs state: SPLIT;
     *     No change in remote replication container (group/set) for the pairs;
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read/write enabled;
     *     replication links should be in not ready state;
     *
     * @param replicationArgument: set/group/pair
     * @return driver task
     */
    public DriverTask split(RemoteReplicationArgument replicationArgument);

    /**
     * Establish replication link for remote replication argument.
     *
     * At the completion of this operation all remote replication pairs which belong to the replication argument should
     * be in the following state:
     *     Pairs state: ACTIVE;
     *     No change in remote replication container (group/set) for the pairs;
     *     R1 elements should be read/write enabled;
     *     R2 elements should be read enabled/write disabled;
     *     data on R2 elements is synchronized with R1 data;
     *     replication links should be in ready state;
     *
     * @param replicationArgument replication argument: set/group/pair
     * @return driver task
     */
    public DriverTask establish(RemoteReplicationArgument replicationArgument);

    /**
     * Failover remote replication link for remote replication argument.
     * At the completion of this operation all remote replication pairs which belong to the replication argument should
     * be in the following state:
     *     Pairs state: FAILED_OVER;
     *     No change in remote replication container (group/set) for the pairs;
     *     R1 elements should be write disabled;
     *     R2 elements should be read/write enabled;
     *     replication links should be in not ready state;
     *
     * @param replicationArgument: set/group/pair
     * @return driver task
     */
    public DriverTask failover(RemoteReplicationArgument replicationArgument);

    /**
     * Failback remote replication link for remote replication argument.
     * At the completion of this operation all remote replication pairs which belong to the replication argument should
     * be in the following state:
     *     Pairs state:ACTIVE;
     *     No change in remote replication container (group/set) for the pairs;
     *     R1 elements should be read/write enabled;
     *     R2 elements should be write disabled;
     *     data on R1 elements is synchronized with new R2 data copied to R1;
     *     replication links should be in ready state;
     *
     * @param replicationArgument: set/group/pair
     * @return driver task
     */
    public DriverTask failback(RemoteReplicationArgument replicationArgument);

    /**
     * Swap remote replication link for remote replication argument.
     * Changes roles of replication elements in each replication pair.
     *
     * At the completion of this operation all remote replication pairs which belong to the replication argument should
     * be in the following state:
     *     Pairs state: SWAPPED;
     *     No change in remote replication container (group/set) for the pairs;
     *     R1 elements should be read enabled/write disabled;
     *     R2 elements should be read/write enabled;
     *     data on R2 elements is synchronized with new R1 data copied to R2;
     *     replication links should be in ready state for replication from R2 to R1;
     *
     * @param replicationArgument: set/group/pair
     * @return driver task
     */
    public DriverTask swap(RemoteReplicationArgument replicationArgument);

    /**
     * Move replication pair from its parent group to other replication group.
     * At the completion of this operation remote replication pair should be in the same state as it was before the
     * operation. The only change should be that the pair changed its parent replication group.
     *
     * @param replicationPair replication pair to move
     * @param targetGroup new parent replication group for the pair
     * @return driver task
     */
    public DriverTask movePair(RemoteReplicationPair replicationPair, RemoteReplicationGroup targetGroup);
}
