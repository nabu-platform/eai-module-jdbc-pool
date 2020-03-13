package nabu.protocols.jdbc.pool;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import be.nabu.eai.api.EnvironmentSpecific;
import be.nabu.eai.api.ValueEnumerator;
import be.nabu.eai.module.jdbc.pool.JDBCPoolArtifact;
import be.nabu.eai.module.jdbc.pool.JDBCPoolUtils;
import be.nabu.eai.module.jdbc.pool.SQLDialectEnumerator;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.util.ClassAdapter;
import be.nabu.libs.datastore.DatastoreOutputStream;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.jdbc.api.SQLDialect;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import nabu.protocols.jdbc.pool.types.JDBCPoolInformation;
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
	
	@WebResult(name = "information")
	public JDBCPoolInformation information(@WebParam(name = "jdbcPoolId") String jdbcPoolId) {
		if (jdbcPoolId != null) {
			JDBCPoolArtifact resolve = (JDBCPoolArtifact) EAIResourceRepository.getInstance().resolve(jdbcPoolId);
			if (resolve != null) {
				JDBCPoolInformation information = new JDBCPoolInformation();
				information.setDefaultLanguage(resolve.getConfig().getDefaultLanguage());
				information.setTranslatable(resolve.getConfig().getTranslationGet() != null && resolve.getConfig().getTranslationSet() != null);
				if (resolve.getConfig().getDialect() != null) {
					information.setDialect(resolve.getConfig().getDialect().getName());
				}
				information.setDriverClass(resolve.getConfig().getDriverClassName());
				information.setJdbcUrl(resolve.getConfig().getJdbcUrl());
				information.setUsername(resolve.getConfig().getUsername());
				return information;
			}
		}
		return null;
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
					boolean includeDdl = details.getIncludeDdl() != null && details.getIncludeDdl();
					boolean includeDml = details.getIncludeDml() != null && details.getIncludeDml();
					JDBCPoolUtils.dump(connection, null, schemaPattern, tablePattern, writer, dialect, includeDdl, includeDml, details.getTableBlacklistRegex(), details.getTableWhitelistRegex(), details.getMappings());
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
	public List<TableChange> synchronizeManagedTypes(@WebParam(name = "jdbcPoolId") String jdbcPoolId, @WebParam(name = "force") Boolean force) throws SQLException {
		JDBCPoolArtifact resolve = (JDBCPoolArtifact) EAIResourceRepository.getInstance().resolve(jdbcPoolId);
		if (resolve == null) {
			throw new IllegalArgumentException("Could not find pool: " + jdbcPoolId);
		}
		return resolve.synchronizeTypes(force != null && force);
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
	public List<TableDescription> listTables(@WebParam(name = "jdbcPoolId") String jdbcPoolId, @WebParam(name = "catalogus") String catalogus, @WebParam(name = "schema") String schema, @WebParam(name = "tablePattern") String tableNamePattern, @WebParam(name = "limitToCurrentSchema") Boolean limitToCurrentSchema) throws SQLException {
		JDBCPoolArtifact resolve = (JDBCPoolArtifact) EAIResourceRepository.getInstance().resolve(jdbcPoolId);
		if (resolve == null) {
			throw new IllegalArgumentException("Could not find pool: " + jdbcPoolId);
		}
		return JDBCPoolUtils.describeTables(catalogus, schema, tableNamePattern, resolve.getDataSource(), limitToCurrentSchema != null && limitToCurrentSchema);
	}

	
}
