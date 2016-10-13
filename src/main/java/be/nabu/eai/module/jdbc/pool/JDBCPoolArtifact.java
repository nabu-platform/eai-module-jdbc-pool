package be.nabu.eai.module.jdbc.pool;

import java.io.IOException;
import java.util.Properties;
import java.util.Set;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.libs.artifacts.api.StartableArtifact;
import be.nabu.libs.artifacts.api.StoppableArtifact;
import be.nabu.libs.metrics.api.MetricInstance;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.services.jdbc.JDBCService;
import be.nabu.libs.services.jdbc.api.DataSourceWithDialectProviderArtifact;
import be.nabu.libs.services.jdbc.api.SQLDialect;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.SimpleTypeWrapper;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.java.BeanResolver;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.structure.Structure;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class JDBCPoolArtifact extends JAXBArtifact<JDBCPoolConfiguration> implements StartableArtifact, StoppableArtifact, DataSourceWithDialectProviderArtifact, DefinedService {

	private HikariDataSource dataSource;
	private Logger logger = LoggerFactory.getLogger(getClass());
	private SQLDialect dialect;
	private MetricInstance metrics;
	private Structure input, output;
	
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
		// closing current dataSource
		if (dataSource != null) {
			dataSource.close();
			dataSource = null;
		}
		try {
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
		catch (IOException e) {
			logger.error("Could not load properties", e);
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public boolean isStarted() {
		return dataSource != null;
	}

	@Override
	public DataSource getDataSource() {
		if (!isStarted()) {
			start();
		}
		return dataSource;
	}

	private Properties getAsProperties() throws IOException {
		Properties properties = new Properties();
		setIfNotNull(properties, "driverClassName", getConfiguration().getDriverClassName());
		setIfNotNull(properties, "jdbcUrl", getConfiguration().getJdbcUrl());
		setIfNotNull(properties, "username", getConfiguration().getUsername());
		setIfNotNull(properties, "password", getConfiguration().getPassword());
		setIfNotNull(properties, "connectionTimeout", getConfiguration().getConnectionTimeout());
		setIfNotNull(properties, "idleTimeout", getConfiguration().getIdleTimeout());
		setIfNotNull(properties, "maximumPoolSize", getConfiguration().getMaximumPoolSize());
		setIfNotNull(properties, "minimumIdle", getConfiguration().getMinimumIdle());
		setIfNotNull(properties, "autoCommit", getConfiguration().getAutoCommit());
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

	@Override
	public boolean isAutoCommit() {
		try {
			// default is true according to https://github.com/brettwooldridge/HikariCP
			return getConfiguration().getAutoCommit() == null || getConfiguration().getAutoCommit();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
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
					input.add(new SimpleElementImpl<Integer>(JDBCService.OFFSET, wrapper.wrap(Integer.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
					input.add(new SimpleElementImpl<Integer>(JDBCService.LIMIT, wrapper.wrap(Integer.class), input, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
//					input.add(new ComplexElementImpl(JDBCService.PARAMETERS, (ComplexType) BeanResolver.getInstance().resolve(KeyValuePair.class), input, 
//						new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0),
//						new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)));
					Structure output = new Structure();
					output.setName("output");
					output.add(new ComplexElementImpl(JDBCService.RESULTS, (ComplexType) BeanResolver.getInstance().resolve(Object.class), output, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
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
}
