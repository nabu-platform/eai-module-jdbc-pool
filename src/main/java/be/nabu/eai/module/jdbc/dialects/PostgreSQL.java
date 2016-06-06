package be.nabu.eai.module.jdbc.dialects;

import java.util.Date;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.services.jdbc.api.SQLDialect;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.properties.FormatProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.utils.DateUtils;
import be.nabu.libs.types.utils.DateUtils.Granularity;

public class PostgreSQL implements SQLDialect {

	private Logger logger = LoggerFactory.getLogger(getClass());
	
	@Override
	public String getSQLName(Class<?> instanceClass) {
		if (UUID.class.isAssignableFrom(instanceClass)) {
			return "uuid";
		}
		else {
			return SQLDialect.super.getSQLName(instanceClass);
		}
	}
	
	@Override
	public String rewrite(String sql, ComplexType input, ComplexType output) {
		Pattern pattern = Pattern.compile("(?<!:)[:$][\\w]+(?!::)\\b");
		Matcher matcher = pattern.matcher(sql);
		StringBuilder result = new StringBuilder();
		int last = 0;
		while (matcher.find()) {
			if (matcher.end() > last) {
				result.append(sql.substring(last, matcher.end()));
			}
			String name = matcher.group().substring(1);
			Element<?> element = input.get(name);
			if (element.getType() instanceof SimpleType) {
				SimpleType<?> type = (SimpleType<?>) element.getType();
				String postgreType = null;
				if (UUID.class.isAssignableFrom(type.getInstanceClass())) {
					postgreType = "uuid";
				}
				else if (Date.class.isAssignableFrom(type.getInstanceClass())) {
					String format = ValueUtils.getValue(FormatProperty.getInstance(), element.getProperties());
					Granularity granularity = format == null ? Granularity.TIMESTAMP : DateUtils.getGranularity(format);
					switch(granularity) {
						case DATE: postgreType = "date"; break;
						case TIME: postgreType = "time"; break;
						default: postgreType = "timestamp";
					}
				}
				else if (Boolean.class.isAssignableFrom(type.getInstanceClass())) {
					postgreType = "boolean";
				}
				if (postgreType != null) {
					result.append("::").append(postgreType);
					boolean isList = element.getType().isList(element.getProperties());
					if (isList) {
						result.append("[]");
					}
				}
			}
			last = matcher.end();
		}
		if (last < sql.length()) {
			result.append(sql.substring(last, sql.length()));
		}
		logger.trace("Rewrote '{}'\n{}", new Object[] { sql, result });
		return result.toString();
	}

	@Override
	public String limit(String sql, Integer offset, Integer limit) {
		if (offset != null) {
			sql = sql + " OFFSET " + offset;
		}
		if (limit != null) {
			sql = sql + " LIMIT " + limit;
		}
		return sql;
	}

	@Override
	public String buildCreateSQL(ComplexType type) {
		StringBuilder builder = new StringBuilder();
		builder.append("create table " + EAIRepositoryUtils.uncamelify(type.getName()) + " (\n");
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
		if (String.class.isAssignableFrom(instanceClass) || char[].class.isAssignableFrom(instanceClass)) {
			return "varchar";
		}
		else if (byte[].class.isAssignableFrom(instanceClass)) {
			return "varbinary";
		}
		else if (Integer.class.isAssignableFrom(instanceClass)) {
			return "integer";
		}
		else if (Long.class.isAssignableFrom(instanceClass)) {
			return "bigint";
		}
		else if (Double.class.isAssignableFrom(instanceClass)) {
			return "decimal";
		}
		else if (Float.class.isAssignableFrom(instanceClass)) {
			return "decimal";
		}
		else if (Short.class.isAssignableFrom(instanceClass)) {
			return "smallint";
		}
		else if (Boolean.class.isAssignableFrom(instanceClass)) {
			return "boolean";
		}
		else if (UUID.class.isAssignableFrom(instanceClass)) {
			return "uuid";
		}
		else if (Date.class.isAssignableFrom(instanceClass)) {
			return "timestamp";
		}
		else {
			return null;
		}
	}
}
