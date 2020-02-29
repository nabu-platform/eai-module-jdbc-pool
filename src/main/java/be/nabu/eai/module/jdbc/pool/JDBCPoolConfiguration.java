package be.nabu.eai.module.jdbc.pool;

import java.util.List;

import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.api.EnvironmentSpecific;
import be.nabu.eai.api.InterfaceFilter;
import be.nabu.eai.api.ValueEnumerator;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.eai.repository.util.ClassAdapter;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.jdbc.api.DataSourceWithAffixes.AffixMapping;
import be.nabu.libs.services.jdbc.api.SQLDialect;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.utils.security.EncryptionXmlAdapter;

@XmlRootElement(name = "jdbcPool")
@XmlType(propOrder = { "driverClassName", "jdbcUrl", "username", "password", "context", "connectionTimeout", "idleTimeout", "maximumPoolSize", "minimumIdle", "autoCommit", "maxLifetime", "dialect", "enableMetrics", 
	"defaultLanguage", "translationGet", "translationSet", "affixes", "managedTypes" })
public class JDBCPoolConfiguration {
	private String driverClassName, jdbcUrl, username, password, context;
	private Long connectionTimeout, idleTimeout, maxLifetime;
	private Integer maximumPoolSize, minimumIdle;
	private Boolean autoCommit;
	private Class<SQLDialect> dialect;
	private Boolean enableMetrics;
	private List<AffixMapping> affixes;
	
	private String defaultLanguage;
	private DefinedService translationGet, translationSet;
	private List<String> managedTypes;
	
	@EnvironmentSpecific	// you can use a different database
	@ValueEnumerator(enumerator = SQLDriverEnumerator.class)
	@NotNull
	public String getDriverClassName() {
		return driverClassName;
	}
	public void setDriverClassName(String driverClassName) {
		this.driverClassName = driverClassName;
	}
	
	@EnvironmentSpecific
	@NotNull
	public String getJdbcUrl() {
		return jdbcUrl;
	}
	public void setJdbcUrl(String jdbcUrl) {
		this.jdbcUrl = jdbcUrl;
	}
	
	@EnvironmentSpecific
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	
	@EnvironmentSpecific
	@XmlJavaTypeAdapter(value=EncryptionXmlAdapter.class)
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	
	public Long getConnectionTimeout() {
		return connectionTimeout;
	}
	public void setConnectionTimeout(Long connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}
	
	public Long getIdleTimeout() {
		return idleTimeout;
	}
	public void setIdleTimeout(Long idleTimeout) {
		this.idleTimeout = idleTimeout;
	}
	
	@EnvironmentSpecific
	public Integer getMaximumPoolSize() {
		return maximumPoolSize;
	}
	public void setMaximumPoolSize(Integer maximumPoolSize) {
		this.maximumPoolSize = maximumPoolSize;
	}
	
	public Integer getMinimumIdle() {
		return minimumIdle;
	}
	public void setMinimumIdle(Integer minimumIdle) {
		this.minimumIdle = minimumIdle;
	}
	
	public Boolean getAutoCommit() {
		return autoCommit;
	}
	public void setAutoCommit(Boolean autoCommit) {
		this.autoCommit = autoCommit;
	}
	
	public Long getMaxLifetime() {
		return maxLifetime;
	}
	public void setMaxLifetime(Long maxLifetime) {
		this.maxLifetime = maxLifetime;
	}

	@EnvironmentSpecific
	@ValueEnumerator(enumerator = SQLDialectEnumerator.class)
	@XmlJavaTypeAdapter(ClassAdapter.class)
	public Class<SQLDialect> getDialect() {
		return dialect;
	}
	public void setDialect(Class<SQLDialect> dialect) {
		this.dialect = dialect;
	}
	
	@EnvironmentSpecific
	public Boolean getEnableMetrics() {
		return enableMetrics;
	}
	public void setEnableMetrics(Boolean enableMetrics) {
		this.enableMetrics = enableMetrics;
	}
	
	public List<AffixMapping> getAffixes() {
		return affixes;
	}
	public void setAffixes(List<AffixMapping> affixes) {
		this.affixes = affixes;
	}
	
	public String getContext() {
		return context;
	}
	public void setContext(String context) {
		this.context = context;
	}
	
	public String getDefaultLanguage() {
		return defaultLanguage;
	}
	public void setDefaultLanguage(String defaultLanguage) {
		this.defaultLanguage = defaultLanguage;
	}
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	@InterfaceFilter(implement = "be.nabu.libs.services.jdbc.api.JDBCTranslator.get")
	public DefinedService getTranslationGet() {
		return translationGet;
	}
	public void setTranslationGet(DefinedService translationGet) {
		this.translationGet = translationGet;
	}
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	@InterfaceFilter(implement = "be.nabu.libs.services.jdbc.api.JDBCTranslator.set")
	public DefinedService getTranslationSet() {
		return translationSet;
	}
	public void setTranslationSet(DefinedService translationSet) {
		this.translationSet = translationSet;
	}
	
	public List<String> getManagedTypes() {
		return managedTypes;
	}
	public void setManagedTypes(List<String> managedTypes) {
		this.managedTypes = managedTypes;
	}
	
}
