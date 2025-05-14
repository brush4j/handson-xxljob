package com.cqfy.xxl.job.core.log;

import com.cqfy.xxl.job.core.biz.model.LogResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/17
 * @Description:该类是操作日志的类，对日志文件进行操作的功能全部封装在该类中
 */
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
	 * 通过触发器参数传递给执行器的
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

	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/17
	 * @Description:也是读取日志文件内容，一行一行地读
	 */
	public static String readLines(File logFile){
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(logFile), "utf-8"));
			if (reader != null) {
				StringBuilder sb = new StringBuilder();
				String line = null;
				while ((line = reader.readLine()) != null) {
					sb.append(line).append("\n");
				}
				return sb.toString();
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
		return null;
	}

}
