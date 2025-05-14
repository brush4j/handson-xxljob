package com.cqfy.xxl.job.admin.core.model;

import org.springframework.util.StringUtils;

/**
 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
 * @Date:2023/7/11
 * @Description:用户信息对应的实体类
 */
public class XxlJobUser {
	//用户id
	private int id;
	//用户名
	private String username;
	//密码
	private String password;
	//用户角色，0是普通用户，1是管理员
	private int role;
	//对应权限
	private String permission;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public int getRole() {
		return role;
	}

	public void setRole(int role) {
		this.role = role;
	}

	public String getPermission() {
		return permission;
	}

	public void setPermission(String permission) {
		this.permission = permission;
	}



	/**
	 * @author:B站UP主九九打码，从零带你写框架系列教程的作者，个人微信号：。
	 * @Description:系列教程目前包括手写Netty，XXL-JOB，Spring，RocketMq，Javac，JVM等课程。
	 * @Date:2023/7/11
	 * @Description:判断当前用户有没有执行器权限的方法
	 */
	public boolean validPermission(int jobGroup){
		if (this.role == 1) {
			return true;
		} else {
			if (StringUtils.hasText(this.permission)) {
				for (String permissionItem : this.permission.split(",")) {
					if (String.valueOf(jobGroup).equals(permissionItem)) {
						return true;
					}
				}
			}
			return false;
		}

	}

	@Override
	public String toString() {
		return "XxlJobUser{" +
				"id=" + id +
				", username='" + username + '\'' +
				", password='" + password + '\'' +
				", role=" + role +
				", permission='" + permission + '\'' +
				'}';
	}
}
