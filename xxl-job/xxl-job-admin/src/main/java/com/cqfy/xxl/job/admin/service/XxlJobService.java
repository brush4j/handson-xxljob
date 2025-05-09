package com.cqfy.xxl.job.admin.service;

import com.cqfy.xxl.job.admin.core.model.XxlJobInfo;
import com.cqfy.xxl.job.core.biz.model.ReturnT;

import java.util.Date;
import java.util.Map;


public interface XxlJobService {


	public Map<String, Object> pageList(int start, int length, int jobGroup, int triggerStatus, String jobDesc, String executorHandler, String author);


	public ReturnT<String> add(XxlJobInfo jobInfo);


	public ReturnT<String> update(XxlJobInfo jobInfo);


	public ReturnT<String> remove(int id);


	public ReturnT<String> start(int id);


	public ReturnT<String> stop(int id);


	public Map<String,Object> dashboardInfo();


	public ReturnT<Map<String,Object>> chartInfo(Date startDate, Date endDate);

}
