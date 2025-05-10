package com.cqfy.xxl.job.admin.controller;

import com.cqfy.xxl.job.admin.core.model.XxlJobInfo;
import com.cqfy.xxl.job.admin.core.model.XxlJobLogGlue;
import com.cqfy.xxl.job.admin.core.util.I18nUtil;
import com.cqfy.xxl.job.admin.dao.XxlJobInfoDao;
import com.cqfy.xxl.job.admin.dao.XxlJobLogGlueDao;
import com.cqfy.xxl.job.core.biz.model.ReturnT;
import com.cqfy.xxl.job.core.glue.GlueTypeEnum;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;


/**
 * @author:Halfmoonly
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/12
 * @Description:如果定时任务是glue模式，需要在前端页面编写代码的化，这个类就是负责在线编辑定时任务的查找和保存。
 */
@Controller
@RequestMapping("/jobcode")
public class JobCodeController {

	@Resource
	private XxlJobInfoDao xxlJobInfoDao;
	@Resource
	private XxlJobLogGlueDao xxlJobLogGlueDao;


	@RequestMapping
	public String index(HttpServletRequest request, Model model, int jobId) {
		XxlJobInfo jobInfo = xxlJobInfoDao.loadById(jobId);
		List<XxlJobLogGlue> jobLogGlues = xxlJobLogGlueDao.findByJobId(jobId);
		if (jobInfo == null) {
			throw new RuntimeException(I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
		}
		if (GlueTypeEnum.BEAN == GlueTypeEnum.match(jobInfo.getGlueType())) {
			throw new RuntimeException(I18nUtil.getString("jobinfo_glue_gluetype_unvalid"));
		}
		JobInfoController.validPermission(request, jobInfo.getJobGroup());
		model.addAttribute("GlueTypeEnum", GlueTypeEnum.values());
		model.addAttribute("jobInfo", jobInfo);
		model.addAttribute("jobLogGlues", jobLogGlues);
		return "jobcode/jobcode.index";
	}

	@RequestMapping("/save")
	@ResponseBody
	public ReturnT<String> save(Model model, int id, String glueSource, String glueRemark) {
		if (glueRemark==null) {
			return new ReturnT<String>(500, (I18nUtil.getString("system_please_input") + I18nUtil.getString("jobinfo_glue_remark")) );
		}
		if (glueRemark.length()<4 || glueRemark.length()>100) {
			return new ReturnT<String>(500, I18nUtil.getString("jobinfo_glue_remark_limit"));
		}
		XxlJobInfo exists_jobInfo = xxlJobInfoDao.loadById(id);
		if (exists_jobInfo == null) {
			return new ReturnT<String>(500, I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
		}
		exists_jobInfo.setGlueSource(glueSource);
		exists_jobInfo.setGlueRemark(glueRemark);
		exists_jobInfo.setGlueUpdatetime(new Date());
		exists_jobInfo.setUpdateTime(new Date());
		xxlJobInfoDao.update(exists_jobInfo);
		XxlJobLogGlue xxlJobLogGlue = new XxlJobLogGlue();
		xxlJobLogGlue.setJobId(exists_jobInfo.getId());
		xxlJobLogGlue.setGlueType(exists_jobInfo.getGlueType());
		xxlJobLogGlue.setGlueSource(glueSource);
		xxlJobLogGlue.setGlueRemark(glueRemark);
		xxlJobLogGlue.setAddTime(new Date());
		xxlJobLogGlue.setUpdateTime(new Date());
		xxlJobLogGlueDao.save(xxlJobLogGlue);
		xxlJobLogGlueDao.removeOld(exists_jobInfo.getId(), 30);
		return ReturnT.SUCCESS;
	}

}
