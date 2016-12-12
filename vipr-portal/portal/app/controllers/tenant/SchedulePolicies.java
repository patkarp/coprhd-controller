/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package controllers.tenant;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.file.policy.FilePolicyListRestRep;
import com.emc.storageos.model.file.policy.FilePolicyParam;
import com.emc.storageos.model.file.policy.FilePolicyRestRep;
import com.emc.storageos.model.file.policy.FilePolicyScheduleParams;
import com.emc.storageos.model.file.policy.FileSnapshotPolicyExpireParam;
import com.emc.storageos.model.file.policy.FileSnapshotPolicyParam;
import com.emc.vipr.client.core.ACLResources;
import com.emc.vipr.client.core.FileProtectionPolicies;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.Models;
import controllers.util.ViprResourceController;
import jobs.vipr.TenantsCall;
import models.datatable.ScheculePoliciesDataTable;
import play.Logger;
import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Util;
import play.mvc.With;
import util.MessagesUtils;
import util.StringOption;
import util.TenantUtils;
import util.VirtualPoolUtils;
import util.builders.ACLUpdateBuilder;
import util.datatable.DataTablesSupport;

@With(Common.class)
@Restrictions({ @Restrict("PROJECT_ADMIN"), @Restrict("TENANT_ADMIN") })
public class SchedulePolicies extends ViprResourceController {

    protected static final String UNKNOWN = "schedule.policies.unknown";

    public static void list() {
        ScheculePoliciesDataTable dataTable = new ScheculePoliciesDataTable();
        TenantSelector.addRenderArgs();
        render(dataTable);
    }

    /*
     * @FlashException(value = "list", keep = true)
     * public static void listJson() {
     * String userId = Security.getUserInfo().getIdentifier();
     * List<SchedulePolicyRestRep> viprSchedulePolicies = getViprClient().schedulePolicies().getByTenant(uri(Models.currentAdminTenant()));
     * List<ScheculePoliciesDataTable.ScheculePolicy> scheculePolicies = Lists.newArrayList();
     * for (SchedulePolicyRestRep viprSchedulePolicy : viprSchedulePolicies) {
     * if (Security.isTenantAdmin()
     * || Security.isProjectAdmin()) {
     * scheculePolicies.add(new ScheculePoliciesDataTable.ScheculePolicy(viprSchedulePolicy));
     * }
     * }
     * renderJSON(DataTablesSupport.createJSON(scheculePolicies, params));
     * }
     */

    @FlashException(value = "list", keep = true)
    public static void listJson() {
        FilePolicyListRestRep filePolicies = getViprClient().fileProtectionPolicies().listFilePolicies();
        List<ScheculePoliciesDataTable.FileProtectionPolicy> listPolicies = Lists.newArrayList();
        for (NamedRelatedResourceRep policy : filePolicies.getFilePolicies()) {
            listPolicies.add(new ScheculePoliciesDataTable.FileProtectionPolicy(
                    getViprClient().fileProtectionPolicies().getFilePolicy(policy.getId())));
        }
        renderJSON(DataTablesSupport.createJSON(listPolicies, params));
    }

    @FlashException(value = "list", keep = true)
    public static void create() {
        SchedulePolicyForm schedulePolicy = new SchedulePolicyForm();
        schedulePolicy.tenantId = Models.currentAdminTenant();
        addRenderArgs();
        addDateTimeRenderArgs();
        addTenantOptionsArgs();
        render("@edit", schedulePolicy);
    }

    @FlashException(value = "list", keep = true)
    public static void edit(String id) {
        FilePolicyRestRep filePolicyRestRep = getViprClient().fileProtectionPolicies().get(uri(id));
        if (filePolicyRestRep != null) {
            SchedulePolicyForm schedulePolicy = new SchedulePolicyForm().form(filePolicyRestRep);

            addRenderArgs();
            addDateTimeRenderArgs();
            addTenantOptionsArgs();

            render(schedulePolicy);
        } else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }

    }

    @Util
    private static void addRenderArgs() {
        List<StringOption> policyTypeOptions = Lists.newArrayList();
        policyTypeOptions.add(new StringOption("file_snapshot", MessagesUtils.get("schedulePolicy.snapshot")));
        policyTypeOptions.add(new StringOption("file_replication", MessagesUtils.get("schedulePolicy.replication")));
        renderArgs.put("policyTypeOptions", policyTypeOptions);

    }

    private static void addDateTimeRenderArgs() {
        final String LAST_DAY_OF_MONTH = "L";
        // Days of the Week
        Map<String, String> daysOfWeek = Maps.newLinkedHashMap();
        for (int i = 1; i <= 7; i++) {
            String num = String.valueOf(i);
            daysOfWeek.put(MessagesUtils.get("datetime.daysOfWeek." + num).toLowerCase(), MessagesUtils.get("datetime.daysOfWeek." + num));
        }
        renderArgs.put("daysOfWeek", daysOfWeek);

        // Days of the Month
        Map<String, String> daysOfMonth = Maps.newLinkedHashMap();
        for (int i = 1; i <= 31; i++) {
            String num = String.valueOf(i);
            daysOfMonth.put(num, num);
        }

        renderArgs.put("daysOfMonth", daysOfMonth);

        List<StringOption> expirationTypeOptions = Lists.newArrayList();
        expirationTypeOptions.add(new StringOption("hours", MessagesUtils.get("schedulePolicy.hours")));
        expirationTypeOptions.add(new StringOption("days", MessagesUtils.get("schedulePolicy.days")));
        expirationTypeOptions.add(new StringOption("weeks", MessagesUtils.get("schedulePolicy.weeks")));
        expirationTypeOptions.add(new StringOption("months", MessagesUtils.get("schedulePolicy.months")));

        renderArgs.put("expirationTypeOptions", expirationTypeOptions);

        String[] hoursOptions = new String[24];
        for (int i = 0; i < 24; i++) {
            String num = "";
            if (i < 10) {
                num = "0" + String.valueOf(i);
            } else {
                num = String.valueOf(i);
            }
            hoursOptions[i] = num;
        }
        String[] minutesOptions = new String[60];
        for (int i = 0; i < 60; i++) {
            String num = "";
            if (i < 10) {
                num = "0" + String.valueOf(i);
            } else {
                num = String.valueOf(i);
            }
            minutesOptions[i] = num;
        }

        renderArgs.put("hours", StringOption.options(hoursOptions));
        renderArgs.put("minutes", StringOption.options(minutesOptions));

    }

    private static void addTenantOptionsArgs() {

        if (TenantUtils.canReadAllTenants() && VirtualPoolUtils.canUpdateACLs()) {
            renderArgs.put("tenantOptions", new TenantsCall().asPromise());
        }
    }

    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void save(SchedulePolicyForm schedulePolicy) {
        if (schedulePolicy == null) {
            Logger.error("No policy parameters passed");
            badRequest("No policy parameters passed");
            return;
        }
        schedulePolicy.validate("schedulePolicy");
        if (Validation.hasErrors()) {
            Common.handleError();
        }
        schedulePolicy.id = params.get("id");
        if (schedulePolicy.isNew()) {
            schedulePolicy.tenantId = Models.currentAdminTenant();
            FilePolicyParam policyParam = updatePolicyParam(schedulePolicy);
            getViprClient().fileProtectionPolicies().create(policyParam);
        } else {
            FilePolicyRestRep schedulePolicyRestRep = getViprClient().fileProtectionPolicies().get(uri(schedulePolicy.id));
            FilePolicyParam input = updatePolicyParam(schedulePolicy);
            getViprClient().fileProtectionPolicies().update(schedulePolicyRestRep.getId(), input);
        }
        // Update the ACLs
        FileProtectionPolicies filePolicies = getViprClient().fileProtectionPolicies();
        schedulePolicy.saveTenantACLs(filePolicies);
        flash.success(MessagesUtils.get("projects.saved", schedulePolicy.policyName));
        if (StringUtils.isNotBlank(schedulePolicy.referrerUrl)) {
            redirect(schedulePolicy.referrerUrl);
        } else {
            list();
        }

    }

    /*
     * private static PolicyParam updatePolicyParam(SchedulePolicyForm schedulePolicy) {
     * PolicyParam param = new PolicyParam();
     * param.setPolicyName(schedulePolicy.policyName);
     * param.setPolicyType(schedulePolicy.policyType);
     * 
     * SchedulePolicyParam scheduleParam = new SchedulePolicyParam();
     * scheduleParam.setScheduleTime(schedulePolicy.scheduleHour + ":" + schedulePolicy.scheduleMin);
     * scheduleParam.setScheduleFrequency(schedulePolicy.frequency);
     * scheduleParam.setScheduleRepeat(schedulePolicy.repeat);
     * 
     * if (schedulePolicy.frequency != null && "weeks".equals(schedulePolicy.frequency)) {
     * if (schedulePolicy.scheduleDayOfWeek != null) {
     * scheduleParam.setScheduleDayOfWeek(schedulePolicy.scheduleDayOfWeek);
     * }
     * 
     * } else if (schedulePolicy.frequency != null && "months".equals(schedulePolicy.frequency)) {
     * scheduleParam.setScheduleDayOfMonth(schedulePolicy.scheduleDayOfMonth);
     * }
     * param.setPolicySchedule(scheduleParam);
     * 
     * ScheduleSnapshotExpireParam expireParam = new ScheduleSnapshotExpireParam();
     * 
     * if (schedulePolicy.expiration != null && !"NEVER".equals(schedulePolicy.expiration)) {
     * expireParam.setExpireType(schedulePolicy.expireType);
     * expireParam.setExpireValue(schedulePolicy.expireValue);
     * param.setSnapshotExpire(expireParam);
     * }
     * if("NEVER".equals(schedulePolicy.expiration)){
     * expireParam.setExpireType("never");
     * param.setSnapshotExpire(expireParam);
     * }
     * 
     * return param;
     * 
     * }
     */

    private static FilePolicyParam updatePolicyParam(SchedulePolicyForm schedulePolicy) {
        FilePolicyParam param = new FilePolicyParam();
        param.setPolicyName(schedulePolicy.policyName);
        param.setPolicyType(schedulePolicy.policyType);

        FilePolicyScheduleParams scheduleParam = new FilePolicyScheduleParams();
        scheduleParam.setScheduleTime(schedulePolicy.scheduleHour + ":" + schedulePolicy.scheduleMin);
        scheduleParam.setScheduleFrequency(schedulePolicy.frequency);
        scheduleParam.setScheduleRepeat(schedulePolicy.repeat);

        if (schedulePolicy.frequency != null && "weeks".equals(schedulePolicy.frequency)) {
            if (schedulePolicy.scheduleDayOfWeek != null) {
                scheduleParam.setScheduleDayOfWeek(schedulePolicy.scheduleDayOfWeek);
            }

        } else if (schedulePolicy.frequency != null && "months".equals(schedulePolicy.frequency)) {
            scheduleParam.setScheduleDayOfMonth(schedulePolicy.scheduleDayOfMonth);
        }
        param.setPolicySchedule(scheduleParam);

        FileSnapshotPolicyParam snapshotParam = new FileSnapshotPolicyParam();
        FileSnapshotPolicyExpireParam snapExpireParam = new FileSnapshotPolicyExpireParam();
        if (schedulePolicy.expiration != null && !"NEVER".equals(schedulePolicy.expiration)) {
            snapExpireParam.setExpireType(schedulePolicy.expireType);
            snapExpireParam.setExpireValue(schedulePolicy.expireValue);
            snapshotParam.setSnapshotExpireParams(snapExpireParam);
            param.setSnapshotPolicyPrams(snapshotParam);
        }
        if ("NEVER".equals(schedulePolicy.expiration)) {
            snapExpireParam.setExpireType("never");
            snapshotParam.setSnapshotExpireParams(snapExpireParam);
            param.setSnapshotPolicyPrams(snapshotParam);
        }

        return param;

    }

    @FlashException("list")
    public static void delete(@As(",") String[] ids) {
        if (ids != null && ids.length > 0) {
            for (String id : ids) {
                getViprClient().fileProtectionPolicies().delete(uri(id));
            }
            flash.success(MessagesUtils.get("schedulepolicies.deleted"));
        }
        list();
    }

    public static class SchedulePolicyForm {

        public String id;

        public String tenantId;

        @Required
        @MaxSize(128)
        @MinSize(2)
        // Schedule policy name
        public String policyName;
        // Type of the policy
        public String policyType;

        // File Policy schedule type - daily, weekly, monthly.
        public String frequency = "days";

        // Policy execution repeats on
        public Long repeat = 1L;

        // Time when policy run
        public String scheduleTime;

        // week day when policy run
        public String scheduleDayOfWeek;

        // Day of the month
        public Long scheduleDayOfMonth;

        // Schedule Snapshot expire type e.g hours, days, weeks, months and never
        public String expireType;

        // Schedule Snapshot expire after
        public int expireValue = 2;

        public String expiration = "EXPIRE_TIME";
        public String referrerUrl;

        public String scheduleHour;
        public String scheduleMin;

        public boolean enableTenants;

        public List<String> tenants;

        /*
         * public SchedulePolicyForm form(SchedulePolicyRestRep restRep) {
         * 
         * this.id = restRep.getPolicyId().toString();
         * this.tenantId = restRep.getTenant().getId().toString();
         * this.policyType = restRep.getPolicyType();
         * this.policyName = restRep.getPolicyName();
         * this.frequency = restRep.getScheduleFrequency();
         * this.scheduleTime = restRep.getScheduleTime();
         * 
         * if (restRep.getScheduleDayOfMonth() != null) {
         * this.scheduleDayOfMonth = restRep.getScheduleDayOfMonth().intValue();
         * }
         * 
         * if (restRep.getScheduleDayOfWeek() != null) {
         * this.scheduleDayOfWeek = restRep.getScheduleDayOfWeek();
         * }
         * 
         * if (restRep.getSnapshotExpireType() != null) {
         * this.expireType = restRep.getSnapshotExpireType();
         * }
         * 
         * if (restRep.getSnapshotExpireTime() != null) {
         * this.expireValue = restRep.getSnapshotExpireTime().intValue();
         * }
         * if (restRep.getScheduleRepeat() != null) {
         * this.repeat = restRep.getScheduleRepeat().intValue();
         * }
         * String[] hoursMin = this.scheduleTime.split(":");
         * if (hoursMin.length > 1) {
         * this.scheduleHour = hoursMin[0];
         * String[] minWithStrings = hoursMin[1].split(" ");
         * if (minWithStrings.length > 0) {
         * this.scheduleMin = minWithStrings[0];
         * }
         * 
         * }
         * if (this.expireType == null || "never".equals(this.expireType)) {
         * this.expiration = "NEVER";
         * }else{
         * this.expiration = "EXPIRE_TIME";
         * }
         * 
         * return this;
         * 
         * }
         */

        public SchedulePolicyForm form(FilePolicyRestRep restRep) {

            this.id = restRep.getId().toString();
            // this.tenantId = restRep.getTenant().getId().toString();
            this.policyType = restRep.getType();
            this.policyName = restRep.getName();
            this.frequency = restRep.getSchedule().getFrequency();
            this.scheduleTime = restRep.getSchedule().getTime();

            if (restRep.getSchedule().getDayOfMonth() != null) {
                this.scheduleDayOfMonth = restRep.getSchedule().getDayOfMonth();
            }

            if (restRep.getSchedule().getDayOfWeek() != null) {
                this.scheduleDayOfWeek = restRep.getSchedule().getDayOfWeek();
            }

            if (restRep.getSnapshotSettings() != null && restRep.getSnapshotSettings().getExpiryType() != null) {
                this.expireType = restRep.getSnapshotSettings().getExpiryType();
            }

            if (restRep.getSnapshotSettings() != null && restRep.getSnapshotSettings().getExpiryTime() != null) {
                this.expireValue = restRep.getSnapshotSettings().getExpiryTime().intValue();
            }
            if (restRep.getSchedule() != null) {
                this.repeat = restRep.getSchedule().getRepeat();
            }
            String[] hoursMin = this.scheduleTime.split(":");
            if (hoursMin.length > 1) {
                this.scheduleHour = hoursMin[0];
                String[] minWithStrings = hoursMin[1].split(" ");
                if (minWithStrings.length > 0) {
                    this.scheduleMin = minWithStrings[0];
                }

            }
            if (this.expireType == null || "never".equals(this.expireType)) {
                this.expiration = "NEVER";
            } else {
                this.expiration = "EXPIRE_TIME";
            }

            // Get the ACLs
            FileProtectionPolicies fileProtectionPolicies = getViprClient().fileProtectionPolicies();
            loadTenantACLs(fileProtectionPolicies);
            return this;

        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void validate(String formName) {
            Validation.valid(formName, this);
            Validation.required(formName + ".policyName", policyName);

            if (policyName == null || policyName.isEmpty()) {
                Validation.addError(formName + ".policyName", MessagesUtils.get("schedulePolicy.policyName.error.required"));
            }
        }

        /**
         * Loads the tenant ACL information from the provided ACLResources.
         * 
         * @param resources
         *            the resources from which to load the ACLs.
         */
        protected void loadTenantACLs(ACLResources resources) {
            this.tenants = Lists.newArrayList();

            URI policyId = ResourceUtils.uri(id);
            if (policyId != null) {
                for (ACLEntry acl : resources.getACLs(policyId)) {
                    if (StringUtils.isNotBlank(acl.getTenant())) {
                        this.tenants.add(acl.getTenant());
                    }
                }
            }
            this.enableTenants = !tenants.isEmpty();
        }

        /**
         * Saves the tenant ACL information using the provided ACLResources.
         * 
         * @param resources
         *            the resources on which to save the tenant ACLs.
         */
        protected void saveTenantACLs(ACLResources resources) {
            // Only allow a user than can read all tenants and update ACLs do this
            if (TenantUtils.canReadAllTenants() && VirtualPoolUtils.canUpdateACLs()) {
                URI policyId = ResourceUtils.uri(id);
                if (policyId != null) {
                    Set<String> tenantIds = Sets.newHashSet();
                    if (isTrue(enableTenants) && (tenants != null)) {
                        tenantIds.addAll(tenants);
                    }
                    ACLUpdateBuilder builder = new ACLUpdateBuilder(resources.getACLs(policyId));
                    builder.setTenants(tenantIds);
                    resources.updateACLs(policyId, builder.getACLUpdate());
                }
            }
        }

    }

}
