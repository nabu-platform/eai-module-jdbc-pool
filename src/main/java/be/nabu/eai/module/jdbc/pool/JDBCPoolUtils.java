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
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.api.NamingConvention;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.module.types.structure.StructureManager;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.jdbc.JDBCUtils;
import be.nabu.libs.services.jdbc.api.SQLDialect;
import be.nabu.libs.types.DefinedTypeResolverFactory;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedSimpleType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.CollectionNameProperty;
import be.nabu.libs.types.properties.CommentProperty;
import be.nabu.libs.types.properties.ForeignKeyProperty;
import be.nabu.libs.types.properties.FormatProperty;
import be.nabu.libs.types.properties.GeneratedProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.properties.PrimaryKeyProperty;
import be.nabu.libs.types.properties.RestrictProperty;
import be.nabu.libs.types.properties.UniqueProperty;
import be.nabu.libs.types.resultset.ResultSetCollectionHandler;
import be.nabu.libs.types.structure.DefinedStructure;
import be.nabu.libs.types.structure.Structure;
import nabu.protocols.jdbc.pool.Services.DumpMapping;
import nabu.protocols.jdbc.pool.types.TableColumnDescription;
import nabu.protocols.jdbc.pool.types.TableDescription;
import nabu.protocols.jdbc.pool.types.TableKeyDescription;

public class JDBCPoolUtils {
	
public static final class ForeignKeyComparator implements Comparator<ComplexType> {
		
		private boolean reverse;
		private Map<String, List<String>> foreign = new HashMap<String, List<String>>();

		public ForeignKeyComparator(boolean reverse) {
			this.reverse = reverse;
		}
		
		private List<String> getForeign(ComplexType type) {
			String id = ((DefinedType) type).getId();
			if (!foreign.containsKey(id)) {
				List<String> result = new ArrayList<String>();
				foreign.put(id, result);
				for (Element<?> element : TypeUtils.getAllChildren(type)) {
					Value<String> foreign = element.getProperty(ForeignKeyProperty.getInstance());
					if (foreign != null) {
						String other = foreign.getValue().split(":")[0];
						result.add(other);
						result.addAll(getForeign((ComplexType) DefinedTypeResolverFactory.getInstance().getResolver().resolve(other)));
					}
				}
			}
			return foreign.get(id);
		}
		
		@Override
		public int compare(ComplexType o1, ComplexType o2) {
			int multiplier = reverse ? -1 : 1;
			boolean has1To2 = false;
			boolean has2To1 = false;
			boolean hasForeign1 = false;
			boolean hasForeign2 = false;
			
			List<String> foreign1 = getForeign(o1);
			List<String> foreign2 = getForeign(o2);
			
			has1To2 = foreign1.contains(((DefinedType) o2).getId());
			has2To1 = foreign2.contains(((DefinedType) o1).getId());
			// circular!
			if (has1To2 && has2To1) {
				System.out.println("Circular reference found between managed types: " + o1 + " and " + o2);
				return 0;
			}
			else if (has1To2) {
				return 1 * multiplier;
			}
			else if (has2To1) {
				return -1 * multiplier;
			}
			return 0;
			
//			for (Element<?> element : TypeUtils.getAllChildren(o1)) {
//				Value<String> foreign = element.getProperty(ForeignKeyProperty.getInstance());
//				hasForeign1 |= (foreign != null && !foreign.getValue().equals(((DefinedType) o1).getId()));
//				if (foreign != null && foreign.getValue().split(":")[0].equals(((DefinedType) o2).getId())) {
////					return 1 * multiplier;
//					has1To2 = true;
//				}
//			}
//			for (Element<?> element : TypeUtils.getAllChildren(o2)) {
//				Value<String> foreign = element.getProperty(ForeignKeyProperty.getInstance());
//				hasForeign2 |= (foreign != null && !foreign.getValue().equals(((DefinedType) o2).getId()));
//				if (foreign != null && foreign.getValue().split(":")[0].equals(((DefinedType) o1).getId())) {
////					return -1 * multiplier;
//					has2To1 = true;
//				}
//			}
//			System.out.println("comparing: " + o1 + " and " + o2 + " --> " + has1To2 + ", " + has2To1 + ", " + hasForeign1 + ", " + hasForeign2);
//			// circular!
//			if (has1To2 && has2To1) {
//				System.out.println("Circular reference found between managed types: " + o1 + " and " + o2);
//				return 0;
//			}
//			else if (has1To2) {
//				return 1 * multiplier;
//			}
//			else if (has2To1) {
//				return -1 * multiplier;
//			}
//			// if there are no foreign keys to one another, we order by the existence of _any_ foreign key
//			if (!hasForeign1 && hasForeign2) {
//				return -1 * multiplier;
//			}
//			else if (hasForeign1 && !hasForeign2) {
//				return 1 * multiplier;
//			}
//			else {
//				return 0;
//			}
		}
	}
	
	// this does not pick up circular dependencies
	public static <T> void deepSort(List<T> objects, Comparator<T> comparator) {
		// do an initial sort
		Collections.sort(objects, comparator);
		boolean changed = true;
		changing: while(changed) {
			changed = false;
			for (int i = 0; i < objects.size() - 1; i++) {
				for (int j = i + 1; j < objects.size(); j++) {
					int compare = comparator.compare(objects.get(i), objects.get(j));
					if (compare > 0) {
						T tmp = objects.get(j);
						objects.set(j, objects.get(i));
						objects.set(i, tmp);
						changed = true;
						continue changing;
					}
				}
			}
		}
	}
	
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
	
	public static List<TableDescription> describeTables(String catalogus, String schema, String tableNamePattern, DataSource resolve, boolean limitToSchema) throws SQLException {
		List<TableDescription> result = new ArrayList<TableDescription>();
		Connection connection = resolve.getConnection();
		try {
			ResultSet tables = connection.getMetaData().getTables(catalogus, limitToSchema ? connection.getSchema() : schema, tableNamePattern, null);
			while (tables.next()) {
				String tableCatalogue = tables.getString("TABLE_CAT");
				String tableSchema = tables.getString("TABLE_SCHEM");
				String tableName = tables.getString("TABLE_NAME");
				String tableType = tables.getString("TABLE_TYPE");
				if ("TABLE".equalsIgnoreCase(tableType)) {
					TableDescription description = new TableDescription();
					description.setCatalogus(tableCatalogue);
					description.setSchema(tableSchema);
					description.setName(tableName);
					
					ResultSet importedKeys = connection.getMetaData().getImportedKeys(tableCatalogue, tableSchema, tableName);
					try {
						List<TableKeyDescription> tableReferences = new ArrayList<TableKeyDescription>();
						while (importedKeys.next()) {
							TableKeyDescription reference = new TableKeyDescription();
							reference.setCatalogus(importedKeys.getString("PKTABLE_CAT"));
							reference.setSchema(importedKeys.getString("PKTABLE_SCHEM"));
							reference.setName(importedKeys.getString("PKTABLE_NAME"));
							reference.setRemoteField(importedKeys.getString("PKCOLUMN_NAME"));
							reference.setLocalField(importedKeys.getString("FKCOLUMN_NAME"));
							tableReferences.add(reference);
						}
						description.setTableReferences(tableReferences);
					}
					finally {
						importedKeys.close();
					}
					
					ResultSet exportedKeys = connection.getMetaData().getExportedKeys(tableCatalogue, tableSchema, tableName);
					try {
						List<TableKeyDescription> tableDependencies = new ArrayList<TableKeyDescription>();
						while (exportedKeys.next()) {
							TableKeyDescription reference = new TableKeyDescription();
							reference.setCatalogus(exportedKeys.getString("FKTABLE_CAT"));
							reference.setSchema(exportedKeys.getString("FKTABLE_SCHEM"));
							reference.setName(exportedKeys.getString("FKTABLE_NAME"));
							reference.setRemoteField(exportedKeys.getString("FKCOLUMN_NAME"));
							reference.setLocalField(exportedKeys.getString("PKCOLUMN_NAME"));
							tableDependencies.add(reference);
						}
						description.setTableDependencies(tableDependencies);
					}
					finally {
						exportedKeys.close();
					}
					
					List<TableColumnDescription> columnDescriptions = new ArrayList<TableColumnDescription>();
					ResultSet columns = connection.getMetaData().getColumns(tableCatalogue, tableSchema, tableName, null);
					try {
						while (columns.next()) {
							TableColumnDescription column = new TableColumnDescription();
							column.setName(columns.getString("COLUMN_NAME"));
							column.setDatabaseType(columns.getString("TYPE_NAME"));
							Class<?> classFor = getClassFor(columns.getInt("DATA_TYPE"));
							DefinedSimpleType<?> simpleType = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(classFor);
							column.setType(classFor.getName());
							column.setTypeName(simpleType.getName());
							
							// we need an additional format
							if (Date.class.isAssignableFrom(classFor)) {
								switch (columns.getInt("DATA_TYPE")) {
									case Types.DATE:
										column.setFormat("date");
									break;
									case Types.TIME:
										column.setFormat("time");
									break;
								}
							}
							
							int nullable = columns.getInt("NULLABLE");
							if (nullable == DatabaseMetaData.columnNullable) {
								column.setOptional(true);
							}
							try {
								String generated = columns.getString("IS_GENERATEDCOLUMN");
								if (generated != null && generated.equalsIgnoreCase("YES")) {
									column.setGenerated(true);
								}
							}
							catch (Exception e) {
								// might not exist
							}
							try {
								String comment = columns.getString("REMARKS");
								if (comment != null && !comment.trim().isEmpty()) {
									column.setDescription(comment);
								}
							}
							catch (Exception e) {
								// might not exist
							}
							columnDescriptions.add(column);
						}
						description.setColumnDescriptions(columnDescriptions);
					}
					finally {
						columns.close();
					}
					
					ResultSet indexInfo = connection.getMetaData().getIndexInfo(tableCatalogue, tableSchema, tableName, true, false);
					try {
						// indexes can be unique combinations of fields
						// for example if we have a unique combination of 3 fields, it will return 3 separate records with the same index name and a different column name
						// we only want to mark fields that are unique of themselves
						Map<String, List<String>> indexes = new HashMap<String, List<String>>();
						while (indexInfo.next()) {
							boolean nonUnique = indexInfo.getBoolean("NON_UNIQUE");
							String columnName = indexInfo.getString("COLUMN_NAME");
							String indexName = indexInfo.getString("INDEX_NAME");
							if (indexName != null && !nonUnique) {
								if (!indexes.containsKey(indexName)) {
									indexes.put(indexName, new ArrayList<String>());
								}
								indexes.get(indexName).add(columnName);
							}
						}
						for (Map.Entry<String, List<String>> entry : indexes.entrySet()) {
							if (entry.getValue().size() == 1) {
								for (TableColumnDescription column : description.getColumnDescriptions()) {
									if (column.getName().equals(entry.getValue().get(0))) {
										column.setUnique(true);
									}
								}
							}
						}
					}
					finally {
						indexInfo.close();
					}
					
					ResultSet primaryKeys = connection.getMetaData().getPrimaryKeys(tableCatalogue, tableSchema, tableName);
					try {
						while (primaryKeys.next()) {
							String columName = primaryKeys.getString("COLUMN_NAME");
							for (TableColumnDescription column : description.getColumnDescriptions()) {
								if (column.getName().equals(columName)) {
									column.setUnique(true);
									column.setPrimary(true);
								}
							}
						}
					}
					finally {
						primaryKeys.close();
					}
					result.add(description);
				}
			}
		}
		finally {
			connection.close();
		}
		return result;
	}
	
	private static <T> boolean set(Element<?> element, Property<T> property, T value) {
		T asIs = ValueUtils.getValue(property, element.getProperties());
		if (asIs == null || !asIs.equals(value)) {
			element.setProperty(new ValueImpl<T>(property, value));
			return true;
		}
		return false;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static boolean toType(Structure into, TableDescription description) {
		boolean changed = false;
		if (description.getColumnDescriptions() != null) {
//			into.setName(NamingConvention.LOWER_CAMEL_CASE.apply(description.getName()));
			String collectionName = ValueUtils.getValue(CollectionNameProperty.getInstance(), into.getProperties());
			if (collectionName == null || !collectionName.equals(description.getName())) {
				changed = true;
				into.setProperty(new ValueImpl<String>(CollectionNameProperty.getInstance(), description.getName()));
			}
			List<String> existingElements = new ArrayList<String>();
			for (Element<?> element : JDBCUtils.getFieldsInTable(into)) {
				existingElements.add(element.getName());
			}
			for (TableColumnDescription column : description.getColumnDescriptions()) {
				try {
					String columnName = NamingConvention.LOWER_CAMEL_CASE.apply(column.getName());
					existingElements.remove(columnName);
					Element<?> element = into.get(columnName);
					if (element == null) {
						Class<?> simpleClass;
						if (column.getDatabaseType() != null && column.getDatabaseType().toLowerCase().contains("uuid")) {
							simpleClass = UUID.class;
						}
						else if (column.getType() != null) {
							simpleClass = Thread.currentThread().getContextClassLoader().loadClass(column.getType());
						}
						else {
							throw new IllegalArgumentException("Could not find correct class for column: " + column.getName());
						}
						element = new SimpleElementImpl(
							columnName,
							SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(simpleClass), 
							into
						);
						into.add(element);
						changed = true;
					}
					changed |= set(element, MinOccursProperty.getInstance(), column.isOptional() ? 0 : 1);
					changed |= set(element, GeneratedProperty.getInstance(), column.isGenerated());
					changed |= set(element, PrimaryKeyProperty.getInstance(), column.isPrimary());
					changed |= set(element, UniqueProperty.getInstance(), column.isUnique());
					if (column.getFormat() != null) {
						if (Date.class.isAssignableFrom(((SimpleType<?>) element.getType()).getInstanceClass())) {
							changed |= set(element, FormatProperty.getInstance(), column.getFormat());
						}
					}
					if (column.getDescription() != null && !column.getDescription().trim().isEmpty()) {
						String comment = ValueUtils.getValue(CommentProperty.getInstance(), element.getProperties());
						// local comments are not override
						if (comment == null || comment.trim().isEmpty()) {
							changed |= set(element, CommentProperty.getInstance(), column.getDescription());
						}
					}
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			// remove any superfluous
			for (String existingElement : existingElements) {
				// try to remove it
				into.remove(into.get(existingElement));
				// if it was inherited, restrict it
				if (into.get(existingElement) != null) {
					String restricted = ValueUtils.getValue(RestrictProperty.getInstance(), into.getProperties());
					if (restricted == null || restricted.trim().isEmpty()) {
						restricted = existingElement;
					}
					else {
						restricted += "," + existingElement;
					}
					into.setProperty(new ValueImpl<String>(RestrictProperty.getInstance(), restricted));
					changed = true;
				}
			}
		}
		return changed;
	}
	
	// relink the tables with foreign keys
	public static void relink(JDBCPoolArtifact artifact, List<TableDescription> descriptions) {
		Map<String, TableDescription> map = new HashMap<String, TableDescription>();
		for (TableDescription description : descriptions) {
			map.put(description.getName(), description);
		}
		Map<String, Structure> types = new HashMap<String, Structure>();
		if (artifact.getConfig().getManagedTypes() != null) {
			for (DefinedType type : artifact.getConfig().getManagedTypes()) {
				if (type instanceof Structure) {
					String collectionName = ValueUtils.getValue(CollectionNameProperty.getInstance(), ((Structure) type).getProperties());
					if (collectionName != null && map.containsKey(collectionName)) {
						types.put(collectionName, (Structure) type);
					}
				}
			}
			for (Map.Entry<String, Structure> entry : types.entrySet()) {
				boolean changed = false;
				TableDescription description = map.get(entry.getKey());
				if (description.getTableReferences() != null) {
					for (TableKeyDescription reference : description.getTableReferences()) {
						String localField = NamingConvention.LOWER_CAMEL_CASE.apply(reference.getLocalField());
						Element<?> element = entry.getValue().get(localField);
						if (element != null) {
							// the structure we are referencing
							Structure structure = types.get(reference.getName());
							if (structure instanceof DefinedType) {
								String remoteField = NamingConvention.LOWER_CAMEL_CASE.apply(reference.getRemoteField());
								Element<?> to = structure.get(remoteField);
								if (to != null) {
									String toBe = ((DefinedType) structure).getId() + ":" + to.getName();
									String asIs = ValueUtils.getValue(ForeignKeyProperty.getInstance(), element.getProperties());
									if (asIs == null || !asIs.equals(toBe)) {
										element.setProperty(new ValueImpl<String>(ForeignKeyProperty.getInstance(), toBe));
										changed = true;
									}
								}
							}
						}
					}
				}
				if (changed) {
					try {
						new StructureManager().save((ResourceEntry) artifact.getRepository().getEntry(((DefinedType) entry.getValue()).getId()), (DefinedStructure) entry.getValue());
						MainController.getInstance().getServer().getRemote().reload(((DefinedType) entry.getValue()).getId());
						MainController.getInstance().getCollaborationClient().updated(((DefinedType) entry.getValue()).getId(), "Relinked managed types");
					}
					catch (Exception e) {
						MainController.getInstance().notify(e);
					}
				}
			}
		}
	}
}
