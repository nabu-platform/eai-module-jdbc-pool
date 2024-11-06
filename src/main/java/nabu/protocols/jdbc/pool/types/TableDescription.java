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
