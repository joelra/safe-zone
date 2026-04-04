package com.simpleforapanda.safezone.data;

public class OpsSettings {
	public boolean auditLogEnabled = false;
	public boolean mirrorAuditToServerLog = false;
	public boolean createDataBackups = false;
	public boolean recoverFromBackupOnLoadFailure = false;

	public void ensureDefaults() {
	}

	public OpsSettings copy() {
		OpsSettings copy = new OpsSettings();
		copy.auditLogEnabled = this.auditLogEnabled;
		copy.mirrorAuditToServerLog = this.mirrorAuditToServerLog;
		copy.createDataBackups = this.createDataBackups;
		copy.recoverFromBackupOnLoadFailure = this.recoverFromBackupOnLoadFailure;
		return copy;
	}
}
