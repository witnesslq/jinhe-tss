package com.jinhe.tss.portal.dao;

import java.util.List;

import com.jinhe.tss.framework.component.cache.CacheLife;
import com.jinhe.tss.framework.component.cache.Cached;
import com.jinhe.tss.framework.persistence.ITreeSupportDao;
import com.jinhe.tss.portal.PortalConstants;
import com.jinhe.tss.portal.entity.Navigator;
import com.jinhe.tss.portal.permission.PermissionFilter4Navigator;
import com.jinhe.tss.um.permission.filter.PermissionFilter4Branch;
import com.jinhe.tss.um.permission.filter.PermissionTag;

public interface INavigatorDao extends ITreeSupportDao<Navigator> {

    /**
     * 保存一个菜单或菜单项。
     * @param navigator
     * @return
     */
    Navigator save(Navigator navigator);
    
    /**
     * 删除一个导航栏。
     * @param navigator
     */
    void deleteNavigator(Navigator navigator);
    
    /**
     * 根据菜单获取其下的所有菜单项集合。
     * 本方法将被拦截以进行权限过滤。
     * @param menuId
     * @param userId 用于对不同用户进行针对性缓存
     * @return
     */
    @PermissionTag(filter = PermissionFilter4Navigator.class)
    @Cached(cyclelife = CacheLife.LONG)
    List<Navigator> getMenuItems(Long menuId, Long userId);
    
    /**
     * 获取属于指定门户的菜单列表
     * @param portalId
     * @return
     */
	@PermissionTag(
			operation = PortalConstants.NAVIGATOR_VIEW_OPERRATION, 
			resourceType = PortalConstants.NAVIGATOR_RESOURCE_TYPE)
    List<Navigator> getMenusByPortal(Long portalId);
	
    @PermissionTag(
            resourceType = PortalConstants.NAVIGATOR_RESOURCE_TYPE,
            filter = PermissionFilter4Branch.class)
    List<Navigator> getChildrenById(Long id, String operationId);
}
