#!/usr/bin/python

# Copyright (c) 2016-17 Dell EMC Technologies
# All Rights Reserved
#
# This software contains the intellectual property of Dell EMC Technologies
# or is licensed to Dell EMC Technologies from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of Dell EMC Technologies.

import common
import tag
import json
import socket
import commands
from common import SOSError
from threading import Timer
import schedulepolicy
import virtualpool
import host
import pprint
from ppretty import ppretty 


class FilePolicy(object):

    '''
    The class definition for operations on 'Filepolicy'.
    '''
    # Commonly used URIs for the 'Filepolicy' module
    URI_FILE_POLICIES='/file/file-policies'
    URI_FILE_POLICY_SHOW= URI_FILE_POLICIES + '/{0}'
    URI_FILE_POLICY_DELETE= URI_FILE_POLICIES+'/{0}'
    URI_FILE_POLICY_UPDATE= URI_FILE_POLICIES+'/{0}'
    URI_FILE_POLICY_ASSIGN= URI_FILE_POLICIES+'/{0}/assign-policy'
    URI_FILE_POLICY_UNASSIGN= URI_FILE_POLICIES+'/{0}/unassign-policy'



    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port


    #Filepolicy Query
    def filepolicy_query(self, name):
        
        if (common.is_uri(name)):
            return name

        policies = self.list_file_polices()

        for policy in policies:
            if(policy['name'] == name):
                filepolicy = self.filepolicy_show_by_uri(policy['id'], False)
                return filepolicy

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "filepolicy " + name + ": not found")
    
    def list_file_polices(self):
        '''
        Makes REST API call and retrieves filepolicy 
        Parameters:
            
        Returns:
            List of FilePolicies UUIDs in JSON response payload
        '''
        uri = FilePolicy.URI_FILE_POLICIES

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET", uri, None)
        o = common.json_decode(s)
        returnlst = []

        if( "file_policy" in o):
            return common.get_list(o, 'file_policy')
        return returnlst        


    def filepolicy_show(self, label, xml=False):

        filepolicy = self.filepolicy_query(label)
        return filepolicy


    def filepolicy_show_by_uri(self, uri, xml=False):

        if(xml):
            (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                    FilePolicy.URI_FILE_POLICY_SHOW.format(uri),
                    None, None, xml)
            return s

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                    "GET", FilePolicy.URI_FILE_POLICY_SHOW.format(uri), None)

        
        o = common.json_decode(s)
        inactive = common.get_node_value(o, 'inactive')

        if(inactive == True):
            return None
        return o

    def filepolicy_delete_by_uri(self, uri):
        '''
        Deletes a filepolicy based on project UUID
        Parameters:
            uri: UUID of filepolicy
        '''
        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                    "DELETE", FilePolicy.URI_FILE_POLICY_DELETE.format(uri), None)
        return

    def filepolicy_delete(self, name):
        '''
        Deletes a filepolicy based on policy name
        Parameters:
            name: name of filepolicy
        '''
        filepolicy_uri = self.filepolicy_query(name)
        return self.filepolicy_delete_by_uri(filepolicy_uri)

    def filepolicy_create(self, name ,type, tenants_access, description, priority,
       policyschedulefrequency, policyschedulerepeat, policyscheduletime, policyscheduleweek,
       policyschedulemonth, replicationcopymode, replicationconfiguration, replicationtype,
       snapshotnamepattern, snapshotexpiretype, snapshotexpirevalue,applyat):
        '''
        Creates a filepolicy based on policy name
        parameters:
        type policy type 
        tenants_access tenants access flag,
        description policy description,
        priority priority of the policys,
        policyschedulefrequency sType of schedule policy e.g days, week or months,
        policyschedulerepeat policy run on every,
        policyscheduletime Time when policy run,
        policyscheduleweek day of week when policy run,
        policyschedulemonth day of month when policy run,
        replicationcopymode,
        replicationconfiguration,
        replicationtype,
        snapshotnamepattern,
        snapshotexpiretype,
        snapshotexpirevalue
        '''
        create_request={}
        policy_schedule={}
        snapshot_params={}
        replication_params={}
        snapshot_expire_params={}

        create_request["policy_type"]=type
        create_request["policy_name"]=name
        create_request["policy_description"]=description
        create_request["priority"]=priority
        create_request["is_access_to_tenants"]=tenants_access
        create_request["apply_at"]=applyat

        policy_schedule["schedule_frequency"]=policyschedulefrequency
        policy_schedule["schedule_repeat"]=policyschedulerepeat
        policy_schedule["schedule_time"]=policyscheduletime
        policy_schedule["schedule_day_of_week"]=policyscheduleweek
        policy_schedule["schedule_day_of_month"]=policyschedulemonth

        if(type =="file_replication"):
                  replication_params["replication_type"]=replicationtype
                  replication_params["replication_copy_mode"]=replicationcopymode
                  replication_params["replicate_configuration"]=replicationconfiguration
                  replication_params["policy_schedule"]=policy_schedule
        elif(type == "file_snapshot"):
                  snapshot_expire_params["expire_type"]=snapshotexpiretype
                  snapshot_expire_params["expire_value"]=snapshotexpirevalue
                  snapshot_params["snapshot_name_pattern"]=snapshotnamepattern
                  snapshot_params["snapshot_expire_params"]=snapshot_expire_params
                  snapshot_params["policy_schedule"]=policy_schedule

        create_request["replication_params"]=replication_params
        create_request["snapshot_params"]=snapshot_params

        try:
            body = json.dumps(create_request)
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                        "POST",FilePolicy.URI_FILE_POLICIES,body)
            if(not s):
                return None
            o = common.json_decode(s)
            if(sync):
                return self.check_for_sync(o, sync,synctimeout)
            else:
                return o
        except SOSError as e:
           errorMessage = str(e)
        common.format_err_msg_and_raise("create", "filepolicy",
            errorMessage, e.err_code)


    def filepolicy_update(self, name , tenants_access, description, priority,
       policyschedulefrequency, policyschedulerepeat, policyscheduletime, policyscheduleweek,
       policyschedulemonth, replicationcopymode, replicationconfiguration, replicationtype,
       snapshotnamepattern, snapshotexpiretype, snapshotexpirevalue,applyat):
        '''
        Creates a filepolicy based on policy name
        parameters:
        tenants_access tenants access flag,
        description policy description,
        priority priority of the policys,
        policyschedulefrequency sType of schedule policy e.g days, week or months,
        policyschedulerepeat policy run on every,
        policyscheduletime Time when policy run,
        policyscheduleweek day of week when policy run,
        policyschedulemonth day of month when policy run,
        replicationcopymode,
        replicationconfiguration,
        replicationtype,
        snapshotnamepattern,
        snapshotexpiretype,
        snapshotexpirevalue
        '''
        filepolicy_uri =self.filepolicy_query(name)

        update_request={}
        update_request["policy_name"]=name
        update_request["policy_description"]=description
        update_request["priority"]=priority
        update_request["is_access_to_tenants"]=tenants_access
        update_request["apply_at"]=applyat

        policy_schedule={}
        snapshot_params={}
        replication_params={}
        snapshot_expire_params={}

        policy_schedule["schedule_frequency"]=policyschedulefrequency
        policy_schedule["schedule_repeat"]=policyschedulerepeat
        policy_schedule["schedule_time"]=policyscheduletime
        policy_schedule["schedule_day_of_week"]=policyscheduleweek
        policy_schedule["schedule_day_of_month"]=policyschedulemonth

        if(type =="file_replication"):
                  replication_params["replication_type"]=replicationtype
                  replication_params["replication_copy_mode"]=replicationcopymode
                  replication_params["replicate_configuration"]=replicationconfiguration
                  replication_params["policy_schedule"]=policy_schedule
        elif(type == "file_snapshot"):
                  snapshot_expire_params["expire_type"]=snapshot_expire_type
                  snapshot_expire_value["expire_value"]=snapshot_expire_value
                  snapshot_params["snapshot_name_pattern"]=snapshotnamepattern
                  snapshot_params["snapshot_expire_params"]=snapshot_expire_params
                  snapshot_params["policy_schedule"]=policy_schedule

        update_request["replication_params"]=replication_params
        update_request["snapshot_params"]=snapshot_params

        try:
            body = json.dumps(create_request)
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                        "POST",FilePolicy.URI_FILE_POLICY_UPDATE.format(filepolicy_uri),body)
            if(not s):
                return None
            o = common.json_decode(s)
            if(sync):
                return self.check_for_sync(o, sync,synctimeout)
            else:
                return o
        except SOSError as e:
           errorMessage = str(e)
        if(common.is_uri(filepolicy_uri)):
            errorMessage = str(e).replace(filepolicy_uri, name)
        common.format_err_msg_and_raise("update", "filepolicy",
            errorMessage, e.err_code)


    def filepolicy_assign(self, name,apply_on_target_site,assign_to_vpools,project_assign_vpool, assign_to_projects, filesystem_assign_vpool ):

        filepolicy_uri =self.filepolicy_query(name)
        assign_request={}

        vpool_assign_param={}
        project_assign_param={}
        filesystem_assign_param={}

        assign_request_vpools={}
        assign_request_projects={}


        vpool_names=assign_to_vpools.split(",")
        
        for name in vpool_names:
            uri = VirtualPool.vpool_query(name,'file')
            assign_request_vpools.append(uri)

        vpool_assign_param["assign_to_vpools"]=assign_request_vpools

        project_names=assign_to_projects.split(",")
        for name in project_names:
            uri = Project.project_query(name)
            assign_request_projects.append(uri)

        project_assign_param["vpool"]=project_assign_vpool
        project_assign_param["assign_to_projects"]=assign_request_projects

        filesystem_assign_param["vpool"]=filesystem_assign_vpool

        assign_request["apply_on_target_site"]=apply_on_target_site
        assign_request["vpool_assign_param"]=vpool_assign_param
        assign_request["filesystem_assign_param"]=filesystem_assign_param

        try:
            body = json.dumps(assign_request)
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                        "POST",FilePolicy.URI_FILE_POLICY_ASSIGN.format(filepolicy_uri),body)
            if(not s):
                return None
            o = common.json_decode(s)
            if(sync):
                return self.check_for_sync(o, sync,synctimeout)
            else:
                return o
        except SOSError as e:
           errorMessage = str(e)
        if(common.is_uri(filepolicy_uri)):
            errorMessage = str(e).replace(filepolicy_uri, name)
        common.format_err_msg_and_raise("assign", "filepolicy",
            errorMessage, e.err_code)


    def filepolicy_unassign(self, name,unassign_resource_type,unassign_from_vpools,unassign_from_vpool,unassign_from_projects):

        filepolicy_uri =self.filepolicy_query(name)
        unassign_request={}

        
        projects_uris={}
        vpools_uris={}
        unassign_uris={}

        if(unassign_resource_type=="vpool"):
            vpools=unassign_from_vpools.split(",")
            for vpool in vpools:
                uri = VirtualPool.vpool_query(vpool)
                vpools_uris.append(uri)
            unassign_uris.append(vpools_uris)
        elif(unassign_resource_type=="project"):
            projects=unassign_from_projects.split(",")
            for project in projects:
                uri = Project.project_query(project)
                projects_uris.append(uri)
            unassign_uris.append(projects_uris)
        elif(unassign_resource_type=="filesystem"):
            vpool_uri=VirtualPool.vpool_query(unassign_from_vpool)
            unassign_uris.append(vpool_uri)
        unassign_request["unassign_from"]=unassign_uris

        try:
            body = json.dumps(unassign_request)
            (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                        "POST",FilePolicy.URI_FILE_POLICY_UNASSIGN.format(filepolicy_uri),body)
            if(not s):
                return None
            o = common.json_decode(s)
            if(sync):
                return self.check_for_sync(o, sync,synctimeout)
            else:
                return o
        except SOSError as e:
           errorMessage = str(e)
        if(common.is_uri(filepolicy_uri)):
            errorMessage = str(e).replace(filepolicy_uri, name)
        common.format_err_msg_and_raise("unassign", "filepolicy",
            errorMessage, e.err_code)


def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Filepolicy List CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List filepolicies')
    mandatory_args = list_parser.add_argument_group('mandatory arguments')

    list_parser.set_defaults(func=filepolicy_list)


def filepolicy_list(args):
    obj = FilePolicy(args.ip, args.port)
    result = obj.list_file_polices()

    from common import TableGenerator
    TableGenerator(result, ['name']).printTable()

def show_parser(subcommand_parsers, common_parser):
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Filepolicy Show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='show filepolicies')

    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of Filepolicy',
                                required=True)

    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')

    show_parser.set_defaults(func=filepolicy_show)

def filepolicy_show(args):
    obj = FilePolicy(args.ip, args.port)
    try:
        res = obj.filepolicy_show(args.name, args.xml)
        if(res):
            if (args.xml == True):
                return common.format_xml(res)
            return common.format_json_object(res)
    except SOSError as e:
        raise e



def create_parser(subcommand_parsers, common_parser):
    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR FilePolicy Create CLI usage.',
        parents=[common_parser], conflict_handler='resolve',
        help='Create a filepolicy')
    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<policy_name>',
                                dest='name',
                                help='Name of the policy',
                                required=True)
    mandatory_args.add_argument('-type', '-t',
                                metavar='<policy_type>',
                                dest='policy_type',
                                help='Type of the policy, valid values are : file_snapshot, file_replication, file_quota',
                                choices=["file_snapshot", "file_replication", "file_quota"],
                                required=True)
    create_parser.add_argument('-description', '-dc',
                               metavar='<policy_description>',
                               dest='description',
                               help='Policy Description')
    create_parser.add_argument('-priority', '-pr',
                                metavar='<priority>', dest='priority',
                                help='Priority of the policy')

    create_parser.add_argument('-policyschedulefrequency', '-plscfr',
                                metavar='<policy_schedule_frequency>', dest='policy_sched_frequnecy',
                                help='Type of schedule policy e.g days, weeks or months')
    create_parser.add_argument('-policyschedulerepeat', '-plscrp',
                                metavar='<policy_schedule_repeat>', dest='policy_schedule_repeat',
                                help='Policy run on every')    
    create_parser.add_argument('-policyscheduletime', '-plsctm',
                                metavar='<policy_schedule_time>', dest='policy_schedule_time',
                                help='Time when policy run')    
    create_parser.add_argument('-policyscheduleweek', '-plscwk',
                                metavar='<policy_schedule_week>', dest='policy_schedule_week',
                                help='Day of week when policy run')    
    create_parser.add_argument('-policyschedulemonth', '-plscmn',
                                metavar='<policy_schedule_month>', dest='policy_schedule_month',
                                help='Day of month when policy run')    

    create_parser.add_argument('-replicationtype', '-reptype',
                                metavar='<replication_type>', dest='replication_type',
                                help='File Replication type Valid values are: LOCAL, REMOTE',
                                choices=["LOCAL", "REMOTE"])    
    create_parser.add_argument('-replicationcopymode', '-repcpmode',
                                metavar='<replication_copy_mode>', dest='replication_copy_mode',
                                help='File Replication copy type Valid values are: SYNC, ASYNC',
                                choices=["LOCAL", "REMOTE"])    
    create_parser.add_argument('-replicationconfiguration', '-repconf',
                                metavar='<replicate_configuration>', dest='replicate_configuration',
                                help='Whether to replicate File System configurations i.e CIFS shares, NFS Exports at the time of failover/failback. Default value is False',
                                choices=["True", "False","true","false"])    


    create_parser.add_argument('-snapshotnamepattern', '-snpnmptrn',
                                metavar='<snapshot_name_pattern>', dest='snapshot_name_pattern',
                                help='Snapshot pattern ')    
    create_parser.add_argument('-snapshotexpiretype', '-snpexptp',
                                metavar='<snapshot_expire_type>', dest='snapshot_expire_type',
                                help='Snapshot expire type e.g hours, days, weeks, months or never',
                                choices=["hours", "days", "weeks", "months","never"])     
    create_parser.add_argument('-snapshotexpirevalue', '-snpexpvl',
                                metavar='<snapshot_expire_value>', dest='snapshot_expire_value',
                                help='Snapshot expire after this value')  

    mandatory_args.add_argument('-tenants_access', '-ta',
                                metavar='<is_access_to_tenants>', dest='tenants_access',
                                help='Tenants access',
                                required=False)
    mandatory_args.add_argument('-apply_at', '-aplat',
                                metavar='<apply_at>', dest='apply_at',
                                help='Level at which policy has to applied. Valid values are vpool, project, file_system',
                                required=False)


    create_parser.set_defaults(func=filepolicy_create)

def filepolicy_create(args):
    obj = FilePolicy(args.ip, args.port)
    SYNC="SYNC"
    ASYNC="ASYNC"
    if(args)
    try:
        obj.filepolicy_create(args.name, args.policy_type, args.tenants_access,
        args.description,  args.priority,  args.policy_sched_frequnecy, args.policy_schedule_repeat,
        args.policy_schedule_time, args.policy_schedule_week, args.policy_schedule_month, args.replication_copy_mode,
        args.replicate_configuration, args.replication_type,  args.snapshot_name_pattern,
        args.snapshot_expire_type,  args.snapshot_expire_value, args.apply_at)

    except SOSError as e:
        common.format_err_msg_and_raise("create", "filepolicy",
                                        e.err_text, e.err_code)


def update_parser(subcommand_parsers, common_parser):
    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR FilePolicy Update CLI usage.',
        parents=[common_parser], conflict_handler='resolve',
        help='Update a filepolicy')
    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<policy_name>',
                                dest='name',
                                help='Name of the policy',
                                required=True)
    mandatory_args.add_argument('-type', '-t',
                                metavar='<policy_type>',
                                dest='policy_type',
                                help='Type of the policy, valid values are : file_snapshot, file_replication, file_quota',
                                required=True)
    update_parser.add_argument('-description', '-dc',
                               metavar='<policy_description>',
                               dest='description',
                               help='Policy Description')
    update_parser.add_argument('-priority', '-pr',
                                metavar='<priority>', dest='priority',
                                help='Priority of the policy')

    update_parser.add_argument('-policyschedulefrequency', '-plscfr',
                                metavar='<policy_schedule_frequency>', dest='policy_sched_frequnecy',
                                help='Type of schedule policy e.g days, weeks or months')
    update_parser.add_argument('-policyschedulerepeat', '-plscrp',
                                metavar='<policy_schedule_repeat>', dest='policy_sched_repeat',
                                help='Policy run on every')    
    update_parser.add_argument('-policyscheduletime', '-plsctm',
                                metavar='<policy_schedule_time>', dest='policy_sched_time',
                                help='Time when policy run')    
    update_parser.add_argument('-policyscheduleweek', '-plscwk',
                                metavar='<policy_schedule_week>', dest='policy_sched_week',
                                help='Day of week when policy run')    
    update_parser.add_argument('-policyschedulemonth', '-plscmn',
                                metavar='<policy_schedule_month>', dest='policy_sched_month',
                                help='Day of month when policy run')    

    update_parser.add_argument('-replicationtype', '-reptype',
                                metavar='<replication_type>', dest='replication_type',
                                help='File Replication type Valid values are: LOCAL, REMOTE')    
    update_parser.add_argument('-replicationcopymode', '-repcpmode',
                                metavar='<replication_copy_mode>', dest='replication_copy_mode',
                                help='File Replication copy type Valid values are: SYNC, ASYNC')    
    update_parser.add_argument('-replicationconfiguration', '-repconf',
                                metavar='<replicate_configuration>', dest='replicate_configuration',
                                help='Whether to replicate File System configurations i.e CIFS shares, NFS Exports at the time of failover/failback. Default value is False')    


    update_parser.add_argument('-snapshotnamepattern', '-snpnmptrn',
                                metavar='<snapshot_name_pattern>', dest='snapshot_name_pattern',
                                help='Snapshot pattern ')    
    update_parser.add_argument('-snapshotexpiretype', '-snpexptp',
                                metavar='<snapshot_expire_type>', dest='snapshot_expire_type',
                                help='Snapshot expire type e.g hours, days, weeks, months or never')     
    update_parser.add_argument('-snapshotexpirevalue', '-snpexpvl',
                                metavar='<snapshot_expire_value>', dest='snapshot_expire_value',
                                help='Snapshot expire after this value')  

    mandatory_args.add_argument('-tenants_access', '-ta',
                                metavar='<is_access_to_tenants>', dest='tenants_access',
                                help='Tenants access',
                                required=False)

    update_parser.set_defaults(func=filepolicy_update)


def filepolicy_update(subcommand_parsers, common_parser):
    obj = FilePolicy(args.ip, args.port)
    try:
        obj.filepolicy_update(args.name, args.tenants_access, args.description, 
         args.priority, args.policyschedulefrequency, args.policyschedulerepeat,
         args.policyscheduletime, args.policyscheduleweek, args.policyschedulemonth, 
         args.replicationcopymode, args.replicationconfiguration, args.replicationtype, 
         args.snapshotnamepattern, args.snapshotexpiretype, args.snapshotexpirevalue)

    except SOSError as e:
        common.format_err_msg_and_raise("update", "filepolicy",
                                        e.err_text, e.err_code)


# FilePolicy Delete routines

def delete_parser(subcommand_parsers, common_parser):
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Filesystem Delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete a filepolicy')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<policy-name>',
                                dest='name',
                                help='Name of FilePolicy',
                                required=True)
    delete_parser.set_defaults(func=filepolicy_delete)


def filepolicy_delete(subcommand_parsers, common_parser):
    obj = FilePolicy(args.ip, args.port)
    try:
        if(not args.name):
            args.name = ""
        obj.filepolicy_delete(args.name)

    except SOSError as e:
        if (e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "FilePolicy delete failed: " + e.err_text)
        else:
            raise e


# FilePolicy assign

def assign_parser(subcommand_parsers, common_parser):
    update_parser = subcommand_parsers.add_parser(
        'assign',
        description='ViPR FilePolicy assign CLI usage.',
        parents=[common_parser], conflict_handler='resolve',
        help='Assign FilePolicy to vpool, project, file system')
    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<policy_name>',
                                dest='name',
                                help='Name of the policy',
                                required=True)
    mandatory_args.add_argument('-applyontargetsite', '-aptrgtsite',
                                metavar='<apply_on_target_site>',
                                dest='apply_on_target_site',
                                help='Appply on target site true/false',
                                required=True)
    update_parser.add_argument('-assigntovpools', '-asignvpls',
                               metavar='<assign_to_vpools>',
                               dest='assign_to_vpools',
                               help='assign to vpools')
    update_parser.add_argument('-assigntoprojects', '-asignprjs',
                                metavar='<assign_to_projects>', dest='assign_to_projects',
                                help='Assign to projects')
    update_parser.add_argument('-assigntoprojectsvpool', '-asignprjvpool',
                                metavar='<project_assign_vpool>', dest='project_assign_vpool',
                                help='vpool of to-be asssigned projects ')

    update_parser.add_argument('-filesystemvpool', '-fsvpool',
                                metavar='<filesystem_assign_vpool>', dest='filesystem_assign_vpool',
                                help='vpool of filesystems to be assigned to')
    
    update_parser.set_defaults(func=filepolicy_assign)


def filepolicy_assign(args):
    obj = FilePolicy(args.ip, args.port)
    try:
        if(not args.name):
            args.name = ""
        obj.filepolicy_assign(args.name,args.apply_on_target_site,args.assign_to_vpools,args.project_assign_vpool, args.assign_to_projects, args.filesystem_assign_vpool)
    except SOSError as e:
        if (e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "FilePolicy assign failed: " + e.err_text)
        else:
            raise e


# FilePolicy unassign

def unassign_parser(subcommand_parsers, common_parser):
    update_parser = subcommand_parsers.add_parser(
        'unassign',
        description='ViPR FilePolicy unassign CLI usage.',
        parents=[common_parser], conflict_handler='resolve',
        help='Unassign FilePolicy from vpool, project, file system')
    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<policy_name>',
                                dest='name',
                                help='Name of the policy',
                                required=True)
    mandatory_args.add_argument('-unassignresourcetype', '-unasngrestp',
                                metavar='<unassign_resource_type>',
                                dest='unassign_resource_type',
                                help='Resource type to be unassigned from. type values : vpools or projects or filesystem',
                                required=True)
    update_parser.add_argument('-unassignvpools', '-unasignvpls',
                               metavar='<unassign_from_vpools>',
                               dest='unassign_from_vpools',
                               help='unassign from vpools')
    update_parser.add_argument('-unassignvpool', '-unasignvpl',
                                metavar='<unassign_from_vpool>', dest='unassign_from_vpool',
                                help='unassign from project\'s or filesystem\'s vpool')
    update_parser.add_argument('-unassignfromprojects', '-unasignprjs',
                                metavar='<unassign_from_projects>', dest='unassign_from_projects',
                                help='unassign from projects')
    
    update_parser.set_defaults(func=filepolicy_unassign)


def filepolicy_unassign(args):
    obj = FilePolicy(args.ip, args.port)
    try:
        if(not args.name):
            args.name = ""
        obj.filepolicy_unassign(args.name,args.unassign_resource_type,args.unassign_from_vpools,args.unassign_from_vpool,unassign_from_projects)
    except SOSError as e:
        if (e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "FilePolicy assign failed: " + e.err_text)
        else:
            raise e

#
# FilePolicy Main parser routine
#
def filepolicy_parser(parent_subparser, common_parser):
    # main project parser

    parser = parent_subparser.add_parser(
        'filepolicy',
        description='ViPR filepolicy CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on filepolicy')
    subcommand_parsers = parser.add_subparsers(help='Use one of subcommands')    

     # list command parser
    list_parser(subcommand_parsers,common_parser)

    #show command parser
    show_parser(subcommand_parsers, common_parser)

    # create command parser
    create_parser(subcommand_parsers, common_parser)

    # update command parser
    update_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # policy assign command parser
    assign_parser(subcommand_parsers, common_parser)

    # policy unassign command parser
    unassign_parser(subcommand_parsers, common_parser)