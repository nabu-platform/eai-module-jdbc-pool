package nabu.protocols.jdbc.pool.types;

import java.util.List;

public class TableDescription {
	private String schema, name, catalogus;
	private List<TableKeyDescription> tableReferences, tableDependencies;
	private List<TableColumnDescription> columnDescriptions;

	public String getSchema() {
		return schema;
	}
	public void setSchema(String schema) {
		this.schema = schema == null ? null : schema.toLowerCase();
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name == null ? null : name.toLowerCase();
	}
	public String getCatalogus() {
		return catalogus;
	}
	public void setCatalogus(String catalogus) {
		this.catalogus = catalogus == null ? null : catalogus.toLowerCase();
	}
	public List<TableKeyDescription> getTableReferences() {
		return tableReferences;
	}
	public void setTableReferences(List<TableKeyDescription> tableReferences) {
		this.tableReferences = tableReferences;
	}
	public List<TableKeyDescription> getTableDependencies() {
		return tableDependencies;
	}
	public void setTableDependencies(List<TableKeyDescription> tableDependencies) {
		this.tableDependencies = tableDependencies;
	}
	public List<TableColumnDescription> getColumnDescriptions() {
		return columnDescriptions;
	}
	public void setColumnDescriptions(List<TableColumnDescription> columnDescriptions) {
		this.columnDescriptions = columnDescriptions;
	}
}
