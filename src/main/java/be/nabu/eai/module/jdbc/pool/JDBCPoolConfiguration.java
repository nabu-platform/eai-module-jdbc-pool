/*
* Copyright (C) 2015 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.eai.module.jdbc.pool;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.api.ArtifactFilter;
import be.nabu.eai.api.InterfaceFilter;
import be.nabu.eai.api.ValueEnumerator;
import be.nabu.eai.repository.jaxb.ArtifactXMLAdapter;
import be.nabu.eai.repository.util.ClassAdapter;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.jdbc.api.DataSourceWithAffixes.AffixMapping;
import be.nabu.libs.services.jdbc.api.SQLDialect;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.DefinedTypeRegistry;
import be.nabu.libs.types.api.annotation.Field;
import be.nabu.utils.security.EncryptionXmlAdapter;

@XmlRootElement(name = "jdbcPool")
@XmlType(propOrder = { "poolProxy", "driverClassName", "jdbcUrl", "username", "password", "context", "connectionTimeout", "idleTimeout", "maximumPoolSize", "minimumIdle", "autoCommit", "maxLifetime", "dialect", "enableMetrics", 
	"defaultLanguage", "translationGet", "translationSet", "translationGetBinding", "translationMapLanguage", "affixes", "managedModels", "managedTypes" })
public class JDBCPoolConfiguration {
	private String driverClassName, jdbcUrl, username, password, context;
	private Long connectionTimeout, idleTimeout, maxLifetime;
	private Integer maximumPoolSize, minimumIdle;
	private Boolean autoCommit;
	private Class<SQLDialect> dialect;
	private Boolean enableMetrics;
	private List<AffixMapping> affixes;
	
	private String defaultLanguage;
	private DefinedService translationGet, translationSet, translationGetBinding, translationMapLanguage;
	private List<DefinedType> managedTypes;
	private List<DefinedTypeRegistry> managedModels;
	private JDBCPoolArtifact poolProxy;

	// you can use a different database
	@Field(hide = "poolProxy != null", environmentSpecific = true, minOccurs = 1)
	@ValueEnumerator(enumerator = SQLDriverEnumerator.class)
	public String getDriverClassName() {
		return driverClassName;
	}
	public void setDriverClassName(String driverClassName) {
		this.driverClassName = driverClassName;
	}
	
	@Field(hide = "poolProxy != null", environmentSpecific = true, minOccurs = 1)
	public String getJdbcUrl() {
		return jdbcUrl;
	}
	public void setJdbcUrl(String jdbcUrl) {
		this.jdbcUrl = jdbcUrl;
	}
	
	@Field(hide = "poolProxy != null", environmentSpecific = true)
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	
	@Field(hide = "poolProxy != null", environmentSpecific = true)
	@XmlJavaTypeAdapter(value=EncryptionXmlAdapter.class)
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	
	@Field(hide = "poolProxy != null", environmentSpecific = true)
	public Long getConnectionTimeout() {
		return connectionTimeout;
	}
	public void setConnectionTimeout(Long connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}
	
	@Field(hide = "poolProxy != null", environmentSpecific = true)
	public Long getIdleTimeout() {
		return idleTimeout;
	}
	public void setIdleTimeout(Long idleTimeout) {
		this.idleTimeout = idleTimeout;
	}
	
	@Field(hide = "poolProxy != null", environmentSpecific = true)
	public Integer getMaximumPoolSize() {
		return maximumPoolSize;
	}
	public void setMaximumPoolSize(Integer maximumPoolSize) {
		this.maximumPoolSize = maximumPoolSize;
	}
	
	@Field(hide = "poolProxy != null", environmentSpecific = true)
	public Integer getMinimumIdle() {
		return minimumIdle;
	}
	public void setMinimumIdle(Integer minimumIdle) {
		this.minimumIdle = minimumIdle;
	}
	
	@Field(hide = "poolProxy != null", environmentSpecific = true)
	public Boolean getAutoCommit() {
		return autoCommit;
	}
	public void setAutoCommit(Boolean autoCommit) {
		this.autoCommit = autoCommit;
	}
	
	@Field(hide = "poolProxy != null", environmentSpecific = true)
	public Long getMaxLifetime() {
		return maxLifetime;
	}
	public void setMaxLifetime(Long maxLifetime) {
		this.maxLifetime = maxLifetime;
	}

	@Field(hide = "poolProxy != null", environmentSpecific = true)
	@ValueEnumerator(enumerator = SQLDialectEnumerator.class)
	@XmlJavaTypeAdapter(ClassAdapter.class)
	public Class<SQLDialect> getDialect() {
		return dialect;
	}
	public void setDialect(Class<SQLDialect> dialect) {
		this.dialect = dialect;
	}
	
	@Field(hide = "poolProxy != null", environmentSpecific = true)
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
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	@InterfaceFilter(implement = "be.nabu.libs.services.jdbc.api.JDBCTranslator.getBinding")
	public DefinedService getTranslationGetBinding() {
		return translationGetBinding;
	}
	public void setTranslationGetBinding(DefinedService translationGetBinding) {
		this.translationGetBinding = translationGetBinding;
	}
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	@InterfaceFilter(implement = "be.nabu.libs.services.jdbc.api.JDBCTranslator.mapLanguage")
	public DefinedService getTranslationMapLanguage() {
		return translationMapLanguage;
	}
	public void setTranslationMapLanguage(DefinedService translationMapLanguage) {
		this.translationMapLanguage = translationMapLanguage;
	}
	
	@ArtifactFilter(suggest = false)
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public List<DefinedType> getManagedTypes() {
		return managedTypes;
	}
	public void setManagedTypes(List<DefinedType> managedTypes) {
		this.managedTypes = managedTypes;
	}
	
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public List<DefinedTypeRegistry> getManagedModels() {
		return managedModels;
	}
	public void setManagedModels(List<DefinedTypeRegistry> managedModels) {
		this.managedModels = managedModels;
	}

	@Field(environmentSpecific = true)
	@XmlJavaTypeAdapter(value = ArtifactXMLAdapter.class)
	public JDBCPoolArtifact getPoolProxy() {
		return poolProxy;
	}
	public void setPoolProxy(JDBCPoolArtifact poolProxy) {
		this.poolProxy = poolProxy;
	}
	
}
