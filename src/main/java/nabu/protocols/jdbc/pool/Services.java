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

package nabu.protocols.jdbc.pool;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.api.EnvironmentSpecific;
import be.nabu.eai.api.ValueEnumerator;
import be.nabu.eai.module.jdbc.pool.JDBCPoolArtifact;
import be.nabu.eai.module.jdbc.pool.JDBCPoolUtils;
import be.nabu.eai.module.jdbc.pool.SQLDialectEnumerator;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.impl.RepositoryArtifactResolver;
import be.nabu.eai.repository.impl.RepositoryArtifactResolver.Strategy;
import be.nabu.eai.repository.util.ClassAdapter;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.artifacts.api.ArtifactProxy;
import be.nabu.libs.datastore.DatastoreOutputStream;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.ServiceUtils;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.jdbc.JDBCServiceInstance;
import be.nabu.libs.services.jdbc.JDBCUtils;
import be.nabu.libs.services.jdbc.api.DataSourceWithDialectProviderArtifact;
import be.nabu.libs.services.jdbc.api.SQLDialect;
import be.nabu.libs.types.DefinedTypeResolverFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.DefinedTypeRegistry;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.properties.CollectionNameProperty;
import be.nabu.libs.types.properties.ForeignKeyProperty;
import be.nabu.libs.types.properties.PrimaryKeyProperty;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import nabu.protocols.jdbc.pool.types.JDBCPoolInformation;
import nabu.protocols.jdbc.pool.types.SqlResult;
import nabu.protocols.jdbc.pool.types.TableChange;
import nabu.protocols.jdbc.pool.types.TableDescription;

@WebService
public class Services {

	public static class DumpMapping {
		private String table;
		private String clause;
		public String getTable() {
			return table;
		}
		public void setTable(String table) {
			this.table = table;
		}
		public String getClause() {
			return clause;
		}
		public void setClause(String clause) {
			this.clause = clause;
		}
	}
	
	public static class DumpDetails {
		private Class<SQLDialect> dialect;
		private Boolean includeDdl, includeDml;
		private String tableBlacklistRegex, tableWhitelistRegex;
		private List<DumpMapping> mappings;
		
		@NotNull
		@EnvironmentSpecific
		@ValueEnumerator(enumerator = SQLDialectEnumerator.class)
		@XmlJavaTypeAdapter(ClassAdapter.class)
		public Class<SQLDialect> getDialect() {
			return dialect;
		}
		public void setDialect(Class<SQLDialect> dialect) {
			this.dialect = dialect;
		}
		public Boolean getIncludeDdl() {
			return includeDdl;
		}
		public void setIncludeDdl(Boolean includeDdl) {
			this.includeDdl = includeDdl;
		}
		public Boolean getIncludeDml() {
			return includeDml;
		}
		public void setIncludeDml(Boolean includeDml) {
			this.includeDml = includeDml;
		}
		public String getTableBlacklistRegex() {
			return tableBlacklistRegex;
		}
		public void setTableBlacklistRegex(String tableBlacklistRegex) {
			this.tableBlacklistRegex = tableBlacklistRegex;
		}
		public List<DumpMapping> getMappings() {
			return mappings;
		}
		public void setMappings(List<DumpMapping> mappings) {
			this.mappings = mappings;
		}
		public String getTableWhitelistRegex() {
			return tableWhitelistRegex;
		}
		public void setTableWhitelistRegex(String tableWhitelistRegex) {
			this.tableWhitelistRegex = tableWhitelistRegex;
		}
	}
	
	public SqlResult execute(@WebParam(name = "jdbcPoolId") String jdbcPoolId, @WebParam(name = "sql") String sql, @WebParam(name = "limit") Integer limit, @WebParam(name = "offset") Long offset) throws ServiceException {
		JDBCPoolArtifact resolve;
		if (jdbcPoolId != null) { 
			resolve = (JDBCPoolArtifact) EAIResourceRepository.getInstance().resolve(jdbcPoolId);
		}
		else {
			DataSourceWithDialectProviderArtifact resolveFor = EAIResourceRepository.getInstance().resolveFor(ServiceUtils.getServiceContext(ServiceRuntime.getRuntime()), DataSourceWithDialectProviderArtifact.class);
			if (resolveFor instanceof ArtifactProxy)  {
				Artifact proxied = ((ArtifactProxy) resolveFor).getProxied();
				if (proxied instanceof DataSourceWithDialectProviderArtifact) {
					resolveFor = (DataSourceWithDialectProviderArtifact) proxied;
				}
			}
			resolve = (JDBCPoolArtifact) resolveFor;
		}
		if (resolve == null) {
			throw new IllegalStateException("Could not find matching jdbc pool id");
		}
		ComplexContent input = resolve.getServiceInterface().getInputDefinition().newInstance();
		input.set("limit", limit);
		input.set("offset", offset);
		input.set("sql", sql);
		ComplexContent output = resolve.newInstance().execute(ServiceRuntime.getRuntime().getExecutionContext(), input);
		SqlResult result = new SqlResult();
		result.setResults(output == null ? null : (List<Object>) output.get("results"));
		return result;
	}
	
	@WebResult(name = "information")
	public JDBCPoolInformation information(@WebParam(name = "jdbcPoolId") String jdbcPoolId) {
		if (jdbcPoolId != null) {
			JDBCPoolArtifact resolve = (JDBCPoolArtifact) EAIResourceRepository.getInstance().resolve(jdbcPoolId);
			if (resolve != null) {
				return toInformation(resolve);
			}
		}
		return null;
	}

	private JDBCPoolInformation toInformation(JDBCPoolArtifact resolve) {
		JDBCPoolInformation information = new JDBCPoolInformation();
		information.setStarted(resolve.isStarted());
		information.setId(resolve.getId());
		information.setDefaultLanguage(resolve.getConfig().getDefaultLanguage());
		information.setTranslatable(resolve.getConfig().getTranslationGet() != null && resolve.getConfig().getTranslationSet() != null);
		if (resolve.getConfig().getDialect() != null) {
			information.setDialect(resolve.getConfig().getDialect().getName());
		}
		information.setDriverClass(resolve.getConfig().getDriverClassName());
		information.setJdbcUrl(resolve.getConfig().getJdbcUrl());
		information.setUsername(resolve.getConfig().getUsername());
		information.setProxy(resolve.getConfig().getPoolProxy() != null);
		return information;
	}
	
	@WebResult(name = "uri")
	public URI dump(@WebParam(name = "jdbcPoolId") String jdbcPoolId, @WebParam(name = "context") String context, @WebParam(name = "schemaPattern") String schemaPattern, @WebParam(name = "tablePattern") String tablePattern, @WebParam(name = "details") DumpDetails details) throws URISyntaxException, IOException, SQLException, InstantiationException, IllegalAccessException {
		if (jdbcPoolId != null) {
			JDBCPoolArtifact resolve = (JDBCPoolArtifact) EAIResourceRepository.getInstance().resolve(jdbcPoolId);
			if (resolve == null) {
				throw new IllegalArgumentException("Could not find pool: " + jdbcPoolId);
			}
			Connection connection = resolve.getDataSource().getConnection();
			try {
				DatastoreOutputStream streamable = nabu.frameworks.datastore.Services.streamable(ServiceRuntime.getRuntime(), context, "dump.sql", "text/sql");
				OutputStreamWriter writer = new OutputStreamWriter(streamable, Charset.forName("UTF-8"));
				try {
					SQLDialect dialect = details == null || details.getDialect() == null ? resolve.getDialect() : details.getDialect().newInstance();
					boolean includeDdl = details == null || (details.getIncludeDdl() != null && details.getIncludeDdl());
					boolean includeDml = details != null && details.getIncludeDml() != null && details.getIncludeDml();
					JDBCPoolUtils.dump(connection, null, schemaPattern, tablePattern, writer, dialect, includeDdl, includeDml, details == null ? null : details.getTableBlacklistRegex(), details == null ? null : details.getTableWhitelistRegex(), details == null ? null : details.getMappings());
				}
				finally {	
					writer.close();
				}
				return streamable.getURI();
			}
			finally {
				connection.close();
			}
		}
		return null;
	}
	
	@WebResult(name = "changes")
	public List<TableChange> synchronizeManagedTypes(@NotNull @WebParam(name = "jdbcPoolId") String jdbcPoolId, @WebParam(name = "force") Boolean force) throws SQLException {
		JDBCPoolArtifact resolve = (JDBCPoolArtifact) EAIResourceRepository.getInstance().resolve(jdbcPoolId);
		if (resolve == null) {
			throw new IllegalArgumentException("Could not find pool: " + jdbcPoolId);
		}
		return resolve.synchronizeTypes(force != null && force);
	}
	
	@WebResult(name = "managedTypes")
	public List<String> getManagedTypes(@WebParam(name = "jdbcPoolId") String jdbcPoolId) {
		if (jdbcPoolId == null) {
			jdbcPoolId = connectionForContext(null, null, null);
		}
		if (jdbcPoolId == null) {
			return null;
		}
		JDBCPoolArtifact resolve = (JDBCPoolArtifact) EAIResourceRepository.getInstance().resolve(jdbcPoolId);
		if (resolve == null) {
			throw new IllegalArgumentException("Could not find pool: " + jdbcPoolId);
		}
		List<String> result = new ArrayList<String>();
		for (DefinedType managed : resolve.getManagedTypes()) {
			result.add(managed.getId());
		}
		return result;
	}
	
	@WebResult(name = "collectionTypeIds")
	public List<String> listCollectionTypes(@WebParam(name = "entryId") String entryId, @WebParam(name = "recursive") Boolean recursive) throws IOException, ParseException {
		List<String> list = new ArrayList<String>();
		Entry entry = EAIResourceRepository.getInstance().getEntry(entryId);
		if (entry != null) {
			for (Entry child : entry) {
				if (child.isNode()) {
					Artifact artifact = child.getNode().getArtifact();
					if (artifact instanceof DefinedType) {
						String collectionName = ValueUtils.getValue(CollectionNameProperty.getInstance(), ((DefinedType) artifact).getProperties());
						if (collectionName != null) {
							list.add(artifact.getId());
						}
					}
				}
				else if (recursive != null && recursive) {
					list.addAll(listCollectionTypes(child.getId(), recursive));
				}
			}
		}
		return list;
	}
	
	@WebResult(name = "pools")
	public List<JDBCPoolInformation> listForDataModel(@WebParam(name = "dataModelId") String modelId) {
		if (modelId == null) {
			return null;
		}
		DefinedTypeRegistry registry = (DefinedTypeRegistry) EAIResourceRepository.getInstance().resolve(modelId);
		if (registry == null) {
			throw new IllegalArgumentException("Unknown data model: " + modelId);
		}
		List<JDBCPoolArtifact> supported = new ArrayList<JDBCPoolArtifact>();
		for (JDBCPoolArtifact pool : EAIResourceRepository.getInstance().getArtifacts(JDBCPoolArtifact.class)) {
			if (pool.getConfig().getManagedModels() != null && pool.getConfig().getManagedModels().contains(registry)) {
				JDBCPoolArtifact poolToAdd = pool.getConfig().getPoolProxy() == null ? pool : pool.getConfig().getPoolProxy();
				if (!supported.contains(poolToAdd)) {
					supported.add(poolToAdd);
				}
			}
		}
		List<JDBCPoolInformation> informations = new ArrayList<JDBCPoolInformation>();
		for (JDBCPoolArtifact single : supported) {
			informations.add(toInformation(single));
		}
		return informations;
	}
	
	@WebResult(name = "changes")
	public List<TableChange> synchronize(@NotNull @WebParam(name = "jdbcPoolId") String jdbcPoolId, @WebParam(name = "force") Boolean force, @WebParam(name = "execute") Boolean execute, @WebParam(name = "typeIds") List<String> types) throws SQLException {
		JDBCPoolArtifact resolve = (JDBCPoolArtifact) EAIResourceRepository.getInstance().resolve(jdbcPoolId);
		if (resolve == null) {
			throw new IllegalArgumentException("Could not find pool: " + jdbcPoolId);
		}
		if (types != null && !types.isEmpty()) {
			List<DefinedType> resolvedTypes = new ArrayList<DefinedType>();
			for (String type : types) {
				DefinedType definedType = DefinedTypeResolverFactory.getInstance().getResolver().resolve(type);
				if (definedType != null) {
					resolvedTypes.add(definedType);
				}
			}
			if (!resolvedTypes.isEmpty()) {
				return resolve.synchronizeTypes(force != null && force, execute != null && execute, resolvedTypes);
			}
		}
		return null;
	}
	
	@WebResult(name = "uri")
	public URI restore(@WebParam(name = "jdbcPoolId") String jdbcPoolId, @WebParam(name = "uri") URI uri, @WebParam(name = "context") String context) throws SQLException, URISyntaxException, IOException {
		JDBCPoolArtifact resolve = (JDBCPoolArtifact) EAIResourceRepository.getInstance().resolve(jdbcPoolId);
		if (resolve == null) {
			throw new IllegalArgumentException("Could not find pool: " + jdbcPoolId);
		}
		Connection connection = resolve.getDataSource().getConnection();
		try {
			ReadableContainer<ByteBuffer> readableContainer = ResourceUtils.toReadableContainer(uri, null);
			try {
				DatastoreOutputStream streamable = nabu.frameworks.datastore.Services.streamable(ServiceRuntime.getRuntime(), context, "dump.sql", "text/sql");
				OutputStreamWriter writer = new OutputStreamWriter(streamable, Charset.forName("UTF-8"));
				try {
					JDBCPoolUtils.restore(connection, new InputStreamReader(IOUtils.toInputStream(readableContainer), Charset.forName("UTF-8")), writer);
				}
				finally {
					writer.close();
				}
				return streamable.getURI();
			}
			finally {
				readableContainer.close();
			}
		}
		finally {
			connection.close();
		}
	}

	@WebResult(name = "tables")
	public List<TableDescription> listTables(@WebParam(name = "jdbcPoolId") String jdbcPoolId, @WebParam(name = "catalogus") String catalogus, @WebParam(name = "schemaPattern") String schema, @WebParam(name = "tablePattern") String tableNamePattern, @WebParam(name = "limitToCurrentSchema") Boolean limitToCurrentSchema) throws SQLException {
		JDBCPoolArtifact resolve = (JDBCPoolArtifact) EAIResourceRepository.getInstance().resolve(jdbcPoolId);
		if (resolve == null) {
			throw new IllegalArgumentException("Could not find pool: " + jdbcPoolId);
		}
		return JDBCPoolUtils.describeTables(catalogus, schema, tableNamePattern, resolve.getDataSource(), limitToCurrentSchema != null && limitToCurrentSchema);
	}

	@WebResult(name = "connectionId")
	public String connectionForContext(@WebParam(name = "context") String context, @WebParam(name = "strategy") Strategy strategy, @WebParam(name = "requiredDependencies") List<String> requiredDependencies) {
		if (context == null) {
			context = ServiceUtils.getServiceContext(ServiceRuntime.getRuntime());
		}
		if (context == null) {
			throw new IllegalArgumentException("No context given nor could a service context be determined");
		}
		RepositoryArtifactResolver<DataSourceWithDialectProviderArtifact> resolver = new RepositoryArtifactResolver<DataSourceWithDialectProviderArtifact>(EAIResourceRepository.getInstance(), DataSourceWithDialectProviderArtifact.class);
		if (requiredDependencies != null) {
			resolver.setRequiredDependencies(requiredDependencies);
		}
		if (strategy != null) {
			resolver.setStrategy(strategy);
		}
		DataSourceWithDialectProviderArtifact resolvedArtifact = resolver.getResolvedArtifact(context);
		return resolvedArtifact == null ? null : resolvedArtifact.getId();
	}
	
	@WebResult(name = "sqls")
	public String updateReference(@NotNull @WebParam(name = "connectionId") String connectionId, @NotNull @WebParam(name = "typeId") String typeId, @WebParam(name = "oldValue") Object oldValue, @WebParam(name = "newValue") Object newValue) {
		// "usually" a foreign key points to a primary key
		// it "can" also point to a unique constrained key, but we generally don't use this
		// so for now, we just resolve the primary key field from the given type
		DefinedType type = DefinedTypeResolverFactory.getInstance().getResolver().resolve(typeId);
		if (type == null) {
			throw new IllegalArgumentException("Type is required");
		}
		String fieldName = null;
		for (Element<?> child : TypeUtils.getAllChildren((ComplexType) type)) {
			Boolean primaryKey = ValueUtils.getValue(PrimaryKeyProperty.getInstance(), child.getProperties());
			if (primaryKey != null && primaryKey) {
				fieldName = child.getName();
				break;
			}
		}
		if (fieldName == null) {
			throw new IllegalArgumentException("Can not find primary key to re-reference");
		}
		JDBCPoolArtifact pool = (JDBCPoolArtifact) EAIResourceRepository.getInstance().resolve(connectionId);
		if (pool == null) {
			throw new IllegalArgumentException("Could not find pool: " + connectionId);
		}
		List<String> sqls = new ArrayList<String>();
		String foreignKeyToMatch = typeId + ":" + fieldName;
		for (DefinedType managedType : pool.getManagedTypes()) {
			for (ComplexType tableType : JDBCUtils.getAllTypes((ComplexType) managedType)) {
				for (Element<?> child : JDBCUtils.getFieldsInTable(tableType)) {
					String foreignKey = ValueUtils.getValue(ForeignKeyProperty.getInstance(), child.getProperties());
					// got one, generate an update statement
					if (foreignKey != null && foreignKey.equals(foreignKeyToMatch)) {
						String tableName = JDBCServiceInstance.uncamelify(JDBCUtils.getTypeName(tableType, true));
						boolean isNumber = Number.class.isAssignableFrom(((SimpleType<?>) child.getType()).getInstanceClass());
						String tableFieldName = EAIRepositoryUtils.uncamelify(child.getName());
						String update = "update " + tableName + " set " + tableFieldName + " = " + (isNumber ? "" : "'") + newValue + (isNumber ? "" : "'") + " where " + tableFieldName + " = " + (isNumber ? "" : "'") + oldValue + (isNumber ? "" : "'");
						sqls.add(update);
						// TODO: can autorun these if we are sure
						// could also switch to prepared at that point if necessary
					}
				}
			}
		}
		StringBuilder result = new StringBuilder();
		for (String sql : sqls) {
			result.append(sql).append(";\n");
		}
		return result.toString();
	}
	
	@WebResult(name = "connections")
	public List<String> listConnections(@WebParam(name = "dialect") String dialect, @WebParam(name = "driver") String driver, @WebParam(name = "active") Boolean active) {
		List<String> connections = new ArrayList<String>();
		for (DataSourceWithDialectProviderArtifact connection : EAIResourceRepository.getInstance().getArtifacts(DataSourceWithDialectProviderArtifact.class)) {
			// we currently want unique connections, so not proxied connections, we can add a parameter to tweak this behavior if needed
			if (connection instanceof ArtifactProxy) {
				Artifact proxied = ((ArtifactProxy) connection).getProxied();
				if (proxied != null) {
					continue;
				}
			}
			boolean matches = true;
			if (dialect != null) {
				matches &= connection.getDialect() != null && dialect.equals(connection.getDialect().getClass().getName());
			}
			if (driver != null && connection instanceof JDBCPoolArtifact) {
				matches &= ((JDBCPoolArtifact) connection).getConfig().getDriverClassName() != null && driver.equals(((JDBCPoolArtifact) connection).getConfig().getDriverClassName());
			}
			if (active != null && connection instanceof JDBCPoolArtifact) {
				// an empty jdbc url points to an inactive connection
				String jdbcUrl = ((JDBCPoolArtifact) connection).getConfig().getJdbcUrl();
				boolean isActive = jdbcUrl != null && !jdbcUrl.trim().isEmpty();
				if (active && !isActive) {
					matches = false;
				}
				else if (!active && isActive) {
					matches = false;
				}
			}
			if (matches && !connections.contains(connection.getId())) {
				connections.add(connection.getId());
			}
		}
		return connections;
	}
}
