package com.cqfy.xxl.job.core.glue;

import com.cqfy.xxl.job.core.glue.impl.SpringGlueFactory;
import com.cqfy.xxl.job.core.handler.IJobHandler;
import groovy.lang.GroovyClassLoader;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/12
 * @Description:运行模式工厂
 */
public class GlueFactory {

	private static GlueFactory glueFactory = new GlueFactory();

	public static GlueFactory getInstance(){
		return glueFactory;
	}

	public static void refreshInstance(int type){
		if (type == 0) {
			glueFactory = new GlueFactory();
		} else if (type == 1) {
			glueFactory = new SpringGlueFactory();
		}
	}

	private GroovyClassLoader groovyClassLoader = new GroovyClassLoader();

	private ConcurrentMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

	//在该方法中创建IJobHandler对象
	public IJobHandler loadNewInstance(String codeSource) throws Exception{
		//对用户在线编辑的源码做判空校验
		if (codeSource!=null && codeSource.trim().length()>0) {
			//把源码转化为Class文件
			Class<?> clazz = getCodeSourceClass(codeSource);
			if (clazz != null) {
				//创建对象
				Object instance = clazz.newInstance();
				if (instance!=null) {
					//public class DemoGlueJobHandler extends IJobHandler {
					//
					//	@Override
					//	public void execute() throws Exception {
					//		XxlJobHelper.log("XXL-JOB, Hello World.");
					//	}
					//
					//}
					//上面是我从xxl-job复制过来的默认例子，可以看到，在新编写的类都要继承IJobHandler抽象类的
					//所以这里要判断一下是否属于这个对象
					if (instance instanceof IJobHandler) {
						//这里其实做的就是属性注入的工作
						this.injectService(instance);
						return (IJobHandler) instance;
					}
					else {
						throw new IllegalArgumentException(">>>>>>>>>>> xxl-glue, loadNewInstance error, "
								+ "cannot convert from instance["+ instance.getClass() +"] to IJobHandler");
					}
				}
			}
		}
		throw new IllegalArgumentException(">>>>>>>>>>> xxl-glue, loadNewInstance error, instance is null");
	}


	private Class<?> getCodeSourceClass(String codeSource){
		try {
			//可以看到，这里其实是用MD5把源码加密成字节
			byte[] md5 = MessageDigest.getInstance("MD5").digest(codeSource.getBytes());
			String md5Str = new BigInteger(1, md5).toString(16);
			//从对应的缓存中查看是否已经缓存了该字节了，如果有就可以直接返回class文件
			Class<?> clazz = CLASS_CACHE.get(md5Str);
			if(clazz == null){
				//如果没有就在这里把源码解析成class文件
				clazz = groovyClassLoader.parseClass(codeSource);
				//键值对缓存到Map中
				CLASS_CACHE.putIfAbsent(md5Str, clazz);
			}
			//返回class文件
			return clazz;
		} catch (Exception e) {
			return groovyClassLoader.parseClass(codeSource);
		}
	}


	public void injectService(Object instance) {

	}

}
