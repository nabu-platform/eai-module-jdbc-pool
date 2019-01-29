package nabu.protocols.jdbc.pool.types;

public class JDBCPoolInformation {
	private String defaultLanguage;
	private boolean translatable;
	private String jdbcUrl, username, driverClass, dialect;
	public String getDefaultLanguage() {
		return defaultLanguage;
	}
	public void setDefaultLanguage(String defaultLanguage) {
		this.defaultLanguage = defaultLanguage;
	}
	public boolean isTranslatable() {
		return translatable;
	}
	public void setTranslatable(boolean translatable) {
		this.translatable = translatable;
	}
	public String getJdbcUrl() {
		return jdbcUrl;
	}
	public void setJdbcUrl(String jdbcUrl) {
		this.jdbcUrl = jdbcUrl;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getDriverClass() {
		return driverClass;
	}
	public void setDriverClass(String driverClass) {
		this.driverClass = driverClass;
	}
	public String getDialect() {
		return dialect;
	}
	public void setDialect(String dialect) {
		this.dialect = dialect;
	}
}
