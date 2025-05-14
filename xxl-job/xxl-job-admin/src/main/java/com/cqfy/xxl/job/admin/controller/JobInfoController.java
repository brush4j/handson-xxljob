package com.cqfy.xxl.job.admin.controller;

import com.cqfy.xxl.job.admin.core.exception.XxlJobException;
import com.cqfy.xxl.job.admin.core.model.XxlJobGroup;
import com.cqfy.xxl.job.admin.core.model.XxlJobInfo;
import com.cqfy.xxl.job.admin.core.model.XxlJobUser;
import com.cqfy.xxl.job.admin.core.route.ExecutorRouteStrategyEnum;
import com.cqfy.xxl.job.admin.core.scheduler.MisfireStrategyEnum;
import com.cqfy.xxl.job.admin.core.scheduler.ScheduleTypeEnum;
import com.cqfy.xxl.job.admin.core.thread.JobScheduleHelper;
import com.cqfy.xxl.job.admin.core.thread.JobTriggerPoolHelper;
import com.cqfy.xxl.job.admin.core.trigger.TriggerTypeEnum;
import com.cqfy.xxl.job.admin.core.util.I18nUtil;
import com.cqfy.xxl.job.admin.dao.XxlJobGroupDao;
import com.cqfy.xxl.job.admin.service.LoginService;
import com.cqfy.xxl.job.admin.service.XxlJobService;
import com.cqfy.xxl.job.core.biz.model.ReturnT;
import com.cqfy.xxl.job.core.enums.ExecutorBlockStrategyEnum;
import com.cqfy.xxl.job.core.glue.GlueTypeEnum;
import com.cqfy.xxl.job.core.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/3
 * @Description:该类对应的是jobInfo这个前端页面，其实就是任务管理页面，对这个页面操作的方法，都在这个类中了
 * 大家可以仔细看一下，找一找每个网页操作对应的方法
 * 朋友们，Controller类中的逻辑都很简单，所以我就不添加详细注释了，复杂的逻辑都早service中，到那里面我们在具体讲解
 */
@Controller
@RequestMapping("/jobinfo")
public class JobInfoController {
	private static Logger logger = LoggerFactory.getLogger(JobInfoController.class);

	@Resource
	private XxlJobGroupDao xxlJobGroupDao;
	@Resource
	private XxlJobService xxlJobService;


	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/11
	 * @Description:查询该界面需要的所有数据
	 */
	@RequestMapping
	public String index(HttpServletRequest request, Model model, @RequestParam(required = false, defaultValue = "-1") int jobGroup) {
		model.addAttribute("ExecutorRouteStrategyEnum", ExecutorRouteStrategyEnum.values());
		model.addAttribute("GlueTypeEnum", GlueTypeEnum.values());
		model.addAttribute("ExecutorBlockStrategyEnum", ExecutorBlockStrategyEnum.values());
		model.addAttribute("ScheduleTypeEnum", ScheduleTypeEnum.values());
		model.addAttribute("MisfireStrategyEnum", MisfireStrategyEnum.values());
		List<XxlJobGroup> jobGroupList_all =  xxlJobGroupDao.findAll();
		List<XxlJobGroup> jobGroupList = filterJobGroupByRole(request, jobGroupList_all);
		if (jobGroupList==null || jobGroupList.size()==0) {
			throw new XxlJobException(I18nUtil.getString("jobgroup_empty"));
		}
		model.addAttribute("JobGroupList", jobGroupList);
		model.addAttribute("jobGroup", jobGroup);
		return "jobinfo/jobinfo.index";
	}



	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/11
	 * @Description:根据用户角色查找执行器的方法
	 */
	public static List<XxlJobGroup> filterJobGroupByRole(HttpServletRequest request, List<XxlJobGroup> jobGroupList_all){
		List<XxlJobGroup> jobGroupList = new ArrayList<>();
		if (jobGroupList_all!=null && jobGroupList_all.size()>0) {
			XxlJobUser loginUser = (XxlJobUser) request.getAttribute(LoginService.LOGIN_IDENTITY_KEY);
			if (loginUser.getRole() == 1) {
				jobGroupList = jobGroupList_all;
			} else {
				List<String> groupIdStrs = new ArrayList<>();
				if (loginUser.getPermission()!=null && loginUser.getPermission().trim().length()>0) {
					groupIdStrs = Arrays.asList(loginUser.getPermission().trim().split(","));
				}
				for (XxlJobGroup groupItem:jobGroupList_all) {
					if (groupIdStrs.contains(String.valueOf(groupItem.getId()))) {
						jobGroupList.add(groupItem);
					}
				}
			}
		}
		return jobGroupList;
	}


	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/11
	 * @Description:校验当前用户是否有某个执行器的权限
	 */
	public static void validPermission(HttpServletRequest request, int jobGroup) {
		XxlJobUser loginUser = (XxlJobUser) request.getAttribute(LoginService.LOGIN_IDENTITY_KEY);
		if (!loginUser.validPermission(jobGroup)) {
			throw new RuntimeException(I18nUtil.getString("system_permission_limit") + "[username="+ loginUser.getUsername() +"]");
		}
	}


	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/11
	 * @Description:分页查询定时任务
	 */
	@RequestMapping("/pageList")
	@ResponseBody
	public Map<String, Object> pageList(@RequestParam(required = false, defaultValue = "0") int start,  
			@RequestParam(required = false, defaultValue = "10") int length,
			int jobGroup, int triggerStatus, String jobDesc, String executorHandler, String author) {
		return xxlJobService.pageList(start, length, jobGroup, triggerStatus, jobDesc, executorHandler, author);
	}


	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/11
	 * @Description:新增一个定时任务
	 */
	@RequestMapping("/add")
	@ResponseBody
	public ReturnT<String> add(XxlJobInfo jobInfo) {
		return xxlJobService.add(jobInfo);
	}


	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/11
	 * @Description:更新定时任务
	 */
	@RequestMapping("/update")
	@ResponseBody
	public ReturnT<String> update(XxlJobInfo jobInfo) {
		return xxlJobService.update(jobInfo);
	}


	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/11
	 * @Description:删除定时任务
	 */
	@RequestMapping("/remove")
	@ResponseBody
	public ReturnT<String> remove(int id) {
		return xxlJobService.remove(id);
	}


	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/11
	 * @Description:停止定时任务
	 */
	@RequestMapping("/stop")
	@ResponseBody
	public ReturnT<String> pause(int id) {
		return xxlJobService.stop(id);
	}


	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/11
	 * @Description:启动定时任务
	 */
	@RequestMapping("/start")
	@ResponseBody
	public ReturnT<String> start(int id) {
		return xxlJobService.start(id);
	}


	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/11
	 * @Description:只执行一次定时任务
	 */
	@RequestMapping("/trigger")
	@ResponseBody
	public ReturnT<String> triggerJob(int id, String executorParam, String addressList) {
		// force cover job param
		if (executorParam == null) {
			executorParam = "";
		}
		//这里任务就是手动触发的
		JobTriggerPoolHelper.trigger(id, TriggerTypeEnum.MANUAL, -1, null, executorParam, addressList);
		return ReturnT.SUCCESS;
	}



	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/11
	 * @Description:获取任务下一次的执行时间
	 */
	@RequestMapping("/nextTriggerTime")
	@ResponseBody
	public ReturnT<List<String>> nextTriggerTime(String scheduleType, String scheduleConf) {
		XxlJobInfo paramXxlJobInfo = new XxlJobInfo();
		paramXxlJobInfo.setScheduleType(scheduleType);
		paramXxlJobInfo.setScheduleConf(scheduleConf);
		List<String> result = new ArrayList<>();
		try {
			Date lastTime = new Date();
			for (int i = 0; i < 5; i++) {
				lastTime = JobScheduleHelper.generateNextValidTime(paramXxlJobInfo, lastTime);
				if (lastTime != null) {
					result.add(DateUtil.formatDateTime(lastTime));
				} else {
					break;
				}
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return new ReturnT<List<String>>(ReturnT.FAIL_CODE, (I18nUtil.getString("schedule_type")+I18nUtil.getString("system_unvalid")) + e.getMessage());
		}
		return new ReturnT<List<String>>(result);
	}
}
