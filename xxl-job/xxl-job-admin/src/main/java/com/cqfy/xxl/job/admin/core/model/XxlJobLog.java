package com.cqfy.xxl.job.admin.core.model;

import java.util.Date;

/**
 * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyang。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/11
 * @Description:这是和日志有关的实体类，第一版本还用不到。
 */
public class XxlJobLog {

	//日志id
	private long id;
	//执行器id
	private int jobGroup;
	//定时任务id
	private int jobId;
	//执行器地址
	private String executorAddress;
	//封装定时任务的JobHandler的名称
	private String executorHandler;
	//执行器参数
	private String executorParam;
	//执行器分片参数
	private String executorShardingParam;
	//失败充实次数
	private int executorFailRetryCount;
	//触发器触发时间
	private Date triggerTime;
	//触发器任务的响应码
	private int triggerCode;
	//触发器任务的具体结果
	private String triggerMsg;
	//定时任务执行时间
	private Date handleTime;
	//执行的响应码
	private int handleCode;
	//执行的具体结果
	private String handleMsg;
	//警报的状态码 0是默认，1是不需要报警，2是报警成功，3是报警失败
	private int alarmStatus;



	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public int getJobGroup() {
		return jobGroup;
	}

	public void setJobGroup(int jobGroup) {
		this.jobGroup = jobGroup;
	}

	public int getJobId() {
		return jobId;
	}

	public void setJobId(int jobId) {
		this.jobId = jobId;
	}

	public String getExecutorAddress() {
		return executorAddress;
	}

	public void setExecutorAddress(String executorAddress) {
		this.executorAddress = executorAddress;
	}

	public String getExecutorHandler() {
		return executorHandler;
	}

	public void setExecutorHandler(String executorHandler) {
		this.executorHandler = executorHandler;
	}

	public String getExecutorParam() {
		return executorParam;
	}

	public void setExecutorParam(String executorParam) {
		this.executorParam = executorParam;
	}

	public String getExecutorShardingParam() {
		return executorShardingParam;
	}

	public void setExecutorShardingParam(String executorShardingParam) {
		this.executorShardingParam = executorShardingParam;
	}

	public int getExecutorFailRetryCount() {
		return executorFailRetryCount;
	}

	public void setExecutorFailRetryCount(int executorFailRetryCount) {
		this.executorFailRetryCount = executorFailRetryCount;
	}

	public Date getTriggerTime() {
		return triggerTime;
	}

	public void setTriggerTime(Date triggerTime) {
		this.triggerTime = triggerTime;
	}

	public int getTriggerCode() {
		return triggerCode;
	}

	public void setTriggerCode(int triggerCode) {
		this.triggerCode = triggerCode;
	}

	public String getTriggerMsg() {
		return triggerMsg;
	}

	public void setTriggerMsg(String triggerMsg) {
		this.triggerMsg = triggerMsg;
	}

	public Date getHandleTime() {
		return handleTime;
	}

	public void setHandleTime(Date handleTime) {
		this.handleTime = handleTime;
	}

	public int getHandleCode() {
		return handleCode;
	}

	public void setHandleCode(int handleCode) {
		this.handleCode = handleCode;
	}

	public String getHandleMsg() {
		return handleMsg;
	}

	public void setHandleMsg(String handleMsg) {
		this.handleMsg = handleMsg;
	}

	public int getAlarmStatus() {
		return alarmStatus;
	}

	public void setAlarmStatus(int alarmStatus) {
		this.alarmStatus = alarmStatus;
	}

}
