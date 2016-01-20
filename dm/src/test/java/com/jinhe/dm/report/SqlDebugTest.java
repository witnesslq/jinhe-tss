package com.jinhe.dm.report;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import com.jinhe.dm.TxTestSupport4DM;
import com.jinhe.dm.data.sqlquery.SqlConfig;
import com.jinhe.tss.framework.sso.context.Context;

public class SqlDebugTest extends TxTestSupport4DM {
    
    @Autowired private ReportAction action;
    @Autowired private _Reporter display;
    
    @Autowired private ReportService service;
    
    @Test
    public void debugSQL() {     
        String sql = SqlConfig.getScript("test1", 1);
        
        final HttpServletResponse response = Context.getResponse();
        final MockHttpServletRequest  request = new MockHttpServletRequest();
        
        Report report1 = new Report();
        report1.setType(Report.TYPE1);
        report1.setParentId(Report.DEFAULT_PARENT_ID);
        report1.setName("report-1");
        report1.setScript(sql);
        
        String paramsConfig = "[ {'label':'报表ID', 'type':'Number', 'nullable':'false'} ]"	;
        report1.setParam(paramsConfig);
        
        action.saveReport(response, report1);
 
        final Long reportId = report1.getId();
        request.addParameter("param1", "-1");
        display.showAsGrid(request, response, reportId, 1, 100);
        
        // test queryCacheInterceptor
        for(int i = 0; i < 10; i++) {
        	new Thread() {
        		public void run() {
        			Map<String, String> requestMap = new HashMap<String, String>();
        			requestMap.put("param1", "0");
					Object ret = service.queryReport(reportId, requestMap , 1, 0, -1L);
					
					System.out.println("-------------" + Thread.currentThread().getId() + "-------------" + ret); 
					// 查看打出来的是不是同一个对象，是的话说明cache拦截器在queryCache拦截器后执行，正常。
    			}
        	}.start();
        	
        	try { Thread.sleep(3); } catch (InterruptedException e) { }
        }
        
        try { Thread.sleep(3*1000); } catch (InterruptedException e) { }
    }
    


}
