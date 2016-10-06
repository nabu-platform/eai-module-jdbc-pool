package be.nabu.eai.module.jdbc.dialects;

import java.net.URI;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.jdbc.api.SQLDialect;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.properties.FormatProperty;
import be.nabu.libs.types.properties.MinOccursProperty;

public class Oracle implements SQLDialect {

	@Override
	public String getSQLName(Class<?> instanceClass) {
		String sqlName = SQLDialect.super.getSQLName(instanceClass);
		return sqlName == null || !sqlName.equals("varchar") ? sqlName : "varchar2";
	}
	
	@Override
	public Class<?> getTargetClass(Class<?> clazz) {
		// make sure we transform booleans to strings true/false"
		return clazz != null && Boolean.class.equals(clazz) ? Integer.class : SQLDialect.super.getTargetClass(clazz);
	}

	@Override
	public Integer getSQLType(Class<?> instanceClass) {
		if (Boolean.class.equals(instanceClass)) {
			return Types.NUMERIC;
		}
		else {
			return SQLDialect.super.getSQLType(instanceClass);
		}
	}

	@Override
	public String rewrite(String sql, ComplexType input, ComplexType output) {
		return sql;
	}

	@Override
	public String limit(String sql, Integer offset, Integer limit) {
		if (offset != null || limit != null) {
			sql = "select * from (" + sql + ") where";
			if (offset != null) {
				sql += " rownum >= " + offset;
			}
			if (limit != null) {
				if (offset != null) {
					limit += offset;
					sql += " and";
				}
				sql += " rownum < " + limit; 
			}
		}
		return sql;
	}

	@Override
	public String buildCreateSQL(ComplexType type) {
		StringBuilder builder = new StringBuilder();
		builder.append("create table " + EAIRepositoryUtils.uncamelify(PostgreSQL.getName(type.getProperties())) + " (\n");
		boolean first = true;
		for (Element<?> child : TypeUtils.getAllChildren(type)) {
			if (first) {
				first = false;
			}
			else {
				builder.append(",\n");
			}
			// if we have a complex type, generate an id field that references it
			if (child.getType() instanceof ComplexType) {
				builder.append("\t" + EAIRepositoryUtils.uncamelify(child.getName()) + "_id uuid");
			}
			else {
				builder.append("\t" + EAIRepositoryUtils.uncamelify(child.getName())).append(" ")
					.append(getPredefinedSQLType(((SimpleType<?>) child.getType()).getInstanceClass()));
			}
			if (child.getName().equals("id")) {
				builder.append(" primary key");
			}
			else {
				Integer value = ValueUtils.getValue(MinOccursProperty.getInstance(), child.getProperties());
				if (value == null || value > 0) {
					builder.append(" not null");
				}
			}
		}
		builder.append("\n);");
		return builder.toString();
	}
	
	public static String getPredefinedSQLType(Class<?> instanceClass) {
		if (String.class.isAssignableFrom(instanceClass) || char[].class.isAssignableFrom(instanceClass) || URI.class.isAssignableFrom(instanceClass) || instanceClass.isEnum()) {
			return "varchar2(255)";
		}
		else if (byte[].class.isAssignableFrom(instanceClass)) {
			return "varbinary";
		}
		else if (Integer.class.isAssignableFrom(instanceClass)) {
			return "number(8, 0)";
		}
		else if (Long.class.isAssignableFrom(instanceClass)) {
			return "number(*, 0)";
		}
		else if (Double.class.isAssignableFrom(instanceClass)) {
			return "number";
		}
		else if (Float.class.isAssignableFrom(instanceClass)) {
			return "number";
		}
		else if (Short.class.isAssignableFrom(instanceClass)) {
			return "number(6, 0)";
		}
		else if (Boolean.class.isAssignableFrom(instanceClass)) {
			return "number(1, 0)";
		}
		else if (UUID.class.isAssignableFrom(instanceClass)) {
			return "varchar2(36)";
		}
		else if (Date.class.isAssignableFrom(instanceClass)) {
			return "timestamp";
		}
		else {
			return null;
		}
	}

	@Override
	public String buildInsertSQL(ComplexContent content) {
		StringBuilder keyBuilder = new StringBuilder();
		StringBuilder valueBuilder = new StringBuilder();
		SimpleDateFormat timestampFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		timestampFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		Date date = new Date();
		for (Element<?> element : TypeUtils.getAllChildren(content.getType())) {
			if (element.getType() instanceof SimpleType) {
				Class<?> instanceClass = ((SimpleType<?>) element.getType()).getInstanceClass();
				if (!keyBuilder.toString().isEmpty()) {
					keyBuilder.append(",\n\t");
					valueBuilder.append(",\n\t");
				}
				keyBuilder.append(EAIRepositoryUtils.uncamelify(element.getName()));
				Object value = content.get(element.getName());
				Integer minOccurs = ValueUtils.getValue(MinOccursProperty.getInstance(), element.getProperties());
				// if there is no value but it is mandatory, try to generate one
				if (value == null && minOccurs != null && minOccurs > 0) {
					if (UUID.class.isAssignableFrom(instanceClass)) {
						value = UUID.randomUUID();
					}
					else if (Date.class.isAssignableFrom(instanceClass)) {
						value = date;
					}
					else if (Number.class.isAssignableFrom(instanceClass)) {
						value = 0;
					}
					else if (Boolean.class.isAssignableFrom(instanceClass)) {
						value = false;
					}
				}
				if (value == null) {
					valueBuilder.append("null");
				}
				else {
					boolean closeQuote = false;
					if (Date.class.isAssignableFrom(instanceClass)) {
						Value<String> property = element.getProperty(FormatProperty.getInstance());
						if (property != null && !property.getValue().equals("timestamp") && !property.getValue().contains("S") && !property.getValue().equals("time")) {
							valueBuilder.append("to_timestamp('").append(timestampFormatter.format(value)).append("', 'yyyy-mm-dd hh24:mi:ss.ff3')");
						}
						else {
							valueBuilder.append("to_date('").append(dateFormatter.format(value)).append("', 'yyyy-mm-dd hh24:mi:ss')");
						}
					}
					else {
						if (URI.class.isAssignableFrom(instanceClass) || String.class.isAssignableFrom(instanceClass) || UUID.class.isAssignableFrom(instanceClass)) {
							valueBuilder.append("'");
							closeQuote = true;
						}
						valueBuilder.append(value.toString());
						if (closeQuote) {
							valueBuilder.append("'");
						}
					}
				}
			}
		}
		return "insert into " + EAIRepositoryUtils.uncamelify(PostgreSQL.getName(content.getType().getProperties())) + " (\n\t" + keyBuilder.toString() + "\n) values (\n\t" + valueBuilder.toString() + "\n);";
	}
}
