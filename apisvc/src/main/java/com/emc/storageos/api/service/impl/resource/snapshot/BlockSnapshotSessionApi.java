/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.snapshot;

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.UriInfo;

import com.emc.storageos.api.service.impl.resource.fullcopy.BlockFullCopyManager;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.BlockSnapshotSession;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.services.OperationTypeEnum;

/**
 * Defines the API for platform specific implementations for block snapshot
 * session operations.
 */
public interface BlockSnapshotSessionApi {

    /**
     * Validate a create block snapshot session request.
     * 
     * @param requestedSourceObj A reference to the source object.
     * @param sourceObjList A list of all source objects to be processed for the request.
     * @param project A reference to the source project.
     * @param name The requested name for the new block snapshot session.
     * @param newTargetsCount The number of new targets to create and link to the session.
     * @param newTargetsName The requested name for the new linked targets.
     * @param skipInternalCheck true if the check for INTERNAL source is skipped, otherwise false.
     * @param newTargetCopyMode The copy mode for newly linked targets.
     * @param fcManager A reference to a full copy manager.
     */
    public void validateSnapshotSessionCreateRequest(BlockObject requestedSourceObj, List<BlockObject> sourceObjList, Project project,
            String name, int newTargetsCount, String newTargetsName, String newTargetCopyMode, boolean skipInternalCheck,
            BlockFullCopyManager fcManager);

    /**
     * Prepare a ViPR BlockSnapshotSession instance for each source. Also, if new linked
     * targets are to be created and linked to the snapshot sessions, then prepare ViPR
     * BlockSnapshot instances to represent these linked targets.
     * 
     * @param sourceObjList The list of source objects for which we are to create a snapshot session.
     * @param snapSessionLabel The snapshot session label for these snapshot sessions.
     * @param newTargetCount The number of new targets to create and link to each snapshot session.
     * @param newTargetsName The requested name for the new linked targets.
     * @param snapSessionSnapshots This OUT parameter gets populated with the BlockSnaphot instances created for each session, if any.
     * @param taskId The unique task identifier.
     * @param inAppication Is the source volume in an application 
     * 
     * @return
     */
    public BlockSnapshotSession prepareSnapshotSession(List<BlockObject> sourceObjList, String snapSessionLabel, int newTargetCount,
            String newTargetsName, List<Map<URI, BlockSnapshot>> snapSessionSnapshots, String taskId, boolean inApplication);

    /**
     * Prepare a ViPR BlockSnapshotSession instance for the passed source object.
     * 
     * @param sourceObj The snapshot session source.
     * @param snapSessionLabel The snapshot session label.
     * @param instanceLabel The unique snapshot session instance label.
     * @param taskId The unique task identifier.
     * @param inAppication Is the source volume in an application 
     * 
     * @return A ViPR BlockSnapshotSession instance for the passed source object
     */
    public BlockSnapshotSession prepareSnapshotSessionFromSource(BlockObject sourceObj, String snapSessionLabel, String instanceLabel,
            String taskId, boolean inApplication);

    /**
     * Prepare ViPR BlockSnapshot instances for the new targets to be created and
     * linked to a block snapshot session.
     * 
     * @param sourceObjList The snapshot session source.
     * @param sessionCount The snapshot session count when preparing snapshots for multiple sessions.
     * @param newTargetCount The number of new targets to be created.
     * @param newTargetsName The requested name for the new linked targets.
     * @param inAppication Is the source volume in an application 
     *
     * @return A map containing the prepared BlockSnapshot instances, keyed by the snapshot URI.
     */
    public List<Map<URI, BlockSnapshot>> prepareSnapshotsForSession(List<BlockObject> sourceObjList, int sessionCount, int newTargetCount,
            String newTargetsName, boolean inApplication);

    /**
     * Prepare a ViPR BlockSnapshot instance for a new target to be created and
     * linked to a block snapshot session.
     * 
     * @param sourceObj The snapshot source object.
     * @param snapsetLabel The snapset label for snapshots in a group.
     * @param instanceLabel The snapshot instance label.
     *
     * @return The prepared BlockSnapshot instance.
     */
    public BlockSnapshot prepareSnapshotForSession(BlockObject sourceObj, String snapsetLabel, String instanceLabel);

    /**
     * Creates a new block snapshot session.
     * 
     * @param sourceObj A reference to the source object.
     * @param snapSessionURI The URI of the ViPR BlockSnashotSession instances to be created.
     * @param snapSessionSnapshotURIs A map containing the URis of the BlockSnapshot instances for each session.
     * @param copyMode The copy mode for linked targets.
     * @param taskId A unique task identifier.
     */
    public void createSnapshotSession(BlockObject sourceObj, URI snapSessionURI,
            List<List<URI>> snapSessionSnapshotURIs, String copyMode, String taskId);

    /**
     * Validates a link new targets to block snapshot session request.
     * 
     * @param snapSessionSourceObj A reference to the snapshot session source.
     * @param project A reference to the source project.
     * @param newTargetsCount The number of new targets to create and link to the session.
     * @param newTargetsName The requested name for the new linked targets.
     * @param newTargetCopyMode The copy mode for newly linked targets.
     */
    public void validateLinkNewTargetsRequest(BlockObject snapSessionSourceObj, Project project, int newTargetsCount,
            String newTargetsName, String newTargetCopyMode);

    /**
     * Creates a new block snapshot session.
     * 
     * @param snapSessionSourceObj A reference to the source object.
     * @param snapSession A reference to the BlockSnapshotSession instance.
     * @param snapshotURIs The URIs of the BlockSnapshot instances representing the linked targets.
     * @param copyMode The copy mode for linked targets.
     * @param taskId A unique task identifier.
     */
    public void linkNewTargetVolumesToSnapshotSession(BlockObject snapSessionSourceObj, BlockSnapshotSession snapSession,
            List<List<URI>> snapshotURIs, String copyMode, String taskId);

    /**
     * Validates a re-link targets to block snapshot session request.
     * 
     * @param snapSessionSourceObj A reference to the snapshot session source.
     * @param tgtSnapSession A reference to the BlockSnapshotSession instance to which the
     *            targets will be re-linked.
     * @param snapSessionSourceObj A reference to the snapshot session source.
     * @param project A reference to the source project.
     * @param snapshotURIs The URI of the BlockSnapshot instances representing the targets
     *            to be re-linked.
     * @param uriInfo A reference to the URI information.
     */
    public void validateRelinkSnapshotSessionTargets(BlockObject snapSessionSourceObj, BlockSnapshotSession tgtSnapSession,
            Project project, List<URI> snapshotURIs, UriInfo uriInfo);

    /**
     * Re-links the targets represented by the BlockSnapshot instances with the passed
     * URIs to the passed BlockSnapshotSession.
     * 
     * @param snapSessionSourceObj A reference to the snapshot session source.
     * @param tgtSnapSession A reference to the BlockSnapshotSession instance to which the targets
     *            are to be re-linked.
     * @param snapshotURIs The URI of the BlockSnapshot instances representing the targets
     *            to be re-linked.
     * @param taskId A unique task identifier.
     */
    public void relinkTargetVolumesToSnapshotSession(BlockObject snapSessionSourceObj, BlockSnapshotSession tgtSnapSession,
            List<URI> snapshotURIs, String taskId);

    /**
     * Validates a link new targets to block snapshot session request.
     * 
     * @param snapSession A reference to the BlockSnapshotSession instance.
     * @param snapSessionSourceObj A reference to the snapshot session source.
     * @param project A reference to the source project.
     * @param targetMap A map of the URIs of the BlockSnapshot instances representing the linked targets
     *            and whether or not they should be deleted.
     * @param uriInfo A reference to the URI information.
     */
    public void validateUnlinkSnapshotSessionTargets(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, Project project,
            Map<URI, Boolean> targetMap, UriInfo uriInfo);

    /**
     * Unlinks the targets represented by the BlockSnapshot instances with the passed
     * URIs from the passed BlockSnapshotSession.
     * 
     * @param snapSessionSourceObj A reference to the snapshot session source.
     * @param snapSession A reference to the BlockSnapshotSession instance.
     * @param snapshotMap A map of the containing the URIs of the BlockSnapshot instances representing the targets to be unlinked and
     *            whether or not each target should be deleted.
     * @param opType The operation type for the audit and event logs.
     * @param taskId A unique task identifier.
     */
    public void unlinkTargetVolumesFromSnapshotSession(BlockObject snapSessionSourceObj, BlockSnapshotSession snapSession,
            Map<URI, Boolean> snapshotDeletionMap, OperationTypeEnum opType, String taskId);

    /**
     * Validates a restore snapshot session request.
     * 
     * @param snapSessionSourceObjs A list of the snapshot session source objects.
     * @param project A reference to the source project.
     */
    public void validateRestoreSnapshotSession(List<BlockObject> snapSessionSourceObjs, Project project);

    /**
     * Restores the source with the data from the array snapshot point-in-time
     * copy represented by the passed BlockSnapshotSession instance.
     * 
     * @param snapSession A reference to a BlockSnapshotSession instance.
     * @param snapSessionSourceObj A reference to the snapshot session source.
     * @param taskId A unique task identifier.
     */
    public void restoreSnapshotSession(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, String taskId);

    /**
     * Validates a delete snapshot session request.
     * 
     * @param snapSession A reference to a BlockSnapshotSession instance.
     * @param snapSessionSourceObj A reference to the snapshot session source.
     * @param project A reference to the source project.
     */
    public void validateDeleteSnapshotSession(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, Project project);

    /**
     * Deletes the array snapshot point-in-time copy represented by the passed
     * BlockSnapshotSession instance.
     * 
     * @param snapSession A reference to a BlockSnapshotSession instance.
     * @param snapSessionSourceObj A reference to the snapshot session source.
     * @param deleteType The deletion type i.e, VIPR_ONLY or FULL.
     * @param taskId A unique task identifier.
     */
    public void deleteSnapshotSession(BlockSnapshotSession snapSession, BlockObject snapSessionSourceObj, String taskId, String deleteType);

    /**
     * Get all BlockSnapshotSessions for the passed source.
     * 
     * @param sourceObj A reference to the source object.
     */
    public List<BlockSnapshotSession> getSnapshotSessionsForSource(BlockObject sourceObj);

    /**
     * Get all BlockSnapshotSessions for the passed consistency group.
     * 
     * @param groupObj A reference to the consistency group object.
     */
    public List<BlockSnapshotSession> getSnapshotSessionsForConsistencyGroup(BlockConsistencyGroup groupObj);

    /**
     * Verifies there are no active mirrors for the snapshot session source volume.
     * Should be overridden when there are additional or different platform restrictions.
     * 
     * @param sourceVolume A reference to the snapshot session source.
     */
    public void verifyActiveMirrors(Volume sourceVolume);

    /**
     * Gets all snapshot sessions with a common session instance.
     * 
     * @param instance
     * @return
     */
    public List<BlockSnapshotSession> getSnapshotSessionsBySessionInstance(String instance);
}
