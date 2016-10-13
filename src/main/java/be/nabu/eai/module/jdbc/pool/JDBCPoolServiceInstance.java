package be.nabu.eai.module.jdbc.pool;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.Transactionable;
import be.nabu.libs.services.jdbc.JDBCService;
import be.nabu.libs.services.jdbc.JDBCServiceInstance.ConnectionTransactionable;
import be.nabu.libs.types.DefinedTypeResolverFactory;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedSimpleType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.structure.DefinedStructure;
import be.nabu.libs.types.structure.Structure;
import be.nabu.libs.types.structure.StructureInstance;

public class JDBCPoolServiceInstance implements ServiceInstance {

	private JDBCPoolArtifact pool;

	public JDBCPoolServiceInstance(JDBCPoolArtifact pool) {
		this.pool = pool;
	}
	
	@Override
	public Service getDefinition() {
		return pool;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public ComplexContent execute(ExecutionContext executionContext, ComplexContent content) throws ServiceException {
		Connection connection = null;
		try {
			if (!pool.isAutoCommit()) {
				String transactionId = content == null ? null : (String) content.get(JDBCService.TRANSACTION);
				// if there is no open transaction, create one
				Transactionable transactionable = executionContext.getTransactionContext().get(transactionId, pool.getId());
				if (transactionable == null) {
					connection = pool.getDataSource().getConnection();
					executionContext.getTransactionContext().add(transactionId, new ConnectionTransactionable(pool.getId(), connection));
				}
				else {
					connection = ((ConnectionTransactionable) transactionable).getConnection();
				}
			}
			// it's autocommitted, just start a new connection
			else {
				connection = pool.getDataSource().getConnection();
			}
			
			String sql = content == null ? null : (String) content.get("sql");
			if (sql == null) {
				throw new ServiceException("JDBCPOOL-0", "No sql found");
			}

			// add offset & limit
			Integer offset = content == null ? null : (Integer) content.get(JDBCService.OFFSET);
			Integer limit = content == null ? null : (Integer) content.get(JDBCService.LIMIT);
			boolean nativeLimit = false;
			if (offset != null || limit != null) {
				if (pool.getDialect() != null) {
					String limitedSql = pool.getDialect().limit(sql, offset, limit);
					if (limitedSql != null) {
						sql = limitedSql;
						nativeLimit = true;
					}
				}
			}
			
			ComplexContent output = pool.getServiceInterface().getOutputDefinition().newInstance();
			Statement statement = connection.createStatement();
			if (sql.trim().toLowerCase().startsWith("select")) {
				if (!nativeLimit && limit != null) {
					statement.setMaxRows(offset != null ? offset + limit : limit);
				}
				ResultSet executeQuery = statement.executeQuery(sql);
				ComplexType type = content.get("resultType") == null ? null : (ComplexType) DefinedTypeResolverFactory.getInstance().getResolver().resolve((String) content.get("resultType"));
				if (type == null) {
					Structure structure = new Structure();
					structure.setName("result");
					ResultSetMetaData metaData = executeQuery.getMetaData();
					for (int i = 1; i <= metaData.getColumnCount(); i++) {
						String columnClassName = metaData.getColumnClassName(i);
						try {
							Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(columnClassName);
							if (java.util.Date.class.isAssignableFrom(clazz)) {
								clazz = java.util.Date.class;
							}
							DefinedSimpleType<?> wrap = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(clazz);
							if (wrap == null) {
								throw new RuntimeException("No simple type found for: " + clazz);
							}
							structure.add(new SimpleElementImpl(metaData.getColumnLabel(i).replaceAll("[^\\w]+", ""), wrap, structure, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
							type = structure;
						}
						catch (Exception e) {
							throw new RuntimeException("Unknown result set class: " + columnClassName, e);
						}
					}
				}
				DefinedStructure root = new DefinedStructure();
				root.setId("$generated");
				root.setName("root");
				root.add(new ComplexElementImpl(JDBCService.RESULTS, type, root, new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0),  new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
				List results = new ArrayList();
				int recordCounter = 0;
				while (executeQuery.next()) {
					// if we don't have a native (dialect) limit but we did set an offset, do it programmatically
					if (!nativeLimit && offset != null) {
						recordCounter++;
						if (recordCounter < offset) {
							continue;
						}
					}
					ComplexContent result = type.newInstance();
					int column = 1;
					for (Element<?> child : TypeUtils.getAllChildren(type)) {
						result.set(child.getName(), executeQuery.getObject(column++));
					}
					results.add(result);
				}
				StructureInstance rootInstance = root.newInstance();
				rootInstance.set(JDBCService.RESULTS, results);
				output.set(JDBCService.RESULTS, rootInstance);
			}
			else {
				output.set(JDBCService.RESULTS, statement.executeUpdate(sql));
			}
			return output;
		}	
		catch (SQLException e) {
			while (e.getNextException() != null) {
				e = e.getNextException();
			}
			throw new ServiceException(e);
		}
		finally {
			// if the pool is set to auto commit and a connection was created, close it so it is released to the pool again
			if (pool.isAutoCommit() && connection != null) {
				try {
					connection.close();
				}
				catch (SQLException e) {
					// do nothing
				}
			}
		}
	}

}
