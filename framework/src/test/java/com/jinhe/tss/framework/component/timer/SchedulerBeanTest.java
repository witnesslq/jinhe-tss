package com.jinhe.tss.framework.component.timer;

import org.junit.Assert;
import org.junit.Test;

import com.jinhe.tss.cache.Pool;
import com.jinhe.tss.framework.TxTestSupport;
import com.jinhe.tss.framework.component.cache.CacheHelper;
import com.jinhe.tss.framework.component.param.Param;
import com.jinhe.tss.framework.component.param.ParamConstants;
import com.jinhe.tss.framework.component.param.ParamManager;
import com.jinhe.tss.framework.test.TestUtil;
import com.jinhe.tss.util.EasyUtils;

public class SchedulerBeanTest extends TxTestSupport {
	
	String jobConfig = "com.jinhe.tss.framework.component.timer.DemoJob | 10,20,30,40,55 * * * * ? | " +
			"1:报表一:lovejava@163.com,lovejava@163.com:param1=0,param2=0\n" + 
            "2:报表二:lovejava@163.com,lovejava@163.com:param1=0"; 
	
	@Test
	public void testSchedulerBean() {
        Param paramGroup = ParamManager.addParamGroup(ParamConstants.DEFAULT_PARENT_ID, "我的分组");
        String comboParamCode = SchedulerBean.TIMER_PARAM_CODE;
		Param comboParam = ParamManager.addComboParam(paramGroup.getId(), comboParamCode, "定时Job配置");
		Long cpId = comboParam.getId();
		
		Param item1 = ParamManager.addParamItem(cpId, jobConfig + ",param2=1", "Job1", ParamConstants.COMBO_PARAM_MODE);
		Param item2 = ParamManager.addParamItem(cpId, jobConfig + ",param2=2", "Job2", ParamConstants.COMBO_PARAM_MODE);
        
		// 修改、新增、删除定时配置
		item1.setValue(jobConfig + "1");
		paramService.saveParam(item1);
		paramService.delete(item2.getId());
		ParamManager.addParamItem(cpId, jobConfig + ",param2=3", "Job3", ParamConstants.COMBO_PARAM_MODE);
		
		// 清除service method cache
		Pool shortCache = CacheHelper.getShortCache();
		shortCache.removeObject("com.jinhe.tss.framework.component.param.ParamService.getComboParam(TIMER_PARAM_CODE)");
		
		// DemoJob配了每分钟里10,20,30,40,55执行
		try { Thread.sleep(1000 * 16); } catch (InterruptedException e) { }
		
		TestUtil.printLogs(logService);
	}
	
	@Test
	public void testParseConfig() {
    	String[] array = EasyUtils.split(jobConfig, "|");
		Assert.assertTrue( array.length == 3 );
		Assert.assertEquals( array[0].trim(), "com.jinhe.tss.framework.component.timer.DemoJob");
		Assert.assertEquals( array[1].trim(), "10,20,30,40,55 * * * * ?");
    	
    	array = EasyUtils.split(array[2].trim(), "\n");
    	Assert.assertTrue( array.length == 2 );
		
		Assert.assertTrue( EasyUtils.split(array[1].trim(), ":").length == 4);
	}

}
