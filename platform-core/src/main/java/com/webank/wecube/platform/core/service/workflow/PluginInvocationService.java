package com.webank.wecube.platform.core.service.workflow;

import static com.webank.wecube.platform.core.utils.Constants.ASYNC_SERVICE_SYMBOL;
import static com.webank.wecube.platform.core.utils.Constants.FIELD_REQUIRED;
import static com.webank.wecube.platform.core.utils.Constants.MAPPING_TYPE_CONSTANT;
import static com.webank.wecube.platform.core.utils.Constants.MAPPING_TYPE_CONTEXT;
import static com.webank.wecube.platform.core.utils.Constants.MAPPING_TYPE_ENTITY;
import static com.webank.wecube.platform.core.utils.Constants.MAPPING_TYPE_SYSTEM_VARIABLE;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.webank.wecube.platform.core.commons.WecubeCoreException;
import com.webank.wecube.platform.core.dto.plugin.ItsDangerConfirmResultDto;
import com.webank.wecube.platform.core.entity.plugin.PluginConfigInterfaceParameters;
import com.webank.wecube.platform.core.entity.plugin.PluginConfigInterfaces;
import com.webank.wecube.platform.core.entity.plugin.PluginConfigs;
import com.webank.wecube.platform.core.entity.plugin.PluginInstances;
import com.webank.wecube.platform.core.entity.plugin.PluginPackages;
import com.webank.wecube.platform.core.entity.plugin.SystemVariables;
import com.webank.wecube.platform.core.entity.workflow.ExtraTaskEntity;
import com.webank.wecube.platform.core.entity.workflow.ProcDefInfoEntity;
import com.webank.wecube.platform.core.entity.workflow.ProcExecBindingEntity;
import com.webank.wecube.platform.core.entity.workflow.ProcInstInfoEntity;
import com.webank.wecube.platform.core.entity.workflow.TaskNodeDefInfoEntity;
import com.webank.wecube.platform.core.entity.workflow.TaskNodeExecParamEntity;
import com.webank.wecube.platform.core.entity.workflow.TaskNodeExecRequestEntity;
import com.webank.wecube.platform.core.entity.workflow.TaskNodeInstInfoEntity;
import com.webank.wecube.platform.core.entity.workflow.TaskNodeParamEntity;
import com.webank.wecube.platform.core.model.workflow.InputParamAttr;
import com.webank.wecube.platform.core.model.workflow.InputParamObject;
import com.webank.wecube.platform.core.model.workflow.PluginInvocationCommand;
import com.webank.wecube.platform.core.model.workflow.PluginInvocationResult;
import com.webank.wecube.platform.core.model.workflow.WorkflowNotifyEvent;
import com.webank.wecube.platform.core.repository.workflow.ExtraTaskMapper;
import com.webank.wecube.platform.core.repository.workflow.ProcDefInfoMapper;
import com.webank.wecube.platform.core.repository.workflow.ProcExecBindingMapper;
import com.webank.wecube.platform.core.repository.workflow.ProcInstInfoMapper;
import com.webank.wecube.platform.core.repository.workflow.TaskNodeExecRequestMapper;
import com.webank.wecube.platform.core.repository.workflow.TaskNodeParamMapper;
import com.webank.wecube.platform.core.service.dme.EntityOperationRootCondition;
import com.webank.wecube.platform.core.service.dme.EntityTreeNodesOverview;
import com.webank.wecube.platform.core.service.dme.TreeNode;
import com.webank.wecube.platform.core.service.plugin.PluginInstanceMgmtService;
import com.webank.wecube.platform.core.service.plugin.SystemVariableService;
import com.webank.wecube.platform.core.service.workflow.PluginInvocationProcessor.PluginInterfaceInvocationContext;
import com.webank.wecube.platform.core.service.workflow.PluginInvocationProcessor.PluginInterfaceInvocationResult;
import com.webank.wecube.platform.core.service.workflow.PluginInvocationProcessor.PluginInvocationOperation;
import com.webank.wecube.platform.core.support.plugin.PluginInvocationRestClient;
import com.webank.wecube.platform.workflow.WorkflowConstants;
import com.webank.wecube.platform.workflow.commons.LocalIdGenerator;

/**
 * 
 * @author gavin
 *
 */
@Service
public class PluginInvocationService extends AbstractPluginInvocationService {

    @Autowired
    private PluginInvocationRestClient pluginInvocationRestClient;

    @Autowired
    private PluginInvocationProcessor pluginInvocationProcessor;

    @Autowired
    private ProcInstInfoMapper procInstInfoRepository;

    @Autowired
    private ProcDefInfoMapper procDefInfoMapper;

    @Autowired
    protected PluginInstanceMgmtService pluginInstanceMgmtService;

    @Autowired
    private ProcExecBindingMapper procExecBindingMapper;

    @Autowired
    private SystemVariableService systemVariableService;

    @Autowired
    private TaskNodeParamMapper taskNodeParamRepository;

    @Autowired
    private TaskNodeExecRequestMapper taskNodeExecRequestRepository;

    @Autowired
    private WorkflowProcInstEndEventNotifier workflowProcInstEndEventNotifier;

    @Autowired
    private RiskyCommandVerifier riskyCommandVerifier;

    @Autowired
    private ExtraTaskMapper extraTaskMapper;

    /**
     * 
     * @param cmd
     */
    public void handleProcessInstanceEndEvent(PluginInvocationCommand cmd) {
        if (log.isInfoEnabled()) {
            log.info("handle end event:{}", cmd);
        }

        Date currTime = new Date();

        ProcInstInfoEntity procInstEntity = null;
        int times = 0;

        while (times < 20) {
            procInstEntity = procInstInfoRepository.selectOneByProcInstKernelId(cmd.getProcInstId());
            if (procInstEntity != null) {
                break;
            }

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                log.info("exceptions while handling end event.", e.getMessage());
            }

            times++;
        }

        if (procInstEntity == null) {
            log.warn("Cannot find process instance entity currently for {}", cmd.getProcInstId());
            return;
        }

        String oldProcInstStatus = procInstEntity.getStatus();
        if (ProcInstInfoEntity.INTERNALLY_TERMINATED_STATUS.equalsIgnoreCase(procInstEntity.getStatus())) {
            return;
        }
        procInstEntity.setUpdatedTime(currTime);
        procInstEntity.setUpdatedBy(WorkflowConstants.DEFAULT_USER);
        procInstEntity.setStatus(ProcInstInfoEntity.COMPLETED_STATUS);
        procInstInfoRepository.updateByPrimaryKeySelective(procInstEntity);
        log.info("updated process instance {} from {} to {}", procInstEntity.getId(), oldProcInstStatus,
                ProcInstInfoEntity.COMPLETED_STATUS);

        List<TaskNodeInstInfoEntity> nodeInstEntities = taskNodeInstInfoRepository
                .selectAllByProcInstId(procInstEntity.getId());
        List<TaskNodeDefInfoEntity> nodeDefEntities = taskNodeDefInfoRepository
                .selectAllByProcDefId(procInstEntity.getProcDefId());

        for (TaskNodeInstInfoEntity n : nodeInstEntities) {
            if ("endEvent".equals(n.getNodeType()) && n.getNodeId().equals(cmd.getNodeId())) {
                TaskNodeDefInfoEntity currNodeDefInfo = findExactTaskNodeDefInfoEntityWithNodeId(nodeDefEntities,
                        n.getNodeId());
                refreshStatusOfPreviousNodes(nodeInstEntities, currNodeDefInfo);
                n.setUpdatedTime(currTime);
                n.setUpdatedBy(WorkflowConstants.DEFAULT_USER);
                n.setStatus(TaskNodeInstInfoEntity.COMPLETED_STATUS);

                taskNodeInstInfoRepository.updateByPrimaryKeySelective(n);

                log.debug("updated node {} to {}", n.getId(), TaskNodeInstInfoEntity.COMPLETED_STATUS);
            }
        }

        workflowProcInstEndEventNotifier.notify(WorkflowNotifyEvent.PROCESS_INSTANCE_END, cmd, procInstEntity);

    }

    /**
     * 
     * @param cmd
     */
    public void invokePluginInterface(PluginInvocationCommand cmd) {
        if (log.isInfoEnabled()) {
            log.info("invoke plugin interface with:{}", cmd);
        }

        ProcInstInfoEntity procInstEntity = null;
        TaskNodeInstInfoEntity taskNodeInstEntity = null;
        try {
            procInstEntity = retrieveProcInstInfoEntity(cmd);
            taskNodeInstEntity = retrieveTaskNodeInstInfoEntity(procInstEntity.getId(), cmd.getNodeId());
            doInvokePluginInterface(procInstEntity, taskNodeInstEntity, cmd);
        } catch (Exception e) {
            log.warn("errors while processing {} {}", cmd.getClass().getSimpleName(), cmd, e);
            pluginInvocationResultService.responsePluginInterfaceInvocation(
                    new PluginInvocationResult().parsePluginInvocationCommand(cmd).withResultCode(RESULT_CODE_ERR));

            updateTaskNodeInstInfoEntityFaulted(taskNodeInstEntity, e);
        }
    }

    /**
     * handle results of plugin interface invocation.
     * 
     * @param pluginInvocationResult
     * @param ctx
     */
    public void handlePluginInterfaceInvocationResult(PluginInterfaceInvocationResult pluginInvocationResult,
            PluginInterfaceInvocationContext ctx) {
        if (log.isDebugEnabled()) {
            log.debug("handle plugin interface invocation result");
        }

        if (!pluginInvocationResult.isSuccess() || pluginInvocationResult.hasErrors()) {
            handleErrorInvocationResult(pluginInvocationResult, ctx);

            return;
        }

        PluginConfigInterfaces pci = ctx.getPluginConfigInterface();
        if (ASYNC_SERVICE_SYMBOL.equalsIgnoreCase(pci.getIsAsyncProcessing())) {
            log.debug("such interface is asynchronous service : {} ", pci.getServiceName());
            return;
        }

        List<Object> resultData = pluginInvocationResult.getResultData();

        if (resultData == null) {
            handleNullResultData(pluginInvocationResult, ctx);
            return;
        }

        PluginInvocationResult result = new PluginInvocationResult()
                .parsePluginInvocationCommand(ctx.getPluginInvocationCommand());
        try {
            handleResultData(pluginInvocationResult, ctx, resultData);
            result.setResultCode(RESULT_CODE_OK);
            pluginInvocationResultService.responsePluginInterfaceInvocation(result);
            handlePluginInterfaceInvocationSuccess(pluginInvocationResult, ctx);

            return;
        } catch (Exception e) {
            log.warn("result data handling failed", e);
            result.setResultCode(RESULT_CODE_ERR);
            pluginInvocationResultService.responsePluginInterfaceInvocation(result);
            String errMsg = e.getMessage() == null ? "error" : trimWithMaxLength(e.getMessage());
            handlePluginInterfaceInvocationFailure(pluginInvocationResult, ctx, "5002",
                    "result data handling failed:" + errMsg);
        }

        return;
    }

    private void updateTaskNodeInstInfoEntityFaulted(TaskNodeInstInfoEntity taskNodeInstEntity, Exception e) {
        if (taskNodeInstEntity == null) {
            return;
        }
        log.debug("mark task node instance {} as {}", taskNodeInstEntity.getId(),
                TaskNodeInstInfoEntity.FAULTED_STATUS);
        TaskNodeInstInfoEntity toUpdateTaskNodeInstInfoEntity = taskNodeInstInfoRepository
                .selectByPrimaryKey(taskNodeInstEntity.getId());

        toUpdateTaskNodeInstInfoEntity.setStatus(TaskNodeInstInfoEntity.FAULTED_STATUS);
        toUpdateTaskNodeInstInfoEntity.setUpdatedTime(new Date());
        toUpdateTaskNodeInstInfoEntity.setUpdatedBy(WorkflowConstants.DEFAULT_USER);
        toUpdateTaskNodeInstInfoEntity.setErrMsg(trimWithMaxLength(e == null ? "errors" : e.getMessage()));

        taskNodeInstInfoRepository.updateByPrimaryKeySelective(toUpdateTaskNodeInstInfoEntity);
    }

    private boolean isDynamicBindTaskNode(TaskNodeDefInfoEntity taskNodeDef) {
        return TaskNodeDefInfoEntity.DYNAMIC_BIND_YES.equalsIgnoreCase(taskNodeDef.getDynamicBind());
    }

    private boolean isBoundTaskNodeInst(TaskNodeInstInfoEntity taskNodeInst) {
        return TaskNodeInstInfoEntity.BIND_STATUS_BOUND.equalsIgnoreCase(taskNodeInst.getBindStatus());
    }

    private boolean verifyIfExcludeModeExecBindings(ProcDefInfoEntity procDefInfo, ProcInstInfoEntity procInst,
            TaskNodeDefInfoEntity taskNodeDef, TaskNodeInstInfoEntity taskNodeInst, PluginInvocationCommand cmd,
            List<ProcExecBindingEntity> nodeObjectBindings) {
        if (nodeObjectBindings == null || nodeObjectBindings.isEmpty()) {
            return false;
        }

        String excludeMode = procDefInfo.getExcludeMode();
        if (ProcDefInfoEntity.EXCLUDE_MODE_YES.equalsIgnoreCase(excludeMode)) {
            return tryVerifyIfAnyRunningProcInstBound(procDefInfo, procInst, taskNodeDef, taskNodeInst, cmd,
                    nodeObjectBindings);
        } else {
            return tryVerifyIfAnyExclusiveRunningProcInstBound(procDefInfo, procInst, taskNodeDef, taskNodeInst, cmd,
                    nodeObjectBindings);
        }
    }

    private boolean tryVerifyIfAnyRunningProcInstBound(ProcDefInfoEntity procDefInfo, ProcInstInfoEntity procInst,
            TaskNodeDefInfoEntity taskNodeDef, TaskNodeInstInfoEntity taskNodeInst, PluginInvocationCommand cmd,
            List<ProcExecBindingEntity> nodeObjectBindings) {
        if (nodeObjectBindings == null || nodeObjectBindings.isEmpty()) {
            return false;
        }

        Set<Integer> boundProcInstIds = new HashSet<>();
        for (ProcExecBindingEntity nodeObjectBinding : nodeObjectBindings) {
            if (StringUtils.isBlank(nodeObjectBinding.getEntityDataId())) {
                continue;
            }
            int boundCount = procExecBindingMapper.countAllBoundRunningProcInstancesWithoutProcInst(
                    nodeObjectBinding.getEntityDataId(), procInst.getId());
            if (boundCount <= 0) {
                continue;
            }

            List<Integer> boundProcInstIdsOfSingleEntity = procExecBindingMapper
                    .selectAllBoundRunningProcInstancesWithoutProcInst(nodeObjectBinding.getEntityDataId(),
                            procInst.getId());
            if (boundProcInstIdsOfSingleEntity == null || boundProcInstIdsOfSingleEntity.isEmpty()) {
                continue;
            }

            boundProcInstIds.addAll(boundProcInstIdsOfSingleEntity);
        }

        if (boundProcInstIds.isEmpty()) {
            return false;
        }

        log.info("Current process {}:{}:{} is exclusive but still {} processes running.", procDefInfo.getId(),
                procDefInfo.getProcDefName(), procInst.getId(), boundProcInstIds.size());
        for (Integer boundProcInstId : boundProcInstIds) {
            log.info("boundProcInstId:{}", boundProcInstId);
        }

        return true;

    }

    private boolean tryVerifyIfAnyExclusiveRunningProcInstBound(ProcDefInfoEntity procDefInfo,
            ProcInstInfoEntity procInst, TaskNodeDefInfoEntity taskNodeDef, TaskNodeInstInfoEntity taskNodeInst,
            PluginInvocationCommand cmd, List<ProcExecBindingEntity> nodeObjectBindings) {
        if (nodeObjectBindings == null || nodeObjectBindings.isEmpty()) {
            return false;
        }
        Set<Integer> boundExclusiveProcInstIds = new HashSet<>();

        for (ProcExecBindingEntity nodeObjectBinding : nodeObjectBindings) {
            if (StringUtils.isBlank(nodeObjectBinding.getEntityDataId())) {
                continue;
            }

            int exclusiveProcInstCount = procExecBindingMapper
                    .countAllExclusiveBoundRunningProcInstancesWithoutProcInst(nodeObjectBinding.getEntityDataId(),
                            procInst.getId());

            if (exclusiveProcInstCount <= 0) {
                continue;
            }

            List<Integer> boundProcInstIdsOfSingleEntity = procExecBindingMapper
                    .selectAllExclusiveBoundRunningProcInstancesWithoutProcInst(nodeObjectBinding.getEntityDataId(),
                            procInst.getId());
            if (boundProcInstIdsOfSingleEntity == null || boundProcInstIdsOfSingleEntity.isEmpty()) {
                continue;
            }

            boundExclusiveProcInstIds.addAll(boundProcInstIdsOfSingleEntity);
        }

        if (boundExclusiveProcInstIds.isEmpty()) {
            return false;
        }

        log.info("Current process {}:{}:{} is shared but there are {}  exclusive processes running.",
                procDefInfo.getId(), procDefInfo.getProcDefName(), procInst.getId(), boundExclusiveProcInstIds.size());

        for (Integer boundExclusiveProcInstId : boundExclusiveProcInstIds) {
            log.info("boundExclusiveProcInstId:{}", boundExclusiveProcInstId);
        }

        return true;
    }

    private void storeProcExecBindingEntities(List<ProcExecBindingEntity> nodeObjectBindings) {
        if (nodeObjectBindings == null || nodeObjectBindings.isEmpty()) {
            return;
        }

        for (ProcExecBindingEntity nob : nodeObjectBindings) {
            procExecBindingMapper.insert(nob);
        }
    }

    private String marshalPluginInvocationCommand(PluginInvocationCommand cmd) {
        String json;
        try {
            json = objectMapper.writeValueAsString(cmd);
            return json;
        } catch (JsonProcessingException e) {
            throw new WecubeCoreException("Failed to marshal plugin invocation command.", e);
        }
    }

    protected void doInvokePluginInterface(ProcInstInfoEntity procInstEntity, TaskNodeInstInfoEntity taskNodeInstEntity,
            PluginInvocationCommand cmd) {

        Map<Object, Object> externalCacheMap = new HashMap<>();
        ProcDefInfoEntity procDefInfoEntity = procDefInfoMapper.selectByPrimaryKey(procInstEntity.getProcDefId());
        TaskNodeDefInfoEntity taskNodeDefEntity = retrieveTaskNodeDefInfoEntity(procInstEntity.getProcDefId(),
                cmd.getNodeId());

        List<ProcExecBindingEntity> nodeObjectBindings = null;

        if (isDynamicBindTaskNode(taskNodeDefEntity) && !isBoundTaskNodeInst(taskNodeInstEntity)) {
            nodeObjectBindings = dynamicCalculateTaskNodeExecBindings(taskNodeDefEntity, procInstEntity,
                    taskNodeInstEntity, cmd, externalCacheMap);
            boolean hasExcludeModeExecBindings = verifyIfExcludeModeExecBindings(procDefInfoEntity, procInstEntity,
                    taskNodeDefEntity, taskNodeInstEntity, cmd, nodeObjectBindings);
            if (hasExcludeModeExecBindings) {

                ExtraTaskEntity extraTaskEntity = new ExtraTaskEntity();
                extraTaskEntity.setCreatedBy(WorkflowConstants.DEFAULT_USER);
                extraTaskEntity.setCreatedTime(new Date());
                extraTaskEntity.setPriority(0);
                extraTaskEntity.setRev(0);
                extraTaskEntity.setTaskType(ExtraTaskEntity.TASK_TYPE_DYNAMIC_BIND_TASK_NODE_RETRY);
                extraTaskEntity.setStatus(ExtraTaskEntity.STATUS_NEW);
                extraTaskEntity.setTaskSeqNo(LocalIdGenerator.generateId());
                String taskDef = marshalPluginInvocationCommand(cmd);
                extraTaskEntity.setTaskDef(taskDef);

                extraTaskMapper.insert(extraTaskEntity);
                return;
            } else {
                storeProcExecBindingEntities(nodeObjectBindings);
                taskNodeInstEntity.setBindStatus(TaskNodeInstInfoEntity.BIND_STATUS_BOUND);
                taskNodeInstInfoRepository.updateByPrimaryKeySelective(taskNodeInstEntity);
            }

        } else {
            nodeObjectBindings = retrieveProcExecBindingEntities(taskNodeInstEntity);
        }

        PluginConfigInterfaces pluginConfigInterface = retrievePluginConfigInterface(taskNodeDefEntity,
                cmd.getNodeId());

        List<InputParamObject> inputParamObjs = calculateInputParamObjects(procInstEntity, taskNodeInstEntity,
                taskNodeDefEntity, nodeObjectBindings, pluginConfigInterface, externalCacheMap);

        if (inputParamObjs == null || inputParamObjs.isEmpty()) {
            inputParamObjs = tryCalculateInputParamObjectsFromSystem(procInstEntity, taskNodeInstEntity,
                    taskNodeDefEntity, nodeObjectBindings, pluginConfigInterface);
        }

        PluginInterfaceInvocationContext ctx = new PluginInterfaceInvocationContext() //
                .withNodeObjectBindings(nodeObjectBindings) //
                .withPluginConfigInterface(pluginConfigInterface) //
                .withProcInstEntity(procInstEntity) //
                .withTaskNodeInstEntity(taskNodeInstEntity)//
                .withTaskNodeDefEntity(taskNodeDefEntity)//
                .withPluginInvocationCommand(cmd);

        parsePluginInstance(ctx);

        buildTaskNodeExecRequestEntity(ctx);
        List<Map<String, Object>> pluginParameters = calculateInputParameters(ctx, inputParamObjs, ctx.getRequestId(),
                procInstEntity.getOper());

        if (riskyCommandVerifier.needPerformDangerousCommandsChecking(taskNodeInstEntity, taskNodeDefEntity)) {
            log.info("risky commands pre checking needed by task node : {}:{}", taskNodeDefEntity.getId(),
                    taskNodeInstEntity.getId());
            ItsDangerConfirmResultDto confirmResult = riskyCommandVerifier.performDangerousCommandsChecking(ctx,
                    pluginParameters);

            if (confirmResult != null) {
                postProcessRiskyVerifyingResult(ctx, cmd, taskNodeInstEntity, confirmResult);
                return;
            } else {
                postProcessNoneRiskyVerifyingResult(ctx, cmd, taskNodeInstEntity, taskNodeDefEntity);
            }

        }

        PluginInvocationOperation operation = new PluginInvocationOperation() //
                .withCallback(this::handlePluginInterfaceInvocationResult) //
                .withPluginInvocationRestClient(this.pluginInvocationRestClient) //
                .withPluginParameters(pluginParameters) //
                .withInstanceHost(ctx.getInstanceHost()) //
                .withInterfacePath(ctx.getInterfacePath()) //
                .withPluginInterfaceInvocationContext(ctx) //
                .withRequestId(ctx.getRequestId());

        pluginInvocationProcessor.process(operation);
    }

    private void postProcessNoneRiskyVerifyingResult(PluginInterfaceInvocationContext ctx, PluginInvocationCommand cmd,
            TaskNodeInstInfoEntity taskNodeInstEntity, TaskNodeDefInfoEntity taskNodeDefEntity) {
        taskNodeInstEntity.setPreCheckRet(TaskNodeInstInfoEntity.PRE_CHECK_RESULT_NONE_RISK);
        taskNodeInstEntity.setUpdatedTime(new Date());
        taskNodeInstInfoRepository.updateByPrimaryKeySelective(taskNodeInstEntity);
        log.info("RISKY commands checking performed and passed by task node: {}:{}:{}", taskNodeDefEntity.getNodeName(),
                taskNodeDefEntity.getId(), taskNodeInstEntity.getId());
    }

    private void postProcessRiskyVerifyingResult(PluginInterfaceInvocationContext ctx, PluginInvocationCommand cmd,
            TaskNodeInstInfoEntity taskNodeInstEntity, ItsDangerConfirmResultDto confirmResult) {
        taskNodeInstEntity.setStatus(TaskNodeInstInfoEntity.RISKY_STATUS);
        taskNodeInstEntity.setUpdatedBy(WorkflowConstants.DEFAULT_USER);
        taskNodeInstEntity.setUpdatedTime(new Date());
        taskNodeInstEntity.setPreCheckRet(TaskNodeInstInfoEntity.PRE_CHECK_RESULT_RISKY);
        taskNodeInstInfoRepository.updateByPrimaryKeySelective(taskNodeInstEntity);

        pluginInvocationResultService.responsePluginInterfaceInvocation(
                new PluginInvocationResult().parsePluginInvocationCommand(cmd).withResultCode(RESULT_CODE_ERR));

        TaskNodeExecRequestEntity requestEntity = ctx.getTaskNodeExecRequestEntity();
        requestEntity.setErrCode("CONFIRM");
        requestEntity.setErrMsg(confirmResult.getMessage());
        requestEntity.setUpdatedTime(new Date());

        taskNodeExecRequestRepository.updateByPrimaryKey(requestEntity);
    }

    private List<ProcExecBindingEntity> dynamicCalculateTaskNodeExecBindings(TaskNodeDefInfoEntity taskNodeDefEntity,
            ProcInstInfoEntity procInstEntity, TaskNodeInstInfoEntity taskNodeInstEntity, PluginInvocationCommand cmd,
            Map<Object, Object> cacheMap) {

        log.info("about to calculate bindings for task node {} {}", taskNodeDefEntity.getId(),
                taskNodeDefEntity.getNodeName());
        int procInstId = procInstEntity.getId();
        int nodeInstId = taskNodeInstEntity.getId();
        procExecBindingMapper.deleteAllTaskNodeBindings(procInstId, nodeInstId);

        List<ProcExecBindingEntity> entities = new ArrayList<>();

        ProcExecBindingEntity procInstBinding = procExecBindingMapper.selectProcInstBindings(procInstId);
        if (procInstBinding == null) {
            log.info("cannot find process instance exec binding for {}", procInstId);
            return entities;
        }

        String rootDataId = procInstBinding.getEntityDataId();

        if (StringUtils.isBlank(rootDataId)) {
            log.info("root data id is blank for process instance {}", procInstId);
            return entities;
        }
        String routineExpr = calculateDataModelExpression(taskNodeDefEntity);

        if (StringUtils.isBlank(routineExpr)) {
            log.info("the routine expression is blank for {} {}", taskNodeDefEntity.getId(),
                    taskNodeDefEntity.getNodeName());
            return entities;
        }

        log.info("About to fetch data for node {} {} with expression {} and data id {}", taskNodeDefEntity.getId(),
                taskNodeDefEntity.getNodeName(), routineExpr, rootDataId);
        EntityOperationRootCondition condition = new EntityOperationRootCondition(routineExpr, rootDataId);
        try {
            EntityTreeNodesOverview overview = entityOperationService.generateEntityLinkOverview(condition, cacheMap);

            List<ProcExecBindingEntity> boundEntities = calDynamicLeafNodeEntityNodesBindings(taskNodeDefEntity,
                    procInstEntity, taskNodeInstEntity, overview.getLeafNodeEntityNodes());

            log.info("DYNAMIC BINDING:total {} entities bound for {}-{}-{}", boundEntities.size(),
                    taskNodeInstEntity.getNodeDefId(), taskNodeInstEntity.getNodeName(), taskNodeInstEntity.getId());
            return boundEntities;
        } catch (Exception e) {
            String errMsg = String.format("Errors while fetching data for node %s %s with expr %s and data id %s",
                    taskNodeDefEntity.getId(), taskNodeDefEntity.getNodeName(), routineExpr, rootDataId);
            log.error(errMsg, e);
            throw new WecubeCoreException("3191", errMsg, taskNodeDefEntity.getId(), taskNodeDefEntity.getNodeName(),
                    routineExpr, rootDataId);
        }

    }

    private List<ProcExecBindingEntity> calDynamicLeafNodeEntityNodesBindings(TaskNodeDefInfoEntity f,
            ProcInstInfoEntity procInstEntity, TaskNodeInstInfoEntity taskNodeInstEntity,
            List<TreeNode> leafNodeEntityNodes) {
        List<ProcExecBindingEntity> entities = new ArrayList<>();
        if (leafNodeEntityNodes == null) {
            return entities;
        }

        if (log.isInfoEnabled()) {
            log.info("total {} nodes returned as default bindings for {} {} {}", leafNodeEntityNodes.size(), f.getId(),
                    f.getNodeId(), f.getNodeName());
        }

        for (TreeNode tn : leafNodeEntityNodes) {

            ProcExecBindingEntity taskNodeBinding = new ProcExecBindingEntity();
            taskNodeBinding.setBindType(ProcExecBindingEntity.BIND_TYPE_TASK_NODE_INSTANCE);
            taskNodeBinding.setBindFlag(ProcExecBindingEntity.BIND_FLAG_YES);
            taskNodeBinding.setProcDefId(f.getProcDefId());
            taskNodeBinding.setProcInstId(procInstEntity.getId());
            taskNodeBinding.setEntityDataId(String.valueOf(tn.getRootId()));
            taskNodeBinding.setEntityTypeId(String.format("%s:%s", tn.getPackageName(), tn.getEntityName()));
            taskNodeBinding.setNodeDefId(f.getId());
            taskNodeBinding.setTaskNodeInstId(taskNodeInstEntity.getId());
            taskNodeBinding.setEntityDataName(String.valueOf(tn.getDisplayName()));
            // taskNodeBinding.setOrderedNo(f.getOrderedNo());
            taskNodeBinding.setCreatedBy(WorkflowConstants.DEFAULT_USER);
            taskNodeBinding.setCreatedTime(new Date());

            // procExecBindingRepository.insert(taskNodeBinding);

            entities.add(taskNodeBinding);
        }

        return entities;

    }

    private String calculateDataModelExpression(TaskNodeDefInfoEntity f) {
        if (StringUtils.isBlank(f.getRoutineExp())) {
            return null;
        }

        String expr = f.getRoutineExp();

        if (StringUtils.isBlank(f.getServiceId())) {
            return expr;
        }

        PluginConfigInterfaces inter = pluginConfigMgmtService.getPluginConfigInterfaceByServiceName(f.getServiceId());
        if (inter == null) {
            return expr;
        }

        if (StringUtils.isBlank(inter.getFilterRule())) {
            return expr;
        }

        return expr + inter.getFilterRule();
    }

    private List<InputParamObject> tryCalculateInputParamObjectsFromSystem(ProcInstInfoEntity procInstEntity,
            TaskNodeInstInfoEntity taskNodeInstEntity, TaskNodeDefInfoEntity taskNodeDefEntity,
            List<ProcExecBindingEntity> nodeObjectBindings, PluginConfigInterfaces pluginConfigInterface) {
        if (nodeObjectBindings != null && !nodeObjectBindings.isEmpty()) {
            return new ArrayList<>();
        }

        List<InputParamObject> inputParamObjs = new ArrayList<InputParamObject>();

        List<PluginConfigInterfaceParameters> configInterfaceInputParams = pluginConfigInterface.getInputParameters();

        if (!checkIfCouldCalculateFromSystem(configInterfaceInputParams)) {
            return new ArrayList<>();
        }

        // TODO to support multiple records from context
        InputParamObject inputObj = new InputParamObject();

        inputObj.setEntityTypeId("TaskNode");
        inputObj.setEntityDataId(String.format("%s-%s-%s-%s", taskNodeInstEntity.getProcDefId(),
                taskNodeInstEntity.getNodeDefId(), taskNodeInstEntity.getProcInstId(), taskNodeInstEntity.getId()));

        for (PluginConfigInterfaceParameters param : configInterfaceInputParams) {
            String paramName = param.getName();
            String paramType = param.getDataType();

            inputObj.addAttrNames(paramName);

            InputParamAttr inputAttr = new InputParamAttr();
            inputAttr.setName(paramName);
            inputAttr.setType(paramType);
            inputAttr.setSensitive(IS_SENSITIVE_ATTR.equalsIgnoreCase(param.getSensitiveData()));

            List<Object> objectVals = new ArrayList<Object>();
            //
            String mappingType = param.getMappingType();
            inputAttr.setMapType(mappingType);

            if (MAPPING_TYPE_SYSTEM_VARIABLE.equalsIgnoreCase(mappingType)) {
                handleSystemMapping(mappingType, param, paramName, objectVals);
            }

            if (MAPPING_TYPE_CONSTANT.equalsIgnoreCase(mappingType)) {
                handleConstantMapping(mappingType, taskNodeDefEntity, paramName, objectVals);
            }

            inputAttr.addValues(objectVals);

            inputObj.addAttrs(inputAttr);
        }

        inputParamObjs.add(inputObj);

        return inputParamObjs;
    }

    private boolean checkIfCouldCalculateFromSystem(List<PluginConfigInterfaceParameters> configInterfaceInputParams) {
        if (configInterfaceInputParams == null || configInterfaceInputParams.isEmpty()) {
            return false;
        }

        for (PluginConfigInterfaceParameters c : configInterfaceInputParams) {
            if ((!MAPPING_TYPE_SYSTEM_VARIABLE.equalsIgnoreCase(c.getMappingType()))
                    && (!MAPPING_TYPE_CONSTANT.equalsIgnoreCase(c.getMappingType()))) {
                return false;
            }
        }

        return true;
    }

    private void buildTaskNodeExecRequestEntity(PluginInterfaceInvocationContext ctx) {

        List<TaskNodeExecRequestEntity> formerRequestEntities = taskNodeExecRequestRepository
                .selectCurrentEntityByNodeInstId(ctx.getTaskNodeInstEntity().getId());

        if (formerRequestEntities != null) {
            for (TaskNodeExecRequestEntity formerRequestEntity : formerRequestEntities) {
                formerRequestEntity.setIsCurrent(false);
                formerRequestEntity.setUpdatedTime(new Date());
                taskNodeExecRequestRepository.updateByPrimaryKeySelective(formerRequestEntity);
            }
        }

        String requestId = UUID.randomUUID().toString();

        TaskNodeInstInfoEntity taskNodeInstEntity = ctx.getTaskNodeInstEntity();

        PluginInvocationCommand cmd = ctx.getPluginInvocationCommand();
        TaskNodeExecRequestEntity requestEntity = new TaskNodeExecRequestEntity();
        requestEntity.setNodeInstId(taskNodeInstEntity.getId());
        requestEntity.setReqId(requestId);
        requestEntity.setReqUrl(ctx.getInstanceHost() + ctx.getInterfacePath());

        requestEntity.setExecutionId(cmd.getExecutionId());
        requestEntity.setNodeId(cmd.getNodeId());
        requestEntity.setNodeName(cmd.getNodeName());
        requestEntity.setProcDefKernelId(cmd.getProcDefId());
        requestEntity.setProcDefKernelKey(cmd.getProcDefKey());
        requestEntity.setProcDefVer(cmd.getProcDefVersion());
        requestEntity.setProcInstKernelId(cmd.getProcInstId());
        requestEntity.setProcInstKernelKey(cmd.getProcInstKey());
        requestEntity.setCreatedBy(WorkflowConstants.DEFAULT_USER);
        requestEntity.setCreatedTime(new Date());
        requestEntity.setIsCurrent(true);
        requestEntity.setIsCompleted(false);

        taskNodeExecRequestRepository.insert(requestEntity);

        ctx.withTaskNodeExecRequestEntity(requestEntity);
        ctx.setRequestId(requestId);

    }

    private void parsePluginInstance(PluginInterfaceInvocationContext ctx) {
        PluginConfigInterfaces pluginConfigInterface = ctx.getPluginConfigInterface();
        PluginInstances pluginInstance = retrieveAvailablePluginInstance(pluginConfigInterface);
        String interfacePath = pluginConfigInterface.getPath();
        if (pluginInstance == null) {
            log.warn("cannot find an available plugin instance for {}", pluginConfigInterface.getServiceName());
            throw new WecubeCoreException("3169", "Cannot find an available plugin instance.");
        }

        String instanceHostAndPort = applicationProperties.getGatewayUrl();
        ctx.setInstanceHost(instanceHostAndPort);
        ctx.setInterfacePath(interfacePath);
    }

    private List<InputParamObject> calculateInputParamObjects(ProcInstInfoEntity procInstEntity,
            TaskNodeInstInfoEntity taskNodeInstEntity, TaskNodeDefInfoEntity taskNodeDefEntity,
            List<ProcExecBindingEntity> nodeObjectBindings, PluginConfigInterfaces pluginConfigInterface,
            Map<Object, Object> externalCacheMap) {

        List<InputParamObject> inputParamObjs = new ArrayList<InputParamObject>();

        List<PluginConfigInterfaceParameters> configInterfaceInputParams = pluginConfigInterface.getInputParameters();
        for (ProcExecBindingEntity nodeObjectBinding : nodeObjectBindings) {
            String entityTypeId = nodeObjectBinding.getEntityTypeId();
            String entityDataId = nodeObjectBinding.getEntityDataId();

            InputParamObject inputObj = new InputParamObject();
            inputObj.setEntityTypeId(entityTypeId);
            inputObj.setEntityDataId(entityDataId);

            for (PluginConfigInterfaceParameters param : configInterfaceInputParams) {
                String paramName = param.getName();
                String paramType = param.getDataType();

                inputObj.addAttrNames(paramName);

                InputParamAttr inputAttr = new InputParamAttr();
                inputAttr.setName(paramName);
                inputAttr.setType(paramType);
                inputAttr.setSensitive(IS_SENSITIVE_ATTR.equalsIgnoreCase(param.getSensitiveData()));

                List<Object> objectVals = new ArrayList<Object>();
                //
                String mappingType = param.getMappingType();
                inputAttr.setMapType(mappingType);

                handleEntityMapping(mappingType, param, entityDataId, objectVals, externalCacheMap);

                handleContextMapping(mappingType, taskNodeDefEntity, paramName, procInstEntity, param, paramType,
                        objectVals);

                handleSystemMapping(mappingType, param, paramName, objectVals);

                handleConstantMapping(mappingType, taskNodeDefEntity, paramName, objectVals);

                inputAttr.addValues(objectVals);

                inputObj.addAttrs(inputAttr);
            }

            inputParamObjs.add(inputObj);

        }

        return inputParamObjs;
    }

    private void handleEntityMapping(String mappingType, PluginConfigInterfaceParameters param, String entityDataId,
            List<Object> objectVals, Map<Object, Object> cacheMap) {
        if (MAPPING_TYPE_ENTITY.equals(mappingType)) {
            String mappingEntityExpression = param.getMappingEntityExpression();

            if (log.isDebugEnabled()) {
                log.debug("expression:{}", mappingEntityExpression);
            }

            EntityOperationRootCondition condition = new EntityOperationRootCondition(mappingEntityExpression,
                    entityDataId);

            List<Object> attrValsPerExpr = entityOperationService.queryAttributeValues(condition, cacheMap);

            if (attrValsPerExpr == null) {
                log.info("returned null while fetch data with expression:{}", mappingEntityExpression);
                attrValsPerExpr = new ArrayList<>();
            }

            if (log.isDebugEnabled()) {
                log.debug("retrieved objects with expression,size={},values={}", attrValsPerExpr.size(),
                        attrValsPerExpr);
            }

            objectVals.addAll(attrValsPerExpr);

        }
    }

    private void handleContextMapping(String mappingType, TaskNodeDefInfoEntity taskNodeDefEntity, String paramName,
            ProcInstInfoEntity procInstEntity, PluginConfigInterfaceParameters param, String paramType,
            List<Object> objectVals) {
        if (!MAPPING_TYPE_CONTEXT.equals(mappingType)) {
            return;
        }
        // #1993
        String curTaskNodeDefId = taskNodeDefEntity.getId();
        TaskNodeParamEntity nodeParamEntity = taskNodeParamRepository
                .selectOneByTaskNodeDefIdAndParamName(curTaskNodeDefId, paramName);

        if (nodeParamEntity == null) {
            log.error("mapping type is {} but node parameter entity is null for {}", mappingType, curTaskNodeDefId);

            // TODO surpress errors if not required
            throw new WecubeCoreException("3170", "Task node parameter entity does not exist.");
        }

        String bindNodeId = nodeParamEntity.getBindNodeId();
        String bindParamType = nodeParamEntity.getBindParamType();
        String bindParamName = nodeParamEntity.getBindParamName();

        // get by procInstId and nodeId
        TaskNodeInstInfoEntity bindNodeInstEntity = taskNodeInstInfoRepository
                .selectOneByProcInstIdAndNodeId(procInstEntity.getId(), bindNodeId);

        if (bindNodeInstEntity == null) {
            log.error("Bound node instance entity does not exist for {} {}", procInstEntity.getId(), bindNodeId);
            throw new WecubeCoreException("3171", "Bound node instance entity does not exist.");
        }

        if (TaskNodeDefInfoEntity.NODE_TYPE_START_EVENT.equalsIgnoreCase(bindNodeInstEntity.getNodeType())) {
            handleContextMappingForStartEvent(mappingType, taskNodeDefEntity, paramName, procInstEntity, param,
                    paramType, objectVals, bindNodeInstEntity, bindParamName, bindParamType);
            return;
        } else {
            handleContextMappingForTaskNode(mappingType, taskNodeDefEntity, paramName, procInstEntity, param, paramType,
                    objectVals, bindNodeInstEntity, bindParamName, bindParamType);

            return;
        }

    }

    private void handleContextMappingForStartEvent(String mappingType, TaskNodeDefInfoEntity taskNodeDefEntity,
            String paramName, ProcInstInfoEntity procInstEntity, PluginConfigInterfaceParameters param,
            String paramType, List<Object> objectVals, TaskNodeInstInfoEntity bindNodeInstEntity, String bindParamName,
            String bindParamType) {
        // #1993
        // 1
        if (LocalWorkflowConstants.CONTEXT_NAME_PROC_DEF_KEY.equals(bindParamName)) {
            String procDefKey = procInstEntity.getProcDefKey();
            objectVals.add(procDefKey);
            return;
        }

        // 2
        if (LocalWorkflowConstants.CONTEXT_NAME_PROC_DEF_NAME.equals(bindParamName)) {
            String procDefName = procInstEntity.getProcDefName();
            objectVals.add(procDefName);

            return;
        }

        // 3
        if (LocalWorkflowConstants.CONTEXT_NAME_PROC_INST_ID.equals(bindParamName)) {
            String procInstId = String.valueOf(procInstEntity.getId());
            objectVals.add(procInstId);
            return;
        }

        // 4
        if (LocalWorkflowConstants.CONTEXT_NAME_PROC_INST_KEY.equals(bindParamName)) {
            String procInstKey = procInstEntity.getProcInstKey();
            objectVals.add(procInstKey);
            return;
        }

        // 5
        if (LocalWorkflowConstants.CONTEXT_NAME_PROC_INST_NAME.equals(bindParamName)) {
            ProcExecBindingEntity procExecBindingEntity = procExecBindingMapper
                    .selectProcInstBindings(procInstEntity.getId());
            String rootEntityName = "";
            if (procExecBindingEntity != null) {
                rootEntityName = procExecBindingEntity.getEntityDataName();
            }
            String procInstName = procInstEntity.getProcDefName() + " " + rootEntityName + " "
                    + procInstEntity.getOper() + " " + formatDate(procInstEntity.getCreatedTime());
            objectVals.add(procInstName);

            return;
        }

        // 6
        if (LocalWorkflowConstants.CONTEXT_NAME_ROOT_ENTITY_NAME.equals(bindParamName)) {
            //

            ProcExecBindingEntity procExecBindingEntity = procExecBindingMapper
                    .selectProcInstBindings(procInstEntity.getId());
            String rootEntityName = null;
            if (procExecBindingEntity != null) {
                rootEntityName = procExecBindingEntity.getEntityDataName();
            }

            objectVals.add(rootEntityName);

            return;
        }

        // 7
        if (LocalWorkflowConstants.CONTEXT_NAME_ROOT_ENTITY_ID.equals(bindParamName)) {
            //

            ProcExecBindingEntity procExecBindingEntity = procExecBindingMapper
                    .selectProcInstBindings(procInstEntity.getId());
            String rootEntityId = null;
            if (procExecBindingEntity != null) {
                rootEntityId = procExecBindingEntity.getEntityDataId();
            }

            objectVals.add(rootEntityId);

            return;
        }
    }

    private void handleContextMappingForTaskNode(String mappingType, TaskNodeDefInfoEntity taskNodeDefEntity,
            String paramName, ProcInstInfoEntity procInstEntity, PluginConfigInterfaceParameters param,
            String paramType, List<Object> objectVals, TaskNodeInstInfoEntity bindNodeInstEntity, String bindParamName,
            String bindParamType) {
        List<TaskNodeExecRequestEntity> requestEntities = taskNodeExecRequestRepository
                .selectCurrentEntityByNodeInstId(bindNodeInstEntity.getId());

        if (requestEntities == null || requestEntities.isEmpty()) {
            log.error("cannot find request entity for {}", bindNodeInstEntity.getId());
            throw new WecubeCoreException("3172", "Bound request entity does not exist.");
        }

        if (requestEntities.size() > 1) {
            log.warn("duplicated request entity found for {} ", bindNodeInstEntity.getId());
            // throw new WecubeCoreException("3173", "Duplicated request entity
            // found.");
        }

        TaskNodeExecRequestEntity requestEntity = requestEntities.get(0);

        List<TaskNodeExecParamEntity> execParamEntities = taskNodeExecParamRepository
                .selectAllByRequestIdAndParamNameAndParamType(requestEntity.getReqId(), bindParamName, bindParamType);

        if (execParamEntities == null || execParamEntities.isEmpty()) {
            if (FIELD_REQUIRED.equals(param.getRequired())) {
                log.error("parameter entity does not exist but such plugin parameter is mandatory for {} {}",
                        bindParamName, bindParamType);
                throw new WecubeCoreException("3174",
                        String.format(
                                "parameter entity does not exist but such plugin parameter is mandatory for {%s} {%s}",
                                bindParamName, bindParamType),
                        bindParamName, bindParamType);
            }
        }

        Object finalInputParam = calculateContextValue(paramType, execParamEntities);

        log.debug("context final input parameter {} {} {}", paramName, paramType, finalInputParam);

        objectVals.add(finalInputParam);
    }

    private void handleSystemMapping(String mappingType, PluginConfigInterfaceParameters param, String paramName,
            List<Object> objectVals) {
        if (MAPPING_TYPE_SYSTEM_VARIABLE.equals(mappingType)) {
            String systemVariableName = param.getMappingSystemVariableName();
            SystemVariables sVariable = systemVariableService.getSystemVariableByPackageNameAndName(
                    param.getPluginConfigInterface().getPluginConfig().getPluginPackage().getName(),
                    systemVariableName);

            if (sVariable == null && FIELD_REQUIRED.equals(param.getRequired())) {
                log.error("variable is null but [{}] is mandatory", paramName);
                throw new WecubeCoreException("3175",
                        String.format("Variable is absent but [%s] is mandatory.", paramName), paramName);
            }

            String sVal = null;
            if (sVariable != null) {
                sVal = sVariable.getValue();
                if (StringUtils.isBlank(sVal)) {
                    sVal = sVariable.getDefaultValue();
                }
            }

            if (StringUtils.isBlank(sVal) && FIELD_REQUIRED.equals(param.getRequired())) {
                log.error("variable is blank but [{}] is mandatory", paramName);
                throw new WecubeCoreException("3176",
                        String.format("Variable is absent but [%s] is mandatory.", paramName));
            }

            objectVals.add(sVal);
        }
    }

    private void handleConstantMapping(String mappingType, TaskNodeDefInfoEntity taskNodeDefEntity, String paramName,
            List<Object> objectVals) {
        if (MAPPING_TYPE_CONSTANT.equals(mappingType)) {
            String curTaskNodeDefId = taskNodeDefEntity.getId();
            TaskNodeParamEntity nodeParamEntity = taskNodeParamRepository
                    .selectOneByTaskNodeDefIdAndParamName(curTaskNodeDefId, paramName);

            if (nodeParamEntity == null) {
                log.error("mapping type is {} but node parameter entity is null for {}", mappingType, curTaskNodeDefId);
                throw new WecubeCoreException("3177",
                        String.format("Task node parameter entity does not exist for {%s}.", paramName), paramName);
            }

            Object val = null;

            if (MAPPING_TYPE_CONSTANT.equalsIgnoreCase(nodeParamEntity.getBindType())) {
                val = nodeParamEntity.getBindVal();
            }

            if (val != null) {
                objectVals.add(val);
            }
        }
    }

    private Object calculateContextValue(String paramType, List<TaskNodeExecParamEntity> execParamEntities) {
        List<Object> retDataValues = parseDataValueFromContext(execParamEntities);
        if (retDataValues == null || retDataValues.isEmpty()) {
            return null;
        }

        if (retDataValues.size() == 1) {
            return retDataValues.get(0);
        }

        if (DATA_TYPE_STRING.equalsIgnoreCase(paramType)) {
            return assembleValueList(retDataValues);
        } else {
            return retDataValues;
        }
    }

    private List<Object> parseDataValueFromContext(List<TaskNodeExecParamEntity> execParamEntities) {
        List<Object> retDataValues = new ArrayList<>();
        if (execParamEntities == null) {
            return retDataValues;
        }

        for (TaskNodeExecParamEntity e : execParamEntities) {
            String paramDataValue = e.getParamDataValue();
            if (e.getIsSensitive() != null && e.getIsSensitive() == true) {
                paramDataValue = tryDecodeParamDataValue(paramDataValue);
            }
            retDataValues.add(fromString(e.getParamDataValue(), e.getParamDataType()));
        }

        return retDataValues;
    }

    private PluginConfigInterfaces retrievePluginConfigInterface(TaskNodeDefInfoEntity taskNodeDefEntity,
            String nodeId) {
        String serviceId = retrieveServiceId(taskNodeDefEntity, nodeId);
        PluginConfigInterfaces pluginConfigInterface = pluginConfigMgmtService
                .getPluginConfigInterfaceByServiceName(serviceId);

        if (pluginConfigInterface == null) {
            log.error("Plugin config interface does not exist for {} {} {}", taskNodeDefEntity.getId(), nodeId,
                    serviceId);
            throw new WecubeCoreException("3178", "Plugin config interface does not exist.");
        }

        return pluginConfigInterface;
    }

    private List<ProcExecBindingEntity> retrieveProcExecBindingEntities(TaskNodeInstInfoEntity taskNodeInstEntity) {
        List<ProcExecBindingEntity> nodeObjectBindings = procExecBindingMapper
                .selectAllBoundTaskNodeBindings(taskNodeInstEntity.getProcInstId(), taskNodeInstEntity.getId());

        if (nodeObjectBindings == null) {
            log.info("node object bindings is empty for {} {}", taskNodeInstEntity.getProcInstId(),
                    taskNodeInstEntity.getId());
            nodeObjectBindings = new ArrayList<>();
        }

        return nodeObjectBindings;
    }

    private String retrieveServiceId(TaskNodeDefInfoEntity taskNodeDefEntity, String nodeId) {
        String serviceId = taskNodeDefEntity.getServiceId();
        if (StringUtils.isBlank(serviceId)) {
            log.error("service ID is invalid for {} {}", taskNodeDefEntity.getProcDefId(), nodeId);
            throw new WecubeCoreException("3179", "Service ID is invalid.");
        }

        if (log.isDebugEnabled()) {
            log.debug("retrieved service id {} for {},{}", serviceId, taskNodeDefEntity.getProcDefId(), nodeId);
        }
        return serviceId;
    }

    private TaskNodeInstInfoEntity retrieveTaskNodeInstInfoEntity(Integer procInstId, String nodeId) {
        Date currTime = new Date();
        TaskNodeInstInfoEntity taskNodeInstEntity = taskNodeInstInfoRepository
                .selectOneByProcInstIdAndNodeId(procInstId, nodeId);
        if (taskNodeInstEntity == null) {
            log.warn("Task node instance does not exist for {} {}", procInstId, nodeId);
            throw new WecubeCoreException("3180", "Task node instance does not exist.");
        }

        //
        String originalStatus = taskNodeInstEntity.getStatus();
        taskNodeInstEntity.setStatus(TaskNodeInstInfoEntity.IN_PROGRESS_STATUS);

        log.debug("task node instance {} update status from {} to {}", taskNodeInstEntity.getId(), originalStatus,
                taskNodeInstEntity.getStatus());

        taskNodeInstEntity.setUpdatedTime(currTime);
        taskNodeInstEntity.setUpdatedBy(WorkflowConstants.DEFAULT_USER);
        taskNodeInstEntity.setErrMsg(EMPTY_ERROR_MSG);
        taskNodeInstInfoRepository.updateByPrimaryKeySelective(taskNodeInstEntity);

        List<TaskNodeExecRequestEntity> formerRequestEntities = taskNodeExecRequestRepository
                .selectCurrentEntityByNodeInstId(taskNodeInstEntity.getId());

        if (formerRequestEntities != null) {
            for (TaskNodeExecRequestEntity formerRequestEntity : formerRequestEntities) {
                formerRequestEntity.setIsCurrent(false);
                formerRequestEntity.setUpdatedTime(currTime);
                formerRequestEntity.setUpdatedBy(WorkflowConstants.DEFAULT_USER);
                taskNodeExecRequestRepository.updateByPrimaryKeySelective(formerRequestEntity);
            }
        }

        return taskNodeInstEntity;
    }

    private TaskNodeDefInfoEntity retrieveTaskNodeDefInfoEntity(String procDefId, String nodeId) {
        TaskNodeDefInfoEntity taskNodeDefEntity = taskNodeDefInfoRepository
                .selectOneWithProcessIdAndNodeIdAndStatus(procDefId, nodeId, TaskNodeDefInfoEntity.DEPLOYED_STATUS);

        if (taskNodeDefEntity == null) {
            log.warn("Task node definition does not exist for {} {} {}", procDefId, nodeId,
                    TaskNodeDefInfoEntity.DEPLOYED_STATUS);
            throw new WecubeCoreException("3181", "Task node definition does not exist.");
        }

        return taskNodeDefEntity;
    }

    private ProcInstInfoEntity retrieveProcInstInfoEntity(PluginInvocationCommand cmd) {
        return doRetrieveProcInstInfoEntity(cmd);
    }

    private ProcInstInfoEntity doRetrieveProcInstInfoEntity(PluginInvocationCommand cmd) {
        String procInstKernelId = cmd.getProcInstId();

        ProcInstInfoEntity procInstEntity = null;
        int round = 0;
        while (round < 15) {
            procInstEntity = procInstInfoRepository.selectOneByProcInstKernelId(procInstKernelId);

            if (procInstEntity != null) {
                break;
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
            }

            round++;
        }

        if (procInstEntity == null) {
            log.error("Process instance info does not exist for id:{}", procInstKernelId);
            throw new WecubeCoreException("3182", "Process instance information does not exist.");
        }

        if (!ProcInstInfoEntity.IN_PROGRESS_STATUS.equals(procInstEntity.getStatus())) {

            String orignalStatus = procInstEntity.getStatus();
            procInstEntity.setUpdatedTime(new Date());
            procInstEntity.setUpdatedBy(WorkflowConstants.DEFAULT_USER);
            procInstEntity.setStatus(ProcInstInfoEntity.IN_PROGRESS_STATUS);

            if (log.isDebugEnabled()) {
                log.debug("process instance {} update status from {} to {}", procInstEntity.getId(), orignalStatus,
                        ProcInstInfoEntity.IN_PROGRESS_STATUS);
            }

            procInstInfoRepository.updateByPrimaryKeySelective(procInstEntity);
        }

        return procInstEntity;
    }

    private List<Map<String, Object>> calculateInputParameters(PluginInterfaceInvocationContext ctx,
            List<InputParamObject> inputParamObjs, String requestId, String operator) {
        List<Map<String, Object>> pluginParameters = new ArrayList<Map<String, Object>>();

        int objectId = 0;

        for (InputParamObject ipo : inputParamObjs) {
            if (log.isDebugEnabled()) {
                log.debug("process input parameters for entity:{} {}", ipo.getEntityTypeId(), ipo.getEntityDataId());
            }

            String sObjectId = String.valueOf(objectId);
            String entityTypeId = ipo.getEntityTypeId();
            String entityDataId = ipo.getEntityDataId();

            Map<String, Object> inputMap = new HashMap<String, Object>();
            inputMap.put(CALLBACK_PARAMETER_KEY, entityDataId);
            TaskNodeExecParamEntity p = new TaskNodeExecParamEntity();
            p.setReqId(requestId);
            p.setParamName(CALLBACK_PARAMETER_KEY);
            p.setParamType(TaskNodeExecParamEntity.PARAM_TYPE_REQUEST);
            p.setParamDataType(DATA_TYPE_STRING);
            p.setObjId(sObjectId);
            p.setParamDataValue(entityDataId);
            p.setEntityDataId(entityDataId);
            p.setEntityTypeId(entityTypeId);
            p.setCreatedBy(WorkflowConstants.DEFAULT_USER);
            p.setCreatedTime(new Date());
            p.setIsSensitive(false);

            taskNodeExecParamRepository.insert(p);

            inputMap.put(INPUT_PARAMETER_KEY_OPERATOR, operator);

            for (InputParamAttr attr : ipo.getAttrs()) {
                TaskNodeExecParamEntity e = new TaskNodeExecParamEntity();
                e.setReqId(requestId);
                e.setParamName(attr.getName());
                e.setParamType(TaskNodeExecParamEntity.PARAM_TYPE_REQUEST);
                e.setParamDataType(attr.getType());
                e.setObjId(sObjectId);
                e.setParamDataValue(tryCalculateParamDataValue(attr));
                e.setEntityDataId(entityDataId);
                e.setEntityTypeId(entityTypeId);
                e.setCreatedBy(WorkflowConstants.DEFAULT_USER);
                e.setCreatedTime(new Date());

                e.setIsSensitive(attr.isSensitive());

                taskNodeExecParamRepository.insert(e);

                inputMap.put(attr.getName(), attr.getExpectedValue());
            }

            pluginParameters.add(inputMap);

            objectId++;
        }

        return pluginParameters;
    }

    private String tryCalculateParamDataValue(InputParamAttr attr) {
        if (attr.getExpectedValue() == null) {
            return null;
        }

        String dataValue = attr.getExpectedValue().toString();
        if (attr.isSensitive()) {
            dataValue = tryEncodeParamDataValue(dataValue);
        }

        return dataValue;
    }

    private PluginInstances retrieveAvailablePluginInstance(PluginConfigInterfaces itf) {
        PluginConfigs config = itf.getPluginConfig();
        PluginPackages pkg = config.getPluginPackage();
        String pluginName = pkg.getName();

        List<PluginInstances> instances = pluginInstanceMgmtService.getRunningPluginInstances(pluginName);

        return instances.get(0);

    }

    private void handleErrorInvocationResult(PluginInterfaceInvocationResult pluginInvocationResult,
            PluginInterfaceInvocationContext ctx) {

        if (pluginInvocationResult.getResultData() != null && !pluginInvocationResult.getResultData().isEmpty()) {
            log.debug("plugin invocation partially succeeded.{} {}", ctx.getRequestId(), ctx.getInterfacePath());
            handleResultData(pluginInvocationResult, ctx, pluginInvocationResult.getResultData());
        }

        PluginInvocationResult result = new PluginInvocationResult()
                .parsePluginInvocationCommand(ctx.getPluginInvocationCommand());

        log.warn("system errors:{}", pluginInvocationResult.getErrMsg());
        result.setResultCode(RESULT_CODE_ERR);
        pluginInvocationResultService.responsePluginInterfaceInvocation(result);
        handlePluginInterfaceInvocationFailure(pluginInvocationResult, ctx, "5001",
                "Errors:" + trimWithMaxLength(pluginInvocationResult.getErrMsg()));

        return;
    }

    private void handleNullResultData(PluginInterfaceInvocationResult pluginInvocationResult,
            PluginInterfaceInvocationContext ctx) {
        PluginInvocationResult result = new PluginInvocationResult()
                .parsePluginInvocationCommand(ctx.getPluginInvocationCommand());
        PluginConfigInterfaces pluginConfigInterface = ctx.getPluginConfigInterface();
        List<PluginConfigInterfaceParameters> outputParameters = pluginConfigInterface.getOutputParameters();

        if (outputParameters == null || outputParameters.isEmpty()) {
            log.debug("output parameter is NOT configured for interface {}", pluginConfigInterface.getServiceName());
            result.setResultCode(RESULT_CODE_OK);
            pluginInvocationResultService.responsePluginInterfaceInvocation(result);
            handlePluginInterfaceInvocationSuccess(pluginInvocationResult, ctx);
            return;
        }

        if (outputParameters != null && !outputParameters.isEmpty()) {
            if (ctx.getPluginParameters() == null || ctx.getPluginParameters().isEmpty()) {
                log.debug("output parameter is configured but INPUT is empty for interface {}",
                        pluginConfigInterface.getServiceName());
                result.setResultCode(RESULT_CODE_OK);
                pluginInvocationResultService.responsePluginInterfaceInvocation(result);
                handlePluginInterfaceInvocationSuccess(pluginInvocationResult, ctx);
                return;
            } else {
                log.warn("output parameter is configured but result is empty for interface {}",
                        pluginConfigInterface.getServiceName());
                result.setResultCode(RESULT_CODE_ERR);
                pluginInvocationResultService.responsePluginInterfaceInvocation(result);
                handlePluginInterfaceInvocationFailure(pluginInvocationResult, ctx, "5003", "output is null");
                return;
            }
        }

        return;
    }

    private void handleResultData(PluginInterfaceInvocationResult pluginInvocationResult,
            PluginInterfaceInvocationContext ctx, List<Object> resultData) {

        List<Map<String, Object>> outputParameterMaps = validateAndCastResultData(resultData);
        storeOutputParameterMaps(ctx, outputParameterMaps);

        if (log.isDebugEnabled()) {
            log.debug("about to process output parameters for {}", ctx.getPluginConfigInterface().getServiceName());
        }
        for (Map<String, Object> outputParameterMap : outputParameterMaps) {
            handleSingleOutputMap(pluginInvocationResult, ctx, outputParameterMap);
        }

        if (log.isDebugEnabled()) {
            log.debug("finished processing {} output parameters for {}", outputParameterMaps.size(),
                    ctx.getPluginConfigInterface().getServiceName());
        }

        return;
    }

    private void storeOutputParameterMaps(PluginInterfaceInvocationContext ctx,
            List<Map<String, Object>> outputParameterMaps) {
        int count = 0;
        for (Map<String, Object> outputParameterMap : outputParameterMaps) {
            String objectId = String.valueOf(count);
            storeSingleOutputParameterMap(ctx, outputParameterMap, objectId);
            count++;
        }
    }

    private void storeSingleOutputParameterMap(PluginInterfaceInvocationContext ctx,
            Map<String, Object> outputParameterMap, String objectId) {

        String entityTypeId = null;
        String entityDataId = null;

        String requestId = ctx.getTaskNodeExecRequestEntity().getReqId();

        String callbackParameter = (String) outputParameterMap.get(CALLBACK_PARAMETER_KEY);

        TaskNodeExecParamEntity callbackParameterInputEntity = null;
        if (StringUtils.isNotBlank(callbackParameter)) {
            List<TaskNodeExecParamEntity> callbackParameterInputEntities = taskNodeExecParamRepository
                    .selectOneByRequestIdAndParamTypeAndParamNameAndValue(requestId,
                            TaskNodeExecParamEntity.PARAM_TYPE_REQUEST, CALLBACK_PARAMETER_KEY, callbackParameter);
            if (callbackParameterInputEntities != null && !callbackParameterInputEntities.isEmpty()) {
                callbackParameterInputEntity = callbackParameterInputEntities.get(0);
            }
        }

        if (callbackParameterInputEntity != null) {
            objectId = callbackParameterInputEntity.getObjId();
            entityTypeId = callbackParameterInputEntity.getEntityTypeId();
            entityDataId = callbackParameterInputEntity.getEntityDataId();
        }

        List<PluginConfigInterfaceParameters> outputParameters = ctx.getPluginConfigInterface().getOutputParameters();

        for (Map.Entry<String, Object> entry : outputParameterMap.entrySet()) {

            PluginConfigInterfaceParameters p = findPreConfiguredPluginConfigInterfaceParameter(outputParameters,
                    entry.getKey());

            String paramDataType = null;
            boolean isSensitiveData = false;
            if (p == null) {
                paramDataType = DATA_TYPE_STRING;
            } else {
                paramDataType = p.getDataType();
                isSensitiveData = (IS_SENSITIVE_ATTR.equalsIgnoreCase(p.getSensitiveData()));
            }

            String paramDataValue = trimExceedParamValue(asString(entry.getValue(), paramDataType), MAX_PARAM_VAL_SIZE);

            if (isSensitiveData) {
                paramDataValue = tryEncodeParamDataValue(paramDataValue);
            }

            TaskNodeExecParamEntity paramEntity = new TaskNodeExecParamEntity();
            paramEntity.setEntityTypeId(entityTypeId);
            paramEntity.setEntityDataId(entityDataId);
            paramEntity.setObjId(objectId);
            paramEntity.setParamType(TaskNodeExecParamEntity.PARAM_TYPE_RESPONSE);
            paramEntity.setParamName(entry.getKey());
            paramEntity.setParamDataType(paramDataType);
            paramEntity.setParamDataValue(paramDataValue);
            paramEntity.setReqId(requestId);
            paramEntity.setIsSensitive(isSensitiveData);
            paramEntity.setCreatedBy(WorkflowConstants.DEFAULT_USER);
            paramEntity.setCreatedTime(new Date());

            taskNodeExecParamRepository.insert(paramEntity);
        }
    }

    private void handleSingleOutputMap(PluginInterfaceInvocationResult pluginInvocationResult,
            PluginInterfaceInvocationContext ctx, Map<String, Object> outputParameterMap) {

        PluginConfigInterfaces pci = ctx.getPluginConfigInterface();
        List<PluginConfigInterfaceParameters> outputParameters = pci.getOutputParameters();

        if (outputParameters == null) {
            return;
        }

        if (outputParameterMap == null || outputParameterMap.isEmpty()) {
            log.info("returned output is empty for request {}", ctx.getRequestId());
            return;
        }

        String nodeEntityId = (String) outputParameterMap.get(CALLBACK_PARAMETER_KEY);

        if (StringUtils.isBlank(nodeEntityId)) {
            log.info("none entity ID found in output for request {}", ctx.getRequestId());
            return;
        }

        String errorCodeOfSingleRecord = (String) outputParameterMap.get(PLUGIN_RESULT_CODE_PARTIALLY_KEY);
        if (StringUtils.isNotBlank(errorCodeOfSingleRecord)
                && PLUGIN_RESULT_CODE_PARTIALLY_FAIL.equalsIgnoreCase(errorCodeOfSingleRecord)) {
            log.info("such request is partially failed for request:{} and {}:{}", ctx.getRequestId(),
                    CALLBACK_PARAMETER_KEY, nodeEntityId);
            // TODO to store status
            return;
        }

        for (PluginConfigInterfaceParameters pciParam : outputParameters) {
            String paramName = pciParam.getName();
            String paramExpr = pciParam.getMappingEntityExpression();

            if (StringUtils.isBlank(paramExpr)) {
                log.info("expression not configured for {}", paramName);
                continue;
            }

            Object retVal = outputParameterMap.get(paramName);

            if (retVal == null) {
                log.info("returned value is null for {} {}", ctx.getRequestId(), paramName);
                continue;
            }

            EntityOperationRootCondition condition = new EntityOperationRootCondition(paramExpr, nodeEntityId);

            try {
                this.entityOperationService.update(condition, retVal, null);
            } catch (Exception e) {
                log.warn("Exceptions while updating entity.But still keep going to update.", e);
            }

        }
    }

    private void handlePluginInterfaceInvocationSuccess(PluginInterfaceInvocationResult pluginInvocationResult,
            PluginInterfaceInvocationContext ctx) {
        Date now = new Date();
        TaskNodeExecRequestEntity requestEntity = ctx.getTaskNodeExecRequestEntity();

        requestEntity.setUpdatedTime(now);
        requestEntity.setUpdatedBy(WorkflowConstants.DEFAULT_USER);
        requestEntity.setIsCompleted(true);

        taskNodeExecRequestRepository.updateByPrimaryKeySelective(requestEntity);

        TaskNodeInstInfoEntity nodeInstEntity = ctx.getTaskNodeInstEntity();

        nodeInstEntity.setUpdatedTime(now);
        nodeInstEntity.setUpdatedBy(WorkflowConstants.DEFAULT_USER);
        nodeInstEntity.setStatus(TaskNodeInstInfoEntity.COMPLETED_STATUS);
        nodeInstEntity.setErrMsg(EMPTY_ERROR_MSG);

        taskNodeInstInfoRepository.updateByPrimaryKeySelective(nodeInstEntity);
    }

    private void handlePluginInterfaceInvocationFailure(PluginInterfaceInvocationResult pluginInvocationResult,
            PluginInterfaceInvocationContext ctx, String errorCode, String errorMsg) {

        Date now = new Date();
        TaskNodeExecRequestEntity requestEntity = ctx.getTaskNodeExecRequestEntity();

        requestEntity.setUpdatedTime(now);
        requestEntity.setUpdatedBy(WorkflowConstants.DEFAULT_USER);
        requestEntity.setErrCode(errorCode);
        requestEntity.setErrMsg(errorMsg);
        requestEntity.setIsCompleted(true);

        taskNodeExecRequestRepository.updateByPrimaryKeySelective(requestEntity);

        TaskNodeInstInfoEntity nodeInstEntity = ctx.getTaskNodeInstEntity();

        nodeInstEntity.setUpdatedTime(now);
        nodeInstEntity.setUpdatedBy(WorkflowConstants.DEFAULT_USER);
        nodeInstEntity.setStatus(TaskNodeInstInfoEntity.FAULTED_STATUS);
        nodeInstEntity.setErrMsg(errorMsg);

        taskNodeInstInfoRepository.updateByPrimaryKeySelective(nodeInstEntity);

    }

    private void refreshStatusOfPreviousNodes(List<TaskNodeInstInfoEntity> nodeInstEntities,
            TaskNodeDefInfoEntity currNodeDefInfo) {
        List<String> previousNodeIds = unmarshalNodeIds(currNodeDefInfo.getPrevNodeIds());
        log.debug("previousNodeIds:{}", previousNodeIds);
        for (String prevNodeId : previousNodeIds) {
            TaskNodeInstInfoEntity prevNodeInst = findExactTaskNodeInstInfoEntityWithNodeId(nodeInstEntities,
                    prevNodeId);
            log.debug("prevNodeInst:{} - {}", prevNodeInst, prevNodeId);
            if (prevNodeInst != null) {
                if (statelessNodeTypes.contains(prevNodeInst.getNodeType())
                        && !TaskNodeInstInfoEntity.COMPLETED_STATUS.equalsIgnoreCase(prevNodeInst.getStatus())) {
                    prevNodeInst.setUpdatedTime(new Date());
                    prevNodeInst.setUpdatedBy(WorkflowConstants.DEFAULT_USER);
                    prevNodeInst.setStatus(TaskNodeInstInfoEntity.COMPLETED_STATUS);

                    taskNodeInstInfoRepository.updateByPrimaryKeySelective(prevNodeInst);
                }
            }
        }
    }

}
