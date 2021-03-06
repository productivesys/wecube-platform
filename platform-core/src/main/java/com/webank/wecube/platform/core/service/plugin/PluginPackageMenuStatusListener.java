package com.webank.wecube.platform.core.service.plugin;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.webank.wecube.platform.core.entity.plugin.PluginInstances;
import com.webank.wecube.platform.core.entity.plugin.PluginPackageMenus;
import com.webank.wecube.platform.core.entity.plugin.PluginPackages;
import com.webank.wecube.platform.core.repository.plugin.PluginInstancesMapper;
import com.webank.wecube.platform.core.repository.plugin.PluginPackageMenusMapper;
import com.webank.wecube.platform.core.repository.plugin.PluginPackagesMapper;

@Service
public class PluginPackageMenuStatusListener {
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    @Autowired
    private PluginPackageMenusMapper packageMenuRepository;
    @Autowired
    private PluginPackagesMapper pluginPackagesMapper;
    
    @Autowired
    private PluginInstancesMapper pluginInstancesMapper;

    public void prePersist(PluginInstances pluginInstance){
        updatePluginPackageMenuStatusForPluginPackage(pluginInstance, false, true);
    }

    public void preRemove(PluginInstances pluginInstance) {
        updatePluginPackageMenuStatusForPluginPackage(pluginInstance, true, false);
    }

    public void postLoad(PluginInstances pluginInstance) {
        updatePluginPackageMenuStatusForPluginPackage(pluginInstance, false, true);
    }

    private void updatePluginPackageMenuStatusForPluginPackage(PluginInstances pluginInstance, boolean fromStatus, boolean toStatus) {
        PluginPackages pluginPackage = pluginInstance.getPluginPackage();
        if(pluginPackage == null){
            pluginPackage = pluginPackagesMapper.selectByPrimaryKey(pluginInstance.getPackageId());
        }
        String packageId = pluginPackage.getId();
        
        boolean hasOneMoreRunningInstances = false;
        List<PluginInstances> instances = pluginInstancesMapper.selectAllByPluginPackage(packageId);
        
        if(instances != null){
            for(PluginInstances inst : instances){
                if(inst.getId().equals(pluginInstance.getId())){
                    continue;
                }
                
                if(PluginInstances.CONTAINER_STATUS_RUNNING.equalsIgnoreCase(inst.getContainerStatus())){
                    hasOneMoreRunningInstances = true;
                    break;
                }
            }
        }
        
        if(hasOneMoreRunningInstances){
            logger.info("There are still one or more plugin instances running for {}", packageId);
            return;
        }
        
        List<PluginPackageMenus> pluginPackageMenusList = packageMenuRepository.selectAllMenusByStatusAndPluginPackage(fromStatus, packageId);
        if (pluginPackageMenusList != null) {
            for(PluginPackageMenus pluginPackageMenus : pluginPackageMenusList){
                logger.info("Updating PluginPackageMenu[{}] to {}", pluginPackageMenus.getId(), toStatus);
                pluginPackageMenus.setActive(toStatus);
                packageMenuRepository.updateByPrimaryKeySelective(pluginPackageMenus);
            }
        }
    }

}
