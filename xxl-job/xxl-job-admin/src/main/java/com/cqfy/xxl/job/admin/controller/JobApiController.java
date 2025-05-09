//package com.cqfy.xxl.job.admin.controller;
//
//import com.cqfy.xxl.job.admin.controller.annotation.PermissionLimit;
//import com.cqfy.xxl.job.admin.core.conf.XxlJobAdminConfig;
//import com.cqfy.xxl.job.core.biz.AdminBiz;
//import com.cqfy.xxl.job.core.biz.model.RegistryParam;
//import com.cqfy.xxl.job.core.biz.model.ReturnT;
//import com.cqfy.xxl.job.core.util.GsonTool;
//import com.cqfy.xxl.job.core.util.XxlJobRemotingUtil;
//import org.springframework.stereotype.Controller;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.ResponseBody;
//
//import javax.annotation.Resource;
//import javax.servlet.http.HttpServletRequest;
//
///**
// * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyang。
// * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
// * @Date:2023/7/11
// * @Description:这个类不对web界面开放，而是程序内部执行远程调用时使用的，这个类中的接口是对执行器那一端暴露的
// */
//@Controller
//@RequestMapping("/api")
//public class JobApiController {
//
//    @Resource
//    private AdminBiz adminBiz;
//
//    /**
//     * @author:B站UP主陈清风扬，从零带你写框架系列教程的作者，个人微信号：chenqingfengyang。
//     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
//     * @Date:2023/7/11
//     * @Description:该方法就是执行注册执行器的方法，执行器那一端会访问下面这个接口，进行注册
//     * 其实该方法内还有其他功能，这里暂时只引入注册功能
//     */
//    @RequestMapping("/{uri}")
//    @ResponseBody
//    @PermissionLimit(limit=false)
//    public ReturnT<String> api(HttpServletRequest request, @PathVariable("uri") String uri, @RequestBody(required = false) String data) {
//        //判断是不是post请求
//        if (!"POST".equalsIgnoreCase(request.getMethod())) {
//            return new ReturnT<String>(ReturnT.FAIL_CODE, "invalid request, HttpMethod not support.");
//        }
//        //对路径做判空处理
//        if (uri==null || uri.trim().length()==0) {
//            return new ReturnT<String>(ReturnT.FAIL_CODE, "invalid request, uri-mapping empty.");
//        }
//        //判断执行器配置的token和调度中心的是否相等
//        if (XxlJobAdminConfig.getAdminConfig().getAccessToken()!=null
//                && XxlJobAdminConfig.getAdminConfig().getAccessToken().trim().length()>0
//                && !XxlJobAdminConfig.getAdminConfig().getAccessToken().equals(request.getHeader(XxlJobRemotingUtil.XXL_JOB_ACCESS_TOKEN))) {
//            return new ReturnT<String>(ReturnT.FAIL_CODE, "The access token is wrong.");
//        }
//        //判断是不是注册操作
//        else if ("registry".equals(uri)) {
//            RegistryParam registryParam = GsonTool.fromJson(data, RegistryParam.class);
//            //执行注册任务
//            return adminBiz.registry(registryParam);
//            //判断是不是从调度中心移除执行器的操作
//        } else if ("registryRemove".equals(uri)) {
//            RegistryParam registryParam = GsonTool.fromJson(data, RegistryParam.class);
//            //执行移除任务
//            return adminBiz.registryRemove(registryParam);
//        } else {
//            //都不匹配则返回失败
//            return new ReturnT<String>(ReturnT.FAIL_CODE, "invalid request, uri-mapping("+ uri +") not found.");
//        }
//
//    }
//
//}
