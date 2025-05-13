package com.cqfy.xxl.job.admin.controller.resolver;

import com.cqfy.xxl.job.admin.core.exception.XxlJobException;
import com.cqfy.xxl.job.admin.core.util.JacksonUtil;
import com.cqfy.xxl.job.core.biz.model.ReturnT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author:Halfmoonly
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/11
 * @Description:异常解析器
 */
@Component
public class WebExceptionResolver implements HandlerExceptionResolver {

	private static transient Logger logger = LoggerFactory.getLogger(WebExceptionResolver.class);

	@Override
	public ModelAndView resolveException(HttpServletRequest request,
			HttpServletResponse response, Object handler, Exception ex) {
		if (!(ex instanceof XxlJobException)) {
			logger.error("WebExceptionResolver:{}", ex);
		}
		boolean isJson = false;
		if (handler instanceof HandlerMethod) {
			HandlerMethod method = (HandlerMethod)handler;
			ResponseBody responseBody = method.getMethodAnnotation(ResponseBody.class);
			if (responseBody != null) {
				isJson = true;
			}
		}
		ReturnT<String> errorResult = new ReturnT<String>(ReturnT.FAIL_CODE, ex.toString().replaceAll("\n", "<br/>"));
		ModelAndView mv = new ModelAndView();
		if (isJson) {
			try {
				response.setContentType("application/json;charset=utf-8");
				response.getWriter().print(JacksonUtil.writeValueAsString(errorResult));
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
			return mv;
		} else {
			mv.addObject("exceptionMsg", errorResult.getMsg());
			mv.setViewName("/common/common.exception");
			return mv;
		}
	}
}