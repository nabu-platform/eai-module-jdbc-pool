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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.eai.repository.util.SystemPrincipal;
import be.nabu.libs.artifacts.ExternalDependencyImpl;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.artifacts.api.ArtifactProxy;
import be.nabu.libs.artifacts.api.ContextualArtifact;
import be.nabu.libs.artifacts.api.ExternalDependency;
import be.nabu.libs.artifacts.api.ExternalDependencyArtifact;
import be.nabu.libs.artifacts.api.StartableArtifact;
import be.nabu.libs.artifacts.api.StoppableArtifact;
import be.nabu.libs.artifacts.api.TunnelableArtifact;
import be.nabu.libs.metrics.api.MetricInstance;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.services.jdbc.DefaultDialect;
import be.nabu.libs.services.jdbc.JDBCService;
import be.nabu.libs.services.jdbc.JDBCUtils;
import be.nabu.libs.services.jdbc.api.DataSourceWithAffixes;
import be.nabu.libs.services.jdbc.api.DataSourceWithDialectProviderArtifact;
import be.nabu.libs.services.jdbc.api.DataSourceWithTranslator;
import be.nabu.libs.services.jdbc.api.JDBCTranslator;
import be.nabu.libs.services.jdbc.api.SQLDialect;
import be.nabu.libs.services.pojo.POJOUtils;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.DefinedTypeRegistry;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleTypeWrapper;
import be.nabu.libs.types.api.SynchronizableTypeRegistry;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.java.BeanResolver;
import be.nabu.libs.types.properties.CollectionNameProperty;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.properties.NameProperty;
import be.nabu.libs.types.structure.Structure;
import nabu.protocols.jdbc.pool.types.TableChange;
import nabu.protocols.jdbc.pool.types.TableColumnDescription;
import nabu.protocols.jdbc.pool.types.TableDescription;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class JDBCPoolArtifact extends JAXBArtifact<JDBCPoolConfiguration> implements StartableArtifact, StoppableArtifact, ContextualArtifact, DataSourceWithDialectProviderArtifact, DefinedService, TunnelableArtifact, DataSourceWithAffixes, DataSourceWithTranslator, ExternalDependencyArtifact, ArtifactProxy {

	private HikariDataSource dataSource;
	private Logger logger = LoggerFactory.getLogger(getClass());
	private SQLDialect dialect;
	private MetricInstance metrics;
	private Structure input, output;
	private JDBCTranslator translator;
	
	public JDBCPoolArtifact(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "jdbcPool.xml", JDBCPoolConfiguration.class);
	}

	@Override
	public void stop() {
		if (dataSource != null) {
			dataSource.close();
			dataSource = null;
		}
	}

	@Override
	public void start() {
		if (getConfig().getPoolProxy() != null) {
			if (!getConfig().getPoolProxy().isStarted()) {
				getConfig().getPoolProxy().start();
			}
		}
		else {
			// closing current dataSource
			if (dataSource != null) {
				dataSource.close();
				dataSource = null;
			}
			try {
				// we can not proceed if we don't have a JDBC url
				if (getConfig().getJdbcUrl() != null) {
					Properties properties = getAsProperties();
					if (!properties.isEmpty()) {
						HikariConfig hikariConfig = new HikariConfig(properties);
						if (getConfiguration().getEnableMetrics() != null && getConfiguration().getEnableMetrics()) {
							metrics = getRepository().getMetricInstance(getId());
							if (metrics != null) {
								hikariConfig.setMetricsTrackerFactory(new MetricsTrackerFactoryImpl(metrics));
							}
						}
						dataSource = new HikariDataSource(hikariConfig);
					}
				}
				else {
					logger.warn("Can not start database connection " + getId() + ", no jdbcUrl has been configured");
				}
			}
			catch (Exception e) {
				logger.error("Could not initialize jdbc pool", e);
				if (dataSource != null) {
					try {
						dataSource.close();
					}
					catch (Exception f) {
						// best effort
					}
					finally {
						dataSource = null;
					}
				}
				throw new RuntimeException(e);
			}
		}
	}
	
	private static String getName(Value<?>...properties) {
		String value = ValueUtils.getValue(CollectionNameProperty.getInstance(), properties);
		if (value == null) {
			value = ValueUtils.getValue(NameProperty.getInstance(), properties);
		}
		return EAIRepositoryUtils.uncamelify(value);
	}
	
	public List<DefinedType> getManagedTypes() {
		List<DefinedType> types = new ArrayList<DefinedType>();
		if (getConfig().getManagedTypes() != null) {
			for (DefinedType type : getConfig().getManagedTypes()) {
				if (type != null && !types.contains(type)) {
					types.add(type);
				}
			}
		}
		if (getConfig().getManagedModels() != null) {
			for (DefinedTypeRegistry registry : getConfig().getManagedModels()) {
				if (registry != null) {
					for (String namespace : registry.getNamespaces()) {
						for (ComplexType type : registry.getComplexTypes(namespace)) {
							if (type == null) {
								continue;
							}
							boolean synchronize = true;
							if (registry instanceof SynchronizableTypeRegistry) {
								synchronize = ((SynchronizableTypeRegistry) registry).isSynchronizable(type);
							}
							if (synchronize) {
								String collectionName = ValueUtils.getValue(CollectionNameProperty.getInstance(), type.getProperties());
								if (collectionName != null && type instanceof DefinedType && !types.contains((DefinedType) type)) {
									types.add((DefinedType) type);
								}
							}
						}
					}
				}
			}
		}
		return types;
	}
	
	public List<TableChange> synchronizeTypes(boolean force) throws SQLException {
		return synchronizeTypes(force, true, getManagedTypes());
	}
	
	public List<TableChange> synchronizeTypes(boolean force, boolean execute, List<DefinedType> managedTypes) throws SQLException {
		List<TableChange> changes = new ArrayList<TableChange>();
		if (managedTypes != null && !managedTypes.isEmpty()) {
			logger.info("Starting DDL synchronization for " + getId() + " (force = " + force + ")");
			try {
				List<ComplexType> typesToSync = getTableTypes(managedTypes);
				DataSource dataSource = getDataSource();
				if (dataSource != null) {
					Connection connection = dataSource.getConnection();
					try {
						SQLDialect dialect = getDialect();
						if (dialect == null) {
							dialect = new DefaultDialect();
						}
						JDBCPoolUtils.deepSort(typesToSync, new JDBCPoolUtils.ForeignKeyComparator(false));
						for (ComplexType type : typesToSync) {
							try {
								String tableName = getName(type.getProperties());
								List<TableDescription> describeTables = JDBCPoolUtils.describeTables(null, null, dialect.standardizeTablePattern(tableName), getDataSource(), true);
								// the table exists
								if (describeTables.size() == 1) {
									Map<String, Element<?>> children = new LinkedHashMap<String, Element<?>>();
									for (Element<?> child : JDBCUtils.getFieldsInTable(type)) {
										children.put(EAIRepositoryUtils.uncamelify(child.getName()), child);
									}
									// we remove all the children that already have a column
									TableDescription description = describeTables.get(0);
									List<TableColumnDescription> columns = new ArrayList<TableColumnDescription>(description.getColumnDescriptions());
									Iterator<TableColumnDescription> iterator = columns.iterator();
									while (iterator.hasNext()) {
										TableColumnDescription column = iterator.next();
										String keyMatch = null;
										for (String key : children.keySet()) {
											if (key.equalsIgnoreCase(column.getName())) {
												keyMatch = key;
												break;
											}
										}
										// if we have a key match, first we check if the metadata still checks out
										if (keyMatch != null) {
											Element<?> element = children.get(keyMatch);
											Value<Integer> minOccurs = element.getProperty(MinOccursProperty.getInstance());
											boolean isOptional = minOccurs != null && minOccurs.getValue() != null && minOccurs.getValue() == 0;
											// if the database is defined as optional and the element is not, we actually don't change it
											// chances are that there is already data in there that does not conform to this requirement
											// we will notify you however
											if (column.isOptional() && !isOptional) { 
												logger.warn("[" + getId() + "] Found optional column " + column.getName() + " for table " + tableName + " that is required in data type: " + ((DefinedType) type).getId() + ":" + element.getName());
											}
											// you made it optional in your data type, let's do this!
											else if (!column.isOptional() && isOptional) {
												String nillable = dialect.buildAlterNillable(type, element.getName(), isOptional);
												for (String sql : nillable.split(";")) {
													if (sql.trim().isEmpty()) {
														continue;
													}
													TableChange change = new TableChange();
													change.setTable(description.getName());
													change.setColumn(column.getName());
													change.setScript(sql);
													change.setReason("Column is no longer required, dropping the not null requirement");
													changes.add(change);
													if (execute) {
														Statement statement = connection.createStatement();
														try {
															logger.info("[" + getId() + "] Making column optional: " + column.getName());
															logger.info(sql);
															statement.execute(sql);
														}
														finally {
															statement.close();
														}
													}
												}
											}
											// TODO: check type as well!
										}
										if (keyMatch != null && children.remove(keyMatch) != null) {
											iterator.remove();
										}
									}
									// any remaining columns are no longer in the data type, if they are optional it doesn't matter, otherwise some action will need to be taken
									// currently we won't automatically drop a column...yet...
									for (TableColumnDescription column : columns) {
										if (!column.isOptional() || force) {
											if (force) {
												String drop = dialect.buildDropSQL(type, column.getName());
												for (String sql : drop.split(";")) {
													if (sql.trim().isEmpty()) {
														continue;
													}
													TableChange change = new TableChange();
													change.setTable(description.getName());
													change.setColumn(column.getName());
													change.setScript(sql);
													change.setReason("Column no longer exists in the definition and we forced the drop");
													changes.add(change);
													if (execute) {
														Statement statement = connection.createStatement();
														try {
															logger.info("[" + getId() + "] Dropping existing column: " + column.getName());
															logger.info(sql);
															statement.execute(sql);
														}
														finally {
															statement.close();
														}
													}
												}
											}
											else {
												logger.warn("[" + getId() + "] Found required column " + column.getName() + " for table " + tableName + " that is not present in data type: " + ((DefinedType) type).getId());
											}
										}
									}
									// if we drop the unused columns before we add new ones, some (all?) databases don't allow dropping the last column
									// so if you changed the type substantially, you have to create first, then drop
									// a new problem has cropped up though: you can't add two primary key fields in a lot of cases
									// so if we actually renamed the primary key field, we need to drop it first...
									for (Element<?> newChild : children.values()) {
										String alter = dialect.buildAlterSQL(type, newChild.getName());
										for (String sql : alter.split(";")) {
											if (sql.trim().isEmpty()) {
												continue;
											}
											TableChange change = new TableChange();
											change.setTable(description.getName());
											change.setColumn(newChild.getName());
											change.setScript(sql);
											change.setReason("New column was found in the definition");
											changes.add(change);
											if (execute) {
												Statement statement = connection.createStatement();
												try {
													logger.info("[" + getId() + "] Adding new column: " + newChild.getName());
													logger.info(sql);
													statement.execute(sql);
												}
												finally {
													statement.close();
												}
											}
										}
									}
								}
								// the table does not exist
								else if (describeTables.size() == 0) {
									String create = dialect.buildCreateSQL(type);
									// the default dialect does not generate create scripts
									if (create == null) {
										TableChange change = new TableChange();
										change.setTable(tableName);
										change.setReason("The table does not yet exist, without a proper dialect a create script can not be provided");
										changes.add(change);
										if (execute) {
											throw new RuntimeException("No dialect configured, can not generate correct scripts");
										}
									}
									else {
										for (String sql : create.split(";")) {
											if (sql.trim().isEmpty()) {
												continue;
											}
											TableChange change = new TableChange();
											change.setTable(tableName);
											change.setScript(sql);
											change.setReason("The table did not yet exist");
											changes.add(change);
											if (execute) {
												Statement statement = connection.createStatement();
												try {
													logger.info("[" + getId() + "] Adding new table: " + tableName);
													logger.info(sql);
													statement.execute(sql);
												}
												finally {
													statement.close();
												}
											}
										}
									}
								}
								// there are multiple tables, we could not correctly limit it to the schema
								else {
									throw new IllegalStateException("Could not correctly limit the check to the current schema");
								}
								logger.debug("[" + getId() + "] Synchronized table: " + tableName);
								connection.commit();
							}
							catch (Exception e) {
								logger.error("Could not synchronize type " + ((DefinedType) type).getId() + " to pool " + getId(), e);
								connection.rollback();
							}
						}
					}
					finally {
						connection.close();
					}
					logger.info("Successfully finalized DDL synchronization for " + getId());
				}
				else {
					throw new IllegalStateException("A valid datasource can not be obtained for " + getId() + ", make sure the connection is valid");
				}
			}
			catch (Exception e) {
				logger.error("Could not synchronize types for pool " + getId(), e);
			}
		}
		return changes;
	}

	private List<ComplexType> getTableTypes(List<DefinedType> managedTypes) {
		List<ComplexType> typesToSync = new ArrayList<ComplexType>();
		for (DefinedType type : managedTypes) {
			if (type instanceof ComplexType) {
				// we need to make sure we create them in the correct order
				List<ComplexType> localTypes = new ArrayList<ComplexType>();
				localTypes.add((ComplexType) type);
				Type parent = ((Type) type).getSuperType();
				while (parent instanceof ComplexType) {
					String collectionName = ValueUtils.getValue(CollectionNameProperty.getInstance(), parent.getProperties());
					if (collectionName != null) {
						localTypes.add((ComplexType) parent);
					}
					parent = parent.getSuperType();
				}
				Collections.reverse(localTypes);
				for (ComplexType localType : localTypes) {
					if (!typesToSync.contains(localType)) {
						typesToSync.add(localType);
					}
				}
			}
		}
		return typesToSync;
	}
	
	@Override
	public boolean isStarted() {
		if (getConfig().getPoolProxy() != null) {
			return getConfig().getPoolProxy().isStarted();
		}
		else {
			return dataSource != null;
		}
	}

	@Override
	public DataSource getDataSource() {
		if (getConfig().getPoolProxy() != null) {
			return getConfig().getPoolProxy().getDataSource();
		}
		else {
			if (!isStarted()) {
				start();
			}
			return dataSource;
		}
	}

	private Properties getAsProperties() throws IOException {
		Properties properties = new Properties();
		setIfNotNull(properties, "driverClassName", getConfiguration().getDriverClassName());
		setIfNotNull(properties, "jdbcUrl", getJdbcUrl());
		setIfNotNull(properties, "username", getConfiguration().getUsername());
		setIfNotNull(properties, "password", getConfiguration().getPassword());
		setIfNotNull(properties, "connectionTimeout", getConfiguration().getConnectionTimeout());
		setIfNotNull(properties, "idleTimeout", getConfiguration().getIdleTimeout());
		setIfNotNull(properties, "maximumPoolSize", getConfiguration().getMaximumPoolSize());
		setIfNotNull(properties, "minimumIdle", getConfiguration().getMinimumIdle());
		setIfNotNull(properties, "autoCommit", getConfiguration().getAutoCommit() == null ? false : getConfiguration().getAutoCommit());
		setIfNotNull(properties, "maxLifetime", getConfiguration().getMaxLifetime());
		properties.put("poolName", getId());
		return properties;
	}
	
	private static void setIfNotNull(Properties properties, String name, Object value) {
		if (value != null) {
			properties.put(name, value.toString());
		}
	}
	
	@Override
	public SQLDialect getDialect() {
		if (getConfig().getPoolProxy() != null) {
			return getConfig().getPoolProxy().getDialect();
		}
		else {
			try {
				// if a dialect is set but the properties don't contain one (or a different one) reset it to null
				if (dialect != null && (getConfiguration().getDialect() == null || !getConfiguration().getDialect().equals(dialect.getClass()))) {
					dialect = null;
				}
				// if a dialect is configured but none is found, load it
				if (dialect == null && getConfiguration().getDialect() != null) {
					synchronized(this) {
						if (dialect == null && getConfiguration().getDialect() != null) {
							dialect = (SQLDialect) getConfiguration().getDialect().newInstance();
						}
					}
				}
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
			return dialect;
		}
	}

	@Override
	public boolean isAutoCommit() {
		if (getConfig().getPoolProxy() != null) {
			return getConfig().getPoolProxy().isAutoCommit();
		}
		else {
			try {
				// default is true according to https://github.com/brettwooldridge/HikariCP
				// however, in the above (and as default practice) we set autocommit to false, it is _very_ rarely needed and only causes issues if not set correctly
				return getConfiguration().getAutoCommit() != null && getConfiguration().getAutoCommit();
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public ServiceInterface getServiceInterface() {
		if (input == null) {
			synchronized(this) {
				if (input == null) {
					Structure input = new Structure();
					input.setName("input");
					SimpleTypeWrapper wrapper = SimpleTypeWrapperFactory.getInstance().getWrapper();
					input.add(new SimpleElementImpl<String>("sql", wrapper.wrap(String.class), input));
					input.add(new SimpleElementImpl<String>("resultType", wrapper.wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					input.add(new SimpleElementImpl<String>(JDBCService.TRANSACTION, wrapper.wrap(String.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					input.add(new SimpleElementImpl<Long>(JDBCService.OFFSET, wrapper.wrap(Long.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					input.add(new SimpleElementImpl<Integer>(JDBCService.LIMIT, wrapper.wrap(Integer.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
//					input.add(new ComplexElementImpl(JDBCService.PARAMETERS, (ComplexType) BeanResolver.getInstance().resolve(KeyValuePair.class), input, 
//						new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0),
//						new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)));
					Structure output = new Structure();
					output.setName("output");
					output.add(new ComplexElementImpl(JDBCService.RESULTS, (ComplexType) BeanResolver.getInstance().resolve(Object.class), output, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0),
						new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)));
					this.output = output;
					this.input = input;
				}
			}
		}
		return new ServiceInterface() {
			@Override
			public ComplexType getInputDefinition() {
				return input;
			}
			@Override
			public ComplexType getOutputDefinition() {
				return output;
			}
			@Override
			public ServiceInterface getParent() {
				return null;
			}
		};
	}

	@Override
	public ServiceInstance newInstance() {
		return new JDBCPoolServiceInstance(this);
	}

	@Override
	public Set<String> getReferences() {
		return null;
	}

	@Override
	public String getTunnelHost() {
		try {
			if (getConfig().getJdbcUrl() == null) {
				return null;
			}
			String host = getUri().getHost();
			// defaults to the local host
			return host == null ? "localhost" : host;
		}
		catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}
	
	private String getJdbcUrl() {
		String jdbcUrl = getConfig().getJdbcUrl();
		if (jdbcUrl != null && jdbcUrl.contains("~")) {
			jdbcUrl = jdbcUrl.replace("~", System.getProperty("user.home"));
		}
		return jdbcUrl;
	}

	private URI getUri() throws URISyntaxException {
		URI uri = new URI(URIUtils.encodeURI(getJdbcUrl()));
		if (uri.getScheme().equalsIgnoreCase("jdbc")) {
			uri = new URI(URIUtils.encodeURI(uri.getSchemeSpecificPart()));
		}
		return uri;
	}

	@Override
	public Integer getTunnelPort() {
		try {
			Integer port = getUri().getPort();
			if ((port == null || port < 0) && getConfig().getDialect() != null) {
				try {
					port = getConfig().getDialect().newInstance().getDefaultPort();
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			return port;
		}
		catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<AffixMapping> getAffixes() {
		return getConfig().getAffixes();
	}

	@Override
	public String getContext() {
		return getConfig().getContext();
	}

	@Override
	public JDBCTranslator getTranslator() {
		if (translator == null) {
			translator = getConfig().getTranslationGet() != null || getConfig().getTranslationSet() != null
				? POJOUtils.newProxy(
					JDBCTranslator.class,
					getRepository(), 
					SystemPrincipal.ROOT,
					getConfig().getTranslationGet(), getConfig().getTranslationSet(), getConfig().getTranslationGetBinding(), getConfig().getTranslationMapLanguage()
				) : null;
		}
		return translator;
	}

	@Override
	public String getDefaultLanguage() {
		return getConfig().getDefaultLanguage();
	}

	@Override
	public List<ExternalDependency> getExternalDependencies() {
		if (getConfig().getPoolProxy() != null) {
			return getConfig().getPoolProxy().getExternalDependencies();
		}
		else {
			ExternalDependencyImpl dependency = new ExternalDependencyImpl();
			dependency.setArtifactId(getId());
			try {
				dependency.setEndpoint(getUri());
			}
			catch (URISyntaxException e) {
				// ignore
			}
			dependency.setType("database");
			return Arrays.asList(dependency);
		}
	}

	// we want this accessible early so database operations can be done
	@Override
	public StartPhase getPhase() {
		return StartPhase.FIRST;
	}

	@Override
	public Artifact getProxied() {
		return getConfig().getPoolProxy();
	}
	
}
