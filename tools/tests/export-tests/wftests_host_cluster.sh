#!/bin/sh
#
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
# set -x

# Test - Host Add Initiator
#
# Happy path test for add initiator to a host that is part of an exclusive and shared export group.
#
# 1. Export volume to an exclusive export group
# 2. Export volume to a shared export group
# 3. Add initiator port wwn to network
# 4. Add initiator to host
# 5. Delete exclusive export group
# 6. Delete shared export group
# 7. Delete initiator
# 8. Remove initiator port wwn from network
#
test_host_add_initiator() {
    echot "Test test_host_add_initiator"
    expname=${EXPORT_GROUP_NAME}t1
    cfs="ExportGroup ExportMask Initiator Network"
    mkdir -p results/${item}

    smisprovider list | grep SIM > /dev/null
    if [ $? -eq 0 ]; then
        FC_ZONE_A=${CLUSTER1NET_SIM_NAME}
    fi

    snap_db 1 ${cfs}

    test_pwwn=`randwwn`
    test_nwwn=`randwwn`

    exclusive_export=${expname}1_exclusive
    cluster_export=${expname}1_cluster

    verify_export ${exclusive_export} ${HOST1} gone
    verify_export ${cluster_export} ${CLUSTER} gone

    # Run the exclusive export group create command
    runcmd export_group create $PROJECT ${exclusive_export} $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts ${HOST1}

    # Run the cluster export group create command
    runcmd export_group create $PROJECT ${cluster_export} $NH --type Cluster --volspec ${PROJECT}/${VOLNAME}-2 --clusters ${TENANT}/${CLUSTER}

    # Verify the initiator does not exist in the ExportGroup
    add_init="false"
    if [[ $(export_contains $exclusive_export $test_pwwn) || $(export_contains $cluster_export $test_pwwn) ]]; then
        echo "Add initiator to host test failed. Initiator "${test_pwwn}" already exists" 
    else
        # Add initiator to network
        runcmd run transportzone add ${FC_ZONE_A} ${test_pwwn}

        # Add initiator to host.  This will add the initiator to both the exclusive and shared export groups. This is because
        # The host is already part of the cluster that was used to create the cluster export group.
        runcmd initiator create ${HOST1} FC ${test_pwwn} --node ${test_nwwn}
        add_init="true"

        # Verify the initiator does exists in the ExportGroup
        if [[ $(export_contains $exclusive_export $test_pwwn) && $(export_contains $cluster_export $test_pwwn) ]]; then            
            echo "Verified that initiator "${test_pwwn}" has been added to export"
        else
            echo "Add initiator to host test failed. Initiator "${test_pwwn}" was not added to the export"  
        fi         
    fi

    # Remove the exclusive export
    runcmd export_group delete $PROJECT/${exclusive_export}
    # Remove the shared export
    runcmd export_group delete $PROJECT/${cluster_export}

    verify_export ${exclusive_export} ${HOST1} gone
    verify_export ${cluster_export} ${HOST1} gone

    if [ ${add_init} = "true"  ]; then
        runcmd initiator delete ${HOST1}/${test_pwwn}
        runcmd run transportzone remove ${FC_ZONE_A} ${test_pwwn}
    fi

    snap_db 2 ${cfs}  

    # Validate that nothing was left behind
    validate_db 1 2 ${cfs}          
}

# Test - Host Add Initiator Failure
#
# Failure test for add initiator to a host that is part of an exclusive and shared export group.
#
# 1. Export volume to an exclusive export group
# 2. Export volume to a shared export group
# 3. Add initiator port wwn to network
# 4. Add initiator to host
# 5. Delete exclusive export group
# 6. Delete shared export group
# 7. Delete initiator
# 8. Remove initiator port wwn from network
#
test_host_add_initiator_failure() {
    echot "Test test_host_add_initiator_failure"
    item=${RANDOM}
    cfs="ExportGroup ExportMask Initiator Network"
    expname=${EXPORT_GROUP_NAME}t1
    
    export_failure_injections="failure_001_host_export_ComputeSystemControllerImpl.updateExportGroup_before_update \
                               #failure_002_host_export_ComputeSystemControllerImpl.updateExportGroup_after_update"
    
    mkdir -p results/${item}

    smisprovider list | grep SIM > /dev/null
    if [ $? -eq 0 ]; then
        FC_ZONE_A=${CLUSTER1NET_SIM_NAME}
    fi
    
    snap_db 1 ${cfs}
    
    test_pwwn=`randwwn`
    test_nwwn=`randwwn`

    exclusive_export=${expname}1_exclusive
    cluster_export=${expname}1_cluster

    verify_export ${exclusive_export} ${HOST1} gone
    verify_export ${cluster_export} ${CLUSTER} gone
    
    failure_injections="${export_failure_injections}"
    
    for failure in ${failure_injections}
    do
        secho "Running Add initiator to host with failure scenario: ${failure}..."
        
        # Turn on failure at a specific point
        set_artificial_failure ${failure}
        
        # Check the state before exporting volumes
        snap_db 2 ${cfs}
        
        # Run the exclusive export group create command
        runcmd export_group create $PROJECT ${exclusive_export} $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts ${HOST1}

        # Run the cluster export group create command
        runcmd export_group create $PROJECT ${cluster_export} $NH --type Cluster --volspec ${PROJECT}/${VOLNAME}-2 --clusters ${TENANT}/${CLUSTER}

        # Verify the initiator does not exist in the ExportGroup
        add_init="false"
        if [[ $(export_contains $exclusive_export $test_pwwn) || $(export_contains $cluster_export $test_pwwn) ]]; then
            echo "Add initiator to host test failed. Initiator "${test_pwwn}" already exists" 
        else
            # Add initiator to network
            runcmd run transportzone add ${FC_ZONE_A} ${test_pwwn}
    
            # Add initiator to host.  This will add the initiator to both the exclusive and shared export groups. This is because
            # The host is already part of the cluster that was used to create the cluster export group.
            fail initiator create ${HOST1} FC ${test_pwwn} --node ${test_nwwn}
    
            # Let the async jobs calm down
            sleep 5
    
            # Perform any DB validation in here
            snap_db 3 ${cfs}

            # Validate nothing was left behind
            validate_db 2 3 ${cfs}

            # Rerun the command
            set_artificial_failure none
            runcmd initiator create ${HOST1} FC ${test_pwwn} --node ${test_nwwn}
    
            # Verify the initiator does exists in the ExportGroup
            if [[ $(export_contains $exclusive_export $test_pwwn) && $(export_contains $cluster_export $test_pwwn) ]]; then
                add_init="true"
                echo "Verified that initiator "${test_pwwn}" has been added to export"
            else
                echo "Add initiator to host test failed. Initiator "${test_pwwn}" was not added to the export"  
            fi
        fi

        # Remove the exclusive export
        runcmd export_group delete $PROJECT/${exclusive_export}
        # Remove the shared export
        runcmd export_group delete $PROJECT/${cluster_export}

        verify_export ${exclusive_export} ${HOST1} gone
        verify_export ${cluster_export} ${HOST1} gone
        
        if [ ${add_init} = "true"  ]; then
            runcmd initiator delete ${HOST1}/${test_pwwn}
            runcmd run transportzone remove ${FC_ZONE_A} ${test_pwwn}
        fi
        
        snap_db 4 ${cfs}  

        # Validate that nothing was left behind
        validate_db 1 4 ${cfs}
    done
}

test_vcenter_event() {
    echot "vCenter Event Test: Export to cluster, move host into cluster, rediscover vCenter, approve event"
    expname=${EXPORT_GROUP_NAME}t2
    item=${RANDOM}
    cfs="ExportGroup ExportMask Host Initiator Cluster"
    mkdir -p results/${item}
    set_controller_cs_discovery_refresh_interval 1

    verify_export ${expname}1 ${HOST1} gone

    # Perform any DB validation in here
    snap_db 1 ${cfs}

    # Run the export group command
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec ${PROJECT}/${VOLNAME}-1 --clusters "emcworld/cluster-1"

    # move hosts21 into cluster-1
    remove_host_from_cluster "host21" "cluster-2"
    add_host_to_cluster "host21" "cluster-1"
    discover_vcenter "vcenter1"

    EVENT_ID=$(get_pending_event)
    if [ -z "$EVENT_ID" ]
    then
      echo "FAILED. Expected an event"
    else
      approve_pending_event $EVENT_ID 
    fi

    # Remove the shared export
    runcmd export_group delete ${PROJECT}/${expname}1
    
    # Set vCenter back to previous state
    remove_host_from_cluster "host21" "cluster-1"
    add_host_to_cluster "host21" "cluster-2"
    discover_vcenter "vcenter1"

    # Snap the DB again
    snap_db 2 ${cfs}

    # Validate nothing was left behind
    validate_db 1 2 ${cfs}

    verify_export ${expname}1 ${HOST1} gone
}


# Helper methods for tests
add_host_to_cluster() {
    host=$1
    cluster=$2
    args="addHostTo $cluster $host"
    echo "Adding host $host to $cluster"
    curl -ikL --header "Content-Type: application/json" --header "username:xx" --header "password: yy" --data '{"args": "'"$args"'"}' -X POST http://${HW_SIMULATOR_IP}:8235/vmware/modify &> /dev/null
}

remove_host_from_cluster() {
    host=$1
    cluster=$2
    args="removeHostFrom $cluster $host"
    echo "Removing host $host from $cluster"
    curl -ikL --header "Content-Type: application/json" --header "username:xx" --header "password: yy" --data '{"args": "'"$args"'"}' -X POST http://${HW_SIMULATOR_IP}:8235/vmware/modify &> /dev/null
}

discover_vcenter() {
    vcenter=$1
    echo "Discovering vCenter $vcenter"
    vcenter discover $vcenter
}

get_pending_event() {
    echo $(events list emcworld | grep pending | awk '{print $1}')
} 

approve_pending_event() {
    echo "Approving event $1"
    events approve $1
}


# Test Host Remove Initiator
#
# 1. Add manual host
# 2. Add 4 initiators to the host
# 3. Add these 4 initiators to the network assigned to your virtual array
# 4. Create and export a volume to this host
# 5. Verify that all 4 initiators are in the export group for this host
# 6. Remove 2 initiators from the host
# 7. Monitor the export group update task and verify the export group contains only 2 initiators when complete
# 8. Clean up
test_host_remove_initiator() {
    echot "Test host_remove_initiator Begins"

    common_failure_injections="failure_001_host_export_ComputeSystemControllerImpl.updateExportGroup_before_update \
                               failure_002_host_export_ComputeSystemControllerImpl.updateExportGroup_after_update"


    failure_injections="${common_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    #failure_injections="failure_001_host_export_ComputeSystemControllerImpl.updateExportGroup_before_update"

    # Create volume
    random_number=${RANDOM}    
    volume=${VOLNAME}-${random_number}
    runcmd volume create ${volume} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB
        
    for failure in ${failure_injections}
    do
        secho "Running host_remove_initiator with failure scenario: ${failure}..."
        
        column_family="Volume ExportGroup ExportMask"
        random_number=${RANDOM}
        mkdir -p results/${random_number}
        host=fakehost${random_number}
        exportgroup=exportgroup${random_number}
        
        # Snap DB
        snap_db 1 ${column_family}
            
        # Create new random WWNs for nodes and initiators
        node1=`randwwn 20 C1`
        node2=`randwwn 20 C2`
        node3=`randwwn 20 C3`
        node4=`randwwn 20 C4`
        init1=`randwwn 10 C1`
        init2=`randwwn 10 C2`
        init3=`randwwn 10 C3`
        init4=`randwwn 10 C4`
        
        # Add initator WWNs to the network
        run transportzone add $NH/${FC_ZONE_A} ${init1}
        run transportzone add $NH/${FC_ZONE_A} ${init2}
        run transportzone add $NH/${FC_ZONE_A} ${init3}
        run transportzone add $NH/${FC_ZONE_A} ${init4}
            
        # Create fake host
        runcmd hosts create $host $TENANT Other ${host}.lss.emc.com --port 1       
        
        # Create new initators and add to fakehost
        runcmd initiator create $host FC ${init1} --node ${node1}
        runcmd initiator create $host FC ${init2} --node ${node2}
        runcmd initiator create $host FC ${init3} --node ${node3}
        runcmd initiator create $host FC ${init4} --node ${node4}
        
        # Zzzzzz
        sleep 2
        
        # Export the volume to the fake host    
        runcmd export_group create $PROJECT ${exportgroup} $NH --type Host --volspec ${PROJECT}/${volume} --hosts "${host}"
        
        # Double check the export group to ensure the initiators are present
        foundinit1=`export_group show $PROJECT/${exportgroup} | grep ${init1}`
        foundinit2=`export_group show $PROJECT/${exportgroup} | grep ${init2}`
        foundinit3=`export_group show $PROJECT/${exportgroup} | grep ${init3}`
        foundinit4=`export_group show $PROJECT/${exportgroup} | grep ${init4}`    
        
        if [[ "${foundinit1}" = ""  || "${foundinit2}" = "" || "${foundinit3}" = "" || "${foundinit4}" = "" ]]; then
            # Fail, initiators should have been added to the export group
            echo "+++ FAIL - Some initiators were not found on the export group...fail."
            exit 1
        else
            echo "+++ SUCCESS - All initiators from host present on export group"   
        fi
                
        # Zzzzzz
        sleep 2
        
        # Turn on failure at a specific point
        set_artificial_failure ${failure}
        
        # Try and remove an initiator from the host, this should fail during updateExport()
        fail initiator delete ${host}/${init1}
        
        # Zzzzzz
        sleep 5
        
        # Rerun the command
        set_artificial_failure none 
               
        # Delete two initiators from the host...these should now be auto-removed from the export group 
        runcmd initiator delete ${host}/${init1}
        runcmd initiator delete ${host}/${init2}
        
        # Zzzzzz
        sleep 5
        
        # Ensure that initiator 1 and 2 have been removed
        foundinit1=`export_group show $PROJECT/${exportgroup} | grep ${init1}`
        foundinit2=`export_group show $PROJECT/${exportgroup} | grep ${init2}`
        foundinit3=`export_group show $PROJECT/${exportgroup} | grep ${init3}`
        foundinit4=`export_group show $PROJECT/${exportgroup} | grep ${init4}`
    
        if [[ "${foundinit1}" != "" || "${foundinit2}" != "" ]]; then
            # Fail, initiators 1 and 2 should be removed and initiators 3 and 4 should still be present
            echo "+++ FAIL - Expected host initiators were not removed from the export group."
            exit 1
        else
            echo "+++ SUCCESS - All expected host initiators removed from export group" 
        fi
        
        # Cleanup    
        # 1. Unexport the volume
        # 2. Delete the export group
        # 3. Delete the host initiators
        # 4. Delete the host
        runcmd export_group update ${PROJECT}/${exportgroup} --remVols ${PROJECT}/${volume}                      
        runcmd export_group delete ${PROJECT}/${exportgroup}    
        sleep 5
        runcmd initiator delete ${host}/${init3}
        runcmd initiator delete ${host}/${init4}
        runcmd hosts delete ${host}
        
        # Snap DB
        snap_db 2 ${column_family}
        
        # Validate DB
        validate_db 1 2 ${column_family}
    done
    
    # Cleanup the volume
    runcmd volume delete ${PROJECT}/${volume} --wait 
}

test_happy_path_move_clustered_host_to_another_cluster() {
    echot "Test test_happy_path_move_clustered_host_to_another_cluster Begins"

    secho "Running test_happy_path_move_clustered_host_to_another_cluster"
        
    column_family="Volume ExportGroup ExportMask"
    random_number=${RANDOM}
    mkdir -p results/${random_number}
    volume1=${VOLNAME}-1
    volume2=${VOLNAME}-2
    
    host1=fakehost-1-${random_number}
    host2=fakehost-2-${random_number}
    cluster1=fakecluster-1-${random_number}
    cluster2=fakecluster-2-${random_number}
    
    exportgroup1=exportgroup-1-${random_number}
    exportgroup2=exportgroup-2-${random_number}
    
    # Snap DB
    snap_db 1 ${column_family}
        
    # Create new random WWNs for nodes and initiators
    node1=`randwwn 20 C1`
    node2=`randwwn 20 C2`
    node3=`randwwn 20 C3`
    node4=`randwwn 20 C4`
    init1=`randwwn 10 C1`
    init2=`randwwn 10 C2`
    init3=`randwwn 10 C3`
    init4=`randwwn 10 C4`
        
    # Add initator WWNs to the network
    run transportzone add $NH/${FC_ZONE_A} ${init1}
    run transportzone add $NH/${FC_ZONE_A} ${init2}
    run transportzone add $NH/${FC_ZONE_A} ${init3}
    run transportzone add $NH/${FC_ZONE_A} ${init4}
        
    # Create fake clusters
    runcmd cluster create ${cluster1} $TENANT
    runcmd cluster create ${cluster2} $TENANT
    
    # Create fake hosts
    runcmd hosts create ${host1} $TENANT Other ${host1}.lss.emc.com --port 1 --cluster $TENANT/${cluster1}
    runcmd hosts create ${host2} $TENANT Other ${host2}.lss.emc.com --port 1 --cluster $TENANT/${cluster2}
    
    # Create new initators and add to fakehosts
    runcmd initiator create ${host1} FC ${init1} --node ${node1}
    runcmd initiator create ${host1} FC ${init2} --node ${node2}
    runcmd initiator create ${host2} FC ${init3} --node ${node3}
    runcmd initiator create ${host2} FC ${init4} --node ${node4}

    # Export the volumes to the fake clusters    
    runcmd export_group create $PROJECT ${exportgroup1} $NH --type Cluster --volspec ${PROJECT}/${volume1} --clusters ${TENANT}/${cluster1}
    runcmd export_group create $PROJECT ${exportgroup2} $NH --type Cluster --volspec ${PROJECT}/${volume2} --clusters ${TENANT}/${cluster2}
    
    # Double check the export groups to ensure the initiators are present
    foundinit1=`export_group show $PROJECT/${exportgroup1} | grep ${init1}`
    foundinit2=`export_group show $PROJECT/${exportgroup1} | grep ${init2}`
    foundinit3=`export_group show $PROJECT/${exportgroup2} | grep ${init3}`
    foundinit4=`export_group show $PROJECT/${exportgroup2} | grep ${init4}`
    
    if [[ "${foundinit1}" = ""  || "${foundinit2}" = "" || "${foundinit3}" = "" || "${foundinit4}" = "" ]]; then
        # Fail, initiators should have been added to the export group
        echo "+++ FAIL - Some initiators were not found on the export groups...fail."
        exit 1
    else
        echo "+++ SUCCESS - All initiators from clusters present on export group"   
    fi
    
    # Move host1 into cluster2
    runcmd hosts update $host1 --cluster ${TENANT}/${cluster2}
    
    # TODO wait on export group update tasks instead of sleeping
    sleep 15
    
    # Ensure that all initiators are now in same cluster
    foundinit1=`export_group show $PROJECT/${exportgroup2} | grep ${init1}`
    foundinit2=`export_group show $PROJECT/${exportgroup2} | grep ${init2}`
    foundinit3=`export_group show $PROJECT/${exportgroup2} | grep ${init3}`
    foundinit4=`export_group show $PROJECT/${exportgroup2} | grep ${init4}`

    if [[ "${foundinit1}" = ""  || "${foundinit2}" = "" || "${foundinit3}" = "" || "${foundinit4}" = "" ]]; then
        # Fail, initiators should have been added to the export group
        echo "+++ FAIL - Some initiators were not found  in export group ${exportgroup2}...fail."
        exit 1
    else
        echo "+++ SUCCESS - All initiators from clusters present on export group ${exportgroup2}"   
    fi

    # The other export group should be deleted    
    fail export_group show $PROJECT/${exportgroup1}   
    
    # Cleanup    
    runcmd export_group update ${PROJECT}/${exportgroup2} --remVols ${PROJECT}/${volume2}
    runcmd export_group delete ${PROJECT}/${exportgroup2}    
    runcmd hosts delete ${host1}
    runcmd hosts delete ${host2}
    
    # Snap DB
    snap_db 2 ${column_family}
    
    # Validate DB
    validate_db 1 2 ${column_family}
}


# Test - Manually Move Non Clustered Host to Cluster
#
# Happy path test for manually moving (through API) a non-clustered host to a cluster.
#
# 1. Create host
# 2. Create initiator
# 3. Export volume to an exclusive export group for host
# 4. Export a second volume to a cluster export group
# 5. Host update to move the host to the cluster export group
# 6. Delete exclusive export group
# 7. Delete cluster export group
# 8. Delete host
# 9. Remove initiator port wwn from network
#
test_manual_move_non_clustered_host_to_cluster() {
    echot "Test test_manual_move_non_clustered_host_to_cluster"
    host=fakehost${RANDOM}
    cfs="ExportGroup ExportMask Initiator Network Host"
    mkdir -p results/${item}

    #smisprovider list | grep SIM > /dev/null
    #if [ $? -eq 0 ]; then
    #    FC_ZONE_A=${CLUSTER1NET_SIM_NAME}
    #fi

    snap_db 1 ${cfs}

    fake_pwwn1=`randwwn`
    fake_nwwn1=`randwwn`

    cluster1_name="cluster-1"

    exclusive_export=exclusive1_export
    cluster1_export=cluster1_export

    verify_export ${exclusive_export} $host} gone
    verify_export ${cluster1_export} ${cluster1_name} gone

    # Add initator WWNs to the network
    run transportzone add $NH/${FC_ZONE_A} ${fake_pwwn1}
            
    # Create fake host
    runcmd hosts create $host $TENANT Esx ${host}.lss.emc.com --port 1
        
    # Create new initators and add to fakehost
    runcmd initiator create $host FC ${fake_pwwn1} --node ${fake_nwwn1}

    # Run the exclusive export group create command
    runcmd export_group create $PROJECT ${exclusive_export} $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts ${host}
    
    # Run the cluster export group create command
    runcmd export_group create $PROJECT ${cluster1_export} $NH --type Cluster --volspec ${PROJECT}/${VOLNAME}-2 --clusters ${TENANT}/${cluster1_name}

    # Move the host to first cluster
    runcmd hosts update ${host} --cluster ${TENANT}/${cluster1_name}
    
    # Wait for the update operation to complete
    sleep 15

    if [[ $(export_contains $cluster1_export $host) && $(export_contains $cluster1_export $fake_pwwn1) ]]; then
        echo "Host" ${host} "has been successfully moved to cluster" ${cluster1_name}
    else
        echo "Failed to move host" ${host} "to cluster" ${cluster1_name}  
        #VERIFY_FAIL_COUNT=`expr $VERIFY_FAIL_COUNT + 1`
        #TRIP_VERIFY_FAIL_COUNT=`expr $TRIP_VERIFY_FAIL_COUNT + 1`
    fi    

    runcmd export_group delete $PROJECT/${exclusive_export}
    runcmd export_group delete $PROJECT/${cluster1_export}
    
    #verify the exports have been removed
    verify_export ${exclusive_export} ${host} gone
    verify_export ${cluster1_export} ${cluster1_name} gone

    runcmd hosts delete ${host}
    runcmd run transportzone remove ${FC_ZONE_A} ${fake_pwwn1}
   
    snap_db 2 ${cfs}  

    # Validate that nothing was left behind
    validate_db 1 2 ${cfs}          
}

# Searches an ExportGroup for a given value. Returns 0 if the value is found,
# 1 if the value is not found.
# usage: if [ $(export_contains $export_group_name $search_value) ]; then
export_contains() {
    export_name=$1
    search_value=$2
   
    export_group show $PROJECT/${export_name} | grep ${search_value}
}

# Test Cluster Remove Host
#
# 1. Create 2 hosts and initiators for those hosts.
# 2. Create a cluster and update both hosts to put them into the cluster.
# 3. Export a volume to this cluster (cluster export group is created)
# 4. Update a host to remove it from this cluster.
# 5. This host should no longer be in the cluster's export group.
# 6. Clean up
test_cluster_remove_host() {
    echot "Test cluster_remove_host Begins"

    common_failure_injections="failure_001_host_export_ComputeSystemControllerImpl.updateExportGroup_before_update \
                                failure_002_host_export_ComputeSystemControllerImpl.updateExportGroup_after_update \
                                failure_003_host_export_ComputeSystemControllerImpl.deleteExportGroup_before_delete \ 
                                failure_004_host_export_ComputeSystemControllerImpl.deleteExportGroup_after_delete \
                                failure_005_host_export_ComputeSystemControllerImpl.verifyDatastore_after_verify \
                                failure_006_host_export_ComputeSystemControllerImpl.unmountAndDetach_after_unmount \
                                failure_007_host_export_ComputeSystemControllerImpl.unmountAndDetach_after_detach \
                                failure_008_host_export_ComputeSystemControllerImpl.updateHostAndInitiatorClusterReferences_after_updateHostAndInitiator \
                                failure_009_host_export_ComputeSystemControllerImpl.updateHostAndInitiatorClusterReferences_after_updateHostVcenter"

    #failure_injections="${common_failure_injections}"

    # Placeholder when a specific failure case is being worked...
    failure_injections="failure_001_host_export_ComputeSystemControllerImpl.updateExportGroup_before_update"

    # Create volumes
    random_number=${RANDOM}        
    volume1=${VOLNAME}-1-${random_number}
    volume2=${VOLNAME}-2-${random_number}    
    runcmd volume create ${volume1} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB
    runcmd volume create ${volume2} ${PROJECT} ${NH} ${VPOOL_BASE} 1GB
        
    for failure in ${failure_injections}
    do
        secho "Running cluster_remove_host with failure scenario: ${failure}..."
        
        column_family="Volume ExportGroup ExportMask"
        random_number=${RANDOM}
        mkdir -p results/${random_number}
        host1=fakehost1-${random_number}
        host2=fakehost2-${random_number}
        cluster1=fakecluster1-${random_number}
        exportgroup1=exportgroup1-${random_number}
        
        # Snap DB
        snap_db 1 ${column_family}
            
        # Create new random WWNs for nodes and initiators
        node1=`randwwn 20 C1`
        node2=`randwwn 20 C2`
        node3=`randwwn 20 C3`
        node4=`randwwn 20 C4`
        init1=`randwwn 10 C1`
        init2=`randwwn 10 C2`
        init3=`randwwn 10 C3`
        init4=`randwwn 10 C4`
        
        # Add initator WWNs to the network
        run transportzone add $NH/${FC_ZONE_A} ${init1}
        run transportzone add $NH/${FC_ZONE_A} ${init2}
        run transportzone add $NH/${FC_ZONE_A} ${init3}
        run transportzone add $NH/${FC_ZONE_A} ${init4}
        
         # Create fake cluster
        runcmd cluster create ${cluster1} $TENANT
            
        # Create fake hosts and add them to cluster
        runcmd hosts create $host1 $TENANT Other ${host1}.lss.emc.com --port 1 --cluster ${TENANT}/${cluster1}
        runcmd hosts create $host2 $TENANT Other ${host2}.lss.emc.com --port 1 --cluster ${TENANT}/${cluster1}       
        
        # Create new initators and add to fakehost
        runcmd initiator create $host1 FC ${init1} --node ${node1}
        runcmd initiator create $host1 FC ${init2} --node ${node2}
        runcmd initiator create $host2 FC ${init3} --node ${node3}
        runcmd initiator create $host2 FC ${init4} --node ${node4}
    
        # Zzzzzz
        sleep 2
        
        # Export the volume to the fake cluster    
        runcmd export_group create $PROJECT ${exportgroup1} $NH --type Cluster --volspec ${PROJECT}/${volume1} --clusters ${TENANT}/${cluster1}       
        
        # Double check the export groups to ensure the hosts and initiators are present
        foundinit1=`export_group show $PROJECT/${exportgroup1} | grep ${init1}`
        foundinit2=`export_group show $PROJECT/${exportgroup1} | grep ${init2}`
        foundinit3=`export_group show $PROJECT/${exportgroup1} | grep ${init3}`
        foundinit4=`export_group show $PROJECT/${exportgroup1} | grep ${init4}`
        foundhost1=`export_group show $PROJECT/${exportgroup1} | grep ${host1}`
        foundhost2=`export_group show $PROJECT/${exportgroup1} | grep ${host2}`    
        
        if [[ "${foundinit1}" = ""  || "${foundinit2}" = "" || "${foundinit3}" = "" || "${foundinit4}" = "" || "${foundhost1}" = "" || "${foundhost2}" = "" ]]; then
            # Fail, hosts and initiators should have been added to the export group
            echo "+++ FAIL - Some hosts and host initiators were not found on the export group...fail."
            exit 1
        else
            echo "+++ SUCCESS - All hosts and host initiators present on export group"   
        fi
                               
        # Turn on failure at a specific point
        set_artificial_failure ${failure}
        
        # Try and remove host from cluster, this should fail
        runcmd hosts update $host1 --cluster null        
        
        # Rerun the command with no failures
        set_artificial_failure none 
        runcmd hosts update $host1 --cluster null
                                        
        # Zzzzzz
        sleep 5
        
        # Ensure that initiator 1 and 2 and host1 have been removed
        foundinit1=`export_group show $PROJECT/${exportgroup1} | grep ${init1}`
        foundinit2=`export_group show $PROJECT/${exportgroup1} | grep ${init2}`
        foundhost1=`export_group show $PROJECT/${exportgroup1} | grep ${host1}`
        
        if [[ "${foundinit1}" != "" || "${foundinit2}" != "" || "${foundhost1}" != "" ]]; then
            # Fail, initiators 1 and 2 and host1 should be removed and initiators 3 and 4 should still be present
            echo "+++ FAIL - Expected host and host initiators were not removed from the export group."
            exit 1
        else
            echo "+++ SUCCESS - Expected host and host initiators removed from export group" 
        fi               
        
        # Snap DB
        snap_db 2 ${column_family}
        
        # Validate DB
        validate_db 1 2 ${column_family}
    done
}
