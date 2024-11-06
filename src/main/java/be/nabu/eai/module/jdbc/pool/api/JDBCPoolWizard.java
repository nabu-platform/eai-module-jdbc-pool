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

package be.nabu.eai.module.jdbc.pool.api;

import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.module.jdbc.pool.JDBCPoolArtifact;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.validator.api.ValidationMessage;

public interface JDBCPoolWizard<T> {
	public String getIcon();
	public String getName();
	
	// additional description if relevant
	public default String getDescription() {
		return null;
	}
	
	// the class that will be used
	public Class<T> getWizardClass();
	
	// return an instance of the properties for an existing pool
	public T load(JDBCPoolArtifact pool);
	
	// apply to an existing, for new ones the existing will be null!
	public JDBCPoolArtifact apply(Entry project, RepositoryEntry entry, T properties, boolean isNew, boolean isMain);
	
	public default List<ValidationMessage> validate(T content) {
		return new ArrayList<ValidationMessage>();
	}
}
