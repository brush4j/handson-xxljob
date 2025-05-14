接下来就是定时任务超时功能的实现，其实这个功能也没什么可说的，就是用户创建定时任务的时候，也许已经考虑到了任务执行比较耗时的情况，不管是什么原因吧，用户给要调度的定时任务设置了一个超时时间，比如说这个定时任务只能在3秒内执行完，超过3秒还没有执行完就算是超时了。

这个超时时间是用户在web界面设定的，会被保存到XxlJobInfo对象中，并且存储到数据库中 。

定时任务调度的时候，这个超时时间会被封装到TriggerParam对象中发送给执行器这一端。而执行器这一端得到定时任务的超时时间后，就会采取相应的措施，请看下面的代码块。
```java
public class JobThread extends Thread{

    //其他方法省略


    //在最核心的run方法中，会多出来一段代码
    @Override
	public void run() {
        /其他的逻辑省略
        
        //如果设置了超时时间，就要设置一个新的线程来执行定时任务
		if (triggerParam.getExecutorTimeout() > 0) {
			Thread futureThread = null;
			try {
				FutureTask<Boolean> futureTask = new FutureTask<Boolean>(new Callable<Boolean>() {
					@Override
					public Boolean call() throws Exception {
						//子线程可以访问父线程的本地变量
						XxlJobContext.setXxlJobContext(xxlJobContext);
						//在FutureTask中执行定时任务
						handler.execute();
						return true;
					}
				});
				//创建线程并且启动线程
				futureThread = new Thread(futureTask);
				futureThread.start();
				//最多等待用户设置的超时时间
				Boolean tempResult = futureTask.get(triggerParam.getExecutorTimeout(), TimeUnit.SECONDS);
			} catch (TimeoutException e) {
				XxlJobHelper.log("<br>----------- xxl-job job execute timeout");
				XxlJobHelper.log(e);
				//超时直接设置任务执行超时
				XxlJobHelper.handleTimeout("job execute timeout ");
			} finally {
				futureThread.interrupt();
			}
		}else {
			//没有设置超时时间，通过反射执行了定时任务，终于在这里执行了
			handler.execute();
		}
    }
    
}
```

通过创建一个FutureTask来执行定时任务，然后让一个新的线程来执行这个FutureTask。

在超时时间之内没有获得执行结果，就意味着定时任务超时了。这时候程序就会走到catch块中，将定时任务的执行结果设置为失败。这就是定时任务超时的简单逻辑。


需要注意的是，XxlJobContext上下文要用InheritableThreadLocal类型，才能在子线程中访问父线程中的线程变量。  
```java
private static InheritableThreadLocal<XxlJobContext> contextHolder = new InheritableThreadLocal<XxlJobContext>();
```

因为子线程记录文件日志的时候XxlJobHelper要用到XxlJobContext：
```java
    /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/17
     * @Description:把定时任务的日志存储到日志文件中的方法
     */
    private static boolean logDetail(StackTraceElement callInfo, String appendLog) {
        //从当前线程中获得定时任务上下文对象
        XxlJobContext xxlJobContext = XxlJobContext.getXxlJobContext();
        if (xxlJobContext == null) {
            return false;
        }
        StringBuffer stringBuffer = new StringBuffer();
        //在这里把方法调用的详细信息拼接一下
        stringBuffer.append(DateUtil.formatDateTime(new Date())).append(" ")
                .append("["+ callInfo.getClassName() + "#" + callInfo.getMethodName() +"]").append("-")
                .append("["+ callInfo.getLineNumber() +"]").append("-")
                .append("["+ Thread.currentThread().getName() +"]").append(" ")
                .append(appendLog!=null?appendLog:"");
        //转换成字符串
        String formatAppendLog = stringBuffer.toString();
        //获取定时任务对应的日志存储路径
        String logFileName = xxlJobContext.getJobLogFileName();
        if (logFileName!=null && logFileName.trim().length()>0) {
            //真正存储日志的方法，在这里就把日志存储到本地文件了
            XxlJobFileAppender.appendLog(logFileName, formatAppendLog);
            return true;
        } else {
            logger.info(">>>>>>>>>>> {}", formatAppendLog);
            return false;
        }
    }
```

## 本节测试

启动admin服务

启动sample服务

会发现执行器地址已经注册到执行器注册表`xxl_job_registry`中了
```sql
id;registry_group;registry_key;registry_value;update_time
1;EXECUTOR;xxl-job-executor-sample;http://:9999/;2025-05-09 14:07:27
5;EXECUTOR;xxl-job-executor-sample;http://10.77.182.251:9999/;2025-05-12 10:49:13
```

本节你可以删除xxl_job_group表中的address_list地址字段值，来验证调度中心能够自动的同步xxl_job_registry执行器的地址并注册到到xxl_job_group

自己定义个超时任务，Thread.sleep(10000);
```java
    /**
     * 1、超时任务示例（Bean模式）
     */
    @XxlJob("demoJobHandlerOutTime")
    public void demoJobHandlerOutTime() throws Exception {
        //设置超时10秒
        Thread.sleep(10000);
        for (int i = 0; i < 5; i++) {
            System.out.println("第"+i+"次");
        }
        System.out.println("下一次任务开始了！");
    }
```
同时在admin管理页面新建demoJobHandlerOutTime并指定超时时间为9s（小于10秒即可），手动执行一次demoJobHandlerOutTime，就会发现断点来到了下面这行
```java
    } catch (TimeoutException e) {
        XxlJobHelper.log("<br>----------- xxl-job job execute timeout");
        XxlJobHelper.log(e);
        //超时直接设置任务执行超时
        XxlJobHelper.handleTimeout("job execute timeout ");
    } finally {
        futureThread.interrupt();
    }
```
超时日志也被记录在本地文件中：
```shell
xxl.job.executor.logpath=/Users/chenqingyang/code/my-xxl-job/handlerlog
```

日志内容如下
```shell
2025-05-14 15:32:32 [com.cqfy.xxl.job.core.thread.JobThread#run]-[173]-[xxl-job, JobThread-6-1747207885664] <br>----------- xxl-job job execute start -----------<br>----------- Param:
2025-05-14 15:33:01 [com.cqfy.xxl.job.core.thread.JobThread#run]-[194]-[xxl-job, JobThread-6-1747207885664] <br>----------- xxl-job job execute timeout
2025-05-14 15:33:01 [com.cqfy.xxl.job.core.thread.JobThread#run]-[195]-[xxl-job, JobThread-6-1747207885664] java.util.concurrent.TimeoutException
	at java.base/java.util.concurrent.FutureTask.get(FutureTask.java:204)
	at com.cqfy.xxl.job.core.thread.JobThread.run(JobThread.java:192)

2025-05-14 15:33:01 [com.cqfy.xxl.job.core.thread.JobThread#run]-[224]-[xxl-job, JobThread-6-1747207885664] <br>----------- xxl-job job execute end(finish) -----------<br>----------- Result: handleCode=502, handleMsg = job execute timeout 
2025-05-14 15:33:01 [com.cqfy.xxl.job.core.thread.TriggerCallbackThread#callbackLog]-[224]-[xxl-job, executor TriggerCallbackThread] <br>----------- xxl-job job callback finish.

```

