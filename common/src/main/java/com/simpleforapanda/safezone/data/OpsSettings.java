package com.simpleforapanda.safezone.data;

public class OpsSettings {
	public static final boolean DEFAULT_AUDIT_LOG_ENABLED = false;
	public static final boolean DEFAULT_MIRROR_AUDIT_TO_SERVER_LOG = false;
	public static final boolean DEFAULT_CREATE_DATA_BACKUPS = false;
	public static final boolean DEFAULT_RECOVER_FROM_BACKUP_ON_LOAD_FAILURE = false;

	public boolean auditLogEnabled = DEFAULT_AUDIT_LOG_ENABLED;
	public boolean mirrorAuditToServerLog = DEFAULT_MIRROR_AUDIT_TO_SERVER_LOG;
	public boolean createDataBackups = DEFAULT_CREATE_DATA_BACKUPS;
	public boolean recoverFromBackupOnLoadFailure = DEFAULT_RECOVER_FROM_BACKUP_ON_LOAD_FAILURE;

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
