package com.cqfy.xxl.job.admin.core.thread;

import com.cqfy.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.cqfy.xxl.job.core.biz.model.RegistryParam;
import com.cqfy.xxl.job.core.biz.model.ReturnT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.concurrent.*;

/**
 * @author:Halfmoonly
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/4
 * @Description:该组件会初始化和注册中心相关的线程，大家可以想一想，执行器要注册到服务端，这些工作肯定就需要专门
 * 的线程来工作。而当执行器注册成功之后，如果过了不久就掉线了，也就是心跳检测超时，结果服务器这边不知道，还持有者掉线的执行器的地址
 * 这样一来，远程调用肯定是无法成功的。所以定期检查并清理掉线执行器也需要专门的线程来处理
 * 这两个操作，就是本类的职责
 * 当前的版本我们只需要注册执行器的线程池就可以了，不需要监控执行器是否过期
 */
public class JobRegistryHelper {

	private static Logger logger = LoggerFactory.getLogger(JobRegistryHelper.class);
	//在这里创建本类对象
	private static JobRegistryHelper instance = new JobRegistryHelper();
	//通过该方法把本类对象暴露出去
	public static JobRegistryHelper getInstance(){
		return instance;
	}


	//这个线程池就是用来注册或者溢出执行器地址的
	private ThreadPoolExecutor registryOrRemoveThreadPool = null;


	private volatile boolean toStop = false;

	/**
	 * @author:Halfmoonly
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/4
	 * @Description:创建并启动上面的线程池
	 */
	public void start(){
		//执行注册和移除执行器地址任务的线程池在这里被创建了
		registryOrRemoveThreadPool = new ThreadPoolExecutor(
				2,
				10,
				30L,
				TimeUnit.SECONDS,
				new LinkedBlockingQueue<Runnable>(2000),
				new ThreadFactory() {
					@Override
					public Thread newThread(Runnable r) {
						return new Thread(r, "xxl-job, admin JobRegistryMonitorHelper-registryOrRemoveThreadPool-" + r.hashCode());
					}
				},
				//下面这个是xxl-job定义的线程池拒绝策略，其实就是把被拒绝的任务再执行一遍
				new RejectedExecutionHandler() {
					@Override
					public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
						//在这里能看到，所谓的拒绝，就是把任务再执行一遍
						r.run();
						logger.warn(">>>>>>>>>>> xxl-job, registry or remove too fast, match threadpool rejected handler(run now).");
					}
				});
	}


	/**
	 * @author:Halfmoonly
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/4
	 * @Description:关闭线程池的方法
	 */
	public void toStop(){
		toStop = true;
		registryOrRemoveThreadPool.shutdownNow();

	}




	/**
	 * @author:Halfmoonly
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/4
	 * @Description:注册执行器的方法
	 */
	public ReturnT<String> registry(RegistryParam registryParam) {
		//校验处理
		if (!StringUtils.hasText(registryParam.getRegistryGroup())
				|| !StringUtils.hasText(registryParam.getRegistryKey())
				|| !StringUtils.hasText(registryParam.getRegistryValue())) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, "Illegal Argument.");
		}
		//提交注册执行器的任务给线程池执行
		registryOrRemoveThreadPool.execute(new Runnable() {
			@Override
			public void run() {
				//这里的意思也很简单，就是先根据registryParam参数去数据库中更新相应的数据
				//如果返回的是0，说明数据库中没有相应的信息，该执行器还没注册到注册中心呢，所以下面
				//就可以直接新增这一条数据即可
				int ret = XxlJobAdminConfig.getAdminConfig().getXxlJobRegistryDao().registryUpdate(registryParam.getRegistryGroup(), registryParam.getRegistryKey(), registryParam.getRegistryValue(), new Date());
				if (ret < 1) {
					//这里就是数据库中没有相应数据，直接新增即可
					XxlJobAdminConfig.getAdminConfig().getXxlJobRegistryDao().registrySave(registryParam.getRegistryGroup(), registryParam.getRegistryKey(), registryParam.getRegistryValue(), new Date());
					//该方法从名字上看是刷新注册表信息的意思
					//但是作者还没有实现，源码中就是空的，所以这里我就照搬过来了
					freshGroupRegistryInfo(registryParam);
				}
			}
		});
		return ReturnT.SUCCESS;
	}


	/**
	 * @author:Halfmoonly
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/4
	 * @Description:移除过期的执行器地址
	 */
	public ReturnT<String> registryRemove(RegistryParam registryParam) {
		//校验处理
		if (!StringUtils.hasText(registryParam.getRegistryGroup())
				|| !StringUtils.hasText(registryParam.getRegistryKey())
				|| !StringUtils.hasText(registryParam.getRegistryValue())) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, "Illegal Argument.");
		}
		//将任务提交给线程池来处理
		registryOrRemoveThreadPool.execute(new Runnable() {
			@Override
			public void run() {
				//在这里直接根据registryParam从数据库中删除对应的执行器地址
				//这里的返回结果是删除了几条数据的意思
				int ret = XxlJobAdminConfig.getAdminConfig().getXxlJobRegistryDao().registryDelete(registryParam.getRegistryGroup(), registryParam.getRegistryKey(), registryParam.getRegistryValue());
				if (ret > 0) {
					//上个方法已经讲过了，这里就不再讲了
					freshGroupRegistryInfo(registryParam);
				}
			}
		});
		return ReturnT.SUCCESS;
	}

	/**
	 * @author:Halfmoonly
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/4
	 * @Description:这个方法在源码中就是空的。。作者也没想好要怎么弄呢。
	 */
	private void freshGroupRegistryInfo(RegistryParam registryParam){
	}


}
