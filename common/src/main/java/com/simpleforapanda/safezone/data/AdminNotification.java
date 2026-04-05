package com.simpleforapanda.safezone.data;

public class AdminNotification {
	public String ownerUuid;
	public String ownerName;
	public String adminName;
	public String message;
	public long timestamp;

	public AdminNotification() {
	}

	public AdminNotification(String ownerUuid, String ownerName, String adminName, String message, long timestamp) {
		this.ownerUuid = ownerUuid;
		this.ownerName = ownerName;
		this.adminName = adminName;
		this.message = message;
		this.timestamp = timestamp;
	}
}
