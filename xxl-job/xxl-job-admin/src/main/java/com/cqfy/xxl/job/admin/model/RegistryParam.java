package com.cqfy.xxl.job.admin.model;

import java.io.Serializable;

/**
 * 执行器方注册瞬间的请求参数
 */
public class RegistryParam implements Serializable {

    private static final long serialVersionUID = 42L;

    //定时任务方法的名称
    private String registryKey;
    //定时任务程序部署的服务器的ip地址
    private String registryValue;

    public RegistryParam(){

    }

    public RegistryParam(String registryKey, String registryValue) {
        this.registryKey = registryKey;
        this.registryValue = registryValue;
    }

    public String getRegistryKey() {
        return registryKey;
    }

    public void setRegistryKey(String registryKey) {
        this.registryKey = registryKey;
    }

    public String getRegistryValue() {
        return registryValue;
    }

    public void setRegistryValue(String registryValue) {
        this.registryValue = registryValue;
    }

}