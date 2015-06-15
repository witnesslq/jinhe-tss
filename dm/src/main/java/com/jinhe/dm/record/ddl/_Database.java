package com.jinhe.dm.record.ddl;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.dom4j.Document;

import com.jinhe.dm.DMConstants;
import com.jinhe.dm._Util;
import com.jinhe.dm.data.sqlquery.SQLExcutor;
import com.jinhe.dm.record.Record;
import com.jinhe.dm.record.permission.RecordPermission;
import com.jinhe.dm.record.permission.RecordResource;
import com.jinhe.tss.cache.Cacheable;
import com.jinhe.tss.cache.JCache;
import com.jinhe.tss.cache.Pool;
import com.jinhe.tss.framework.Global;
import com.jinhe.tss.framework.component.log.IBusinessLogger;
import com.jinhe.tss.framework.component.log.Log;
import com.jinhe.tss.framework.component.param.ParamConstants;
import com.jinhe.tss.framework.exception.BusinessException;
import com.jinhe.tss.framework.sso.Environment;
import com.jinhe.tss.um.permission.PermissionHelper;
import com.jinhe.tss.util.EasyUtils;
import com.jinhe.tss.util.XMLDocUtil;

public abstract class _Database {
	
	static Logger log = Logger.getLogger(_Database.class);
	
	public Long recordId;
	public String recordName;
	public String datasource;
	public String table;
	public String customizeTJ;
	boolean needLog;
	
	List<Map<Object, Object>> fields;
	List<String> fieldCodes;
	List<String> fieldTypes;
	List<String> fieldNames;
	
	public _Database(Record record) {
		this.recordId = record.getId();
		this.recordName = record.getName();
		this.datasource = record.getDatasource();
		this.table = record.getTable();
		this.fields = parseJson(record.getDefine());
		this.customizeTJ = record.getCustomizeTJ();
		this.needLog = ParamConstants.TRUE.equals(record.getNeedLog());
		
		this.initFieldCodes();
	}
	
	protected void initFieldCodes() {
		this.fieldCodes = new ArrayList<String>();
		this.fieldTypes = new ArrayList<String>();
		this.fieldNames = new ArrayList<String>();
		for(Map<Object, Object> fDefs : this.fields) {
			this.fieldCodes.add((String) fDefs.get("code"));
			this.fieldTypes.add((String) fDefs.get("type"));
			this.fieldNames.add((String) fDefs.get("label"));
		}
	}
	
	@SuppressWarnings("unchecked")
	protected List<Map<Object, Object>> parseJson(String define) {
		if(EasyUtils.isNullOrEmpty(define)) {
			return new ArrayList<Map<Object,Object>>();
		}
		
		define = define.replaceAll("'", "\"");
		
		try {  
   			List<Map<Object, Object>> list = new ObjectMapper().readValue(define, List.class);  
   			for(int i = 0; i < list.size(); i++) {
   	        	Map<Object, Object> fDefs = list.get(i);
   	        	int index = i + 1;
   	        	
   				String code = (String) fDefs.get("code");
   				code = (EasyUtils.isNullOrEmpty(code) ? "f" + index : code);
   				fDefs.put("code", code);
   			}
   			return list;
   	    } 
   		catch (Exception e) {  
   	        throw new BusinessException("数据录入【" + recordName + "】的参数配置有误，要求为标准JSON格式。", e);
   	    } 
	}
	
	public abstract void createTable();
	
	public void dropTable(String table, String datasource) {
		SQLExcutor.excute("drop table " + this.table, datasource);
	}
	
	public void updateTable(Record _new) {
		String newDS = _new.getDatasource();
		String table = _new.getTable();
		this.customizeTJ = _new.getCustomizeJS();
		this.needLog = ParamConstants.TRUE.equals(_new.getNeedLog());
		
		if(!newDS.equals(this.datasource) || !table.equals(this.table)) {
			this.datasource = newDS;
			this.table = table;
			createTable();
			return;
		}
		
		// 比较新旧字段定义的异同（新增的和删除的，暂时只关心新增的）
		List<Map<Object, Object>> newFields = parseJson(_new.getDefine());
		List<Map<Object, Object>> addFields = new ArrayList<Map<Object,Object>>();
				
		int index = 0;
		for(Map<Object, Object> fDefs1 : newFields) {
			String code = (String) fDefs1.get("code");
			code = (EasyUtils.isNullOrEmpty(code) ? "f" + (++index) : code);
			fDefs1.put("code", code);
			
			boolean exsited = false;
        	for(Map<Object, Object> fDefs2 : this.fields ) {
        		if(code.equals(fDefs2.get("code"))) {
        			exsited = true; 
        			//TODO 进一步判断字段类型及长度，及是否可空等有无发生变化
        		}
        	}
        	
        	if( !exsited ) {
        		addFields.add(fDefs1);
        	}
		}
		
		// 如果原表有数据则重命名原表，没有则删之，然后新建一张新表
		if(addFields.size() > 0) {
			try {
				SQLExcutor ex = new SQLExcutor(false);
				ex.excuteQuery("select id from " + this.table, this.datasource);
				if( ex.result.isEmpty() ) {
					dropTable(this.table, newDS);
				}
				else {
					String oldTable = this.table + "_" + _new.getLockVersion();
					SQLExcutor.excute("alter table " + this.table + " rename to " + oldTable, newDS);
				}
			} catch(Exception e) {
				// 如果表不存在导致删表失败（oracle），则忽略
			}
			
			this.fields = newFields;
			initFieldCodes();
			
			createTable();
		}
	}
	
	public void insert(Map<String, String> valuesMap) {
		Map<Integer, Object> paramsMap = buildInsertParams(valuesMap);
		SQLExcutor.excute(createInsertSQL(), paramsMap, this.datasource);
	}

	private Map<Integer, Object> buildInsertParams(Map<String, String> valuesMap) {
		Map<Integer, Object> paramsMap = new HashMap<Integer, Object>();
		int index = 0;
		for(String field : this.fieldCodes) {
			Object value = _Util.preTreatValue(valuesMap.get(field), fieldTypes.get(index));
			paramsMap.put(++index, value);
		}
		paramsMap.put(++index, new Timestamp(new Date().getTime())); 
		paramsMap.put(++index, Environment.getUserCode());
		paramsMap.put(++index, 0);
		return paramsMap;
	}
	
	public void insertBatch(Collection<Map<String, String>> valuesMaps) {
		if(valuesMaps == null || valuesMaps.isEmpty()) return;
		
		List<Map<Integer, Object>> paramsList = new ArrayList<Map<Integer,Object>>();
		for(Map<String, String> valuesMap : valuesMaps) {
			Map<Integer, Object> paramsMap = buildInsertParams(valuesMap);
			paramsList.add(paramsMap);
		}
		
		SQLExcutor.excuteBatch(createInsertSQL(), paramsList , this.datasource);
	}
	
	protected String createInsertSQL() {
		String valueTags = "", fieldTags = "";
		for(String field : this.fieldCodes) {
			valueTags += "?,";
			fieldTags += field + ",";
		}
		String insertSQL = "insert into " + this.table + "(" + fieldTags + "createtime,creator,version) " +
				" values (" + valueTags + " ?, ?, ?)";
		return insertSQL;
	}

	public void update(Integer id, Map<String, String> valuesMap) {
		Map<String, Object> old = get(id);
		if( old == null ) {
			throw new BusinessException("修改出错，该记录不存在，可能已经被删除。");
		}
		
		Map<Integer, Object> paramsMap = new HashMap<Integer, Object>();
		int index = 0;
		String tags = "";
		for(String field : this.fieldCodes) {
			Object value = valuesMap.get(field);
			if( value != null ) {
				value = _Util.preTreatValue((String)value, fieldTypes.get(index));
			} else {
				value = null;
			}
			
			paramsMap.put(++index, value);
			tags += field + "=?, ";
		}
		paramsMap.put(++index, new Timestamp(new Date().getTime()));
		paramsMap.put(++index, Environment.getUserCode());
		paramsMap.put(++index, id);
		
		String updateSQL = "update " + this.table + " set " + tags + "updatetime=?, updator=?, version=version+1 where id=?";
		SQLExcutor.excute(updateSQL, paramsMap, this.datasource);
		
		if(this.needLog) { // 记录修改日志
			Log excuteLog = new Log(recordName + ", " + id, "\n修改前： " + old + " \n修改后： " + valuesMap);
	    	excuteLog.setOperateTable("数据录入修改");
	        ((IBusinessLogger) Global.getBean("BusinessLogger")).output(excuteLog);
		}
	}

	private Map<String, Object> get(Integer id) {
		String fieldTags = "";
		for(String field : this.fieldCodes) {
			fieldTags += field + ",";
		}
		String sql = "select " + fieldTags + "creator from " + this.table + " where id=?";
		List<Map<String, Object>> list = SQLExcutor.query(this.datasource, sql, id);
		if( EasyUtils.isNullOrEmpty(list) ) {
			return null;
		}
		return list.get(0);
	}

	public void delete(Integer id) {
		Map<String, Object> old = get(id);
		
		String updateSQL = "delete from " + this.table + " where id=" + id;
		SQLExcutor.excute(updateSQL, this.datasource);
		
		// 记录删除日志
		Log excuteLog = new Log(recordName + ", " + id, Environment.getUserCode() + "删除了记录：" + old );
    	excuteLog.setOperateTable("数据录入删除");
        ((IBusinessLogger) Global.getBean("BusinessLogger")).output(excuteLog);
	}
	
	public SQLExcutor select() {
		 return this.select(1, 100, new HashMap<String, String>());
	}

	public SQLExcutor select(int page, int pagesize, Map<String, String> params) {
		Map<Integer, Object> paramsMap = new HashMap<Integer, Object>();
		paramsMap.put(1, Environment.getUserCode());
		
		if(params == null) {
			params = new HashMap<String, String>();
		}
		
		// 增加权限控制，针对有編輯权限的允許查看他人录入数据, '000' <> ? <==> 忽略创建人这个查询条件
		boolean visible = DMConstants.isAdmin();
		try {
			List<String> permissions = PermissionHelper.getInstance().getOperationsByResource(recordId,
	                RecordPermission.class.getName(), RecordResource.class);
			visible = visible || permissions.contains(Record.OPERATION_VDATA) 
					|| permissions.contains(Record.OPERATION_EDATA);
		} catch(Exception e) {
		}
		String condition;
		if( visible && !params.containsKey("creator") ) {
			condition = " '000' <> ? ";
		} else {
			condition = " creator = ? ";
		}
		
		for(String key : params.keySet()) {
			String valueStr = params.get(key);
			if(EasyUtils.isNullOrEmpty(valueStr)) continue;
			
			Object value;
			int fieldIndex = this.fieldCodes.indexOf(key);
			if(fieldIndex >= 0) {
				String paramType = this.fieldTypes.get(fieldIndex);
				value = _Util.preTreatValue(valueStr, paramType);
			}
			else {
				value = valueStr;
			}
			
			if( "creator".equals(key) ) {
				paramsMap.put(1, value);  // 替换登录账号，允许查询其它人创建的数据; 
			}
			
			if(this.fieldCodes.contains(key) || "updator".equals(key)) {
				condition += " and " + key + " = ? ";
				paramsMap.put(paramsMap.size() + 1, value);
			}
		}
		
		if( !EasyUtils.isNullOrEmpty(this.customizeTJ) ) {
			condition += " and " + _Util.customizeParse(this.customizeTJ);
		}
		String selectSQL = "select " + EasyUtils.list2Str(this.fieldCodes) + 
					",createtime,creator,updatetime,updator,version,id from " + this.table + 
					" where " + condition + " order by id desc ";
		
		SQLExcutor ex = new SQLExcutor(false);
		ex.excuteQuery(selectSQL, paramsMap, page, pagesize, this.datasource);
		
		return ex;
	}

	public Document getGridTemplate() {
		StringBuffer sb = new StringBuffer();
        sb.append("<grid><declare sequence=\"true\">");
        
        int index = 0; 
        for(String filed : fieldNames) {
            sb.append("<column name=\"" + fieldCodes.get(index++) + "\" mode=\"string\" caption=\"" + filed + "\" />");
        }
        sb.append("<column name=\"createtime\" mode=\"string\" caption=\"创建时间\" />");
        sb.append("<column name=\"creator\" mode=\"string\" caption=\"创建人\" />");
        sb.append("<column name=\"updatetime\" mode=\"string\" caption=\"最后更新时间\" />");
        sb.append("<column name=\"updator\" mode=\"string\" caption=\"最后更新人\" />");
        sb.append("<column name=\"version\" mode=\"string\" caption=\"修改次数\" />");
        sb.append("<column name=\"id\" display=\"none\"/>");
        
        sb.append("</declare><data></data></grid>");
        
    	return XMLDocUtil.dataXml2Doc(sb.toString());
	}
	
	public List<Map<Object, Object>> getFields() {
		return this.fields;
	}
	
	private static Map<String, String> dsMappingType = new HashMap<String, String>();
	
	public static String getDBType(String datasource) {
		String result = dsMappingType.get(datasource);
		if(result != null) return result;
		
		Pool connpool = JCache.getInstance().getPool(datasource);
        Cacheable connItem = connpool.checkOut(0);
        Connection conn = (Connection) connItem.getValue();
        
		try {
			String driveName = conn.getMetaData().getDriverName();
			log.debug(" database diverName: 【 " + driveName + "】。");
			
			for(String type : DB_TYPE) {
				if (driveName.startsWith(type)) {
					dsMappingType.put(datasource, result = type);
		            return result;
		        }
			}
		} catch (SQLException e) {
			
		} finally {
            connpool.checkIn(connItem); // 返回连接到连接池
        }
        
        return null;
	}
	
	public static String[] DB_TYPE = new String[] {"MySQL", "Oracle", "H2"};
	
	public static _Database getDB(Record record) {
		String type = getDBType(record.getDatasource());
		return getDB(type, record);
	}
	
	public static _Database getDB(String type, Record record) {
		if(DB_TYPE[0].equals(type)) {
			return new _MySQL(record);
		}
		else if(DB_TYPE[1].equals(type)) {
			return new _Oracle(record);
		}
		else {
			return new _H2(record);
		}
	}
}
