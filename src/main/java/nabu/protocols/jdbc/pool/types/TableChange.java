package nabu.protocols.jdbc.pool.types;

public class TableChange {
	private String table, column, script, reason;

	public String getTable() {
		return table;
	}
	public void setTable(String table) {
		this.table = table;
	}

	public String getColumn() {
		return column;
	}
	public void setColumn(String column) {
		this.column = column;
	}

	public String getScript() {
		return script;
	}
	public void setScript(String script) {
		this.script = script;
	}

	public String getReason() {
		return reason;
	}
	public void setReason(String reason) {
		this.reason = reason;
	}
	
}
