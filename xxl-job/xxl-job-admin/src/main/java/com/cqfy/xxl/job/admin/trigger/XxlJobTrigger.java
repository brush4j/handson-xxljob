package com.cqfy.xxl.job.admin.trigger;

import com.cqfy.xxl.job.admin.dao.XxlJobInfo;
import com.cqfy.xxl.job.admin.model.ReturnT;
import com.cqfy.xxl.job.admin.model.TriggerParam;
import com.cqfy.xxl.job.admin.util.GsonTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class XxlJobTrigger {

    private static Logger logger = LoggerFactory.getLogger(XxlJobTrigger.class);

    public static void trigger(XxlJobInfo jobInfo) {
        processTrigger(jobInfo);
    }

    private static void processTrigger(XxlJobInfo jobInfo){
        //初始化触发器参数，这里的这个触发参数，是要在远程调用的另一端，也就是定时任务执行程序那一端使用的
        TriggerParam triggerParam = new TriggerParam();
        //设置执行器要执行的任务的方法名称
        triggerParam.setExecutorHandler(jobInfo.getExecutorHandler());
        //选择具体的定时任务执行器地址，这里默认使用集合中的第一个
        String address = jobInfo.getRegistryList().get(0);
        //在这里执行远程调用，也就是把要执行的定时任务的执行信息发送给定时任务程序，定时任务
        //程序执行完毕后，返回一个执行结果信息，封装在ReturnT对象中
        //这个ReturnT类下面就有解释，是用来封装定时任务程序的返回信息的类
        ReturnT<String> triggerResult = runExecutor(triggerParam, address);
        //就在这里输出一下状态码吧，根据返回的状态码判断任务是否执行成功
        logger.info("返回的状态码"+triggerResult.getCode());

    }


    public static ReturnT<String> runExecutor(TriggerParam triggerParam, String address){
        //在这个方法中把消息发送给定时任务执行程序
        HttpURLConnection connection = null;
        BufferedReader bufferedReader = null;
        try {
            //创建链接
            URL realUrl = new URL(address);
            //得到连接
            connection = (HttpURLConnection) realUrl.openConnection();
            //设置连接属性
            //post请求
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setReadTimeout(3 * 1000);
            connection.setConnectTimeout(3 * 1000);
            connection.setRequestProperty("connection", "Keep-Alive");
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            connection.setRequestProperty("Accept-Charset", "application/json;charset=UTF-8");
            //进行连接
            connection.connect();
            //判断请求体是否为null
            if (triggerParam != null) {
                //序列化请求体，也就是要发送的触发参数
                String requestBody = GsonTool.toJson(triggerParam);
                //下面就开始正式发送消息了
                DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream());
                dataOutputStream.write(requestBody.getBytes("UTF-8"));
                //刷新缓冲区
                dataOutputStream.flush();
                //释放资源
                dataOutputStream.close();
            }
            //获取响应码
            int statusCode = connection.getResponseCode();
            if (statusCode != 200) {
                //设置失败结果
                return new ReturnT<String>(ReturnT.FAIL_CODE, "xxl-job remoting fail, StatusCode("+ statusCode +") invalid. for url : " + realUrl);
            }
            //下面就开始接收返回的结果了
            bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder result = new StringBuilder();
            String line;
            //接收返回信息
            while ((line = bufferedReader.readLine()) != null) {
                result.append(line);
            }
            //转换为字符串
            String resultJson = result.toString();
            try {
                //转换为ReturnT对象，返回给用户
                ReturnT returnT = GsonTool.fromJson(resultJson, ReturnT.class);
                return returnT;
            } catch (Exception e) {
                logger.error("xxl-job remoting (url="+realUrl+") response content invalid("+ resultJson +").", e);
                return new ReturnT<String>(ReturnT.FAIL_CODE, "xxl-job remoting (url="+realUrl+") response content invalid("+ resultJson +").");
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return new ReturnT<String>(ReturnT.FAIL_CODE, "xxl-job remoting error("+ e.getMessage() +"), for url : " );
        } finally {
            try {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (Exception e2) {
                logger.error(e2.getMessage(), e2);
            }
        }
    }
}