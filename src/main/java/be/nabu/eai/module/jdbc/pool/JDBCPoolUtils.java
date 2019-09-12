package be.nabu.eai.module.jdbc.pool;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.api.NamingConvention;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.jdbc.api.SQLDialect;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.DefinedSimpleType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.CommentProperty;
import be.nabu.libs.types.properties.GeneratedProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.resultset.ResultSetCollectionHandler;
import be.nabu.libs.types.structure.Structure;
import nabu.protocols.jdbc.pool.Services.DumpMapping;

public class JDBCPoolUtils {
	
	private static boolean isDependent(String table, String on, Map<String, List<String>> references, Map<String, List<String>> dependencies, List<String> checked) {
		checked.add(table);
		if (references.get(table) == null) {
			return false;
		}
		if (references.get(table).contains(on)) {
			return true;
		}
		for (String reference : references.get(table)) {
			if (!checked.contains(reference)) {
				if (isDependent(reference, on, references, dependencies, checked)) {
					return true;
				}
			}
		}
		return false;
	}
	
	public static void restore(Connection connection, Reader reader, Writer output) throws IOException, SQLException {
		char [] buffer = new char[1024];
		int read = -1;
		Logger logger = LoggerFactory.getLogger("jdbc.backup.restore");
		StringBuilder builder = new StringBuilder();
		Date date = new Date();
		int successful = 0, failed = 0;
		while ((read = reader.read(buffer)) > 0) {
			String string = new String(buffer, 0, read);
			while (string != null && !string.isEmpty()) {
				int indexOf = string.indexOf(';');
				if (indexOf >= 0) {
					builder.append(string.substring(0, indexOf + 1));
					string = string.substring(indexOf + 1);
				}
				else {
					builder.append(string);
					string = null;
				}
				// if the builder is a complete sql statement, execute it
				String sql = builder.toString();
				// remove comments
				sql = sql.replaceAll("(?m)^--.*$", "").trim();
				// if we have an uneven amount of quotes, we are in a string and it is not complete
				if (sql.endsWith(";") && (sql.length() - sql.replace("'", "").length()) % 2 == 0) {
					// reset the stringbuilder
					builder = new StringBuilder();
					// we have a complete statement, execute it
					Statement statement = connection.createStatement();
					try {
						// we don't want to include the last ";"
						statement.execute(sql.substring(0, sql.length() - 1));
						successful++;
					}
					catch (Exception e) {
						if (e.getMessage() != null) {
							output.write("-- " + e.getMessage().trim() + "\n");
						}
						output.write(sql.trim() + "\n\n");
						logger.warn("Could not process statement: " + sql, e);
						failed++;
					}
					finally {
						try {
							statement.close();
						}
						catch(Exception e) {
							// ignore...
						}
					}
					try {
						String message = "";
						connection.commit();
						if (sql.trim().toLowerCase().startsWith("insert")) {
							message = sql.trim().toLowerCase().split("\\(")[0].trim();
						}
						else if (sql.trim().toLowerCase().startsWith("create")) {
							message = sql.trim().toLowerCase().split("\\(")[0].trim();
						}
						else {
							message = sql.trim();
						}
						logger.info("Status [" + successful + " successful + " + failed + " failed = " + (successful + failed) + "]): " + message + " [" + ((new Date().getTime() - date.getTime()) / 1000) + "s]");
					}
					catch (Exception e) {
						logger.error("Could not commit", e);
					}
				}
			}
		}
		logger.info("Restored " + successful + " successfully, failed to restore " + failed);
	}
	
	@SuppressWarnings({ "unchecked", "unused" })
	public static void dump(Connection connection, String catalogue, String schema, String tableNamePattern, Writer output, SQLDialect dialect, boolean includeDdl, boolean includeDml, String tableBlacklistRegex, String tableWhitelistRegex, List<DumpMapping> mappings) throws SQLException, IOException {
		ResultSet tables = connection.getMetaData().getTables(catalogue, schema, tableNamePattern, null);
		try {
			Map<String, Structure> tableMap = new HashMap<String, Structure>();
			Map<String, List<String>> references = new HashMap<String, List<String>>();
			Map<String, List<String>> dependencies = new HashMap<String, List<String>>();
			while (tables.next()) {
				String tableCatalogue = tables.getString("TABLE_CAT");
				String tableSchema = tables.getString("TABLE_SCHEM");
				String tableName = tables.getString("TABLE_NAME");
				String tableType = tables.getString("TABLE_TYPE");
				try {
					if ("TABLE".equalsIgnoreCase(tableType)) {
						if (tableBlacklistRegex != null && tableName.matches(tableBlacklistRegex)) {
							continue;
						}
						if (tableWhitelistRegex != null && !tableName.matches(tableWhitelistRegex)) {
							continue;
						}
						Structure table = toType(connection, tableCatalogue, tableSchema, tableName);
						tableMap.put(tableName, table);
						ResultSet importedKeys = connection.getMetaData().getImportedKeys(tableCatalogue, tableSchema, tableName);
						try {
							List<String> tableReferences = new ArrayList<String>();
							while (importedKeys.next()) {
								String importedCatalogue = importedKeys.getString("PKTABLE_CAT");
								String importedSchema = importedKeys.getString("PKTABLE_SCHEM");
								String importedTable = importedKeys.getString("PKTABLE_NAME");
								tableReferences.add(importedTable);
							}
							references.put(tableName, tableReferences);
						}
						finally {
							importedKeys.close();
						}
						ResultSet exportedKeys = connection.getMetaData().getExportedKeys(tableCatalogue, tableSchema, tableName);
						try {
							List<String> tableDependencies = new ArrayList<String>();
							while (exportedKeys.next()) {
								String importedCatalogue = exportedKeys.getString("PKTABLE_CAT");
								String importedSchema = exportedKeys.getString("PKTABLE_SCHEM");
								String importedTable = exportedKeys.getString("PKTABLE_NAME");
								tableDependencies.add(importedTable);
							}
							dependencies.put(tableName, tableDependencies);
						}
						finally {
							exportedKeys.close();
						}
					}
				}
				catch (Exception e) {
					output.write("-- Could not process table '" + tableName + "': " + e.getMessage().replaceAll("[\\s]+", " ") + "\n");
				}
			}
			// we need to sort the tables according to their dependencies
			List<String> tableNames = new ArrayList<String>(tableMap.keySet());
			boolean changed = true;
			sorting: while(changed) {
				changed = false;
				for (int i = 0; i < tableNames.size(); i++) {
					for (int j = 0; j < tableNames.size(); j++) {
						if (i == j) {
							continue;
						}
						String o1 = tableNames.get(i);
						String o2 = tableNames.get(j);
						// if i is before j but requires it in references, try to switch 
						boolean iDependsOnJ = isDependent(o1, o2, references, dependencies, new ArrayList<String>());
						boolean jDependsOnI = isDependent(o2, o1, references, dependencies, new ArrayList<String>());
						
						if (iDependsOnJ && jDependsOnI) {
							output.write("-- Circular reference detected between " + o1 + " and " + o2 + "\n");		
						}
						else if ((iDependsOnJ && i < j) || (jDependsOnI && j < i)) {
							String iName = tableNames.get(i);
							tableNames.set(i, tableNames.get(j));
							tableNames.set(j, iName);
							changed = true;
							continue sorting;
						}
					}
				}
			}
			Map<String, String> tableMappings = new HashMap<String, String>();
			if (mappings != null) {
				for (DumpMapping mapping : mappings) {
					tableMappings.put(mapping.getTable(), mapping.getClause());
				}
			}
			
			// include a drop
			if (includeDdl) {
				Collections.reverse(tableNames);
				output.write("-- dropping tables\n");
				for (String tableName: tableNames) {
					output.write("drop table " + tableName + ";\n");
				}
				Collections.reverse(tableNames);

				output.write("\n-- creating tables\n");
				for (String tableName : tableNames) {
					Structure table = tableMap.get(tableName);
					output.write(dialect.buildCreateSQL(table, true) + "\n");
				}
			}
			
			if (includeDml) {
				if (includeDdl) {
					output.write("\n");
				}
				output.write("-- inserting all data");
				for (String tableName : tableNames) {
					Structure table = tableMap.get(tableName);
					Statement statement = connection.createStatement();
					try {
						String sql = "select * from " + tableName;
						if (tableMappings.containsKey(tableName)) {
							sql += " where " + tableMappings.get(tableName);
						}
						else {
							if (table.get("created") != null) {
								Element<?> element = table.get("created");
								Class<?> instanceClass = ((SimpleType<?>) element.getType()).getInstanceClass();
								if (Date.class.isAssignableFrom(instanceClass)) {
									sql += " order by created asc";
								}
							}
						}
						output.write("\n-- inserting data for table " + tableName + "\n");						
						statement.execute(sql);
						ResultSet resultSet = statement.getResultSet();
						try {
							for (ComplexContent content : (Iterable<ComplexContent>) new ResultSetCollectionHandler(table).getAsIterable(resultSet)) {
								output.write(dialect.buildInsertSQL(content, true).replaceAll("[\\s]+", " ") + "\n");
							}
						}
						finally {
							resultSet.close();
						}
					}
					catch (Exception e) {
						output.write("-- Could not load DML for table '" + tableName + "': " + (e.getMessage() == null ? "No Message" : e.getMessage().replaceAll("[\\s]+", " ")) + "\n");
					}
					finally {
						statement.close();
					}
				}
				connection.commit();
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			output.write("-- Got 99 problems and this one of 'em: " + (e.getMessage() == null ? "No Message" : e.getMessage().replaceAll("[\\s]+", " ")) + "\n");
		}
		finally {
			tables.close();
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Structure toType(Connection connection, String catalogue, String schema, String tableName) throws SQLException {
		Structure structure = new Structure();
		structure.setName(NamingConvention.LOWER_CAMEL_CASE.apply(tableName));
		
		ResultSet columns = connection.getMetaData().getColumns(catalogue, schema, tableName, null);
		try {
			while (columns.next()) {
				String name = columns.getString("COLUMN_NAME");
				Class<?> classFor = getClassFor(columns.getInt("DATA_TYPE"));
				DefinedSimpleType<?> simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(classFor);
				
				List<Value<?>> values = new ArrayList<Value<?>>();
				int nullable = columns.getInt("NULLABLE");
				if (nullable == DatabaseMetaData.columnNullable) {
					values.add(new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0));
				}
				try {
					String generated = columns.getString("IS_GENERATEDCOLUMN");
					if (generated != null && generated.equalsIgnoreCase("YES")) {
						values.add(new ValueImpl<Boolean>(GeneratedProperty.getInstance(), true));
					}
				}
				catch (Exception e) {
					// might not exist
				}
				try {
					String comment = columns.getString("REMARKS");
					if (comment != null && !comment.trim().isEmpty()) {
						values.add(new ValueImpl<String>(CommentProperty.getInstance(), comment));
					}
				}
				catch (Exception e) {
					// might not exist
				}
				structure.add(new SimpleElementImpl(name, simpleType, structure, values.toArray(new Value[0])));
			}
		}
		finally {
			columns.close();
		}
		return structure;
	}
	
	public static Class<?> getClassFor(Integer type) {
		switch(type) {
			case Types.VARCHAR:
				return String.class;
			case Types.VARBINARY:
				return byte[].class;
			case Types.INTEGER:
				return Integer.class;
			case Types.BIGINT:
				return Long.class;
			case Types.DOUBLE:
			case Types.NUMERIC:
				return Double.class;
			case Types.FLOAT:
				return Float.class;
			case Types.SMALLINT:
				return Short.class;
			case Types.BOOLEAN:
			case Types.BIT:
				return Boolean.class;
			case Types.TIMESTAMP:
			case Types.DATE:
			case Types.TIME:
			case Types.TIME_WITH_TIMEZONE:
				return Date.class;
			default:
				return String.class;
		}
	}
}
