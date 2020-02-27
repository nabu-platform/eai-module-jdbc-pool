package nabu.protocols.jdbc.pool.types;

public class TableKeyDescription extends TableDescription {
	private String localField, remoteField;

	public String getLocalField() {
		return localField;
	}
	public void setLocalField(String localField) {
		this.localField = localField;
	}
	public String getRemoteField() {
		return remoteField;
	}
	public void setRemoteField(String remoteField) {
		this.remoteField = remoteField;
	}
}
