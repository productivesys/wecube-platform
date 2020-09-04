SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS plugin_packages;
DROP TABLE IF EXISTS plugin_package_dependencies;
DROP TABLE IF EXISTS plugin_package_menus;
DROP TABLE IF EXISTS plugin_package_data_model;
DROP TABLE IF EXISTS plugin_package_entities;
DROP TABLE IF EXISTS plugin_package_attributes;
DROP TABLE IF EXISTS system_variables;
DROP TABLE IF EXISTS plugin_package_authorities;
DROP TABLE IF EXISTS plugin_package_runtime_resources_docker;
DROP TABLE IF EXISTS plugin_package_runtime_resources_mysql;
DROP TABLE IF EXISTS plugin_package_runtime_resources_s3;
DROP TABLE IF EXISTS plugin_configs;
DROP TABLE IF EXISTS plugin_config_interfaces;
DROP TABLE IF EXISTS plugin_config_interface_parameters;
DROP TABLE IF EXISTS menu_items;
DROP TABLE IF EXISTS plugin_package_resource_files;
DROP TABLE IF EXISTS resource_server;
DROP TABLE IF EXISTS resource_item;
DROP TABLE IF EXISTS plugin_instances;
DROP TABLE IF EXISTS plugin_mysql_instances;
DROP TABLE IF EXISTS role_menu;
DROP TABLE IF EXISTS core_ru_proc_role_binding;
DROP TABLE IF EXISTS batch_execution_jobs;
DROP TABLE IF EXISTS execution_jobs;
DROP TABLE IF EXISTS execution_job_parameters;
DROP TABLE IF EXISTS favorites;
DROP TABLE IF EXISTS favorites_role;
DROP TABLE IF EXISTS plugin_artifact_pull_req;
#workflow
DROP TABLE IF EXISTS act_ge_bytearray;
DROP TABLE IF EXISTS act_ge_property;
DROP TABLE IF EXISTS act_ge_schema_log;
DROP TABLE IF EXISTS act_hi_actinst;
DROP TABLE IF EXISTS act_hi_attachment;
DROP TABLE IF EXISTS act_hi_batch;
DROP TABLE IF EXISTS act_hi_caseactinst;
DROP TABLE IF EXISTS act_hi_caseinst;
DROP TABLE IF EXISTS act_hi_comment;
DROP TABLE IF EXISTS act_hi_decinst;
DROP TABLE IF EXISTS act_hi_dec_in;
DROP TABLE IF EXISTS act_hi_dec_out;
DROP TABLE IF EXISTS act_hi_detail;
DROP TABLE IF EXISTS act_hi_ext_task_log;
DROP TABLE IF EXISTS act_hi_identitylink;
DROP TABLE IF EXISTS act_hi_incident;
DROP TABLE IF EXISTS act_hi_job_log;
DROP TABLE IF EXISTS act_hi_op_log;
DROP TABLE IF EXISTS act_hi_procinst;
DROP TABLE IF EXISTS act_hi_taskinst;
DROP TABLE IF EXISTS act_hi_varinst;
DROP TABLE IF EXISTS act_id_group;
DROP TABLE IF EXISTS act_id_info;
DROP TABLE IF EXISTS act_id_membership;
DROP TABLE IF EXISTS act_id_tenant;
DROP TABLE IF EXISTS act_id_tenant_member;
DROP TABLE IF EXISTS act_id_user;
DROP TABLE IF EXISTS act_re_case_def;
DROP TABLE IF EXISTS act_re_decision_def;
DROP TABLE IF EXISTS act_re_decision_req_def;
DROP TABLE IF EXISTS act_re_deployment;
DROP TABLE IF EXISTS act_re_procdef;
DROP TABLE IF EXISTS act_ru_authorization;
DROP TABLE IF EXISTS act_ru_batch;
DROP TABLE IF EXISTS act_ru_case_execution;
DROP TABLE IF EXISTS act_ru_case_sentry_part;
DROP TABLE IF EXISTS act_ru_event_subscr;
DROP TABLE IF EXISTS act_ru_execution;
DROP TABLE IF EXISTS act_ru_ext_task;
DROP TABLE IF EXISTS act_ru_filter;
DROP TABLE IF EXISTS act_ru_identitylink;
DROP TABLE IF EXISTS act_ru_incident;
DROP TABLE IF EXISTS act_ru_job;
DROP TABLE IF EXISTS act_ru_jobdef;
DROP TABLE IF EXISTS act_ru_meter_log;
DROP TABLE IF EXISTS act_ru_procinst_status;
DROP TABLE IF EXISTS act_ru_srvnode_status;
DROP TABLE IF EXISTS act_ru_task;
DROP TABLE IF EXISTS act_ru_variable;
DROP TABLE IF EXISTS core_operation_event;
DROP TABLE IF EXISTS core_re_proc_def_info;
DROP TABLE IF EXISTS core_re_task_node_def_info;
DROP TABLE IF EXISTS core_re_task_node_param;
DROP TABLE IF EXISTS core_ru_graph_node;
DROP TABLE IF EXISTS core_ru_proc_exec_binding;
DROP TABLE IF EXISTS core_ru_proc_exec_binding_tmp;
DROP TABLE IF EXISTS core_ru_proc_inst_info;
DROP TABLE IF EXISTS core_ru_proc_role_binding;
DROP TABLE IF EXISTS core_ru_task_node_exec_param;
DROP TABLE IF EXISTS core_ru_task_node_exec_req;
DROP TABLE IF EXISTS core_ru_task_node_inst_info;
DROP TABLE IF EXISTS plugin_config_roles;
SET FOREIGN_KEY_CHECKS = 1;