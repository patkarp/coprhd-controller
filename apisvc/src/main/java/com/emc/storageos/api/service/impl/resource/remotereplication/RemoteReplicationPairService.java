package com.emc.storageos.api.service.impl.resource.remotereplication;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.RemoteReplicationMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.TaskResourceService;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationModeChange;
import com.emc.storageos.model.remotereplication.RemoteReplicationPairList;
import com.emc.storageos.model.remotereplication.RemoteReplicationPairRestRep;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;

@Path("/vdc/block/remotereplicationpairs")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class RemoteReplicationPairService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(RemoteReplicationSetService.class);
    public static final String SERVICE_TYPE = "remote_replication";

    // remote replication service api implementations
    private RemoteReplicationBlockServiceApiImpl remoteReplicationServiceApi;

    public RemoteReplicationBlockServiceApiImpl getRemoteReplicationServiceApi() {
        return remoteReplicationServiceApi;
    }

    public void setRemoteReplicationServiceApi(RemoteReplicationBlockServiceApiImpl remoteReplicationServiceApi) {
        this.remoteReplicationServiceApi = remoteReplicationServiceApi;
    }

    @Override
    public String getServiceType() {
        return SERVICE_TYPE;
    }

    /**
     * Gets the id, name, and self link for all remote replication pairs
     *
     * @brief List remote replication pairs
     * @return A reference to a RemoteReplicationPairList.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationPairList getRemoteReplicationPairs() {
        _log.info("Called: getRemoteReplicationPairs()");
        RemoteReplicationPairList rrPairList = new RemoteReplicationPairList();

        List<URI> ids = _dbClient.queryByType(RemoteReplicationPair.class, true);
        _log.info("Found sets: {}", ids);
        Iterator<RemoteReplicationPair> iter = _dbClient.queryIterativeObjects(RemoteReplicationPair.class, ids);
        while (iter.hasNext()) {
            rrPairList.getRemoteReplicationPairs().add(toNamedRelatedResource(iter.next()));
        }
        return rrPairList;
    }

    /**
     * Get information about the remote replication pair with the passed id.
     *
     * @param id the URN of remote replication pair.
     *
     * @brief Show remote replication pair
     * @return A reference to a RemoteReplicationPairRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public RemoteReplicationPairRestRep getRemoteReplicationPair(@PathParam("id") URI id) {
        _log.info("Called: getRemoteReplicationPair() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);
        RemoteReplicationPairRestRep restRep = map(rrPair);
        return restRep;
    }


    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/failover")
    public TaskResourceRep failoverRemoteReplicationPairLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: failoverRemoteReplicationPairLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);

        // todo: validate that this operation is valid: if operations are allowed on Pairs, if Pair state is valid for the operation, if the Pair is reachable, etc.
        // Create a task for the failover remote replication Pair operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationPair.class, rrPair.getId(),
                taskId, ResourceOperationTypeEnum.SPLIT_REMOTE_REPLICATION_PAIR_LINK);

        RemoteReplicationElement rrElement =
                new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR, id);
        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.failoverRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrPair.getOpStatus().get(taskId);
            op.error(e);
            rrPair.getOpStatus().updateTaskStatus(taskId, op);
            rrPair.setInactive(true);
            _dbClient.updateObject(rrPair);

            throw e;
        }

        auditOp(OperationTypeEnum.FAILOVER_REMOTE_REPLICATION_PAIR_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrPair.getNativeId(), rrPair.getSourceElement(), rrPair.getTargetElement());

        return toTask(rrPair, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/failback")
    public TaskResourceRep failbackRemoteReplicationPairLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: failbackRemoteReplicationPairLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);

        // todo: validate that this operation is valid: if operations are allowed on pairs, if pair state is valid for the operation, if the pair is reachable, etc.
        // Create a task for the failback remote replication pair operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationPair.class, rrPair.getId(),
                taskId, ResourceOperationTypeEnum.FAILBACK_REMOTE_REPLICATION_PAIR_LINK);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR, id);
        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.failbackRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrPair.getOpStatus().get(taskId);
            op.error(e);
            rrPair.getOpStatus().updateTaskStatus(taskId, op);
            rrPair.setInactive(true);
            _dbClient.updateObject(rrPair);

            throw e;
        }

        auditOp(OperationTypeEnum.FAILBACK_REMOTE_REPLICATION_PAIR_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrPair.getNativeId(), rrPair.getSourceElement(), rrPair.getTargetElement());

        return toTask(rrPair, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/establish")
    public TaskResourceRep establishRemoteReplicationPairLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: establishRemoteReplicationPairLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);

        // todo: validate that this operation is valid: if operations are allowed on pairs, if pair state is valid for the operation, if the pair is reachable, etc.
        // Create a task for the establish remote replication pair operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationPair.class, rrPair.getId(),
                taskId, ResourceOperationTypeEnum.ESTABLISH_REMOTE_REPLICATION_PAIR_LINK);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR, id);
        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.establishRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrPair.getOpStatus().get(taskId);
            op.error(e);
            rrPair.getOpStatus().updateTaskStatus(taskId, op);
            rrPair.setInactive(true);
            _dbClient.updateObject(rrPair);

            throw e;
        }

        auditOp(OperationTypeEnum.ESTABLISH_REMOTE_REPLICATION_PAIR_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrPair.getNativeId(), rrPair.getSourceElement(), rrPair.getTargetElement());

        return toTask(rrPair, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/split")
    public TaskResourceRep splitRemoteReplicationPairLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: splitRemoteReplicationPairLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);

        // todo: validate that this operation is valid: if operations are allowed on pairs, if pair state is valid for the operation, if the pair is reachable, etc.
        // Create a task for the split remote replication pair operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationPair.class, rrPair.getId(),
                taskId, ResourceOperationTypeEnum.SPLIT_REMOTE_REPLICATION_PAIR_LINK);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR, id);
        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.splitRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrPair.getOpStatus().get(taskId);
            op.error(e);
            rrPair.getOpStatus().updateTaskStatus(taskId, op);
            rrPair.setInactive(true);
            _dbClient.updateObject(rrPair);

            throw e;
        }

        auditOp(OperationTypeEnum.SPLIT_REMOTE_REPLICATION_PAIR_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrPair.getNativeId(), rrPair.getSourceElement(), rrPair.getTargetElement());

        return toTask(rrPair, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/suspend")
    public TaskResourceRep suspendRemoteReplicationPairLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: suspendRemoteReplicationPairLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);

        // todo: validate that this operation is valid: if operations are allowed on pairs, if pair state is valid for the operation, if the pair is reachable, etc.
        // Create a task for the suspend remote replication pair operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationPair.class, rrPair.getId(),
                taskId, ResourceOperationTypeEnum.SUSPEND_REMOTE_REPLICATION_PAIR_LINK);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR, id);
        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.suspendRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrPair.getOpStatus().get(taskId);
            op.error(e);
            rrPair.getOpStatus().updateTaskStatus(taskId, op);
            rrPair.setInactive(true);
            _dbClient.updateObject(rrPair);

            throw e;
        }

        auditOp(OperationTypeEnum.SUSPEND_REMOTE_REPLICATION_PAIR_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrPair.getNativeId(), rrPair.getSourceElement(), rrPair.getTargetElement());

        return toTask(rrPair, taskId, op);
    }


    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/resume")
    public TaskResourceRep resumeRemoteReplicationPairLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: resumeRemoteReplicationPairLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);

        // todo: validate that this operation is valid: if operations are allowed on pairs, if pair state is valid for the operation, if the pair is reachable, etc.
        // Create a task for the resume remote replication pair operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationPair.class, rrPair.getId(),
                taskId, ResourceOperationTypeEnum.RESUME_REMOTE_REPLICATION_PAIR_LINK);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR, id);
        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.resumeRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrPair.getOpStatus().get(taskId);
            op.error(e);
            rrPair.getOpStatus().updateTaskStatus(taskId, op);
            rrPair.setInactive(true);
            _dbClient.updateObject(rrPair);

            throw e;
        }

        auditOp(OperationTypeEnum.RESUME_REMOTE_REPLICATION_PAIR_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrPair.getNativeId(), rrPair.getSourceElement(), rrPair.getTargetElement());

        return toTask(rrPair, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/swap")
    public TaskResourceRep swapRemoteReplicationPairLink(@PathParam("id") URI id) throws InternalException {
        _log.info("Called: swapRemoteReplicationPairLink() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);

        // todo: validate that this operation is valid: if operations are allowed on pairs, if pair state is valid for the operation, if the pair is reachable, etc.
        // Create a task for the swap remote replication pair operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationPair.class, rrPair.getId(),
                taskId, ResourceOperationTypeEnum.SWAP_REMOTE_REPLICATION_PAIR_LINK);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationSet.ElementType.REPLICATION_PAIR, id);
        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.swapRemoteReplicationElementLink(rrElement, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrPair.getOpStatus().get(taskId);
            op.error(e);
            rrPair.getOpStatus().updateTaskStatus(taskId, op);
            rrPair.setInactive(true);
            _dbClient.updateObject(rrPair);

            throw e;
        }

        auditOp(OperationTypeEnum.SWAP_REMOTE_REPLICATION_PAIR_LINK, true, AuditLogManager.AUDITOP_BEGIN,
                rrPair.getNativeId(), rrPair.getSourceElement(), rrPair.getTargetElement());

        return toTask(rrPair, taskId, op);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/change-replication-mode")
    public TaskResourceRep changeRemoteReplicationPairMode(@PathParam("id") URI id,
                                                            final RemoteReplicationModeChange param) throws InternalException {
        _log.info("Called: changeRemoteReplicationPairMode() with id {}", id);
        ArgValidator.checkFieldUriType(id, RemoteReplicationPair.class, "id");
        RemoteReplicationPair rrPair = queryResource(id);

        String newMode = param.getNewMode();

        // todo: validate that this operation is valid: if operations are allowed on pairs, if pair state is valid for the operation, if the pair is reachable, etc.
        // Create a task for the create remote replication pair operation
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(RemoteReplicationPair.class, rrPair.getId(),
                taskId, ResourceOperationTypeEnum.CHANGE_REMOTE_REPLICATION_MODE);

        RemoteReplicationElement rrElement = new RemoteReplicationElement(RemoteReplicationSet.ElementType.REPLICATION_PAIR, id);
        // send request to controller
        try {
            RemoteReplicationBlockServiceApiImpl rrServiceApi = getRemoteReplicationServiceApi();
            rrServiceApi.changeRemoteReplicationMode(rrElement, newMode, taskId);
        } catch (final ControllerException e) {
            _log.error("Controller Error", e);
            op = rrPair.getOpStatus().get(taskId);
            op.error(e);
            rrPair.getOpStatus().updateTaskStatus(taskId, op);
            rrPair.setInactive(true);
            _dbClient.updateObject(rrPair);

            throw e;
        }

        auditOp(OperationTypeEnum.CHANGE_REMOTE_REPLICATION_MODE, true, AuditLogManager.AUDITOP_BEGIN,
                rrPair.getNativeId(), rrPair.getSourceElement(), rrPair.getTargetElement());

        return toTask(rrPair, taskId, op);
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.REMOTE_REPLICATION_PAIR;
    }

    @Override
    protected RemoteReplicationPair queryResource(URI id) {
        ArgValidator.checkUri(id);
        RemoteReplicationPair replicationPair = _dbClient.queryObject(RemoteReplicationPair.class, id);
        ArgValidator.checkEntityNotNull(replicationPair, id, isIdEmbeddedInURL(id));
        return replicationPair;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

}
