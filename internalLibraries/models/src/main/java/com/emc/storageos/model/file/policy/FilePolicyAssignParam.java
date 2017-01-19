/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.file.policy;

/**
 * @author jainm15
 */
import java.io.Serializable;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "assign_file_policy")
public class FilePolicyAssignParam implements Serializable {

    public FilePolicyAssignParam() {
        super();
    }

    private static final long serialVersionUID = 1L;

    private Boolean applyOnTargetSite;

    // Vpool assignment parameters.
    private FilePolicyVpoolAssignParam vpoolAssignParams;

    // Project assignment parameters.
    private FilePolicyProjectAssignParam projectAssignParams;

    // File System assignment parameters.
    private FilePolicyFileSystemAssignParam fileSystemAssignParams;

    @XmlElement(name = "apply_on_target_site")
    public Boolean getApplyOnTargetSite() {
        return this.applyOnTargetSite;
    }

    public void setApplyOnTargetSite(Boolean applyOnTarget) {
        this.applyOnTargetSite = applyOnTarget;
    }

    @XmlElement(name = "vpool_assign_param")
    public FilePolicyVpoolAssignParam getVpoolAssignParams() {
        return this.vpoolAssignParams;
    }

    public void setVpoolAssignParams(FilePolicyVpoolAssignParam vpoolAssignParams) {
        this.vpoolAssignParams = vpoolAssignParams;
    }

    @XmlElement(name = "project_assign_param")
    public FilePolicyProjectAssignParam getProjectAssignParams() {
        return this.projectAssignParams;
    }

    public void setProjectAssignParams(FilePolicyProjectAssignParam projectAssignParams) {
        this.projectAssignParams = projectAssignParams;
    }

    @XmlElement(name = "filesystem_assign_param")
    public FilePolicyFileSystemAssignParam getFileSystemAssignParams() {
        return this.fileSystemAssignParams;
    }

    public void setFileSystemAssignParams(FilePolicyFileSystemAssignParam fileSystemAssignParams) {
        this.fileSystemAssignParams = fileSystemAssignParams;
    }

}
