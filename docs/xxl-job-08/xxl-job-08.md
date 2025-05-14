这一章我将继续为大家讲解xxl-job的另一个功能，那就是执行器执行完定时任务后的结果回调，当然，是回调给调度中心那一端的。

之所以完善这个功能，是因为在之前调度中心调度定时任务之后，根本不知道定时任务是否调度成功了。

只有把这个功能添加上了，调度中心的调度流程才算真正完整了。调度中心发起任务调度，执行器接收到调度中心发送过来的要调度的定时任务的信息后，开始执行定时任务。定时任务执行完毕后，把定时任务执行成功与否的结果回调给调度中心那一端。

说是回调，其实就是在执行器那一端有一个负责回调执行结果给调度中心的线程，定时任务的执行结果会被封装到HandleCallbackParam对象中，回调线程就负责把这个对象通过http协议发送给调度中心。

而调度中心在接收到这个结果后，会进一步更新数据库中定时任务执行的日志信息。说了这么多，我们还是尽快看一下代码的流程吧。

## 原来的调度中心的触发器
好了，让我们先来看看在没有添加执行结果回调功能之前的代码是什么样子的。
```java
	//该方法内进行远程调用
    public static ReturnT<String> runExecutor(TriggerParam triggerParam, String address){
        ReturnT<String> runResult = null;
        try {
            //获取一个用于远程调用的客户端对象，一个地址就对应着一个客户端，为什么说是客户端，因为远程调用的时候，执行器
            //就成为了服务端，因为执行器要接收来自客户端的调用消息
            ExecutorBiz executorBiz = XxlJobScheduler.getExecutorBiz(address);
            //客户端获得之后，就在run方法内进行远程调用了
            runResult = executorBiz.run(triggerParam);
        } catch (Exception e) {
            logger.error(">>>>>>>>>>> xxl-job trigger error, please check if the executor[{}] is running.", address, e);
            runResult = new ReturnT<String>(ReturnT.FAIL_CODE, ThrowableUtil.toString(e));
        }
        //在这里拼接一下远程调用返回的状态码和消息
        StringBuffer runResultSB = new StringBuffer(I18nUtil.getString("jobconf_trigger_run") + "：");
        runResultSB.append("<br>address：").append(address);
        runResultSB.append("<br>code：").append(runResult.getCode());
        runResultSB.append("<br>msg：").append(runResult.getMsg());
        runResult.setMsg(runResultSB.toString());
        return runResult;
    }
```

在XxlJobTrigger类的runExecutor方法中执行远程调用。而在执行远程调用之前，程序中什么都没有做，比如本次执行的定时任务的执行日志，这些信息都没有收集，更别说记录到数据库中了。

而executorBiz.run(triggerParam)这行代码返回的结果，其实也很简单，到执行器那一端一看便知。请看下面的代码。

## 原来的执行器
```java
//在这个类run方法中返回了定时任务执行的结果
public class ExecutorBizImpl implements ExecutorBiz {

     @Override
    public ReturnT<String> run(TriggerParam triggerParam) {

        //省略部分内容

        
        //如果走到这里，不管上面是什么情况吧，总之jobThread肯定存在了，所以直接把要调度的任务放到这个线程内部的队列中
        //等待线程去调用，返回一个结果
        ReturnT<String> pushResult = jobThread.pushTriggerQueue(triggerParam);
        return pushResult;
    }

}
```
在上面的代码块中，可以看到，在run方法中，最后只是把要执行的定时任务的信息参数对象放到了定时任务对应的工作线程的内部队列中，然后就返回一个结果了。而这个结果代表什么呢？请看下面的代码块。
```java
public ReturnT<String> pushTriggerQueue(TriggerParam triggerParam) {
		//在这里放进队列中
		triggerQueue.add(triggerParam);
		//返回成功结果
        return ReturnT.SUCCESS;
}
```
在JobThread类的pushTriggerQueue方法中，可以看到只要把定时任务的信息对象放进内部队列中，就会返回一个成功的结果。 然后这个结果就会被Netty的服务器返回给调度中心。

所以，到这里大家应该也可以看出来，目前的调度中心每一次调度任务返回的结果只能说明触发成功还是触发失败，并不能说明执行成功，

触发成功仅仅意味着这个定时任务信息被放进对应的工作线程的内部队列中了。至于这个定时任务有没有执行成功，调度中心是不知道的。

## 重构调度中心，记录任务执行日志
首先，有一点必须要意识到，定时任务既然是在执行器那一端执行的，那么执行的结果就必须得从执行器那一端返回。

可是，从执行器那一端返回的执行结果返回给调度中心之后，调度中心怎么知道这个结果是属于哪个定时任务的呢？ 

分析到这里，想必大家也都意识到了，肯定要给定时任务附加一个唯一标识，而且现成的就有一个，那就是定时任务在数据库中的主键ID。

定时任务的信息会封装到XxlJobInfo对象中，该对象在记录到数据库后，其内部的主键ID属性就会被赋值，这样一来，每一个定时任务都会对应一个主键ID。

定时任务的主键ID会被调度中心发送给执行器，执行器执行完定时任务，得到的那个执行结果也会和定时任务的主键ID一起封装到HandleCallbackParam对象中，然后通过回调线程发送给调度中心。

调度中心在接收到HandleCallbackParam对象后，根据里面的定时任务ID就可以知道这个执行结果是属于哪个定时任务的了。

首先是重构之后的调度中心。
```java
public class XxlJobTrigger {

    
    private static void processTrigger(XxlJobGroup group, XxlJobInfo jobInfo, int finalFailRetryCount, TriggerTypeEnum triggerType, int index, int total){
        //得到当前要调度的执行任务的路由策略，默认是没有
        ExecutorRouteStrategyEnum executorRouteStrategyEnum = ExecutorRouteStrategyEnum.match(jobInfo.getExecutorRouteStrategy(), null);
        //这里就要开始执行和定时任务日志相关的操作了
        //先创建一个日志对象，用于记录该定时任务执行是的一些信息
        XxlJobLog jobLog = new XxlJobLog();
        //记录定时任务的执行器组id
        jobLog.setJobGroup(jobInfo.getJobGroup());
        //设置定时任务的id
        jobLog.setJobId(jobInfo.getId());
        //设置定时任务的触发时间
        jobLog.setTriggerTime(new Date());
        //在这里把定时任务日志保存到数据库中，保存成功之后，定时任务日志的id也就有了
        XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().save(jobLog);
        logger.debug(">>>>>>>>>>> xxl-job trigger start, jobId:{}", jobLog.getId());
        //初始化触发器参数，这里的这个触发器参数，是要在远程调用的另一端，也就是执行器那一端使用的
        TriggerParam triggerParam = new TriggerParam();
        //设置任务id
        triggerParam.setJobId(jobInfo.getId());
        //设置执行器要执行的任务的方法名称
        triggerParam.setExecutorHandler(jobInfo.getExecutorHandler());
        //把执行器要执行的任务的参数设置进去
        triggerParam.setExecutorParams(jobInfo.getExecutorParam());
        //定时任务的路由策略设置进去
        triggerParam.setExecutorBlockStrategy(jobInfo.getExecutorBlockStrategy());
        //设置定时任务的日志id
        triggerParam.setLogId(jobLog.getId());
        //设置定时任务的触发时间，这个触发时间就是jobLog刚才设置的那个时间
        triggerParam.setLogDateTime(jobLog.getTriggerTime().getTime());
        //设置执行模式，一般都是bean模式
        triggerParam.setGlueType(jobInfo.getGlueType());
        //接下来要再次设定远程调用的服务实例的地址
        //这里其实是考虑到了路由策略
        String address = null;
        ReturnT<String> routeAddressResult = null;
        //得到所有注册到服务端的执行器的地址，并且做判空处理
        List<String> registryList = group.getRegistryList();
        if (registryList!=null && !registryList.isEmpty()) {
            //在这里根据路由策略获得最终选用的执行器地址
            routeAddressResult = executorRouteStrategyEnum.getRouter().route(triggerParam, registryList);
            if (routeAddressResult.getCode() == ReturnT.SUCCESS_CODE) {
                address = routeAddressResult.getContent();
            } else {
                //如果没得到地址，就赋值失败，这里还用不到这个失败结果，但是先列出来吧
                routeAddressResult = new ReturnT<String>(ReturnT.FAIL_CODE, I18nUtil.getString("jobconf_trigger_address_empty"));
            }
        }
        //接下来就定义一个远程调用的结果变量
        ReturnT<String> triggerResult = null;
        //如果地址不为空
        if (address != null) {
            //在这里进行远程调用，这里就是最核心远程调用的方法，但是方法内部的逻辑很简单，就是用http发送调用
            //消息而已
            triggerResult = runExecutor(triggerParam, address);
            //这里就输出一下状态码吧，根据返回的状态码判断任务是否执行成功
            logger.info("返回的状态码"+triggerResult.getCode());
        } else {
            triggerResult = new ReturnT<String>(ReturnT.FAIL_CODE, null);
        }
        //在这里拼接一下触发任务的信息，其实就是web界面的调度备注
        StringBuffer triggerMsgSb = new StringBuffer();
        triggerMsgSb.append(I18nUtil.getString("jobconf_trigger_type")).append("：").append(triggerType.getTitle());
        triggerMsgSb.append("<br>").append(I18nUtil.getString("jobconf_trigger_admin_adress")).append("：").append(IpUtil.getIp());
        triggerMsgSb.append("<br>").append(I18nUtil.getString("jobconf_trigger_exe_regtype")).append("：")
                .append( (group.getAddressType() == 0)?I18nUtil.getString("jobgroup_field_addressType_0"):I18nUtil.getString("jobgroup_field_addressType_1") );
        triggerMsgSb.append("<br>").append(I18nUtil.getString("jobconf_trigger_exe_regaddress")).append("：").append(group.getRegistryList());
        triggerMsgSb.append("<br>").append(I18nUtil.getString("jobinfo_field_executorRouteStrategy")).append("：").append(executorRouteStrategyEnum.getTitle());
        //triggerMsgSb.append("<br>").append(I18nUtil.getString("jobinfo_field_executorBlockStrategy")).append("：").append(blockStrategy.getTitle());
        triggerMsgSb.append("<br>").append(I18nUtil.getString("jobinfo_field_timeout")).append("：").append(jobInfo.getExecutorTimeout());
        triggerMsgSb.append("<br>").append(I18nUtil.getString("jobinfo_field_executorFailRetryCount")).append("：").append(finalFailRetryCount);
        triggerMsgSb.append("<br><br><span style=\"color:#00c0ef;\" > >>>>>>>>>>>"+ I18nUtil.getString("jobconf_trigger_run") +"<<<<<<<<<<< </span><br>")
                .append((routeAddressResult!=null&&routeAddressResult.getMsg()!=null)?routeAddressResult.getMsg()+"<br><br>":"").append(triggerResult.getMsg()!=null?triggerResult.getMsg():"");
        //设置执行器地址
        jobLog.setExecutorAddress(address);
        //设置执行定时任务的方法名称
        jobLog.setExecutorHandler(jobInfo.getExecutorHandler());
        //设置执行参数
        jobLog.setExecutorParam(jobInfo.getExecutorParam());
        //jobLog.setExecutorShardingParam(shardingParam);
        //设置失败重试次数
        jobLog.setExecutorFailRetryCount(finalFailRetryCount);
        //设置触发结果码
        jobLog.setTriggerCode(triggerResult.getCode());
        //设置触发任务信息，也就是调度备注
        jobLog.setTriggerMsg(triggerMsgSb.toString());
        //更新数据库信息
        XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().updateTriggerInfo(jobLog);
        logger.debug(">>>>>>>>>>> xxl-job trigger end, jobId:{}", jobLog.getId());
    }
}
```

可以看到，在XxlJobTrigger类的processTrigger方法中，在开始远程调度定时任务之前，在上面代码块的第9到第15行，直接为当前要被调度的定时任务创建了一个日志对象，该日志对象定义为XxlJobLog，内部的具体属性如下。
```java
public class XxlJobLog {

	//日志id
	private long id;
	//执行器组id
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
	//失败重试次数
	private int executorFailRetryCount;
	//触发器触发时间
	private Date triggerTime;
	//触发器任务的响应码
	private int triggerCode;
	//触发任务信息
	private String triggerMsg;
	//定时任务执行时间
	private Date handleTime;
	//执行的响应码
	private int handleCode;
	//执行的具体结果
	private String handleMsg;
	//警报的状态码 0是默认，1是不需要报警，2是报警成功，3是报警失败
	private int alarmStatus;

    //其它get和set方法省略
    
}
```
并且通过jobLog.setJobId(jobInfo.getId())这行代码，把定时任务的主键ID，设置到XxlJobLog日志对象中了，除此之外，XxlJobLog对象还有很多属性并没有赋值。但这时候，在调度中心这一边，定时任务已经和其日志信息绑定成功了，只需要耐心等待执行器那一端返回定时任务的执行结果，然后根据执行结果中的定时任务ID，把对应的信息跟新到数据库中的XxlJobLog中即可。

## 执行器侧重构
接下俩的重点，就来到了执行器这一端。刚才我一直在说，定时任务的执行结果会封装到HandleCallbackParam对象中，被回调线程发送给调度中心，但是HandleCallbackParam对象的内部属性究竟有什么？。

现在，就请大家看看下面的代码块。
```java
public class HandleCallbackParam implements Serializable {
    private static final long serialVersionUID = 42L;
	//这个就是定时任务的主键ID
    private long logId;
    private long logDateTim;
    //定时任务执行结果的状态码，成功还是失败
    private int handleCode;
    private String handleMsg;

    //其他内容暂且省略
}
```

接下来HandleCallbackParam对象是如何创建的，怎么交给回调线程的?

经过前面几章的学习，我相信大家应该都掌握了定时任务执行的方式。在执行器这一端，定时任务是在对应的工作线程JobThread中被真正执行的。请看下面的代码块。
```java
public class JobThread extends Thread{


    //其他内容省略
    
    @Override
	public void run() {
        while(!toStop){
            //省略部分内容

            //通过反射执行了定时任务，终于在这里执行了
			handler.execute();
        }
    }
}
```

## 执行器侧记录文件日志
在执行器结果回调之前，我想到了个别的事情，那就是 执行器这一端不仅要把定时任务执行结果回调给调度中心，还要把定时任务执行的详细信息以日志的方式收集起来，并且输出到本地文件中。

这样一来，我就要再给执行器这一端引入两个新的组件。一个就是XxlJobFileAppender类，具体内容请看下面的代码块。
```java
public class XxlJobFileAppender {

	private static Logger logger = LoggerFactory.getLogger(XxlJobFileAppender.class);

	//默认的日志存储的路径，但是在执行器启动的时候，该路径会被用户在配置文件中设置的路径取代
	private static String logBasePath = "/data/applogs/xxl-job/jobhandler";

	//下面这个会在web端在线编辑代码，执行定时任务的时候，用这个路径把用户编辑的代码记录下来
	private static String glueSrcPath = logBasePath.concat("/gluesource");

	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/17
	 * @Description:初始化存储日志文件路径的方法，非常简单，就不细讲了
	 */
	public static void initLogPath(String logPath){
		if (logPath!=null && logPath.trim().length()>0) {
			logBasePath = logPath;
		}
		File logPathDir = new File(logBasePath);
		if (!logPathDir.exists()) {
			logPathDir.mkdirs();
		}
		logBasePath = logPathDir.getPath();
		File glueBaseDir = new File(logPathDir, "gluesource");
		if (!glueBaseDir.exists()) {
			glueBaseDir.mkdirs();
		}
		glueSrcPath = glueBaseDir.getPath();
	}


	public static String getLogPath() {
		return logBasePath;
	}

	public static String getGlueSrcPath() {
		return glueSrcPath;
	}


	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/17
	 * @Description:该方法会根据定时任务的触发时间和其对应的日志id创造一个文件名，这个日志id是在调度中心就创建好的
	 * 通过触发器参数传递给执行器的，文件夹的名称是定时任务执行的年月日时间
	 * 比如，2023-06-30，2023-07-02等等，每个时间都是一个文件夹，文件夹中有很多个日志文件，文件名称就是定时任务的ID
	 */
	public static String makeLogFileName(Date triggerDate, long logId) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		//这里的getLogPath()会得到存储日志的基础路径，就是用户在配置文件设置的那个路径
		File logFilePath = new File(getLogPath(), sdf.format(triggerDate));
		if (!logFilePath.exists()) {
			logFilePath.mkdir();
		}
		String logFileName = logFilePath.getPath()
				.concat(File.separator)
				.concat(String.valueOf(logId))
				.concat(".log");
		return logFileName;
	}


	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/17
	 * @Description:把日志记录到本地的日志文件中
	 */
	public static void appendLog(String logFileName, String appendLog) {
		if (logFileName==null || logFileName.trim().length()==0) {
			return;
		}
		File logFile = new File(logFileName);
		if (!logFile.exists()) {
			try {
				logFile.createNewFile();
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
				return;
			}
		}
		if (appendLog == null) {
			appendLog = "";
		}
		appendLog += "\r\n";
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(logFile, true);
			fos.write(appendLog.getBytes("utf-8"));
			fos.flush();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					logger.error(e.getMessage(), e);
				}
			}
		}
		
	}

	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/17
	 * @Description:读取本地的日志文件内容，这个方法虽然有点长，但都是常规逻辑，就是最基础的读取文件的操作
	 */
	public static LogResult readLog(String logFileName, int fromLineNum){
		if (logFileName==null || logFileName.trim().length()==0) {
            return new LogResult(fromLineNum, 0, "readLog fail, logFile not found", true);
		}
		File logFile = new File(logFileName);
		if (!logFile.exists()) {
            return new LogResult(fromLineNum, 0, "readLog fail, logFile not exists", true);
		}
		StringBuffer logContentBuffer = new StringBuffer();
		int toLineNum = 0;
		LineNumberReader reader = null;
		try {
			reader = new LineNumberReader(new InputStreamReader(new FileInputStream(logFile), "utf-8"));
			String line = null;
			while ((line = reader.readLine())!=null) {
				toLineNum = reader.getLineNumber();
				if (toLineNum >= fromLineNum) {
					logContentBuffer.append(line).append("\n");
				}
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					logger.error(e.getMessage(), e);
				}
			}
		}
		LogResult logResult = new LogResult(fromLineNum, toLineNum, logContentBuffer.toString(), false);
		return logResult;
	}
}
```

另一个组件就是XxlJobHelper类，具体的内容如下。
```java
public class XxlJobHelper {
    //该类只展示部分代码


     /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/17
     * @Description:存储定时任务日志的入口方法
     */
    public static boolean log(String appendLogPattern, Object ... appendLogArguments) {
        //该方法的作用是用来格式化要记录的日志信息
        FormattingTuple ft = MessageFormatter.arrayFormat(appendLogPattern, appendLogArguments);
        String appendLog = ft.getMessage();
        //从栈帧中获得方法的调用信息
        StackTraceElement callInfo = new Throwable().getStackTrace()[1];
        //在这里开始存储日志，但这里实际上只是个入口方法，真正的操作还是会进一步调用XxlJobFileAppender类的方法来完成的
        return logDetail(callInfo, appendLog);
    }



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


    //剩下的方法暂且省略
}
```

上面两个新的组件的关系已经很明显了，XxlJobHelper中的方法是记录日志到本地的入口方法，而真正干活的是XxlJobFileAppender类中的方法。

比如XxlJobHelper调用了其内部的log方法，打算将定时任务执行的详细信息记录到本地，但是在log方法内部，又调用了logDetail方法，而在logDetail方法内部，调用了XxlJobFileAppender类中的appendLog方法，真正将日志内容拼接到对应的本地文件中。

## 任务执行上下文分析
大家别忘了，每一个定时任务都对应着一个工作线程，而每一个定时任务执行的详细信息和返回结果都是独一份的，既然是这样，为什么不直接用ThreadLocal来存储任务执行上下文呢？

同时，任务执行上下文还充当了计算过程封装的作用，这种处理手段既简单又高效，实在是精妙。下面，就请大家看看具体的代码。
```java
public class XxlJobContext {

    //下面就是定时任务执行结果的三种状态码
    //200是成功，500是失败，502是超时
    public static final int HANDLE_CODE_SUCCESS = 200;
    public static final int HANDLE_CODE_FAIL = 500;
    public static final int HANDLE_CODE_TIMEOUT = 502;


    private final long jobId;


    private final String jobParam;


    private final String jobLogFileName;


    private final int shardIndex;


    private final int shardTotal;


    private int handleCode;


    private String handleMsg;


    public XxlJobContext(long jobId, String jobParam, String jobLogFileName, int shardIndex, int shardTotal) {
        this.jobId = jobId;
        this.jobParam = jobParam;
        this.jobLogFileName = jobLogFileName;
        this.shardIndex = shardIndex;
        this.shardTotal = shardTotal;
        //构造方法中唯一值得注意的就是这里，创建XxlJobContext对象的时候默认定时任务的执行结果就是成功
        //如果执行失败了，自有其他方法把这里设置成失败
        this.handleCode = HANDLE_CODE_SUCCESS;
    }

    public long getJobId() {
        return jobId;
    }

    public String getJobParam() {
        return jobParam;
    }

    public String getJobLogFileName() {
        return jobLogFileName;
    }

    public int getShardIndex() {
        return shardIndex;
    }

    public int getShardTotal() {
        return shardTotal;
    }

    public void setHandleCode(int handleCode) {
        this.handleCode = handleCode;
    }

    public int getHandleCode() {
        return handleCode;
    }

    public void setHandleMsg(String handleMsg) {
        this.handleMsg = handleMsg;
    }

    public String getHandleMsg() {
        return handleMsg;
    }

    //这里是一个线程的本地变量，因为定时任务真正执行的时候，在执行器端是一个定时任务任务对应一个线程
    //这样就把定时任务隔离开了，自然就可以利用这个线程的本地变量，把需要的数据存储在里面
    //这里使用的这个变量是可继承的threadlocal，也就子线程可以访问父线程存储在本地的数据了
    private static InheritableThreadLocal<XxlJobContext> contextHolder = new InheritableThreadLocal<XxlJobContext>();


    public static void setXxlJobContext(XxlJobContext xxlJobContext){
        contextHolder.set(xxlJobContext);
    }


    public static XxlJobContext getXxlJobContext(){
        return contextHolder.get();
    }

}
```

接着就是重构之后的JobThread类。
```java
public class JobThread extends Thread{


    //其他内容省略
    
    @Override
	public void run() {
        while(!toStop){
			//现在就要在一个循环中不断的从触发器队列中取出待执行的定时任务，开始执行
			//线程是否工作的标记，默认为false
			running = false;
            //这个是线程的空闲时间
			idleTimes++;
			//先声明一个触发器参数变量
            TriggerParam triggerParam = null;
            try {
				//从触发器参数队列中取出一个触发器参数对象
				//这里是限时的阻塞获取，如果超过3秒没获取到，就不阻塞了
				triggerParam = triggerQueue.poll(3L, TimeUnit.SECONDS);
				if (triggerParam!=null) {
					//走到这里，说明获得了触发器参数，这时候就把线程正在执行的标记设置为true
					running = true;
                    //空闲时间也可以置为0了
					idleTimes = 0;
					//接下来就是一系列的处理执行器端定时任务执行的日志操作
					//先根据定时任务的触发时间和定时任务的日志id，创建一个记录定时任务日的文件名
					String logFileName = XxlJobFileAppender.makeLogFileName(new Date(triggerParam.getLogDateTime()), triggerParam.getLogId());
					//然后创建一个定时任务上下文对象，创建的时候
                    //定时任务的执行结果就被设置为成功状态了
					XxlJobContext xxlJobContext = new XxlJobContext(
							triggerParam.getJobId(),
							triggerParam.getExecutorParams(),
							logFileName,
							triggerParam.getBroadcastIndex(),
							triggerParam.getBroadcastTotal());
					//先把创建出来的定时任务上下文对象存储到执行定时任务线程的私有容器中
					XxlJobContext.setXxlJobContext(xxlJobContext);
					//这里会向logFileName文件中记录一下日志，记录的就是下面的这句话，定时任务开始执行了
					XxlJobHelper.log("<br>----------- xxl-job job execute start -----------<br>----------- Param:" + xxlJobContext.getJobParam());
					//通过反射执行了定时任务，终于在这里执行了
					handler.execute();
					//定时任务执行了，所以这里要判断一下执行结果是什么，注意，这里的XxlJobContext上下文对象
					//从创建的时候就默认执行结果为成功。在源码中，在这行代码之前其实还有任务执行超时时间的判断，开启一个子线程去执行定时任务
					//然后再判断任务执行成功了没，如果没成功XxlJobHelper类就会修改上下文对象的执行结果。等我们引入任务超时的功能后，这里的逻辑就会更丰富了
					if (XxlJobContext.getXxlJobContext().getHandleCode() <= 0) {
						XxlJobHelper.handleFail("job handle result lost.");
					}else {
						//走到这里意味着定时任务执行成功了，从定时任务上下文中取出执行的结果信息
						String tempHandleMsg = XxlJobContext.getXxlJobContext().getHandleMsg();
						//这里有一个三元运算，会判断执行结果信息是不是null，如果执行成功，毫无异常，这个结果信息就会是null
						//只有在执行失败的时候，才会有失败信息被XxlJobHelper记录进去
						tempHandleMsg = (tempHandleMsg!=null&&tempHandleMsg.length()>50000)
								?tempHandleMsg.substring(0, 50000).concat("...")
								:tempHandleMsg;
						//这里是执行成功了，所以得到的是null，赋值其实就是什么也没赋成
						XxlJobContext.getXxlJobContext().setHandleMsg(tempHandleMsg);
					}
					//走到这里，不管是执行成功还是失败，都要把结果存储到对应的日志文件中
					//走到这里大家也应该意识到了，执行器这一端执行的定时任务，实际上是每一个定时任务都会对应一个本地的日志文件，每个定时任务的执行结果都会存储在自己的文件中
					//当然，一个定时任务可能会执行很多次，所以定时任务对应的日志文件就会记录这个定时任务每次执行的信息
					XxlJobHelper.log("<br>----------- xxl-job job execute end(finish) -----------<br>----------- Result: handleCode="
							+ XxlJobContext.getXxlJobContext().getHandleCode()
							+ ", handleMsg = "
							+ XxlJobContext.getXxlJobContext().getHandleMsg()
					);
				} else {
					//走到这里说明触发器队列中没有数据，也就意味着没有要执行的定时任务
					//如果线程的空闲时间大于30次，这里指的是循环的次数，每循环一次空闲时间就自增1，
					//有定时任务被执行空闲时间就清零，不可能没任务线程空转，太浪费资源了
					if (idleTimes > 30) {
						//而且触发器队列也没有数据
						if(triggerQueue.size() == 0) {
							//就从缓存JobThread线程的jobThreadRepository这个Map中移除缓存的JobThread线程
							//在移除的时候，会调用该线程的toStop方法和interrupt方法，让线程真正停下来
							XxlJobExecutor.removeJobThread(jobId, "excutor idel times over limit.");
						}
					}
				}
			} catch (Throwable e) {
				if (toStop) {
					//如果线程停止了，就记录线程停止的日志到定时任务对应的日志文件中
					XxlJobHelper.log("<br>----------- JobThread toStop, stopReason:" + stopReason);
					//下面就是将异常信息记录到日志文件中的操作，因为这些都是在catch中执行的
					//就意味着肯定有异常了，所以要记录异常信息
					StringWriter stringWriter = new StringWriter();
					e.printStackTrace(new PrintWriter(stringWriter));
					String errorMsg = stringWriter.toString();
					XxlJobHelper.handleFail(errorMsg);
					//在这里记录异常信息到日志文件中
					XxlJobHelper.log("<br>----------- JobThread Exception:" + errorMsg + "<br>----------- xxl-job job execute end(error) -----------");
				}
			} finally {
				//这里就走到了finally中，也就要开始执行日志回调给调度中心的操作了
				//别忘了，调度中心在远程调用之前创建了XxlJobLog这个对象，这个对象要记录很多日记调用信息的
				if(triggerParam != null) {
					if (!toStop) {
						//这里要再次判断线程是否停止运行
						//如果没有停止，就创建封装回调信息的HandleCallbackParam对象
						//把这个对象提交给TriggerCallbackThread内部的callBackQueue队列中
						TriggerCallbackThread.pushCallBack(new HandleCallbackParam(
								triggerParam.getLogId(),
								triggerParam.getLogDateTime(),
								XxlJobContext.getXxlJobContext().getHandleCode(),
								XxlJobContext.getXxlJobContext().getHandleMsg() )
						);
					} else {
						//如果走到这里说明线程被终止了，就要封装处理失败的回信
						TriggerCallbackThread.pushCallBack(new HandleCallbackParam(
								triggerParam.getLogId(),
								triggerParam.getLogDateTime(),
								XxlJobContext.HANDLE_CODE_FAIL,
								stopReason + " [job running, killed]" )
						);
					}
				}
			}
        }
    }
}
```

在JobThread的run方法的finally块中，执行器这一端回调线程的组件终于被调用了。就是下面这个代码块中的内容。
```java
创建封装回调信息的HandleCallbackParam对象
//把这个对象提交给TriggerCallbackThread内部的callBackQueue队列中
TriggerCallbackThread.pushCallBack(new HandleCallbackParam(
		triggerParam.getLogId(),
		triggerParam.getLogDateTime(),
		XxlJobContext.getXxlJobContext().getHandleCode(),
		XxlJobContext.getXxlJobContext().getHandleMsg() )
);
```
可以看到，在JobThread中，先创建了一个HandleCallbackParam对象，然后把定时任务的执行结果信息都封装到该对象中，其中最关键的就是定时任务的主键ID，还有执行结果的状态码。

## 结果发送线程TriggerCallbackThread
紧接着，这个对象就会被pushCallBack方法放到TriggerCallbackThread组件中的任务队列中。所以，接下来就让我们直接从代码层面看看TriggerCallbackThread组件中的内容吧。请看下面的代码块。
```java
public class TriggerCallbackThread {

     //单例对象，就不再重复解释了
    private static TriggerCallbackThread instance = new TriggerCallbackThread();

    public static TriggerCallbackThread getInstance(){
        return instance;
    }

    //要被回调给调度中心的定时任务执行结果的信息，会封装在HandleCallbackParam对象中，而该对象会先存放在该队列中
    private LinkedBlockingQueue<HandleCallbackParam> callBackQueue = new LinkedBlockingQueue<HandleCallbackParam>();

    //把封装回调信息的HandleCallbackParam对象提交给callBackQueue任务队列
    public static void pushCallBack(HandleCallbackParam callback){
        getInstance().callBackQueue.add(callback);
        logger.debug(">>>>>>>>>>> xxl-job, push callback request, logId:{}", callback.getLogId());
    }
    
    //回调线程，就是这个线程把回调信息通过http发送给调度中心
    private Thread triggerCallbackThread;
    //重试线程
    private Thread triggerRetryCallbackThread;

    public void start() {
        //对访问调度中心的客户端做一下判空操作
        if (XxlJobExecutor.getAdminBizList() == null) {
            logger.warn(">>>>>>>>>>> xxl-job, executor callback config fail, adminAddresses is null.");
            return;
        }
        //启动回调线程
        triggerCallbackThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while(!toStop){
                    try {
                        //从回调任务队列中取出一个回调的信息对象
                        HandleCallbackParam callback = getInstance().callBackQueue.take();
                        if (callback != null) {
                            List<HandleCallbackParam> callbackParamList = new ArrayList<HandleCallbackParam>();
                            //这里的意思就是说，如果回调的任务队列中有待回调的数据，就把所有数据转移到一个集合中
                            //并且返回有多少条要回调的数据
                            //注意，执行drainTo方法的时候，回调队列中的数据也都被清楚了
                            int drainToNum = getInstance().callBackQueue.drainTo(callbackParamList);
                            //把最开始取出来的数据再放回去，否则就会落下一个数据了
                            //这里要弄清楚，每个定时任务的回调信息都会通过triggerCallbackThread这个组件进行回调
                            //所以会有很多回调信息提交给回调队列，回调的时候，自然也是批量回调
                            callbackParamList.add(callback);
                            if (callbackParamList!=null && callbackParamList.size()>0) {
                                //在这里执行回调给调度中心的操作
                                doCallback(callbackParamList);
                            }
                        }
                    } catch (Exception e) {
                        if (!toStop) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                }
                try {
                    //走到这里，就意味着退出了循环，其实也就意味着triggerCallbackThread线程要停止工作了
                    List<HandleCallbackParam> callbackParamList = new ArrayList<HandleCallbackParam>();
                    //这里会再次把回调队列中的所有数据都放到新的集合中
                    int drainToNum = getInstance().callBackQueue.drainTo(callbackParamList);
                    if (callbackParamList!=null && callbackParamList.size()>0) {
                        //最后再回调一次信息给注册中心
                        doCallback(callbackParamList);
                    }
                } catch (Exception e) {
                    if (!toStop) {
                        logger.error(e.getMessage(), e);
                    }
                }
                logger.info(">>>>>>>>>>> xxl-job, executor callback thread destroy.");
            }
        });
        triggerCallbackThread.setDaemon(true);
        triggerCallbackThread.setName("xxl-job, executor TriggerCallbackThread");
        triggerCallbackThread.start();
    }

     /**
     * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
     * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
     * @Date:2023/7/17
     * @Description:回调定时任务的执行信息给调度中心的方法
     */
    private void doCallback(List<HandleCallbackParam> callbackParamList){
        boolean callbackRet = false;
        //获得访问调度中心的客户端的集合，并且遍历它们
        for (AdminBiz adminBiz: XxlJobExecutor.getAdminBizList()) {
            try {
                //在这里进行回调
                ReturnT<String> callbackResult = adminBiz.callback(callbackParamList);
                if (callbackResult!=null && ReturnT.SUCCESS_CODE == callbackResult.getCode()) {
                    //回调成功了，记录一下日志
                    callbackLog(callbackParamList, "<br>----------- xxl-job job callback finish.");
                    //回调标志置为true，说明回调成功了
                    callbackRet = true;
                    break;
                } else {
                    //回调失败了，记录一下日志
                    callbackLog(callbackParamList, "<br>----------- xxl-job job callback fail, callbackResult:" + callbackResult);
                }
            } catch (Exception e) {
                //回调出现异常了，记录一下日志
                callbackLog(callbackParamList, "<br>----------- xxl-job job callback error, errorMsg:" + e.getMessage());
            }
        }
        if (!callbackRet) {
            //这里就是回调失败了的意思，要把回调失败的数据存储到本地一个专门的文件当中，方便重试线程重新回调
            appendFailCallbackFile(callbackParamList);
        }
    }

    
}
```

在上面的代码块中，可以看到，在TriggerCallbackThread组件其实就是个triggerCallbackThread线程，这个线程就是专门用来把定时任务执行结果发送给调度中心的。

具体逻辑都写在代码块中了，最终就是通过adminBiz.callback(callbackParamList)这行代码，通过http协议发送给调度中心

另外在TriggerCallbackThread组件中还有另一个线程，那就是triggerRetryCallbackThread线程，该线程的功能就是在triggerCallbackThread线程回调定时任务执行结果失败后，重新回调一次，重新回调的这一次，就交给triggerRetryCallbackThread线程来执行。

当然，最终仍然是通过http协议来发送的

## 调度中心端接收处理任务回调结果
调度中心负责处理回调结果的是JobCompleteHelper新的组件
```java
/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/17
 * @Description:调度中心接收执行器回调信息的工作组件
 */
public class JobCompleteHelper {

	private static Logger logger = LoggerFactory.getLogger(JobCompleteHelper.class);
	//单例对象
	private static JobCompleteHelper instance = new JobCompleteHelper();

	public static JobCompleteHelper getInstance(){
		return instance;
	}

	//回调线程池，这个线程池就是处理执行器端回调过来的日志信息的
	private ThreadPoolExecutor callbackThreadPool = null;
	//监控线程
	private Thread monitorThread;
	private volatile boolean toStop = false;


	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/17
	 * @Description:启动该组件
	 */
	public void start(){
		//创建回调线程池
		callbackThreadPool = new ThreadPoolExecutor(
				2,
				20,
				30L,
				TimeUnit.SECONDS,
				new LinkedBlockingQueue<Runnable>(3000),
				new ThreadFactory() {
					@Override
					public Thread newThread(Runnable r) {
						return new Thread(r, "xxl-job, admin JobLosedMonitorHelper-callbackThreadPool-" + r.hashCode());
					}
				},
				new RejectedExecutionHandler() {
					@Override
					public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
						r.run();
						logger.warn(">>>>>>>>>>> xxl-job, callback too fast, match threadpool rejected handler(run now).");
					}
				});


		//创建监控线程
		monitorThread = new Thread(new Runnable() {
			@Override
			public void run() {
				//这里休息了一会，是因为需要等待JobTriggerPoolHelper组件初始化，因为不执行远程调度，也就没有
				//回调过来的定时任务执行结果信息
				try {
					TimeUnit.MILLISECONDS.sleep(50);
				} catch (InterruptedException e) {
					if (!toStop) {
						logger.error(e.getMessage(), e);
					}
				}
				while (!toStop) {
					try {
						//这里得到了一个时间信息，就是当前时间向前10分钟的时间
						//这里传进去的参数-10，就是减10分钟的意思
						Date losedTime = DateUtil.addMinutes(new Date(), -10);
						//这里最后对应的就是这条sql语句
						//t.trigger_code = 200 AND t.handle_code = 0 AND t.trigger_time <![CDATA[ <= ]]> #{losedTime} AND t2.id IS NULL
						//其实就是判断了一下，现在在数据库中的xxljoblog的触发时间，其实就可以当作定时任务在调度中心开始执行的那个时间
						//这里其实就是把当前时间前十分钟内提交执行的定时任务，但是始终没有得到执行器回调的执行结果的定时任务全找出来了
						//因为t.handle_code = 0，并且注册表中也没有对应的数据了，说明心跳断了
						//具体的方法在XxlJobLogMapper中
						List<Long> losedJobIds  = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().findLostJobIds(losedTime);
						if (losedJobIds!=null && losedJobIds.size()>0) {
							//开始遍历定时任务
							for (Long logId: losedJobIds) {
								XxlJobLog jobLog = new XxlJobLog();
								jobLog.setId(logId);
								//设置执行时间
								jobLog.setHandleTime(new Date());
								//设置失败状态
								jobLog.setHandleCode(ReturnT.FAIL_CODE);
								jobLog.setHandleMsg( I18nUtil.getString("joblog_lost_fail") );
								//更新失败的定时任务状态
								XxlJobCompleter.updateHandleInfoAndFinish(jobLog);
							}
						}
					}
					catch (Exception e) {
						if (!toStop) {
							logger.error(">>>>>>>>>>> xxl-job, job fail monitor thread error:{}", e);
						}
					}
                    try {
						//每60秒工作一次
                        TimeUnit.SECONDS.sleep(60);
                    } catch (Exception e) {
                        if (!toStop) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                }
				logger.info(">>>>>>>>>>> xxl-job, JobLosedMonitorHelper stop");
			}
		});
		monitorThread.setDaemon(true);
		monitorThread.setName("xxl-job, admin JobLosedMonitorHelper");
		monitorThread.start();
	}


	//终止组件工作的方法
	public void toStop(){
		toStop = true;
		callbackThreadPool.shutdownNow();
		monitorThread.interrupt();
		try {
			monitorThread.join();
		} catch (InterruptedException e) {
			logger.error(e.getMessage(), e);
		}
	}


	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/17
	 * @Description:处理回调信息的方法
	 */
	public ReturnT<String> callback(List<HandleCallbackParam> callbackParamList) {
		callbackThreadPool.execute(new Runnable() {
			@Override
			public void run() {
				for (HandleCallbackParam handleCallbackParam: callbackParamList) {
					//在这里处理每一个回调的信息
					ReturnT<String> callbackResult = callback(handleCallbackParam);
					logger.debug(">>>>>>>>> JobApiController.callback {}, handleCallbackParam={}, callbackResult={}",
							(callbackResult.getCode()== ReturnT.SUCCESS_CODE?"success":"fail"), handleCallbackParam, callbackResult);
				}
			}
		});

		return ReturnT.SUCCESS;
	}


	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/17
	 * @Description:真正处理回调信息的方法
	 */
	private ReturnT<String> callback(HandleCallbackParam handleCallbackParam) {
		//得到对应的xxljoblog对象
		XxlJobLog log = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().load(handleCallbackParam.getLogId());
		if (log == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, "log item not found.");
		}
		//判断日志对象的处理结果码
		//因为这个响应码无论是哪种情况都是大于0的，如果大于0了，说明已经回调一次了
		//如果等于0，说明还没得到回调信息，任务也可能还处于运行中状态
		if (log.getHandleCode() > 0) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, "log repeate callback.");
		}
		//拼接信息
		StringBuffer handleMsg = new StringBuffer();
		if (log.getHandleMsg()!=null) {
			handleMsg.append(log.getHandleMsg()).append("<br>");
		}
		if (handleCallbackParam.getHandleMsg() != null) {
			handleMsg.append(handleCallbackParam.getHandleMsg());
		}
		log.setHandleTime(new Date());
		//在这里把定时任务执行的状态码赋值给XxlJobLog对象中的handleCode成员变量了
		log.setHandleCode(handleCallbackParam.getHandleCode());
		log.setHandleMsg(handleMsg.toString());
		//更新数据库中的日志信息
		XxlJobCompleter.updateHandleInfoAndFinish(log);
		return ReturnT.SUCCESS;
	}
}
```
可以看到，在JobCompleteHelper组件中，有两个特别重要的成员变量，一个就是callbackThreadPool线程池，一个就是monitorThread监控线程。

## 调度中心callbackThreadPool线程池
callbackThreadPool线程池负责把执行器发送回来的定时任务执行信息赋值给XxlJobLog对象中的成员变量，然后更新数据库中XxlJobLog的信息。 这样，定时任务的执行信息就终于完整了。

整个操作就是在上看代码块的callback方法内执行的，而在callback方法中，又会调用到该类的被private修饰的callback方法。就是在这个callback方法中，会执行下面的几行核心代码。请看下面的代码块。
```java
	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/17
	 * @Description:真正处理回调信息的方法
	 */
	private ReturnT<String> callback(HandleCallbackParam handleCallbackParam) {
		//得到对应的xxljoblog对象
		XxlJobLog log = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().load(handleCallbackParam.getLogId());
		if (log == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, "log item not found.");
		}
		//判断日志对象的处理结果码
		//因为这个响应码无论是哪种情况都是大于0的，如果大于0了，说明已经回调一次了
		//如果等于0，说明还没得到回调信息，任务也可能还处于运行中状态
		if (log.getHandleCode() > 0) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, "log repeate callback.");
		}


    	//省略中间的代码


		//在这里把定时任务执行的状态码赋值给XxlJobLog对象中的handleCode成员变量了
		log.setHandleCode(handleCallbackParam.getHandleCode());
		log.setHandleMsg(handleMsg.toString());
		//更新数据库中的日志信息
		XxlJobCompleter.updateHandleInfoAndFinish(log);
		return ReturnT.SUCCESS;
	}
}
```

到此为止，callbackThreadPool线程池的作用就讲解完了，并且调度中心这边，定时任务的执行信息也终于补全完整了

## 调度中心monitorThread监控线程
值得一提的是调度中心的monitorThread监控线程，是为了引出另一种任务执行失败的情况。也就是monitorThread线程要进行的操作。

XxlJobLog对象在存储数据库的时候，其内部成员变量handleCode实际上没有赋值的，在数据库中默认为0。

只有当执行器那一端返回定时任务的执行结果信息了，handleCode属性才会成功复制，这时候它的值才会大于0。

因为在执行器那一端为handleCode属性设置了三个状态值，请看下面的代码块。
```java
public class XxlJobContext {

    public static final int HANDLE_CODE_SUCCESS = 200;
    public static final int HANDLE_CODE_FAIL = 500;
    public static final int HANDLE_CODE_TIMEOUT = 502;

    //其他内容省略

}
```

handleCode属性可以被成功，失败和超时这三个整数赋值。这也就意味着，只要XxlJobLog对象的handleCode属性为0，就说明当前调度的定时任务还没有一个执行结果，只要数据库中XxlJobLog对象的handleCode属性不为0了，就说明被调度的定时任务一定有结果了，因为从执行器那一端的返回的handleCode状态码，不管是成功，失败还是超时，都是大于0的。

所以，完全可以根据这个状态码的值来判断当前定时任务被调度的状态，看是否收到执行结果了。如果理解了这个逻辑，那么接下来，我们就可以看看monitorThread线程的工作内容了。请看下面的代码块。
```java
//创建监控线程
monitorThread = new Thread(new Runnable() {
	@Override
	public void run() {
		//这里休息了一会，是因为需要等待JobTriggerPoolHelper组件初始化，因为不执行远程调度，也就没有
		//回调过来的定时任务执行结果信息
		try {
			TimeUnit.MILLISECONDS.sleep(50);
		} catch (InterruptedException e) {
			if (!toStop) {
				logger.error(e.getMessage(), e);
			}
		}
		while (!toStop) {
			try {
				//这里得到了一个时间信息，就是当前时间向前10分钟的时间
				//这里传进去的参数-10，就是减10分钟的意思
				Date losedTime = DateUtil.addMinutes(new Date(), -10);
				//这里最后对应的就是这条sql语句
				//t.trigger_code = 200 AND t.handle_code = 0 AND t.trigger_time <![CDATA[ <= ]]> #{losedTime} AND t2.id IS NULL
				//其实就是判断了一下，现在在数据库中的xxljoblog的触发时间，其实就可以当作定时任务在调度中心开始执行的那个时间
				//这里其实就是把当前时间前十分钟内提交执行的定时任务，但是始终没有得到执行器回调的执行结果的定时任务全找出来了
				//因为t.handle_code = 0，并且注册表中也没有对应的数据了，说明心跳断了
				//具体的方法在XxlJobLogMapper中
				List<Long> losedJobIds  = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().findLostJobIds(losedTime);
				if (losedJobIds!=null && losedJobIds.size()>0) {
					//开始遍历定时任务
					for (Long logId: losedJobIds) {
						XxlJobLog jobLog = new XxlJobLog();
						jobLog.setId(logId);
						//设置执行时间
						jobLog.setHandleTime(new Date());
						//设置失败状态
						jobLog.setHandleCode(ReturnT.FAIL_CODE);
						jobLog.setHandleMsg( I18nUtil.getString("joblog_lost_fail") );
						//更新失败的定时任务状态
						XxlJobCompleter.updateHandleInfoAndFinish(jobLog);
					}
				}
			}
			catch (Exception e) {
				if (!toStop) {
					logger.error(">>>>>>>>>>> xxl-job, job fail monitor thread error:{}", e);
				}
			}
            try {
				//每60秒工作一次
                TimeUnit.SECONDS.sleep(60);
            } catch (Exception e) {
                if (!toStop) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
		logger.info(">>>>>>>>>>> xxl-job, JobLosedMonitorHelper stop");
	}
});
monitorThread.setDaemon(true);
monitorThread.setName("xxl-job, admin JobLosedMonitorHelper");
monitorThread.start();
```

可以看到，在上面的代码块中，monitorThread线程启动之后，就会在一个循环中不断地从数据库中查找定时任务的执行信息，并且，查找的时候，会以当前时间为标志，查找到当前时间的十分钟之前调度的所有定时任务的信息，返回的是这些定时任务的ID。

为什么要这么查找呢？原因其实也不复杂，如果一个定时任务被调度了十分钟了，仍然没有收到执行结果，那这个定时任务的执行肯定就出问题了呀。具体查找的逻辑已经添加在上面代码块的注释中了.

最关键的一点就是在sql语句:
```sql
	<select id="findLostJobIds" resultType="long" >
		SELECT
			t.id
		FROM
			xxl_job_log t
			LEFT JOIN xxl_job_registry t2 ON t.executor_address = t2.registry_value
		WHERE
			t.trigger_code = 200
				AND t.handle_code = 0
				AND t.trigger_time <![CDATA[ <= ]]> #{losedTime}
				AND t2.id IS NULL;
	</select>
```
这条sql炸眼一看有疑问，你可能会认为当t2.id IS NULL成立，则连接失败：LEFT JOIN xxl_job_registry t2 ON t.executor_address = t2.registry_value

其实不然，让我们举例分析下上述sql的执行过程：

xxl_job_log（任务日志表）
```sql
id	executor_address	trigger_code	handle_code	trigger_time
1	http://a:9999	       200	            0	     2025-05-10
2	http://b:9999	       200	           200	     2025-05-11
3	http://c:9999	       200	            0	     2025-05-12
```

xxl_job_registry（执行器注册表）
```sql
id	registry_value
1	http://a:9999
2	http://b:9999
```
现在执行器 C 已经下线，所以在 xxl_job_registry 中没有它的记录。

那么上述sql的执行过程是
```sql
id	executor_address	trigger_code	handle_code	trigger_time  t2.id	 t2.registry_value
1	http://a:9999	        200	              0	      2025-05-10    1	http://a:9999
2	http://b:9999	        200	             200	  2025-05-11    2	http://b:9999
3	http://c:9999	        200	              0	      2025-05-12    null  null
```

where条件为：
- 判断handle_code为0
- 并且定时任务所部署的服务器已经不在和调度中心维持心跳了，就是AND t2.id IS NULL

这就表明要查询的地址只存在于XxlJobLog对应的表中，而在xxl_job_register这种表中并没有对应的数据 ，返回结果就是：
```sql
id
3
```

这不就意味着执行定时任务的执行器因为心跳断连而被删除了吗？所以，这就是我要说的另一种定时任务执行失败的情况。

现在，大家应该明白了，除了会在JobCompleteHelper类中通过callbackThreadPool线程池来设置真正的执行结果，

但同时还有另一种情况，那就是定时任务执行了10分钟了还没结果，并且执行器的服务器下线了，这时候也要设置执行失败的结果。

至于这两种定时任务结果设置有什么不同，可以用一句话来总结，那就是callbackThreadPool线程池设置会设置**成功，失败，超时**三种结果，而monitorThread线程设置的**只有执行器下线并且定时任务执行失败**的结果。


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

在admin管理页面手动执行一次定时任务，触发的时候用的xxl_job_group中的数据，测试结果在执行器侧打印如下
```shell
第0次
第1次
第2次
第3次
第4次
下一次任务开始了！
```






