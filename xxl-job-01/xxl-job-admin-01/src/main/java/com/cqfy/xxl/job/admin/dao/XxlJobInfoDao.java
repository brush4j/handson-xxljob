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

}