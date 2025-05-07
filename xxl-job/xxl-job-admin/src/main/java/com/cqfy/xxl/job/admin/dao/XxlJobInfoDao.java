package com.cqfy.xxl.job.admin.dao;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface XxlJobInfoDao {

    //这个方法就是根据定时任务的名字，获得定时任务的具体信息
    XxlJobInfo loadByName(String name);

    //更新数据库中定时任务数据的方法
    int save(XxlJobInfo info);

    //查询所有定时任务信息的方法
    List findAll();

    //根据执行时间查询定时任务信息的方法，这里查询的依据就是
    //定时任务下一次的执行时间。比如当前时间是0秒，要查询10秒以内的可以执行的定时任务
    //那么就判断定时任务下一次的执行时间只要是小于10秒的，都返回给用户
    //这些定时任务都是在10秒内可以执行的
    List<XxlJobInfo> scheduleJobQuery(long maxNextTime);
}